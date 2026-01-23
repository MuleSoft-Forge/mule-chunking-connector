package com.mulesoftforge.mule.extension.chunking.internal.streaming;

import org.mule.runtime.api.streaming.exception.StreamingBufferSizeExceededException;
import org.mule.runtime.api.streaming.object.CursorIterator;
import org.mule.runtime.api.streaming.object.CursorIteratorProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Custom CursorIteratorProvider that implements sliding window eviction.
 *
 * <p>Tracks all open cursors and evicts chunks once ALL cursors have
 * advanced past them, keeping memory bounded.</p>
 *
 * <p><b>Thread Safety:</b> Uses synchronized methods for cache operations
 * and atomic variables for state. Multiple cursors can safely operate
 * concurrently.</p>
 *
 * <p><b>Memory Model:</b></p>
 * <ul>
 *   <li>Cache: NavigableMap of position → chunk</li>
 *   <li>Active cursors: ConcurrentHashMap.newKeySet()</li>
 *   <li>Eviction: Triggered on cursor advancement and closure</li>
 * </ul>
 *
 * <p><b>Eviction Strategy:</b></p>
 * <ol>
 *   <li>Find minimum position across all active cursors</li>
 *   <li>Evict all chunks with position &lt; minPosition</li>
 *   <li>These chunks are no longer accessible (seek will fail)</li>
 * </ol>
 *
 * @param <T> the type of elements in the iterator (typically Chunk)
 * @since 0.2.0
 */
