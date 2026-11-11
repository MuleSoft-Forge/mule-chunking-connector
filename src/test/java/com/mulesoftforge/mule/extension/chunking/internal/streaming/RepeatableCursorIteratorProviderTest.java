package com.mulesoftforge.mule.extension.chunking.internal.streaming;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mulesoftforge.mule.extension.chunking.api.Chunk;
import com.mulesoftforge.mule.extension.chunking.internal.error.ChunkReadException;
import com.mulesoftforge.mule.extension.chunking.internal.iterator.ByDelimiterChunkReader;
import com.mulesoftforge.mule.extension.chunking.internal.iterator.BySizeChunkReader;
import com.mulesoftforge.mule.extension.chunking.internal.iterator.ChunkIterator;
import com.mulesoftforge.mule.extension.chunking.test.TempFiles;
import org.junit.jupiter.api.Test;
import org.mule.runtime.api.streaming.object.CursorIterator;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

class RepeatableCursorIteratorProviderTest {

    /** Ten chunks of five bytes each. */
    private static ChunkIterator tenChunks() {
        return new ChunkIterator(new BySizeChunkReader(new ByteArrayInputStream(source()), 5));
    }

    private static byte[] source() {
        byte[] data = new byte[50];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }
        return data;
    }

    private static List<Chunk> readAll(RepeatableCursorIteratorProvider provider) {
        List<Chunk> chunks = new ArrayList<>();
        CursorIterator<?> cursor = provider.openCursor();
        while (cursor.hasNext()) {
            chunks.add((Chunk) cursor.next());
        }
        cursor.release();
        return chunks;
    }

    private static byte[] concatenate(List<Chunk> chunks) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (Chunk chunk : chunks) {
            out.write(chunk.getData(), 0, chunk.getLength());
        }
        return out.toByteArray();
    }

    @Test
    void shouldReadEveryChunkAgainWhenInMemory() {
        RepeatableCursorIteratorProvider provider = RepeatableCursorIteratorProvider.inMemory(tenChunks(), 10);

        List<Chunk> first = readAll(provider);
        List<Chunk> second = readAll(provider);

        assertEquals(10, first.size(), "The first pass must read all ten chunks");
        assertArrayEquals(source(), concatenate(second), "A second pass must read the same bytes");
    }

    @Test
    void shouldFailAtMaxInMemoryChunksWhenInMemory() {
        RepeatableCursorIteratorProvider provider = RepeatableCursorIteratorProvider.inMemory(tenChunks(), 4);
        CursorIterator<?> cursor = provider.openCursor();

        for (int i = 0; i < 4; i++) {
            cursor.next();
        }
        ChunkReadException error = assertThrows(ChunkReadException.class, cursor::hasNext,
                                                 "A fifth chunk must exceed maxInMemoryChunks 4");
        assertTrue(error.getMessage().contains("limit of 4 chunks"), "The message must give the limit: " + error.getMessage());
    }

    @Test
    void shouldHoldEveryChunkWhenTheContentIsExactlyMaxInMemoryChunks() {
        RepeatableCursorIteratorProvider provider = RepeatableCursorIteratorProvider.inMemory(tenChunks(), 10);

        assertEquals(10, readAll(provider).size(), "Ten chunks must fit a limit of ten");
        assertEquals(10, readAll(provider).size(), "and be read again");
    }

    @Test
    void shouldWriteChunksBeyondMaxInMemoryChunksToDiskAndReadThemBack() {
        int filesBefore = TempFiles.fileStoreFiles();
        long bytesBefore = TempFiles.fileStoreBytes();
        RepeatableCursorIteratorProvider provider = RepeatableCursorIteratorProvider.fileStore(tenChunks(), 4);

        List<Chunk> first = readAll(provider);
        long onDisk = TempFiles.fileStoreBytes() - bytesBefore;
        List<Chunk> second = readAll(provider);

        assertEquals(10, first.size(), "The first pass must read all ten chunks");
        assertEquals(30, onDisk, "The six oldest chunks of five bytes must be on disk; the four newest in memory");
        assertArrayEquals(source(), concatenate(second), "A second pass must read the same bytes, from disk and memory");
        assertEquals(3, second.get(3).getIndex(), "Chunks read back from disk must keep their index");
        assertEquals(15, second.get(3).getOffset(), "Chunks read back from disk must keep their offset");
        assertTrue(second.get(0).isFirst(), "Chunks read back from disk must keep their flags");

        provider.releaseResources();
        assertEquals(filesBefore, TempFiles.fileStoreFiles(), "Releasing the chunks must delete the temporary file");
    }

    @Test
    void shouldDeleteTemporaryFileWhenProviderIsNoLongerReachable() throws InterruptedException {
        int filesBefore = TempFiles.fileStoreFiles();
        RepeatableCursorIteratorProvider provider = RepeatableCursorIteratorProvider.fileStore(tenChunks(), 1);
        readAll(provider);
        assertEquals(filesBefore + 1, TempFiles.fileStoreFiles(), "Nine chunks must have been written to a file");

        provider = null;

        assertEquals(filesBefore, TempFiles.fileStoreFilesAfterCollection(10_000),
                     "The file must be deleted once the provider is collected, with no release call");
    }

    @Test
    void shouldKeepChunksReadableWhenClosedUntilResourcesAreReleased() {
        int filesBefore = TempFiles.fileStoreFiles();
        RepeatableCursorIteratorProvider provider = RepeatableCursorIteratorProvider.fileStore(tenChunks(), 1);
        CursorIterator<?> open = provider.openCursor();
        readAll(provider);

        provider.close();

        assertEquals(10, countRemaining(open), "A cursor opened before close must still read every chunk");
        assertThrows(IllegalStateException.class, provider::openCursor, "No cursor may be opened after close");
        provider.releaseResources();
        assertEquals(filesBefore, TempFiles.fileStoreFiles(), "Releasing resources must delete the file");
    }

    private static int countRemaining(CursorIterator<?> cursor) {
        int count = 0;
        while (cursor.hasNext()) {
            cursor.next();
            count++;
        }
        return count;
    }

    @Test
    void shouldWriteNothingToDiskWhenAllChunksFitInMemory() {
        int filesBefore = TempFiles.fileStoreFiles();
        RepeatableCursorIteratorProvider provider = RepeatableCursorIteratorProvider.fileStore(tenChunks(), 10);

        readAll(provider);

        assertEquals(filesBefore, TempFiles.fileStoreFiles(), "Ten chunks must fit in memory with no file");
        provider.releaseResources();
    }

    @Test
    void shouldKeepRecordCountWhenChunksAreReadBackFromDisk() {
        ChunkIterator chunks = new ChunkIterator(new ByDelimiterChunkReader(
            new ByteArrayInputStream("a\nb\nc\nd\ne\n".getBytes()), new byte[] {'\n'}, 2, 1024));
        RepeatableCursorIteratorProvider provider = RepeatableCursorIteratorProvider.fileStore(chunks, 1);

        readAll(provider);
        List<Chunk> again = readAll(provider);

        assertEquals(Integer.valueOf(2), again.get(0).getRecordCount(), "recordCount must survive the disk");
        assertFalse(again.get(0).isLast(), "The first chunk must not be marked last");
        provider.releaseResources();
    }

    @Test
    void shouldFreeEveryChunkAndCloseTheContentWhenInMemoryIsReleased() {
        // Arrange
        boolean[] contentClosed = {false};
        ChunkIterator source = new ChunkIterator(new BySizeChunkReader(new ByteArrayInputStream(source()) {
            @Override
            public void close() {
                contentClosed[0] = true;
            }
        }, 5));
        HeldChunks.reset();
        RepeatableCursorIteratorProvider provider = RepeatableCursorIteratorProvider.inMemory(source, 10);
        CursorIterator<?> cursor = provider.openCursor();
        cursor.next();
        cursor.next();
        assertEquals(2, HeldChunks.inMemory(), "Two chunks read must be held");

        // Act
        provider.releaseResources();

        // Assert
        assertEquals(0, HeldChunks.inMemory(), "Nothing may be held once released");
        assertEquals(0, HeldChunks.unreleased(), "The read must count as released");
        assertTrue(contentClosed[0], "The content must be closed when the chunks are released");
        ChunkReadException error = assertThrows(ChunkReadException.class, cursor::hasNext,
                                                 "Reading after release must fail, not look like the end of data");
        assertTrue(error.getMessage().startsWith("in-memory: the chunks were released"), error.getMessage());
    }

    @Test
    void shouldDeleteTheFileAndFailLaterReadsWhenFileStoreIsReleased() {
        // Arrange: a limit of 2, so 8 of 10 chunks go to disk
        int filesBefore = TempFiles.fileStoreFiles();
        HeldChunks.reset();
        RepeatableCursorIteratorProvider provider = RepeatableCursorIteratorProvider.fileStore(tenChunks(), 2);
        CursorIterator<?> cursor = provider.openCursor();
        assertEquals(10, countRemaining(cursor), "Every chunk must be read");
        assertEquals(filesBefore + 1, TempFiles.fileStoreFiles(), "Chunks beyond the limit must be on disk");
        assertEquals(2, HeldChunks.inMemory(), "No more than the limit may be held in memory");

        // Act
        provider.releaseResources();
        provider.releaseResources();

        // Assert
        assertEquals(filesBefore, TempFiles.fileStoreFiles(), "Releasing must delete the file");
        assertEquals(0, HeldChunks.inMemory(), "Nothing may be held once released");
        assertEquals(0, HeldChunks.unreleased(), "Releasing twice must count once");
        assertThrows(IllegalStateException.class, provider::openCursor, "No cursor may be opened once released");
        ChunkReadException error = assertThrows(ChunkReadException.class, () -> {
            cursor.seek(0);
            cursor.hasNext();
        }, "Reading again after release must fail, not look like the end of data");
        assertTrue(error.getMessage().startsWith("file-store: the chunks were released"), error.getMessage());
    }
}
