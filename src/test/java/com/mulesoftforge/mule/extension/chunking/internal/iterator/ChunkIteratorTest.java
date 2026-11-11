package com.mulesoftforge.mule.extension.chunking.internal.iterator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mulesoftforge.mule.extension.chunking.api.Chunk;
import com.mulesoftforge.mule.extension.chunking.internal.error.ChunkReadException;
import com.mulesoftforge.mule.extension.chunking.test.FailingInputStreams;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;

class ChunkIteratorTest {

    @Test
    void shouldReadNextChunkInHasNextSoThatNextDoesNoReading() {
        // Arrange
        ChunkIterator iterator = new ChunkIterator(
            new BySizeChunkReader(FailingInputStreams.failAfter(150), 100));

        // Act: the first chunk is read by hasNext; the second read fails, and must fail in hasNext
        assertTrue(iterator.hasNext(), "The first 100 bytes must be readable");
        Chunk first = iterator.next();
        ChunkReadException error = assertThrows(ChunkReadException.class, iterator::hasNext,
                                             "The failure must be raised by hasNext, not by next");

        // Assert
        assertEquals(100, first.getLength(), "The first chunk must be whole");
        assertTrue(error.getMessage().contains("Simulated source failure"),
                   "The message must carry the source's message: " + error.getMessage());
    }

    @Test
    void shouldRaiseFailureAgainAndNeverReportEndWhenReadingHasFailed() {
        ChunkIterator iterator = new ChunkIterator(new BySizeChunkReader(FailingInputStreams.failAfter(10), 100));

        ChunkReadException first = assertThrows(ChunkReadException.class, iterator::hasNext, "Reading must fail");
        ChunkReadException second = assertThrows(ChunkReadException.class, iterator::hasNext,
                                               "A failed iterator must not report the end of the data");

        assertSame(first, second, "The same failure must be raised again");
        assertThrows(ChunkReadException.class, iterator::next, "next must raise the failure too");
    }

    @Test
    void shouldNotAdvanceWhenHasNextIsCalledRepeatedly() {
        ChunkIterator iterator = new ChunkIterator(new BySizeChunkReader(new ByteArrayInputStream(new byte[150]), 100));

        assertTrue(iterator.hasNext(), "There must be a first chunk");
        assertTrue(iterator.hasNext(), "A second call must not consume it");
        assertEquals(0, iterator.next().getIndex(), "next must return the first chunk");
        assertEquals(1, iterator.next().getIndex(), "next must read on its own when hasNext was not called");
        assertFalse(iterator.hasNext(), "There must be no third chunk");
        assertThrows(NoSuchElementException.class, iterator::next, "next after the end must throw");
    }

    @Test
    void shouldCloseSourceWhenDataEnds() {
        AtomicBoolean closed = new AtomicBoolean();
        InputStream source = new ByteArrayInputStream(new byte[10]) {
            @Override
            public void close() throws IOException {
                closed.set(true);
                super.close();
            }
        };
        ChunkIterator iterator = new ChunkIterator(new BySizeChunkReader(source, 100));

        iterator.next();
        assertFalse(iterator.hasNext(), "There must be one chunk");

        assertTrue(closed.get(), "The source must be closed once the data ends");
    }

    @Test
    void shouldReportNoMoreChunksWhenClosed() {
        ChunkIterator iterator = new ChunkIterator(new BySizeChunkReader(new ByteArrayInputStream(new byte[150]), 100));

        iterator.close();

        assertFalse(iterator.hasNext(), "A closed iterator must report no more chunks");
        assertThrows(NoSuchElementException.class, iterator::next, "next on a closed iterator must throw");
    }
}
