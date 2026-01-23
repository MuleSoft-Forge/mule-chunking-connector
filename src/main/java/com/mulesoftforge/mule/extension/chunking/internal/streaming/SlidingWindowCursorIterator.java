package com.mulesoftforge.mule.extension.chunking.internal.streaming;

import org.mule.runtime.api.streaming.object.CursorIterator;
import org.mule.runtime.api.streaming.object.CursorIteratorProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.NoSuchElementException;

/**
 * Cursor implementation for sliding window streaming.
 *
 * <p>Each cursor maintains its own position in the dataset. Multiple cursors
 * can exist simultaneously, each with independent position tracking.</p>
 *
 * <p><b>Position Tracking:</b> Zero-based index of the next chunk to read.
 * After calling {@code next()}, position is incremented.</p>
 *
 * <p><b>Seek Behavior:</b></p>
 * <ul>
 *   <li>Can seek forward or backward within the cached window</li>
 *   <li>Throws IOException if seeking to evicted data (position &lt; minCached)</li>
 *   <li>Can seek to future positions (will fetch on next()/hasNext())</li>
 * </ul>
 *
 * <p><b>Release vs Close:</b> Both methods do the same thing - mark cursor
 * as released and notify provider for cleanup. Following Mule's convention
 * where release() is preferred but close() is supported for try-with-resources.</p>
 *
 * @param <T> the type of elements (typically Chunk)
 * @since 0.2.0
 */
public class SlidingWindowCursorIterator<T> implements CursorIterator<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(SlidingWindowCursorIterator.class);

    private final SlidingWindowCursorIteratorProvider<T> provider;
    private final int cursorId;
    private long position = 0;
    private boolean released = false;

    /**
     * Package-private constructor. Cursors are created via provider.openCursor().
     *
     * @param provider the provider that owns this cursor
     */
    SlidingWindowCursorIterator(SlidingWindowCursorIteratorProvider<T> provider) {
        this.provider = provider;
        this.cursorId = System.identityHashCode(this);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Cursor #{} created at position 0", cursorId);
            if (LOGGER.isTraceEnabled()) {
                StackTraceElement[] stack = Thread.currentThread().getStackTrace();
                StringBuilder sb = new StringBuilder();
                sb.append("Cursor #").append(cursorId).append(" creation stack:\n");
                for (int i = 2; i < Math.min(stack.length, 15); i++) {
                    sb.append("  at ").append(stack[i]).append("\n");
                }
                LOGGER.trace(sb.toString());
            }
        }
    }

    @Override
    public boolean hasNext() {
        if (released) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Cursor #{} hasNext() = false (released)", cursorId);
            }
            return false;
        }
        boolean result = provider.hasChunkAt(position);
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Cursor #{} hasNext() = {} at position {}", cursorId, result, position);
        }
        return result;
    }

    @Override
    public T next() {
        if (released) {
            throw new NoSuchElementException("Cursor has been released");
        }

        long positionBefore = position;
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Cursor #{} next() called at position {}", cursorId, positionBefore);
        }

        T chunk = provider.getChunkAt(position);
        if (chunk == null) {
            throw new NoSuchElementException("No chunk at position " + position);
        }

        position++;
        provider.cursorAdvanced(this);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Cursor #{} next() complete: position {} -> {}", cursorId, positionBefore, position);
        }
        return chunk;
    }

    @Override
    public long getPosition() {
        return position;
    }

    @Override
    public void seek(long newPosition) throws IOException {
        if (released) {
            throw new IOException("Cannot seek on released cursor");
        }

        if (newPosition < 0) {
            throw new IOException("Cannot seek to negative position: " + newPosition);
        }

        long minCached = provider.getMinCachedPosition();
        if (newPosition < minCached) {
            throw new IOException(
                "Cannot seek to position " + newPosition +
                ": data has been evicted from sliding window cache. " +
                "Minimum available position is " + minCached + ". " +
                "This occurs when all cursors advanced past this position. " +
                "Consider increasing maxCachedChunks or ensuring cursors don't diverge widely.");
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Cursor #{} seek: position {} -> {}", cursorId, position, newPosition);
        }
        this.position = newPosition;
    }

    @Override
    public void release() {
        if (!released) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Cursor #{} released at position {}", cursorId, position);
            }
            released = true;
            provider.cursorClosed(this);
        }
    }

    @Override
    public void close() throws IOException {
        // In Mule's streaming model, close() and release() are equivalent
        release();
    }

    @Override
    public boolean isReleased() {
        return released;
    }

    @Override
    public CursorIteratorProvider getProvider() {
        return provider;
    }

    @Override
    public int getSize() {
        return provider.getSize();
    }
}
