package com.mulesoftforge.mule.extension.chunking.internal.iterator;

import com.mulesoftforge.mule.extension.chunking.api.Chunk;
import com.mulesoftforge.mule.extension.chunking.internal.chunk.ChunkFactory;
import com.mulesoftforge.mule.extension.chunking.internal.error.ChunkReadException;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.Arrays;

/**
 * Cuts the source into chunks of a number of delimited records.
 *
 * <p>Each chunk is an exact slice of the source and, except where the source ends without a delimiter, ends with a
 * delimiter. A chunk holds {@code recordsPerChunk} records unless the source ends first or the next record would
 * take the chunk past {@code maxChunkSize} bytes; then it ends at the last whole record and the next record starts
 * the next chunk. A record's size includes the delimiter that ends it. A single record larger than
 * {@code maxChunkSize} fails.</p>
 *
 * <p>No more than {@code maxChunkSize} bytes of the source are held while a chunk is being read.</p>
 */
public final class ByDelimiterChunkReader implements ChunkReader {

    private static final int MIN_BUFFER = 8 * 1024;
    private static final byte[] NONE = new byte[0];

    private final PushbackInputStream source;
    private final byte[] delimiter;
    private final int recordsPerChunk;
    private final int maxChunkSize;

    /** Bytes read past the end of the previous chunk; they begin the next one. */
    private byte[] carried = NONE;
    private boolean sourceEnded = false;
    private int index = 0;
    private long offset = 0;

    /**
     * @param source the content to chunk
     * @param delimiter the bytes that end a record; not empty
     * @param recordsPerChunk the number of records in a chunk; positive
     * @param maxChunkSize the most bytes a chunk may hold; positive
     */
    public ByDelimiterChunkReader(InputStream source, byte[] delimiter, int recordsPerChunk, int maxChunkSize) {
        this.source = new PushbackInputStream(source, 1);
        this.delimiter = delimiter.clone();
        this.recordsPerChunk = recordsPerChunk;
        this.maxChunkSize = maxChunkSize;
    }

    @Override
    public Chunk read() throws IOException {
        byte[] buffer = new byte[Math.min(maxChunkSize, Math.max(MIN_BUFFER, carried.length))];
        System.arraycopy(carried, 0, buffer, 0, carried.length);
        int filled = carried.length;
        carried = NONE;

        int records = 0;
        int end = 0;          // end of the last whole record in the buffer
        int searchFrom = 0;   // no delimiter starts before this position
        boolean chunkComplete = false;

        while (!chunkComplete) {
            int found;
            while (records < recordsPerChunk && (found = indexOf(buffer, searchFrom, filled)) >= 0) {
                records++;
                end = found + delimiter.length;
                searchFrom = end;
            }
            if (records == recordsPerChunk) {
                break;
            }
            searchFrom = Math.max(searchFrom, filled - delimiter.length + 1);

            if (sourceEnded) {
                if (filled > end) {
                    records++; // the final record, not ended by a delimiter
                    end = filled;
                }
                break;
            }
            if (filled == maxChunkSize) {
                if (Sources.atEnd(source)) {
                    sourceEnded = true; // what is buffered is the final record, and it fits
                    continue;
                }
                if (records == 0) {
                    throw new ChunkReadException(
                        "Chunk " + index + ": a record starting at byte offset " + offset + " is larger than the "
                            + "maximum chunk size of " + maxChunkSize + " bytes (record length counts the delimiter "
                            + "that ends it). Raise maxChunkSize or check the delimiter.");
                }
                break; // the next record does not fit; it starts the next chunk
            }
            if (filled == buffer.length) {
                buffer = Arrays.copyOf(buffer, (int) Math.min(maxChunkSize, 2L * buffer.length));
            }
            int read = source.read(buffer, filled, buffer.length - filled);
            if (read == -1) {
                sourceEnded = true;
            } else {
                filled += read;
            }
        }

        if (end == 0) {
            return null;
        }
        if (filled > end) {
            carried = Arrays.copyOfRange(buffer, end, filled);
        }
        byte[] data = end == buffer.length ? buffer : Arrays.copyOf(buffer, end);
        buffer = null;
        boolean last = carried.length == 0 && (sourceEnded || Sources.atEnd(source));
        if (last) {
            sourceEnded = true;
        }
        Chunk chunk = ChunkFactory.create(data, index, offset, index == 0, last, records);
        index++;
        offset += end;
        return chunk;
    }

    private int indexOf(byte[] buffer, int from, int to) {
        int lastStart = to - delimiter.length;
        outer:
        for (int i = from; i <= lastStart; i++) {
            for (int j = 0; j < delimiter.length; j++) {
                if (buffer[i + j] != delimiter[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    @Override
    public void close() throws IOException {
        sourceEnded = true;
        carried = NONE;
        source.close();
    }
}
