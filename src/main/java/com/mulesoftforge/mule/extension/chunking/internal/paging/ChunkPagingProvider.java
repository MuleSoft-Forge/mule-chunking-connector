package com.mulesoftforge.mule.extension.chunking.internal.paging;

import com.mulesoftforge.mule.extension.chunking.api.Chunk;
import com.mulesoftforge.mule.extension.chunking.internal.connection.ChunkingConnection;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.extension.api.runtime.streaming.PagingProvider;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * PagingProvider that lazily produces Chunk objects from an InputStream.
 *
 * Memory usage: O(chunkSize) + O(1) for probe byte
 * - Single buffer for reading chunks
 * - 1-byte pushback buffer for EOF detection
 * - NO read-ahead buffer that would double memory
 *
 * isLast detection strategy:
 * 1. Read chunk into buffer
 * 2. If partial read (< chunkSize), this is last chunk
 * 3. If full read, probe with 1-byte read:
 *    - If returns -1: this is last chunk
 *    - If returns byte: push back, more data exists
 */
public class ChunkPagingProvider implements PagingProvider<ChunkingConnection, Chunk> {

    private final PushbackInputStream source;
    private final int chunkSize;
    private final byte[] buffer;

    private int currentIndex = 0;
    private long currentOffset = 0;
    private boolean exhausted = false;

    public ChunkPagingProvider(InputStream source, int chunkSize) {
        // Wrap in PushbackInputStream for 1-byte probe
        this.source = new PushbackInputStream(source, 1);
        this.chunkSize = chunkSize;
        this.buffer = new byte[chunkSize];
    }

    @Override
    public List<Chunk> getPage(ChunkingConnection connection) {
        if (exhausted) {
            return Collections.emptyList();
        }

        try {
            int bytesRead = readFullChunk();

            if (bytesRead <= 0) {
                exhausted = true;
                return Collections.emptyList();
            }

            boolean isLast;

            if (bytesRead < chunkSize) {
                // Partial chunk = definitely last
                isLast = true;
                exhausted = true;
            } else {
                // Full chunk - probe for more data
                isLast = probeForEof();
                if (isLast) {
                    exhausted = true;
                }
            }

            // Create chunk (copies only bytesRead bytes from buffer)
            Chunk chunk = new Chunk(
                buffer,
                currentIndex,
                currentOffset,
                bytesRead,
                currentIndex == 0,
                isLast
            );

            // Update state
            currentIndex++;
            currentOffset += bytesRead;

            // Return single-element list (one chunk per page)
            List<Chunk> result = new ArrayList<>(1);
            result.add(chunk);
            return result;

        } catch (IOException e) {
            exhausted = true;
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

    @Override
    public Optional<Integer> getTotalResults(ChunkingConnection connection) {
        // Total chunks unknown for streaming sources
        return Optional.empty();
    }

    @Override
    public void close(ChunkingConnection connection) throws MuleException {
        try {
            source.close();
        } catch (IOException e) {
            // Log but don't throw - best effort cleanup
        }
    }
}
