package com.mulesoftforge.mule.extension.chunking.internal.streaming;

import com.mulesoftforge.mule.extension.chunking.api.Chunk;

/**
 * Keeps every chunk read so far, so that any cursor can read any of them again. Calls are made under the owning
 * provider's lock.
 */
interface ChunkStore {

    /**
     * Keeps the next chunk; its position is the number of chunks kept before it.
     *
     * @throws com.mulesoftforge.mule.extension.chunking.internal.error.ChunkReadException if the chunk cannot be kept
     */
    void add(Chunk chunk);

    /** Returns the chunk at a position below {@link #size()}. */
    Chunk get(int position);

    /** Returns the number of chunks kept. */
    int size();

    /** Returns the number of chunks kept in memory. */
    int inMemory();

    /** Returns the streaming strategy's name, for messages. */
    String name();

    /** Discards every chunk and anything written for them. */
    void release();
}
