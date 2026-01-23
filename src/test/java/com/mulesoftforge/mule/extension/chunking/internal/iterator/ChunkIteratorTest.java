package com.mulesoftforge.mule.extension.chunking.internal.iterator;

import com.mulesoftforge.mule.extension.chunking.api.Chunk;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ChunkIterator verifying v0.2.0 true constant memory behavior.
 */
class ChunkIteratorTest {

    @Test
    void testSingleChunk() {
        byte[] input = "Hello, World!".getBytes();
        ChunkIterator iterator = new ChunkIterator(new ByteArrayInputStream(input), 1024);

        assertTrue(iterator.hasNext());
        Chunk chunk = iterator.next();

        assertEquals(0, chunk.getIndex());
        assertEquals(0, chunk.getOffset());
        assertEquals(input.length, chunk.getLength());
        assertTrue(chunk.isFirst());
        assertTrue(chunk.isLast());

        // Verify data content
        byte[] chunkData = Arrays.copyOf(chunk.getData(), chunk.getLength());
        assertArrayEquals(input, chunkData);

        assertFalse(iterator.hasNext());
    }

    @Test
    void testMultipleChunks() {
        byte[] input = new byte[150];
        for (int i = 0; i < input.length; i++) {
            input[i] = (byte) i;
        }

        ChunkIterator iterator = new ChunkIterator(new ByteArrayInputStream(input), 64);

        // First chunk
        assertTrue(iterator.hasNext());
        Chunk chunk1 = iterator.next();
        assertEquals(0, chunk1.getIndex());
        assertEquals(64, chunk1.getLength());
        assertTrue(chunk1.isFirst());
        assertFalse(chunk1.isLast());

        // Middle chunk
        assertTrue(iterator.hasNext());
        Chunk chunk2 = iterator.next();
        assertEquals(1, chunk2.getIndex());
        assertEquals(64, chunk2.getOffset());
        assertEquals(64, chunk2.getLength());
        assertFalse(chunk2.isFirst());
        assertFalse(chunk2.isLast());

        // Last chunk (partial)
        assertTrue(iterator.hasNext());
        Chunk chunk3 = iterator.next();
        assertEquals(2, chunk3.getIndex());
        assertEquals(128, chunk3.getOffset());
        assertEquals(22, chunk3.getLength());
        assertFalse(chunk3.isFirst());
        assertTrue(chunk3.isLast());

        assertFalse(iterator.hasNext());
    }

    @Test
    void testEmptyInput() {
        ChunkIterator iterator = new ChunkIterator(new ByteArrayInputStream(new byte[0]), 64);
        assertFalse(iterator.hasNext());
    }

    @Test
    void testExactChunkAlignment() {
        // File size = exactly 2 × chunkSize
        byte[] input = new byte[128];
        for (int i = 0; i < input.length; i++) {
            input[i] = (byte) i;
        }

        ChunkIterator iterator = new ChunkIterator(new ByteArrayInputStream(input), 64);

        // First chunk
        Chunk chunk1 = iterator.next();
        assertTrue(chunk1.isFirst());
        assertFalse(chunk1.isLast());
        assertEquals(64, chunk1.getLength());

        // Second chunk - MUST be marked as last!
        Chunk chunk2 = iterator.next();
        assertFalse(chunk2.isFirst());
        assertTrue(chunk2.isLast(), "Second chunk must be marked as last when file size = 2 × chunkSize");
        assertEquals(64, chunk2.getLength());

        assertFalse(iterator.hasNext());
    }

    @Test
    void testExactSingleChunk() {
        // File size = exactly 1 × chunkSize
        byte[] input = new byte[64];
        ChunkIterator iterator = new ChunkIterator(new ByteArrayInputStream(input), 64);

        Chunk chunk = iterator.next();
        assertTrue(chunk.isFirst());
        assertTrue(chunk.isLast());
        assertEquals(64, chunk.getLength());

        assertFalse(iterator.hasNext());
    }

