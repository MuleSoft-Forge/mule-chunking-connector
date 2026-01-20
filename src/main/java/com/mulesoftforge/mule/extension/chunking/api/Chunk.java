package com.mulesoftforge.mule.extension.chunking.api;

import java.util.Arrays;

/**
 * Represents a single chunk of binary data from a larger stream.
 * Immutable - all fields set at construction.
 */
public class Chunk {

    private final byte[] data;
    private final int index;
    private final long offset;
    private final int length;
    private final boolean isFirst;
    private final boolean isLast;

    public Chunk(byte[] data, int index, long offset, int length, boolean isFirst, boolean isLast) {
        this.data = data != null ? Arrays.copyOf(data, length) : new byte[0];
        this.index = index;
        this.offset = offset;
        this.length = length;
        this.isFirst = isFirst;
        this.isLast = isLast;
    }

    /** Raw binary data for this chunk. */
    public byte[] getData() {
        return Arrays.copyOf(data, data.length);
    }

    /** 0-based chunk index. */
    public int getIndex() {
        return index;
    }

    /** Byte offset in the original source stream. */
    public long getOffset() {
        return offset;
    }

    /** Number of bytes in this chunk. */
    public int getLength() {
        return length;
    }

    /** True if this is the first chunk (index == 0). */
    public boolean isFirst() {
        return isFirst;
    }

    /** True if this is the last chunk in the stream. */
    public boolean isLast() {
        return isLast;
    }

    @Override
    public String toString() {
        return String.format("Chunk[index=%d, offset=%d, length=%d, isFirst=%s, isLast=%s]",
                index, offset, length, isFirst, isLast);
    }
}
