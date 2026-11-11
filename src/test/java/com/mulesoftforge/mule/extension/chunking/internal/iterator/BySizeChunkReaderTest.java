package com.mulesoftforge.mule.extension.chunking.internal.iterator;

import static com.mulesoftforge.mule.extension.chunking.internal.iterator.TestSources.concatenate;
import static com.mulesoftforge.mule.extension.chunking.internal.iterator.TestSources.readAll;
import static com.mulesoftforge.mule.extension.chunking.internal.iterator.TestSources.trickle;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mulesoftforge.mule.extension.chunking.api.Chunk;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

class BySizeChunkReaderTest {

    private static byte[] sequence(int length) {
        byte[] data = new byte[length];
        for (int i = 0; i < length; i++) {
            data[i] = (byte) i;
        }
        return data;
    }

    @Test
    void shouldReturnOneChunkWhenSourceIsSmallerThanChunkSize() throws IOException {
        List<Chunk> chunks = readAll(new BySizeChunkReader(new ByteArrayInputStream(sequence(13)), 1024));

        assertEquals(1, chunks.size(), "A small source must be one chunk");
        assertEquals(13, chunks.get(0).getLength(), "The chunk must hold the whole source");
        assertTrue(chunks.get(0).isFirst() && chunks.get(0).isLast(), "The only chunk must be first and last");
    }

    @Test
    void shouldHoldExactlyTheRemainingBytesWhenLastChunkIsShort() throws IOException {
        List<Chunk> chunks = readAll(new BySizeChunkReader(new ByteArrayInputStream(sequence(150)), 64));

        assertEquals(3, chunks.size(), "150 bytes in 64-byte chunks must give three chunks");
        assertEquals(22, chunks.get(2).getData().length, "The last chunk's data must hold exactly its 22 bytes");
        assertEquals(128, chunks.get(2).getOffset(), "The last chunk must start at byte 128");
        assertFalse(chunks.get(1).isFirst() || chunks.get(1).isLast(), "The middle chunk is neither first nor last");
        assertTrue(chunks.get(2).isLast(), "The short chunk must be marked last");
    }

    @Test
    void shouldMarkLastChunkWhenSourceIsExactMultipleOfChunkSize() throws IOException {
        List<Chunk> chunks = readAll(new BySizeChunkReader(new ByteArrayInputStream(sequence(128)), 64));

        assertEquals(2, chunks.size(), "128 bytes in 64-byte chunks must give two chunks");
        assertTrue(chunks.get(1).isLast(), "The second chunk must be marked last when the source ends exactly there");
    }

    @Test
    void shouldReturnNoChunksWhenSourceIsEmpty() throws IOException {
        assertTrue(readAll(new BySizeChunkReader(new ByteArrayInputStream(new byte[0]), 64)).isEmpty(),
                   "An empty source must produce no chunks");
    }

    @Test
    void shouldReproduceSourceWhenReadsReturnFewerBytesThanAsked() throws IOException {
        byte[] source = sequence(10_000);

        List<Chunk> chunks = readAll(new BySizeChunkReader(trickle(source, 333), 1000));

        assertArrayEquals(source, concatenate(chunks), "Chunks in order must reproduce the source exactly");
        assertEquals(10, chunks.size(), "Short reads must still fill each chunk");
    }

    @Test
    void shouldGiveEachChunkItsOwnDataWhenChunksAreKept() throws IOException {
        List<Chunk> chunks = readAll(new BySizeChunkReader(new ByteArrayInputStream(sequence(300)), 100));

        assertEquals(0, chunks.get(0).getData()[0], "The first chunk must keep its own bytes after later reads");
        assertEquals(100, chunks.get(1).getData()[0], "The second chunk must start at byte 100");
    }

    @Test
    void shouldLeaveRecordCountEmptyWhenChunkingBySize() throws IOException {
        List<Chunk> chunks = readAll(new BySizeChunkReader(new ByteArrayInputStream(sequence(300)), 100));

        for (Chunk chunk : chunks) {
            assertNull(chunk.getRecordCount(), "recordCount must be empty when chunking by size");
        }
    }
}