    @Test
    void testDataIntegrity() {
        byte[] input = new byte[1000];
        for (int i = 0; i < input.length; i++) {
            input[i] = (byte) (i % 256);
        }

        ChunkIterator iterator = new ChunkIterator(new ByteArrayInputStream(input), 100);

        // Reconstruct from chunks - MUST copy immediately!
        byte[] reconstructed = new byte[input.length];
        int position = 0;

        while (iterator.hasNext()) {
            Chunk chunk = iterator.next();
            // Copy data BEFORE next() call
            System.arraycopy(chunk.getData(), 0, reconstructed, position, chunk.getLength());
            position += chunk.getLength();
        }

        assertArrayEquals(input, reconstructed);
    }

    // ============================================
    // v0.2.0 SPECIFIC TESTS: Buffer Reuse Behavior
    // ============================================

    // NOTE: v0.2.0 behavior changed - chunks are now independent with separate buffers
    // This enables caching in sliding-window strategy. The following tests are
    // disabled as they tested the OLD buffer-reuse behavior.

    // @Test - DISABLED: Buffer reuse no longer happens
    // void testBufferReuse_SameBufferInstance() - Now each chunk has its own buffer

    // @Test - DISABLED: Chunk instances are no longer reused
    // void testChunkReuse_SameChunkInstance() - Now each next() returns a new Chunk

    // @Test - DISABLED: Data is no longer overwritten
    // void testDataOverwrittenOnNext() - Chunks are immutable after creation

    @Test
    void testStoringChunksInList_ImmutableMode() {
        // With reuseBuffer=false: Storing chunks in a list WORKS CORRECTLY
        byte[] input = new byte[300];
        for (int i = 0; i < 300; i++) {
            input[i] = (byte) i;
        }

        ChunkIterator iterator = new ChunkIterator(new ByteArrayInputStream(input), 100, false);

        // ✅ CORRECT: Storing chunks in a list now works
        List<Chunk> storedChunks = new ArrayList<>();
        while (iterator.hasNext()) {
            storedChunks.add(iterator.next());
        }

        // Each chunk should have DIFFERENT buffers
        assertEquals(3, storedChunks.size());
        assertNotSame(storedChunks.get(0).getData(), storedChunks.get(1).getData(),
            "Each chunk should have its own buffer");
        assertNotSame(storedChunks.get(1).getData(), storedChunks.get(2).getData(),
            "Each chunk should have its own buffer");

        // Verify data is preserved correctly
        Chunk firstStored = storedChunks.get(0);
        Chunk secondStored = storedChunks.get(1);
        Chunk lastStored = storedChunks.get(2);

        assertEquals(0, firstStored.getIndex());
        assertEquals(1, secondStored.getIndex());
        assertEquals(2, lastStored.getIndex());

        // Verify first chunk's data is correct (0-99)
        for (int i = 0; i < firstStored.getLength(); i++) {
            assertEquals((byte) i, firstStored.getData()[i],
                "First chunk data should be preserved");
        }
    }

    @Test
    void testImmutableMode_BufferSizeOptimized() {
        // With reuseBuffer=false: Buffers are sized to actual data length
        int chunkSize = 1024;
        byte[] input = new byte[2500];  // 2 full chunks + 1 partial

        ChunkIterator iterator = new ChunkIterator(new ByteArrayInputStream(input), chunkSize, false);

        // First two chunks should have full chunkSize
        Chunk chunk1 = iterator.next();
        assertEquals(chunkSize, chunk1.getData().length,
            "Full chunk buffer should match chunk length");
        assertEquals(chunkSize, chunk1.getLength());

        Chunk chunk2 = iterator.next();
        assertEquals(chunkSize, chunk2.getData().length,
            "Full chunk buffer should match chunk length");
        assertEquals(chunkSize, chunk2.getLength());

        // Last chunk should be sized to actual data (452 bytes)
        Chunk chunk3 = iterator.next();
        assertEquals(452, chunk3.getLength());
        assertEquals(452, chunk3.getData().length,
            "Partial chunk buffer should be sized to actual data");

        assertFalse(iterator.hasNext());
    }

