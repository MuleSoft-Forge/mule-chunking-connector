package com.mulesoftforge.mule.extension.chunking.internal.streaming;

import com.mulesoftforge.mule.extension.chunking.api.Chunk;
import com.mulesoftforge.mule.extension.chunking.internal.error.ChunkReadException;
import org.mule.runtime.api.streaming.object.CursorIterator;
import org.mule.runtime.api.streaming.object.CursorIteratorProvider;

import java.io.Closeable;
import java.lang.ref.Cleaner;
import java.io.IOException;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Makes the chunks fully repeatable: every chunk read from the source is kept in a {@link ChunkStore}, and any
 * number of cursors can read them, from any position, as often as they like. Chunks are read from the source only
 * when a cursor first needs them.
 *
 * <p>Everything kept is released, the store's temporary file deleted and the content closed when Mule releases the
 * chunks at the end of the event that read them. After that a read fails rather than finding no more chunks.</p>
 */
public final class RepeatableCursorIteratorProvider implements CursorIteratorProvider {

    /**
     * Releases a store whose provider is collected without having been released, as happens when a flow is run by
     * reference from a test rather than started by a message source.
     */
    private static final Cleaner CLEANER = Cleaner.create();

    private final Iterator<Chunk> source;
    private final ChunkStore store;
    private final HeldChunks held = HeldChunks.current();
    private final Release releaseAction;
    private final Cleaner.Cleanable release;
    private boolean sourceEnded = false;
    private boolean closed = false;
    private boolean released = false;

    private RepeatableCursorIteratorProvider(Iterator<Chunk> source, ChunkStore store) {
        this.source = source;
        this.store = store;
        held.open();
        this.releaseAction = new Release(source, store, held);
        this.release = CLEANER.register(this, releaseAction);
    }

    /** Keeps every chunk in memory; see {@link InMemoryChunkStore}. */
    public static RepeatableCursorIteratorProvider inMemory(Iterator<Chunk> source, int maxChunks) {
        return new RepeatableCursorIteratorProvider(source, new InMemoryChunkStore(maxChunks));
    }

    /** Keeps the most recent chunks in memory and the rest on disk; see {@link FileChunkStore}. */
    public static RepeatableCursorIteratorProvider fileStore(Iterator<Chunk> source, int maxInMemoryChunks) {
        return new RepeatableCursorIteratorProvider(source, new FileChunkStore(maxInMemoryChunks));
    }

    synchronized boolean hasChunkAt(long position) {
        if (released) {
            throw new ChunkReadException(store.name() + ": the chunks were released when the event that read them "
                + "ended, so they cannot be read any more.");
        }
        fetchThrough(position);
        return position < store.size();
    }

    synchronized Chunk chunkAt(long position) {
        if (!hasChunkAt(position)) {
            throw new NoSuchElementException("No chunk at position " + position);
        }
        return store.get((int) position);
    }

    private void fetchThrough(long position) {
        while (!sourceEnded && store.size() <= position) {
            if (source.hasNext()) {
                int before = store.inMemory();
                store.add(source.next());
                held.add(store.inMemory() - before);
            } else {
                sourceEnded = true;
            }
        }
    }

    @Override
    public CursorIterator openCursor() {
        if (closed) {
            throw new IllegalStateException("Cannot open a cursor on a closed provider");
        }
        return new Cursor(this);
    }

    @Override
    public synchronized void close() {
        closed = true; // no new cursors; cursors already open keep reading until releaseResources()
    }

    @Override
    public synchronized void releaseResources() {
        closed = true;
        if (!released) {
            released = true;
            releaseAction.byMule = true;
            release.clean(); // runs the release once, here or on collection, whichever comes first
        }
    }

    @Override
    public synchronized boolean isClosed() {
        return closed;
    }

    /** Frees the store, deletes anything it wrote, and closes the content; holds no reference to the provider. */
    private static final class Release implements Runnable {
        private final Iterator<Chunk> source;
        private final ChunkStore store;
        private final HeldChunks held;
        private volatile boolean byMule = false;

        Release(Iterator<Chunk> source, ChunkStore store, HeldChunks held) {
            this.source = source;
            this.store = store;
            this.held = held;
        }

        @Override
        public void run() {
            synchronized (store) {
                held.remove(store.inMemory());
                store.release();
                held.release();
                if (!byMule) {
                    held.collect();
                }
            }
            if (source instanceof Closeable) {
                try {
                    ((Closeable) source).close();
                } catch (IOException e) {
                    // the content is finished with either way
                }
            }
        }
    }

    private static final class Cursor implements CursorIterator<Chunk> {
        private final RepeatableCursorIteratorProvider provider;
        private long position = 0;
        private boolean released = false;

        Cursor(RepeatableCursorIteratorProvider provider) {
            this.provider = provider;
        }

        @Override
        public boolean hasNext() {
            return !released && provider.hasChunkAt(position);
        }

        @Override
        public Chunk next() {
            if (released) {
                throw new NoSuchElementException("Cursor has been released");
            }
            Chunk chunk = provider.chunkAt(position);
            position++;
            return chunk;
        }

        @Override
        public long getPosition() {
            return position;
        }

        @Override
        public void seek(long newPosition) throws IOException {
            if (newPosition < 0) {
                throw new IOException("Cannot seek to negative position " + newPosition);
            }
            position = newPosition;
        }

        @Override
        public void release() {
            released = true;
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
        public void close() {
            release();
        }

        @Override
        public int getSize() {
            return -1;
        }
    }
}
