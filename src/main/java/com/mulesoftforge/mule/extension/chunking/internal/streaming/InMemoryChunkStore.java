package com.mulesoftforge.mule.extension.chunking.internal.streaming;

import com.mulesoftforge.mule.extension.chunking.api.Chunk;
import com.mulesoftforge.mule.extension.chunking.internal.error.ChunkReadException;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps every chunk in memory, up to {@code maxChunks}. The next chunk beyond that fails; no chunk is dropped.
 */
final class InMemoryChunkStore implements ChunkStore {

    private final int maxChunks;
    private List<Chunk> chunks = new ArrayList<>();

    InMemoryChunkStore(int maxChunks) {
        this.maxChunks = maxChunks;
    }

    @Override
    public void add(Chunk chunk) {
        if (chunks.size() >= maxChunks) {
            throw new ChunkReadException("in-memory: the limit of " + maxChunks + " chunks held in memory "
                + "(maxInMemoryChunks) was reached at chunk " + chunks.size() + ". Raise maxInMemoryChunks, or use "
                + "file-store or sliding-window for content this large.");
        }
        chunks.add(chunk);
    }

    @Override
    public Chunk get(int position) {
        return chunks.get(position);
    }

    @Override
    public int size() {
        return chunks.size();
    }

    @Override
    public int inMemory() {
        return chunks.size();
    }

    @Override
    public String name() {
        return "in-memory";
    }

    @Override
    public void release() {
        chunks = new ArrayList<>();
    }
}
