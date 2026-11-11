package com.mulesoftforge.mule.extension.chunking.internal.streaming;

import com.mulesoftforge.mule.extension.chunking.api.Chunk;
import com.mulesoftforge.mule.extension.chunking.internal.error.ChunkReadException;
import org.mule.runtime.api.streaming.object.CursorIterator;
import org.mule.runtime.api.streaming.object.CursorIteratorProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * The sliding window: several readers read the chunks, and a chunk is let go once the readers no longer need it,
 * with never more than {@code maxInMemoryChunks} chunks held in memory.
 *
 * <p><b>Readers.</b> A reader is a cursor that has read at least one chunk. A cursor that is opened and never read
 * (DataWeave opens one to check the chunks' type) is not a reader and holds nothing back.</p>
 *
 * <p><b>With {@code readers} set</b>, a chunk is let go as soon as that many readers have read it, and never before.
 * <b>With {@code readers} not set</b>, chunks are kept until {@code maxInMemoryChunks} are held; then the oldest
 * that every reader seen so far has read is let go to make room for the next.</p>
 *
 * <p><b>Failures</b>, each raised to the reader with a message saying what to change:</p>
 * <ul>
 *   <li>The next chunk would be one more than {@code maxInMemoryChunks}, because the chunks held are still owed to a
 *   reader: the reader that asked for it fails.</li>
 *   <li>A reader asks for a chunk already let go, with {@code readers} not set: it started too late.</li>
 *   <li>A reader asks for a chunk already let go, with {@code readers} set: it is one more reader than declared.</li>
 * </ul>
 *
 * <p>Everything held is released when Mule releases the chunks at the end of the event that read them.</p>
 */
public final class SlidingWindowCursorIteratorProvider implements CursorIteratorProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(SlidingWindowCursorIteratorProvider.class);

    private final Iterator<Chunk> source;
    private final int maxInMemoryChunks;
    private final Integer readers;

    /** The chunks held, by position, from {@code first} to {@code first + window.size() - 1}. */
    private final Map<Long, Chunk> window = new HashMap<>();
    private long first = 0;
    private boolean sourceEnded = false;
    private final List<Cursor> readersSeen = new ArrayList<>();
    private int peak = 0;
    private boolean closed = false;
    private boolean released = false;
    private final HeldChunks held = HeldChunks.current();

    /**
     * @param source the chunks, read when a reader first needs each one
     * @param maxInMemoryChunks the most chunks held in memory, positive
     * @param readers how many readers will read the chunks, positive; {@code null} when not set
     */
    public SlidingWindowCursorIteratorProvider(Iterator<Chunk> source, int maxInMemoryChunks, Integer readers) {
        this.source = source;
        this.maxInMemoryChunks = maxInMemoryChunks;
        this.readers = readers;
        held.open();
    }

    @Override
    public synchronized CursorIterator<Chunk> openCursor() {
        if (closed) {
            throw new IllegalStateException("Cannot open a cursor on a closed provider");
        }
        held.cursor();
        return new Cursor(this);
    }

    @Override
    public synchronized void close() {
        closed = true; // no new cursors; cursors already open keep reading until releaseResources()
    }

    @Override
    public synchronized void releaseResources() {
        closed = true;
        if (released) {
            return;
        }
        released = true;
        LOGGER.debug("sliding-window: released {} chunks at the end of the event; at most {} were held, {} readers",
                     window.size(), peak, readersSeen.size());
        held.remove(window.size());
        held.release();
        window.clear();
        readersSeen.clear();
        if (source instanceof Closeable) {
            try {
                ((Closeable) source).close();
            } catch (IOException e) {
                LOGGER.warn("sliding-window: could not close the content: {}", e.getMessage());
            }
        }
    }

    @Override
    public synchronized boolean isClosed() {
        return closed;
    }

    /** @return the chunks held in memory now */
    synchronized int held() {
        return window.size();
    }

    private synchronized boolean hasChunkAt(Cursor cursor, long position) {
        if (released) {
            throw new ChunkReadException("sliding-window: the chunks were released when the event that read them "
                + "ended, so they cannot be read any more.");
        }
        if (position < first) {
            throw letGo(cursor, position);
        }
        while (position >= first + window.size() && !sourceEnded) {
            if (source.hasNext()) {
                long next = first + window.size();
                makeRoomFor(next);
                window.put(next, source.next());
                held.add(1);
                peak = Math.max(peak, window.size());
            } else {
                sourceEnded = true;
            }
        }
        return position < first + window.size();
    }

    private synchronized Chunk next(Cursor cursor) {
        if (!hasChunkAt(cursor, cursor.position)) {
            throw new NoSuchElementException("No more chunks");
        }
        Chunk chunk = window.get(cursor.position);
        cursor.position++;
        cursor.passed = Math.max(cursor.passed, cursor.position);
        if (!cursor.reader) {
            cursor.reader = true;
            readersSeen.add(cursor);
            held.reader();
        }
        if (readers != null) {
            letGoWhatEnoughReadersHaveRead();
        }
        return chunk;
    }

    private synchronized void cursorReleased(Cursor cursor) {
        if (cursor.reader && !released) {
            cursor.passed = Long.MAX_VALUE; // it will read no more, so it no longer holds anything back
            if (readers != null) {
                letGoWhatEnoughReadersHaveRead();
            }
        }
    }

    /** With {@code readers} set: lets go of every chunk that that many readers have read. */
    private void letGoWhatEnoughReadersHaveRead() {
        if (readersSeen.size() < readers) {
            return;
        }
        long[] passed = readersSeen.stream().mapToLong(c -> c.passed).sorted().toArray();
        long readByEnough = passed[passed.length - readers]; // every chunk before this has been read by enough readers
        while (first < readByEnough && !window.isEmpty()) {
            letGoOldest();
        }
    }

    /** Before holding the chunk at {@code position}: makes room for it, or fails if the window is full. */
    private void makeRoomFor(long position) {
        if (readers != null) {
            letGoWhatEnoughReadersHaveRead();
        }
        if (window.size() < maxInMemoryChunks) {
            return;
        }
        if (readers == null && !readersSeen.isEmpty() && first < leastPassed()) {
            letGoOldest();
            return;
        }
        throw overLimit(position);
    }

    private long leastPassed() {
        return readersSeen.stream().mapToLong(c -> c.passed).min().orElse(0);
    }

    private void letGoOldest() {
        window.remove(first);
        first++;
        held.remove(1);
    }

    private ChunkReadException overLimit(long position) {
        int started = readersSeen.size();
        String declared = readers != null
            ? "readers is " + readers + ", " + started + (started == 1 ? " has" : " have") + " started reading"
            : "readers is not set";
        return new ChunkReadException("sliding-window: reading chunk " + position + " would hold more than "
            + "maxInMemoryChunks (" + maxInMemoryChunks + ") chunks in memory, because chunks " + first + " to "
            + (position - 1) + " are still owed to a reader that has not read them (" + declared + "; the readers' "
            + "next chunks: " + positions() + "). Raise maxInMemoryChunks, or use file-store, which keeps chunks "
            + "beyond the limit on disk.");
    }

    private ChunkReadException letGo(Cursor cursor, long position) {
        if (readers == null) {
            return new ChunkReadException("sliding-window: chunk " + position + " is no longer held. readers is not "
                + "set, so once maxInMemoryChunks (" + maxInMemoryChunks + ") chunks were held the oldest that every "
                + "reader seen so far had read were let go, and this reader started too late to read them. Set "
                + "readers to the number of readers of these chunks (each foreach, scatter-gather route or "
                + "expression that reads them), so that no chunk is let go before all of them have read it.");
        }
        return new ChunkReadException("sliding-window: chunk " + position + " was already let go, because readers "
            + "is " + readers + " and " + readers + (readers == 1 ? " reader has" : " readers have") + " read it. "
            + "This read is one more than readers allows. Set readers to the number of readers of these chunks "
            + "(each foreach, scatter-gather route or expression that reads them, counting each time it reads "
            + "them), or use in-memory or file-store to read them any number of times.");
    }

    private String positions() {
        long[] next = readersSeen.stream().mapToLong(c -> c.position).toArray();
        return next.length == 0 ? "none" : Arrays.toString(next);
    }

    /** One reader's place in the chunks. All state is guarded by the provider's lock. */
    private static final class Cursor implements CursorIterator<Chunk> {
        private final SlidingWindowCursorIteratorProvider provider;
        private long position = 0;
        /** One past the furthest chunk this cursor has read: it has passed every chunk before this. */
        private long passed = 0;
        private boolean reader = false;
        private boolean released = false;

        Cursor(SlidingWindowCursorIteratorProvider provider) {
            this.provider = provider;
        }

        @Override
        public boolean hasNext() {
            synchronized (provider) {
                return !released && provider.hasChunkAt(this, position);
            }
        }

        @Override
        public Chunk next() {
            synchronized (provider) {
                if (released) {
                    throw new NoSuchElementException("Cursor has been released");
                }
                return provider.next(this);
            }
        }

        @Override
        public long getPosition() {
            synchronized (provider) {
                return position;
            }
        }

        @Override
        public void seek(long newPosition) throws IOException {
            if (newPosition < 0) {
                throw new IOException("Cannot seek to negative position " + newPosition);
            }
            synchronized (provider) {
                position = newPosition; // a chunk already let go fails, with its reason, when it is read
            }
        }

        @Override
        public void release() {
            synchronized (provider) {
                if (!released) {
                    released = true;
                    provider.cursorReleased(this);
                }
            }
        }

        @Override
        public boolean isReleased() {
            synchronized (provider) {
                return released;
            }
        }

        @Override
        public CursorIteratorProvider getProvider() {
            return provider;
        }

        @Override
        public void close() {
            release();
        }

        @Override
        public int getSize() {
            return -1;
        }
    }
}
