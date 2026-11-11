package com.mulesoftforge.mule.extension.chunking.internal.streaming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mulesoftforge.mule.extension.chunking.api.Chunk;
import com.mulesoftforge.mule.extension.chunking.internal.error.ChunkReadException;
import com.mulesoftforge.mule.extension.chunking.internal.iterator.ByDelimiterChunkReader;
import com.mulesoftforge.mule.extension.chunking.internal.iterator.BySizeChunkReader;
import com.mulesoftforge.mule.extension.chunking.internal.iterator.ChunkIterator;
import org.junit.jupiter.api.Test;
import org.mule.runtime.api.streaming.object.CursorIterator;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

class SlidingWindowCursorIteratorProviderTest {

    /** {@code count} chunks of one byte each; the byte of chunk {@code i} is {@code i % 128}. */
    private static ChunkIterator chunks(int count) {
        byte[] data = new byte[count];
        for (int i = 0; i < count; i++) {
            data[i] = (byte) (i % 128);
        }
        return new ChunkIterator(new BySizeChunkReader(new ByteArrayInputStream(data), 1));
    }

    private static SlidingWindowCursorIteratorProvider window(int count, int limit, Integer readers) {
        return new SlidingWindowCursorIteratorProvider(chunks(count), limit, readers);
    }

    private static List<Integer> readAll(CursorIterator<?> cursor) {
        List<Integer> indexes = new ArrayList<>();
        while (cursor.hasNext()) {
            indexes.add(((Chunk) cursor.next()).getIndex());
        }
        return indexes;
    }

