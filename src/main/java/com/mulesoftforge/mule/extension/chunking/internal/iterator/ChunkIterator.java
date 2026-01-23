package com.mulesoftforge.mule.extension.chunking.internal.iterator;

import com.mulesoftforge.mule.extension.chunking.api.Chunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Iterator that produces Chunk objects from an InputStream.
 *
 * <p><b>v0.2.0 Memory Model - Two Modes:</b></p>
 *
 * <p><b>Buffer Reuse Mode (reuseBuffer=true):</b></p>
 * <ul>
 *   <li>Single byte[] buffer reused across all chunks</li>
 *   <li>Single Chunk instance reused (mutable)</li>
 *   <li>Memory: O(chunkSize) - constant, bounded</li>
 *   <li>Use case: Non-repeatable streaming where chunks are processed immediately</li>
 *   <li>⚠️ WARNING: Data is overwritten on next() call</li>
 * </ul>
 *
 * <p><b>Immutable Mode (reuseBuffer=false):</b></p>
 * <ul>
 *   <li>New byte[] created for each chunk (copy of read buffer)</li>
 *   <li>New Chunk instance per iteration</li>
 *   <li>Memory: Depends on strategy - chunks can be cached/stored</li>
 *   <li>Use case: Repeatable strategies (sliding-window, in-memory, file-store)</li>
 *   <li>✅ Safe to store chunks in collections</li>
 * </ul>
 *
 * @since 0.2.0
 */
public class ChunkIterator implements Iterator<Chunk>, Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChunkIterator.class);

    private final PushbackInputStream source;
    private final byte[] buffer;
    private final int chunkSize;
    private final boolean reuseBuffer;
    private final Chunk reusableChunk;  // Only used when reuseBuffer=true

    private int currentIndex = 0;
    private long currentOffset = 0;
    private Boolean hasMoreData = null;  // Null = not yet checked
    private boolean closed = false;

    /**
     * Creates a new ChunkIterator with buffer reuse (for non-repeatable streaming).
     *
     * @param source The input stream to read from
     * @param chunkSize Size of each chunk in bytes
     * @deprecated Use {@link #ChunkIterator(InputStream, int, boolean)} instead
     */
    @Deprecated
    public ChunkIterator(InputStream source, int chunkSize) {
        this(source, chunkSize, true);  // Default to reuse for backward compatibility
    }

    /**
     * Creates a new ChunkIterator with configurable buffer reuse.
     *
     * @param source The input stream to read from
     * @param chunkSize Size of each chunk in bytes
     * @param reuseBuffer If true, reuses buffer and chunk instance (constant memory).
     *                    If false, creates new instances (enables caching).
     */
    public ChunkIterator(InputStream source, int chunkSize, boolean reuseBuffer) {
        if (source == null) {
            throw new IllegalArgumentException("Source InputStream cannot be null");
        }
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("Chunk size must be positive: " + chunkSize);
        }

        this.source = new PushbackInputStream(source, 1);
        this.chunkSize = chunkSize;
        this.reuseBuffer = reuseBuffer;
        this.buffer = new byte[chunkSize];
        this.reusableChunk = reuseBuffer ? new Chunk() : null;

        LOGGER.debug("ChunkIterator created: chunkSize={}, reuseBuffer={}", chunkSize, reuseBuffer);
    }

    @Override
    public boolean hasNext() {
        if (closed) {
            return false;
        }
        if (hasMoreData == null) {
            // Lazy check: probe for data on first hasNext() call
            try {
                int probe = source.read();
                if (probe == -1) {
                    hasMoreData = false;
                } else {
                    source.unread(probe);
                    hasMoreData = true;
                }
            } catch (IOException e) {
                LOGGER.error("IO error checking for data", e);
                hasMoreData = false;
            }
        }
        return hasMoreData;
    }

    @Override
    public Chunk next() {
        if (closed) {
            throw new NoSuchElementException("Iterator is closed");
        }

        // Check if data exists (lazy initialization)
        if (!hasNext()) {
            throw new NoSuchElementException("No more chunks available");
        }

        try {
            int bytesRead = readFullChunk();

            if (bytesRead <= 0) {
                hasMoreData = false;
                throw new NoSuchElementException("End of stream reached");
            }

            boolean isLast;

            if (bytesRead < chunkSize) {
                // Partial chunk = definitely last
                isLast = true;
                hasMoreData = false;
            } else {
                // Full chunk - probe for more data
                isLast = probeForEof();
                if (isLast) {
                    hasMoreData = false;
                }
            }

            Chunk chunk;

            if (reuseBuffer) {
                // REUSE MODE: Update existing chunk instance with buffer reference
                // ⚠️ Buffer and chunk are reused - constant O(chunkSize) memory
                reusableChunk.update(
                    buffer,
                    currentIndex,
                    currentOffset,
                    bytesRead,
                    currentIndex == 0,
                    isLast
                );
                chunk = reusableChunk;
            } else {
                // IMMUTABLE MODE: Create new chunk with copy of data
                // ✅ Safe for caching - each chunk has its own data
                byte[] chunkData = java.util.Arrays.copyOf(buffer, bytesRead);
                chunk = new Chunk(
                    chunkData,
                    currentIndex,
                    currentOffset,
                    bytesRead,
                    currentIndex == 0,
                    isLast
                );
            }

            // Update state for next iteration
            currentIndex++;
            currentOffset += bytesRead;

            LOGGER.debug("Produced chunk: index={}, offset={}, length={}, isLast={}, mode={}",
                currentIndex - 1, currentOffset - bytesRead, bytesRead, isLast,
                reuseBuffer ? "REUSE" : "IMMUTABLE");

            return chunk;

        } catch (IOException e) {
            hasMoreData = false;
            LOGGER.error("IO error reading chunk at index {}", currentIndex, e);
            throw new RuntimeException("Failed to read chunk from source", e);
        }
    }

    /**
     * Read exactly chunkSize bytes, or whatever remains.
     */
    private int readFullChunk() throws IOException {
        int totalRead = 0;

        while (totalRead < chunkSize) {
            int bytesRead = source.read(buffer, totalRead, chunkSize - totalRead);
            if (bytesRead == -1) {
                break;
            }
            totalRead += bytesRead;
        }

        return totalRead;
    }

    /**
     * Probe for EOF by reading 1 byte.
     * If data exists, push it back for next chunk.
     *
     * Memory cost: 1 byte (in PushbackInputStream buffer)
     */
    private boolean probeForEof() throws IOException {
        int probe = source.read();
        if (probe == -1) {
            return true;  // EOF - this was the last chunk
        } else {
            source.unread(probe);  // Push back for next read
            return false;  // More data exists
        }
    }

    /**
     * Closes the underlying input stream.
     * After calling this, {@link #hasNext()} will return false and {@link #next()} will throw.
     */
    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            hasMoreData = false;
            try {
                source.close();
                LOGGER.debug("ChunkIterator closed after {} chunks", currentIndex);
            } catch (IOException e) {
                LOGGER.warn("Error closing source stream", e);
                throw e;
            }
        }
    }

    /**
     * Returns true if the iterator has been closed.
     */
    public boolean isClosed() {
        return closed;
    }
}