public class SlidingWindowCursorIteratorProvider<T> implements CursorIteratorProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(SlidingWindowCursorIteratorProvider.class);

    private final Iterator<T> sourceIterator;
    private final int maxCachedChunks;
    private final NavigableMap<Long, T> cache = new TreeMap<>();
    private final Set<SlidingWindowCursorIterator<T>> activeCursors = ConcurrentHashMap.newKeySet();
    private final Set<SlidingWindowCursorIterator<T>> zombieCursors = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicLong nextFetchPosition = new AtomicLong(0);
    private volatile boolean sourceExhausted = false;

    /**
     * Creates a new sliding window cursor provider.
     *
     * @param sourceIterator the source iterator to read from
     * @param maxCachedChunks maximum chunks to keep cached (soft limit)
     */
    public SlidingWindowCursorIteratorProvider(Iterator<T> sourceIterator, int maxCachedChunks) {
        this.sourceIterator = sourceIterator;
        this.maxCachedChunks = maxCachedChunks;
    }

    @Override
    public CursorIterator openCursor() {
        if (closed.get()) {
            throw new IllegalStateException("Cannot open cursor on closed provider");
        }
        SlidingWindowCursorIterator<T> cursor = new SlidingWindowCursorIterator<>(this);
        activeCursors.add(cursor);

        // WORKAROUND: Detect DataWeave type-checking cursor leak
        boolean isZombie = isDataWeaveTypeCheckingCursor();
        if (isZombie) {
            zombieCursors.add(cursor);
        }

        // Log cursor creation with stack trace to understand who's opening cursors
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Opened cursor #{}, total active cursors: {}, zombie: {}",
                System.identityHashCode(cursor), activeCursors.size(), isZombie);
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Cursor opened from:", new Exception("Stack trace"));
            }
        }

        return cursor;
    }

    @Override
    public void close() {
        closed.set(true);
    }

    @Override
    public void releaseResources() {
        synchronized (cache) {
            cache.clear();
        }
        activeCursors.clear();
    }

    @Override
    public boolean isClosed() {
        return closed.get();
    }

    // Package-private methods for cursor interaction

    /**
     * Gets the chunk at the specified position.
     * If not cached, fetches forward from source until position is reached.
     *
     * @param position the position to retrieve
     * @return the chunk at that position, or null if position is beyond EOF
     */
    synchronized T getChunkAt(long position) {
        // Return from cache if available
        if (cache.containsKey(position)) {
            LOGGER.trace("Cache hit: position={}", position);
            return cache.get(position);
        }

        LOGGER.debug("Cache miss: position={}, fetching from source (current cache size: {})",
            position, cache.size());

        // Fetch forward until we have the requested position
        int fetchedCount = 0;
        while (nextFetchPosition.get() <= position && !sourceExhausted) {
            // CRITICAL: Check if we need to make room BEFORE fetching
            if (cache.size() >= maxCachedChunks) {
                // Try to evict before fetching
                evictBeforeFetch();

                // If still at limit after eviction, throw error
                if (cache.size() >= maxCachedChunks) {
                    String positions = activeCursors.stream()
                        .map(c -> String.valueOf(c.getPosition()))
                        .collect(java.util.stream.Collectors.joining(", "));

                    String errorMsg = String.format(
                        "Sliding-window cache exceeded maximum size. " +
                        "Cache size: %d, maxCachedChunks: %d, active cursors: %d. " +
                        "Cursor positions: [%s]. " +
                        "Increase maxCachedChunks or use a different streaming strategy.",
                        cache.size(), maxCachedChunks, activeCursors.size(), positions
                    );

                    LOGGER.error(errorMsg);
                    throw new StreamingBufferSizeExceededException(maxCachedChunks);
                }
            }

            if (sourceIterator.hasNext()) {
                T chunk = sourceIterator.next();
                long fetchPos = nextFetchPosition.getAndIncrement();
                cache.put(fetchPos, chunk);
                fetchedCount++;
                LOGGER.trace("Fetched chunk at position {}, cache size now: {}", fetchPos, cache.size());
            } else {
                sourceExhausted = true;
                LOGGER.debug("Source exhausted after fetching {} chunks", fetchedCount);
            }
        }

        if (fetchedCount > 0) {
            LOGGER.debug("Fetched {} chunks, cache size: {}", fetchedCount, cache.size());
        }

        return cache.get(position);
    }

    /**
     * Checks if a chunk exists at the specified position.
     * May trigger fetching from source if not yet cached.
     *
     * @param position the position to check
     * @return true if a chunk exists at that position
     */
    synchronized boolean hasChunkAt(long position) {
        if (cache.containsKey(position)) {
            return true;
        }
        if (sourceExhausted && position >= nextFetchPosition.get()) {
            return false;
        }
        // Try to fetch up to position
        getChunkAt(position);
        return cache.containsKey(position);
    }

    /**
     * Called when a cursor advances. Triggers eviction check.
     *
     * @param cursor the cursor that advanced
     */
    void cursorAdvanced(SlidingWindowCursorIterator<T> cursor) {
        evictIfPossible();
    }

    /**
     * Called when a cursor closes. Removes from active set and triggers eviction.
     *
     * @param cursor the cursor that closed
     */
    void cursorClosed(SlidingWindowCursorIterator<T> cursor) {
        activeCursors.remove(cursor);
        evictIfPossible();

        // If last cursor closed and provider is closed, release resources
        if (activeCursors.isEmpty() && closed.get()) {
            releaseResources();
        }
    }

    /**
     * Evicts chunks before fetch to make room in cache.
     * Called from getChunkAt when cache is at capacity.
     */
    private void evictBeforeFetch() {
        if (activeCursors.isEmpty()) {
            LOGGER.trace("Eviction skipped: no active cursors");
            return;
        }

        int beforeSize = cache.size();

        // WORKAROUND: Exclude DataWeave zombie cursors from eviction calculation
        // These cursors remain at position 0 and are never released due to DataWeave bug
        long minPosition = activeCursors.stream()
            .filter(c -> !zombieCursors.contains(c))  // Exclude zombie cursors
            .mapToLong(SlidingWindowCursorIterator::getPosition)
            .min()
            .orElse(0);

        // Log cursor positions for debugging
        if (LOGGER.isDebugEnabled()) {
            String allPositions = activeCursors.stream()
                .map(c -> String.format("%d%s", c.getPosition(),
                    zombieCursors.contains(c) ? "(zombie)" : ""))
                .collect(java.util.stream.Collectors.joining(", "));
            LOGGER.debug("Eviction before fetch: cursor positions: [{}], minPosition={} (zombies excluded), cacheSize={}",
                allPositions, minPosition, cache.size());
        }

        // Evict all chunks before minPosition
        // Using headMap(minPosition, false) = all entries with key < minPosition
        NavigableMap<Long, T> toEvict = cache.headMap(minPosition, false);
        int evictCount = toEvict.size();

        if (!toEvict.isEmpty()) {
            toEvict.clear();
            LOGGER.debug("Evicted {} chunks before fetch, cacheSize: {} -> {}",
                evictCount, beforeSize, cache.size());
        }
    }

    /**
     * Evicts chunks that all cursors have advanced past.
     * This is the core of the sliding window mechanism.
     * Called after cursor advancement.
     */
    private synchronized void evictIfPossible() {
        if (activeCursors.isEmpty()) {
            LOGGER.trace("Eviction skipped: no active cursors");
            return;
        }

        int beforeSize = cache.size();

        // WORKAROUND: Exclude DataWeave zombie cursors from eviction calculation
        // These cursors remain at position 0 and are never released due to DataWeave bug
        long minPosition = activeCursors.stream()
            .filter(c -> !zombieCursors.contains(c))  // Exclude zombie cursors
            .mapToLong(SlidingWindowCursorIterator::getPosition)
            .min()
            .orElse(0);

        // Evict all chunks before minPosition
        // Using headMap(minPosition, false) = all entries with key < minPosition
        synchronized (cache) {
            NavigableMap<Long, T> toEvict = cache.headMap(minPosition, false);
            int evictCount = toEvict.size();

            if (!toEvict.isEmpty()) {
                toEvict.clear();
                LOGGER.debug("Eviction after advance: minPosition={} (zombies excluded), evicted={} chunks, cacheSize: {} -> {}",
                    minPosition, evictCount, beforeSize, cache.size());
            }
        }
    }

    /**
     * Returns the minimum cached position (earliest chunk still in cache).
     * Used by cursors to check if seek target is available.
     *
     * @return the minimum cached position, or nextFetchPosition if cache is empty
     */
    long getMinCachedPosition() {
        synchronized (cache) {
            if (cache.isEmpty()) {
                return nextFetchPosition.get();
            }
            return cache.firstKey();
        }
    }

    /**
     * Returns the size of the dataset, if known.
     * Since we're streaming, we don't know the size until exhausted.
     *
     * @return -1 (unknown size)
     */
    int getSize() {
        return -1; // Unknown size
    }

    /**
     * WORKAROUND FOR MULE/DATAWEAVE BUG: Detect DataWeave type-checking cursor leak.
     *
     * <p><b>Bug Description:</b> DataWeave's {@code ArrayType.accepts()} opens a cursor
     * to validate that a CursorIteratorProvider is iterable, but never releases it.
     * This creates "zombie cursors" that remain at position 0 indefinitely.</p>
     *
     * <p><b>Impact:</b> Zombie cursors block sliding-window eviction because
     * minPosition calculation includes them, preventing any chunks from being evicted.</p>
     *
     * <p><b>Stack Trace Pattern:</b></p>
     * <pre>
     * at org.mule.weave.v2.model.types.ArrayType$.accepts(Type.scala:667)
     * at org.mule.weave.v2.el.WeaveExpressionLanguageSession.splitInArraySeq(...)
     * at org.mule.runtime.core.internal.routing.Foreach.splitRequest(...)
     * </pre>
     *
     * <p><b>Detection Strategy:</b> Check if cursor was opened from ArrayType.accepts()
     * by examining the current thread's stack trace at cursor creation time.</p>
     *
     * <p><b>This is a temporary workaround.</b> The proper fix is for DataWeave to
     * release cursors after type checking, either via try-with-resources or by using
     * a different validation mechanism that doesn't open cursors.</p>
     *
     * <p><b>Reported to MuleSoft:</b> Mule 4.10.2 / DataWeave 2.10.2</p>
     *
     * <p><b>Evidence:</b> In scatter-gather with 2 foreach loops, 4 cursors are created:
     * <ul>
     *   <li>2 zombie cursors from ArrayType.accepts() - never advance, never released</li>
     *   <li>2 normal cursors for iteration - advance normally, released at end</li>
     * </ul>
     * Zombie cursors remain at position 0, blocking eviction with minPosition=0.</p>
     *
     * @return true if the current call stack indicates DataWeave type-checking
     * @since 0.2.0.5
     */
    private boolean isDataWeaveTypeCheckingCursor() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();

        // Look for the specific DataWeave ArrayType.accepts() pattern
        // This method opens cursors for type validation but never closes them
        for (int i = 0; i < Math.min(stack.length, 25); i++) {
            StackTraceElement ste = stack[i];
            String className = ste.getClassName();
            String methodName = ste.getMethodName();

            // DataWeave's ArrayType validation opens cursors but never closes them
            // Bug present in: org.mule.weave.core@2.10.2/org.mule.weave.v2.model.types.ArrayType$
            if (className != null && methodName != null &&
                className.contains("org.mule.weave.v2.model.types.ArrayType") &&
                methodName.equals("accepts")) {

                if (LOGGER.isWarnEnabled()) {
                    LOGGER.warn("╔═══════════════════════════════════════════════════════════════════════════╗");
                    LOGGER.warn("║ DATAWEAVE BUG DETECTED - CURSOR LEAK WORKAROUND ACTIVATED                ║");
                    LOGGER.warn("╠═══════════════════════════════════════════════════════════════════════════╣");
                    LOGGER.warn("║ Bug: ArrayType.accepts() opens cursor but never releases it              ║");
                    LOGGER.warn("║ Location: org.mule.weave.v2.model.types.ArrayType.accepts()              ║");
                    LOGGER.warn("║ Affected: Mule 4.10.2 / DataWeave 2.10.2                                 ║");
                    LOGGER.warn("║ Impact: Creates zombie cursors at position 0 that block eviction         ║");
                    LOGGER.warn("║ Workaround: This cursor will be excluded from eviction calculation       ║");
                    LOGGER.warn("║ Proper fix: DataWeave should use try-with-resources for type checking    ║");
                    LOGGER.warn("╚═══════════════════════════════════════════════════════════════════════════╝");
                }
                return true;
            }
        }
        return false;
    }
}