    private static List<Integer> upTo(int count) {
        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            indexes.add(i);
        }
        return indexes;
    }

    // readers set

    @Test
    void shouldLetGoOfEachChunkAsSoonAsEveryDeclaredReaderHasReadItWhenReadersIsSet() {
        // Arrange
        SlidingWindowCursorIteratorProvider provider = window(100, 3, 2);
        CursorIterator<?> first = provider.openCursor();
        CursorIterator<?> second = provider.openCursor();
        int maxHeld = 0;
        int read = 0;

        // Act: the two readers keep pace
        while (first.hasNext()) {
            first.next();
            maxHeld = Math.max(maxHeld, provider.held());
            second.next();
            assertEquals(0, provider.held(), "A chunk both readers have read must be let go at once");
            read++;
        }

        // Assert
        assertEquals(100, read, "Both readers must read all 100 chunks");
        assertEquals(1, maxHeld, "Readers in step must hold no more than the one chunk between them");
    }

    @Test
    void shouldKeepChunksForADeclaredReaderThatStartsLateWhenReadersIsSet() {
        // Arrange: the first reader reads 4 of 10 chunks before the second opens its cursor
        SlidingWindowCursorIteratorProvider provider = window(10, 5, 2);
        CursorIterator<?> first = provider.openCursor();
        for (int i = 0; i < 4; i++) {
            first.next();
        }

        // Act
        CursorIterator<?> second = provider.openCursor();
        List<Integer> secondRead = new ArrayList<>();
        List<Integer> firstRead = new ArrayList<>(upTo(4));
        while (second.hasNext()) {
            secondRead.add(((Chunk) second.next()).getIndex());
            if (first.hasNext()) {
                firstRead.add(((Chunk) first.next()).getIndex());
            }
        }

        // Assert
        assertEquals(upTo(10), secondRead, "The late reader must read every chunk");
        assertEquals(upTo(10), firstRead, "The first reader must read every chunk");
        assertEquals(0, provider.held(), "Every chunk has been read by both readers and must be let go");
    }

    @Test
    void shouldFailNamingTheLimitWhenChunksOwedToADeclaredReaderWouldExceedItWhenReadersIsSet() {
        // Arrange: readers 2, but only one reads; it holds chunks 0 to 2, owed to the other
        SlidingWindowCursorIteratorProvider provider = window(10, 3, 2);
        CursorIterator<?> only = provider.openCursor();
        only.next();
        only.next();
        only.next();

        // Act
        ChunkReadException error = assertThrows(ChunkReadException.class, only::hasNext,
                                                 "Chunk 3 would be a fourth chunk held, over the limit of 3");

        // Assert
        String message = error.getMessage();
        assertTrue(message.contains("maxInMemoryChunks (3)"), "The message must name the limit: " + message);
        assertTrue(message.contains("readers is 2"), "The message must say how many readers are declared: " + message);
        assertTrue(message.contains("Raise maxInMemoryChunks") && message.contains("file-store"),
                   "The message must point to a higher limit or file-store: " + message);
        assertEquals(3, provider.held(), "The limit must never be exceeded");
    }

    @Test
    void shouldFailSayingSoWhenAReaderBeyondTheDeclaredNumberNeedsAChunkAlreadyLetGo() {
        // Arrange: readers 1; the one declared reader reads everything
        SlidingWindowCursorIteratorProvider provider = window(5, 3, 1);
        assertEquals(upTo(5), readAll(provider.openCursor()), "The declared reader must read every chunk");

        // Act
        CursorIterator<?> extra = provider.openCursor();
        ChunkReadException error = assertThrows(ChunkReadException.class, extra::hasNext,
                                                 "A second reader must not find the chunks the first let go");

        // Assert
        String message = error.getMessage();
        assertTrue(message.contains("one more than readers allows"), "The message must say the read is one too "
            + "many: " + message);
        assertTrue(message.contains("Set readers"), "The message must say what to change: " + message);
    }

    // readers not set

    @Test
    void shouldStayWithinTheLimitWhenASingleReaderReadsContentLargerThanItWhenReadersIsNotSet() {
        // Arrange
        SlidingWindowCursorIteratorProvider provider = window(1000, 3, null);
        CursorIterator<?> cursor = provider.openCursor();
        int maxHeld = 0;
        int read = 0;

        // Act
        while (cursor.hasNext()) {
            cursor.next();
            maxHeld = Math.max(maxHeld, provider.held());
            read++;
        }

        // Assert
        assertEquals(1000, read, "The reader must read all 1000 chunks");
        assertEquals(3, maxHeld, "Chunks are kept up to the limit and no further");
    }

    @Test
    void shouldServeASecondReaderThatStartsWithinTheWindowWhenReadersIsNotSet() {
        // Arrange: the first reader is 2 chunks ahead when the second starts; the limit is 3
        SlidingWindowCursorIteratorProvider provider = window(50, 3, null);
        CursorIterator<?> first = provider.openCursor();
        first.next();
        first.next();

        // Act
        CursorIterator<?> second = provider.openCursor();
        List<Integer> secondRead = new ArrayList<>();
        while (second.hasNext()) {
            secondRead.add(((Chunk) second.next()).getIndex());
            if (first.hasNext()) {
                first.next();
            }
        }

        // Assert
        assertEquals(upTo(50), secondRead, "A reader starting within the window must read every chunk");
    }

    @Test
    void shouldFailSayingToSetReadersWhenASecondReaderStartsBeyondTheWindowWhenReadersIsNotSet() {
        // Arrange: the first reader reads 10 chunks under a limit of 3, so chunks 0 to 6 are let go
        SlidingWindowCursorIteratorProvider provider = window(20, 3, null);
        CursorIterator<?> first = provider.openCursor();
        for (int i = 0; i < 10; i++) {
            first.next();
        }

        // Act
        CursorIterator<?> late = provider.openCursor();
        ChunkReadException error = assertThrows(ChunkReadException.class, late::hasNext,
                                                 "Chunk 0 was let go before the late reader started");

        // Assert
        String message = error.getMessage();
        assertTrue(message.contains("readers is not set"), "The message must say readers is not set: " + message);
        assertTrue(message.contains("Set readers"), "The message must say to set readers: " + message);
    }

    @Test
    void shouldReadEverythingTwiceWhenALoopFollowsALoopOverContentWithinTheLimitWhenReadersIsNotSet() {
        // Arrange
        SlidingWindowCursorIteratorProvider provider = window(5, 5, null);

        // Act
        List<Integer> firstPass = readAll(provider.openCursor());
        List<Integer> secondPass = readAll(provider.openCursor());

        // Assert
        assertEquals(upTo(5), firstPass, "The first loop must read every chunk");
        assertEquals(upTo(5), secondPass, "The second loop must read every chunk again");
    }

    @Test
    void shouldFailNamingTheLimitWhenASeenReaderFallsMoreThanTheLimitBehindWhenReadersIsNotSet() {
        // Arrange: two readers seen; the second stops after chunk 0, so chunk 0 can be let go but chunk 1 cannot
        SlidingWindowCursorIteratorProvider provider = window(10, 3, null);
        CursorIterator<?> leading = provider.openCursor();
        CursorIterator<?> lagging = provider.openCursor();
        lagging.next();
        for (int i = 0; i < 4; i++) {
            leading.next();
        }

        // Act
        ChunkReadException error = assertThrows(ChunkReadException.class, leading::hasNext,
                                                 "Chunks 1 to 3 are owed to the lagging reader");

        // Assert
        assertTrue(error.getMessage().contains("maxInMemoryChunks (3)"), "The message must name the limit: "
            + error.getMessage());
        assertEquals(3, provider.held(), "The limit must never be exceeded");
    }

    // what counts as a reader

    @Test
    void shouldNotLetACursorThatNeverReadsHoldChunksBack() {
        // Arrange: a cursor opened and never read, as DataWeave's type check opens one, in each mode
        for (Integer readers : Arrays.asList(null, 1)) {
            SlidingWindowCursorIteratorProvider provider = window(100, 3, readers);
            CursorIterator<?> neverRead = provider.openCursor();
            neverRead.hasNext();

            // Act
            List<Integer> read = readAll(provider.openCursor());

            // Assert
            assertEquals(upTo(100), read, "A cursor that never reads must not hold chunks back (readers " + readers
                + ")");
            assertTrue(provider.held() <= 3, "No more than the limit may be held (readers " + readers + ")");
        }
    }

    @Test
    void shouldHoldNoMoreThanTheLimitWhenChunkingByDelimiter() {
        // Arrange
        SlidingWindowCursorIteratorProvider provider = new SlidingWindowCursorIteratorProvider(
            new ChunkIterator(new ByDelimiterChunkReader(new ByteArrayInputStream(new byte[100_000]), new byte[] {0},
                                                         1000, 1000)), 3, null);
        CursorIterator<?> cursor = provider.openCursor();
        int maxHeld = 0;
        int read = 0;

        // Act
        while (cursor.hasNext()) {
            cursor.next();
            maxHeld = Math.max(maxHeld, provider.held());
            read++;
        }

        // Assert
        assertEquals(100, read, "All 100 chunks must be read");
        assertTrue(maxHeld <= 3, "No more than maxInMemoryChunks 3 chunks may be held; held " + maxHeld);
    }

    // release

    @Test
    void shouldFreeEverythingAndCloseTheContentWhenReleased() {
        // Arrange
        AtomicBoolean contentClosed = new AtomicBoolean(false);
        ChunkIterator source = new ChunkIterator(new BySizeChunkReader(new ByteArrayInputStream(new byte[10]) {
            @Override
            public void close() throws IOException {
                contentClosed.set(true);
                super.close();
            }
        }, 1));
        SlidingWindowCursorIteratorProvider provider = new SlidingWindowCursorIteratorProvider(source, 5, null);
        CursorIterator<?> cursor = provider.openCursor();
        cursor.next();
        cursor.next();

        // Act
        provider.close();
        provider.releaseResources();

        // Assert
        assertEquals(0, provider.held(), "Nothing may be held once released");
        assertTrue(contentClosed.get(), "The content must be closed when the chunks are released");
        assertTrue(provider.isClosed(), "A released provider is closed");
        assertThrows(IllegalStateException.class, provider::openCursor, "No cursor may be opened once closed");
        ChunkReadException error = assertThrows(ChunkReadException.class, cursor::hasNext,
                                                 "Reading after release must fail, not look like the end of data");
        assertTrue(error.getMessage().contains("released"), "The message must say the chunks were released: "
            + error.getMessage());
    }

    @Test
    void shouldLetGoWhatAReleasedReaderHeldBackWhenReadersIsSet() {
        // Arrange: readers 2; the first stops after chunk 0 and is released
        SlidingWindowCursorIteratorProvider provider = window(10, 3, 2);
        CursorIterator<?> stopped = provider.openCursor();
        stopped.next();
        stopped.release();

        // Act
        List<Integer> read = readAll(provider.openCursor());

        // Assert
        assertEquals(upTo(10), read, "The other reader must read every chunk");
        assertFalse(provider.held() > 0, "A released reader holds nothing back");
    }
}