    @Test
    void testReuseMode_BufferAlwaysChunkSize() {
        // With reuseBuffer=true: Buffer is always chunkSize (reused)
        int chunkSize = 1024;
        byte[] input = new byte[2500];  // 2 full chunks + 1 partial

        ChunkIterator iterator = new ChunkIterator(new ByteArrayInputStream(input), chunkSize, true);

        // All chunks use the SAME buffer, always chunkSize
        Chunk chunk1 = iterator.next();
        assertEquals(chunkSize, chunk1.getData().length,
            "Reuse mode buffer should always be chunkSize");

        Chunk chunk2 = iterator.next();
        assertEquals(chunkSize, chunk2.getData().length,
            "Reuse mode buffer should always be chunkSize");
        assertSame(chunk1.getData(), chunk2.getData(),
            "Should be same buffer instance");

        Chunk chunk3 = iterator.next();
        assertEquals(chunkSize, chunk3.getData().length,
            "Reuse mode buffer should always be chunkSize (even for last chunk)");
        assertEquals(452, chunk3.getLength(),
            "Last chunk length should be 452 bytes");
        assertSame(chunk1.getData(), chunk3.getData(),
            "Should be same buffer instance");

        assertFalse(iterator.hasNext());
    }

    @Test
    void testClose() throws IOException {
        byte[] input = new byte[100];
        ChunkIterator iterator = new ChunkIterator(new ByteArrayInputStream(input), 64);

        assertFalse(iterator.isClosed());

        iterator.close();
        assertTrue(iterator.isClosed());

        // After close, hasNext should return false
        assertFalse(iterator.hasNext());

        // After close, next should throw
        assertThrows(NoSuchElementException.class, iterator::next);
    }

    @Test
    void testNextAfterExhaustion() {
        byte[] input = new byte[10];
        ChunkIterator iterator = new ChunkIterator(new ByteArrayInputStream(input), 64);

        iterator.next();  // Consume the only chunk
        assertFalse(iterator.hasNext());

        assertThrows(NoSuchElementException.class, iterator::next,
            "Calling next() after exhaustion should throw NoSuchElementException");
    }

    @Test
    void testHasNextMultipleCalls() {
        byte[] input = new byte[100];
        ChunkIterator iterator = new ChunkIterator(new ByteArrayInputStream(input), 64);

        // Multiple hasNext() calls should not advance the iterator
        assertTrue(iterator.hasNext());
        assertTrue(iterator.hasNext());
        assertTrue(iterator.hasNext());

        // First next() should still return the first chunk
        Chunk chunk = iterator.next();
        assertEquals(0, chunk.getIndex());
    }

    @Test
    void testCorrectUsagePattern_ImmediateProcessing() {
        // Demonstrate the CORRECT usage pattern
        byte[] input = new byte[300];
        for (int i = 0; i < 300; i++) {
            input[i] = (byte) (i % 256);
        }

        ChunkIterator iterator = new ChunkIterator(new ByteArrayInputStream(input), 100);

        // ✅ CORRECT: Process or copy data immediately
        List<byte[]> copiedData = new ArrayList<>();
        while (iterator.hasNext()) {
            Chunk chunk = iterator.next();
            // Copy data BEFORE calling next() again
            byte[] copy = Arrays.copyOf(chunk.getData(), chunk.getLength());
            copiedData.add(copy);
        }

        // Verify we got 3 distinct copies
        assertEquals(3, copiedData.size());
        assertNotSame(copiedData.get(0), copiedData.get(1));
        assertNotSame(copiedData.get(1), copiedData.get(2));

        // Verify data integrity
        for (int i = 0; i < 100; i++) {
            assertEquals((byte) i, copiedData.get(0)[i], "First chunk data should match");
        }
        for (int i = 0; i < 100; i++) {
            assertEquals((byte) (100 + i), copiedData.get(1)[i], "Second chunk data should match");
        }
        for (int i = 0; i < 100; i++) {
            assertEquals((byte) (200 + i), copiedData.get(2)[i], "Third chunk data should match");
        }
    }
}
