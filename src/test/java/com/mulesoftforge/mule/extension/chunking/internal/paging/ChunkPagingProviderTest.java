package com.mulesoftforge.mule.extension.chunking.internal.paging;

import com.mulesoftforge.mule.extension.chunking.api.Chunk;
import com.mulesoftforge.mule.extension.chunking.internal.connection.ChunkingConnection;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChunkPagingProviderTest {

    private final ChunkingConnection connection = new ChunkingConnection();

    @Test
    void testSingleChunk() {
        byte[] input = "Hello, World!".getBytes();
        ChunkPagingProvider provider = new ChunkPagingProvider(new ByteArrayInputStream(input), 1024);

        List<Chunk> page1 = provider.getPage(connection);
        assertEquals(1, page1.size());

        Chunk chunk = page1.get(0);
        assertEquals(0, chunk.getIndex());
        assertEquals(0, chunk.getOffset());
        assertEquals(input.length, chunk.getLength());
        assertTrue(chunk.isFirst());
        assertTrue(chunk.isLast());
        assertArrayEquals(input, chunk.getData());

        // Next page should be empty
        assertTrue(provider.getPage(connection).isEmpty());
    }

    @Test
    void testMultipleChunks() {
        byte[] input = new byte[150];
        for (int i = 0; i < input.length; i++) {
            input[i] = (byte) i;
        }

        ChunkPagingProvider provider = new ChunkPagingProvider(new ByteArrayInputStream(input), 64);
        List<Chunk> allChunks = collectAllChunks(provider);

        assertEquals(3, allChunks.size());

        // First chunk
        assertEquals(0, allChunks.get(0).getIndex());
        assertEquals(64, allChunks.get(0).getLength());
        assertTrue(allChunks.get(0).isFirst());
        assertFalse(allChunks.get(0).isLast());

        // Middle chunk
        assertEquals(1, allChunks.get(1).getIndex());
        assertEquals(64, allChunks.get(1).getOffset());
        assertEquals(64, allChunks.get(1).getLength());
        assertFalse(allChunks.get(1).isFirst());
        assertFalse(allChunks.get(1).isLast());

        // Last chunk (partial)
        assertEquals(2, allChunks.get(2).getIndex());
        assertEquals(128, allChunks.get(2).getOffset());
        assertEquals(22, allChunks.get(2).getLength());  // 150 - 128 = 22
        assertFalse(allChunks.get(2).isFirst());
        assertTrue(allChunks.get(2).isLast());
    }

    @Test
    void testEmptyInput() {
        ChunkPagingProvider provider = new ChunkPagingProvider(new ByteArrayInputStream(new byte[0]), 64);
        assertTrue(provider.getPage(connection).isEmpty());
    }

    @Test
    void testExactChunkAlignment() {
        // CRITICAL TEST: File size = exactly 2 × chunkSize
        byte[] input = new byte[128];
        for (int i = 0; i < input.length; i++) {
            input[i] = (byte) i;
        }

        ChunkPagingProvider provider = new ChunkPagingProvider(new ByteArrayInputStream(input), 64);
        List<Chunk> allChunks = collectAllChunks(provider);

        assertEquals(2, allChunks.size());

        // First chunk
        assertTrue(allChunks.get(0).isFirst());
        assertFalse(allChunks.get(0).isLast());
        assertEquals(64, allChunks.get(0).getLength());

        // Second chunk - MUST be marked as last!
        assertFalse(allChunks.get(1).isFirst());
        assertTrue(allChunks.get(1).isLast(), "Second chunk must be marked as last when file size = 2 × chunkSize");
        assertEquals(64, allChunks.get(1).getLength());
    }

    @Test
    void testExactSingleChunk() {
        // File size = exactly 1 × chunkSize
        byte[] input = new byte[64];
        ChunkPagingProvider provider = new ChunkPagingProvider(new ByteArrayInputStream(input), 64);
        List<Chunk> allChunks = collectAllChunks(provider);

        assertEquals(1, allChunks.size());
        assertTrue(allChunks.get(0).isFirst());
        assertTrue(allChunks.get(0).isLast());
        assertEquals(64, allChunks.get(0).getLength());
    }

    @Test
    void testDataIntegrity() {
        byte[] input = new byte[1000];
        for (int i = 0; i < input.length; i++) {
            input[i] = (byte) (i % 256);
        }

        ChunkPagingProvider provider = new ChunkPagingProvider(new ByteArrayInputStream(input), 100);

        // Reconstruct from chunks
        byte[] reconstructed = new byte[input.length];
        int position = 0;

        for (Chunk chunk : collectAllChunks(provider)) {
            System.arraycopy(chunk.getData(), 0, reconstructed, position, chunk.getLength());
            position += chunk.getLength();
        }

        assertArrayEquals(input, reconstructed);
    }

    private List<Chunk> collectAllChunks(ChunkPagingProvider provider) {
        List<Chunk> allChunks = new ArrayList<>();
        List<Chunk> page;
        while (!(page = provider.getPage(connection)).isEmpty()) {
            allChunks.addAll(page);
        }
        return allChunks;
    }
}
