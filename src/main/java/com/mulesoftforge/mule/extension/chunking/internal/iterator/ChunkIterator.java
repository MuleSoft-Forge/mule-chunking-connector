package com.mulesoftforge.mule.extension.chunking.internal.iterator;

import com.mulesoftforge.mule.extension.chunking.api.Chunk;
import com.mulesoftforge.mule.extension.chunking.internal.error.ChunkReadException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Iterates over the chunks a {@link ChunkReader} reads.
 *
 * <p>All reading happens in {@link #hasNext()}: the next chunk is read there and handed over by {@link #next()}.
 * A failure is therefore always raised from {@code hasNext()}, where Mule's {@code foreach} reports it to the flow,
 * and once raised it is raised again on every later call. A failure is never reported as the end of the data.</p>
 */
public final class ChunkIterator implements Iterator<Chunk>, Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChunkIterator.class);

    private final ChunkReader reader;
    private Chunk pending;
    private boolean ended = false;
    private RuntimeException failure;

    /**
     * @param reader reads the chunks; closed when the data ends, when reading fails, or on {@link #close()}
     */
    public ChunkIterator(ChunkReader reader) {
        this.reader = reader;
    }

    @Override
    public boolean hasNext() {
        if (failure != null) {
            throw failure;
        }
        if (pending != null) {
            return true;
        }
        if (ended) {
            return false;
        }
        Chunk chunk;
        try {
            chunk = reader.read();
        } catch (ChunkReadException e) {
            throw fail(e);
        } catch (IOException | RuntimeException e) {
            throw fail(new ChunkReadException("Failed to read the content: " + e.getMessage(), e));
        }
        if (chunk == null) {
            ended = true;
            closeReader();
            return false;
        }
        LOGGER.debug("Read {}", chunk);
        pending = chunk;
        return true;
    }

    @Override
    public Chunk next() {
        if (!hasNext()) {
            throw new NoSuchElementException("No more chunks");
        }
        Chunk chunk = pending;
        pending = null;
        return chunk;
    }

    private RuntimeException fail(RuntimeException e) {
        failure = e;
        pending = null;
        closeReader();
        return e;
    }

    @Override
    public void close() {
        ended = true;
        pending = null;
        closeReader();
    }

    private void closeReader() {
        try {
            reader.close();
        } catch (IOException e) {
            LOGGER.warn("Could not close the content: {}", e.getMessage());
        }
    }
}
