package com.mulesoftforge.mule.extension.chunking.api;

import com.mulesoftforge.mule.extension.chunking.internal.chunk.ChunkFactory;

/**
 * One chunk of the content read by {@code read-chunked}.
 *
 * <p>A chunk is an exact slice of the source: {@link #getData()} holds exactly the chunk's bytes, and the
 * chunks taken in order reproduce the source byte for byte. In a flow the properties are read as
 * {@code payload.data}, {@code payload.index}, {@code payload.offset}, {@code payload.length},
 * {@code payload.first}, {@code payload.last} and {@code payload.recordCount}.</p>
 *
 * <p>Chunks are created by the connector only and cannot be changed once created.</p>
 *
 * @since 0.1.0
 */
public final class Chunk {

    static {
        ChunkFactory.register(Chunk::new);
    }

    private final byte[] data;
    private final int index;
    private final long offset;
    private final boolean first;
    private final boolean last;
    private final Integer recordCount;

    private Chunk(byte[] data, int index, long offset, boolean first, boolean last, Integer recordCount) {
        this.data = data;
        this.index = index;
        this.offset = offset;
        this.first = first;
        this.last = last;
        this.recordCount = recordCount;
    }

    /**
     * Returns the chunk's bytes. The array's length is the chunk's {@link #getLength() length}.
     *
     * @return the chunk's bytes
     * @since 0.1.0
     */
    public byte[] getData() {
        return data;
    }

    /**
     * Returns the chunk's position in the sequence of chunks, counting from 0.
     *
     * @return the 0-based chunk index
     * @since 0.1.0
     */
    public int getIndex() {
        return index;
    }

    /**
     * Returns the position in the source of the chunk's first byte, counting from 0.
     *
     * @return the byte offset of the chunk in the source
     * @since 0.1.0
     */
    public long getOffset() {
        return offset;
    }

    /**
     * Returns the number of bytes in the chunk.
     *
     * @return the chunk's length in bytes
     * @since 0.1.0
     */
    public int getLength() {
        return data.length;
    }

    /**
     * Returns whether this is the first chunk. Read in a flow as {@code payload.first}.
     *
     * @return {@code true} for the first chunk
     * @since 0.1.0
     */
    public boolean isFirst() {
        return first;
    }

    /**
     * Returns whether this is the last chunk, including when the source ends exactly at the end of this chunk.
     * Read in a flow as {@code payload.last}.
     *
     * @return {@code true} for the last chunk
     * @since 0.1.0
     */
    public boolean isLast() {
        return last;
    }

    /**
     * Returns the number of records the chunk holds when chunking by delimiter, or {@code null} when chunking by
     * size. A chunk can hold fewer records than were asked for: the last chunk holds what is left, and a chunk ends
     * early when the next record would take it past the maximum chunk size.
     *
     * @return the number of records in the chunk, or {@code null} when chunking by size
     * @since 1.0.0
     */
    public Integer getRecordCount() {
        return recordCount;
    }

    @Override
    public String toString() {
        return "Chunk[index=" + index + ", offset=" + offset + ", length=" + data.length + ", first=" + first
            + ", last=" + last + ", recordCount=" + recordCount + "]";
    }
}
