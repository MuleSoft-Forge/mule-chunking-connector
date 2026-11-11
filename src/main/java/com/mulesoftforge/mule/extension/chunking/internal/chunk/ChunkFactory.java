package com.mulesoftforge.mule.extension.chunking.internal.chunk;

import com.mulesoftforge.mule.extension.chunking.api.Chunk;

/**
 * Creates chunks. {@code Chunk} has no public constructor; it registers its private one here when
 * its class is initialized, so that only the connector can create chunks.
 */
public final class ChunkFactory {

    /** The shape of {@code Chunk}'s private constructor. */
    @FunctionalInterface
    public interface Constructor {
        Chunk create(byte[] data, int index, long offset, boolean first, boolean last, Integer recordCount);
    }

    private static volatile Constructor constructor;

    private ChunkFactory() {
    }

    /**
     * Called once, from {@code Chunk}'s static initializer.
     *
     * @param chunkConstructor the private constructor of {@code Chunk}
     */
    public static void register(Constructor chunkConstructor) {
        constructor = chunkConstructor;
    }

    /**
     * Creates a chunk.
     *
     * @param data the chunk's bytes, exactly as many as the chunk holds
     * @param index the 0-based chunk index
     * @param offset the byte offset of the chunk in the source
     * @param first whether this is the first chunk
     * @param last whether this is the last chunk
     * @param recordCount the number of records, or {@code null} when chunking by size
     * @return the chunk
     */
    public static Chunk create(byte[] data, int index, long offset, boolean first, boolean last, Integer recordCount) {
        Constructor c = constructor;
        if (c == null) {
            initialize();
            c = constructor;
        }
        return c.create(data, index, offset, first, last, recordCount);
    }

    private static void initialize() {
        try {
            Class.forName(Chunk.class.getName(), true, Chunk.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Chunk class could not be initialized", e);
        }
    }
}
