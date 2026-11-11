package com.mulesoftforge.mule.extension.chunking.internal.iterator;

import static com.mulesoftforge.mule.extension.chunking.internal.iterator.TestSources.bytes;
import static com.mulesoftforge.mule.extension.chunking.internal.iterator.TestSources.concatenate;
import static com.mulesoftforge.mule.extension.chunking.internal.iterator.TestSources.readAll;
import static com.mulesoftforge.mule.extension.chunking.internal.iterator.TestSources.recordCounts;
import static com.mulesoftforge.mule.extension.chunking.internal.iterator.TestSources.texts;
import static com.mulesoftforge.mule.extension.chunking.internal.iterator.TestSources.trickle;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mulesoftforge.mule.extension.chunking.api.Chunk;
import com.mulesoftforge.mule.extension.chunking.internal.error.ChunkReadException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

class ByDelimiterChunkReaderTest {

    private static final byte[] NEWLINE = bytes("\n");

    private static List<Chunk> chunk(String source, String delimiter, int recordsPerChunk, int maxChunkSize)
        throws IOException {
        return readAll(new ByDelimiterChunkReader(new ByteArrayInputStream(bytes(source)), bytes(delimiter),
                                                  recordsPerChunk, maxChunkSize));
    }

    @Test
    void shouldReproduceSourceByteForByteWhenChunksAreConcatenated() throws IOException {
        // Arrange: random bytes with a two-byte delimiter scattered through them, read in uneven pieces
        Random random = new Random(42);
        byte[] source = new byte[200_000];
        random.nextBytes(source);
        for (int i = 0; i < 2000; i++) {
            int at = random.nextInt(source.length - 1);
            source[at] = '\r';
            source[at + 1] = '\n';
        }

        // Act
        List<Chunk> chunks = readAll(new ByDelimiterChunkReader(trickle(source, 977), bytes("\r\n"), 7, 4096));

        // Assert
        assertArrayEquals(source, concatenate(chunks), "Chunks in order must reproduce the source exactly");
        long expectedOffset = 0;
        for (int i = 0; i < chunks.size(); i++) {
            assertEquals(i, chunks.get(i).getIndex(), "Index must count chunks from 0");
            assertEquals(expectedOffset, chunks.get(i).getOffset(), "Offset must be the chunk's position in the source");
            assertEquals(chunks.get(i).getData().length, chunks.get(i).getLength(), "Length must be the data's length");
            assertTrue(chunks.get(i).getLength() <= 4096, "No chunk may exceed the maximum chunk size");
            expectedOffset += chunks.get(i).getLength();
        }
    }

    @Test
    void shouldNotProduceEmptyChunkWhenSourceEndsWithDelimiter() throws IOException {
        List<Chunk> chunks = chunk("a\nb\nc\nd\n", "\n", 2, 1024);

        assertEquals(Arrays.asList("a\nb\n", "c\nd\n"), texts(chunks), "Chunks must end at the second delimiter");
        assertEquals(Arrays.asList(2, 2), recordCounts(chunks), "Each chunk must hold two records");
        assertTrue(chunks.get(1).isLast(), "The chunk ending at the end of the source must be marked last");
    }

    @Test
    void shouldCountFinalRecordWhenSourceEndsWithoutDelimiter() throws IOException {
        List<Chunk> chunks = chunk("a\nb\nc", "\n", 2, 1024);

        assertEquals(Arrays.asList("a\nb\n", "c"), texts(chunks), "The final record must form the last chunk");
        assertEquals(Arrays.asList(2, 1), recordCounts(chunks), "The final record without a delimiter must count");
    }

    @Test
    void shouldCountEmptyRecordsWhenDelimitersAreConsecutive() throws IOException {
        List<Chunk> chunks = chunk("a\n\n\nb\n", "\n", 3, 1024);

        assertEquals(Arrays.asList("a\n\n\n", "b\n"), texts(chunks), "Empty records must count toward the chunk");
        assertEquals(Arrays.asList(3, 1), recordCounts(chunks), "Two empty records must be counted");
    }

    @Test
    void shouldReturnNoChunksWhenSourceIsEmpty() throws IOException {
        assertTrue(chunk("", "\n", 10, 1024).isEmpty(), "An empty source must produce no chunks");
    }

    @Test
    void shouldMatchWholeSequenceWhenDelimiterIsLongerThanOneByte() throws IOException {
        List<Chunk> chunks = chunk("one||two|three||four||", "||", 2, 1024);

        assertEquals(Arrays.asList("one||two|three||", "four||"), texts(chunks),
                     "A single '|' must not end a record when the delimiter is '||'");
        assertEquals(Arrays.asList(2, 1), recordCounts(chunks), "Records must be counted by the whole delimiter");
    }

    @Test
    void shouldFindDelimiterWhenItStraddlesTwoReads() throws IOException {
        // Arrange: one byte per read, so every multi-byte delimiter arrives across reads
        byte[] source = bytes("alpha<END>beta<END>gamma<END>");

        // Act
        List<Chunk> chunks = readAll(new ByDelimiterChunkReader(trickle(source, 1), bytes("<END>"), 1, 1024));

        // Assert
        assertEquals(Arrays.asList("alpha<END>", "beta<END>", "gamma<END>"), texts(chunks),
                     "A delimiter split across reads must still end its record");
    }

    @Test
    void shouldFindDelimiterWhenItStraddlesBufferGrowth() throws IOException {
        // Arrange: a record that ends with its delimiter split across the reader's first buffer boundary
        StringBuilder record = new StringBuilder();
        while (record.length() < 8 * 1024 - 2) {
            record.append('r');
        }
        String source = record + "<END>" + "tail<END>";

        // Act
        List<Chunk> chunks = readAll(new ByDelimiterChunkReader(trickle(bytes(source), 1000), bytes("<END>"), 1,
                                                                64 * 1024));

        // Assert
        assertEquals(Arrays.asList(record + "<END>", "tail<END>"), texts(chunks),
                     "A delimiter split across the buffer's growth must still end its record");
    }

    @Test
    void shouldEndChunkOnBytesOfGivenDelimiterWhenDelimiterIsNotAscii() throws IOException {
        // Arrange: the delimiter's ISO-8859-1 byte, which is not its UTF-8 encoding
        String section = String.valueOf((char) 0xa7);
        byte[] delimiter = section.getBytes(StandardCharsets.ISO_8859_1);
        byte[] source = ("x" + section + "y" + section).getBytes(StandardCharsets.ISO_8859_1);

        // Act
        List<Chunk> chunks = readAll(new ByDelimiterChunkReader(new ByteArrayInputStream(source), delimiter, 1, 1024));

        // Assert
        assertEquals(2, chunks.size(), "Each record must end at the single-byte delimiter");
        assertArrayEquals(new byte[] {'x', (byte) 0xa7}, chunks.get(0).getData(), "The chunk must keep the delimiter");
    }

    @Test
    void shouldHoldWhatIsLeftWhenFinalChunkIsShorterThanCount() throws IOException {
        List<Chunk> chunks = chunk("1\n2\n3\n4\n5\n", "\n", 3, 1024);

        assertEquals(Arrays.asList(3, 2), recordCounts(chunks), "The last chunk must hold the two records left");
        assertTrue(chunks.get(1).isLast(), "The short final chunk must be marked last");
    }

    @Test
    void shouldEndChunkEarlyAndKeepRecordWholeWhenNextRecordExceedsMaxChunkSize() throws IOException {
        // Arrange: records of 4, 4 and 6 bytes (delimiter included), maximum 10 bytes, 3 records asked for
        List<Chunk> chunks = chunk("aaa\nbbb\nccccc\nd\n", "\n", 3, 10);

        // Assert
        assertEquals(Arrays.asList("aaa\nbbb\n", "ccccc\nd\n"), texts(chunks),
                     "The record that does not fit must start the next chunk whole");
        assertEquals(Arrays.asList(2, 2), recordCounts(chunks),
                     "The chunk ended early must report fewer records than asked for");
    }

    @Test
    void shouldFitRecordWhenItIsExactlyMaxChunkSize() throws IOException {
        List<Chunk> chunks = chunk("123456789\nab\n", "\n", 5, 10);

        assertEquals(Arrays.asList("123456789\n", "ab\n"), texts(chunks),
                     "A record of exactly the maximum, delimiter included, must fit in a chunk");
        assertEquals(Arrays.asList(1, 1), recordCounts(chunks), "Each chunk must hold one record");
    }

    @Test
    void shouldFitFinalRecordWithoutDelimiterWhenItIsExactlyMaxChunkSize() throws IOException {
        List<Chunk> chunks = chunk("0123456789", "\n", 5, 10);

        assertEquals(Arrays.asList("0123456789"), texts(chunks), "A final record of exactly the maximum must fit");
        assertTrue(chunks.get(0).isLast(), "It must be marked last");
    }

    @Test
    void shouldFailSayingRecordIsTooLargeWhenRecordExceedsMaxChunkSize() throws IOException {
        // Arrange
        ByDelimiterChunkReader reader = new ByDelimiterChunkReader(
            new ByteArrayInputStream(bytes("ok\n1234567890\nlater\n")), NEWLINE, 5, 10);

        // Act
        Chunk first = reader.read();
        ChunkReadException error = assertThrows(ChunkReadException.class, reader::read,
                                             "An 11-byte record must not fit a 10-byte maximum");

        // Assert
        assertEquals("ok\n", TestSources.text(first), "The records before the large one must be delivered first");
        assertTrue(error.getMessage().contains("larger than the maximum chunk size of 10 bytes"),
                   "The message must say the record is too large and state the maximum: " + error.getMessage());
    }

    @Test
    void shouldReturnOneChunkWhenSourceHasNoDelimiterAndFitsMaxChunkSize() throws IOException {
        List<Chunk> chunks = chunk("no delimiter here", "\n", 10, 1024);

        assertEquals(Arrays.asList("no delimiter here"), texts(chunks), "The whole source must be one record");
        assertEquals(Arrays.asList(1), recordCounts(chunks), "It must count as one record");
    }

    @Test
    void shouldFailSayingRecordIsTooLargeWhenSourceHasNoDelimiterAndExceedsMaxChunkSize() {
        ByDelimiterChunkReader reader = new ByDelimiterChunkReader(
            new ByteArrayInputStream(bytes("no delimiter in all of this")), NEWLINE, 10, 16);

        ChunkReadException error = assertThrows(ChunkReadException.class, reader::read,
                                             "A source with no delimiter beyond the maximum must fail");
        assertTrue(error.getMessage().contains("larger than the maximum chunk size"),
                   "The message must say the record is too large: " + error.getMessage());
    }

    @Test
    void shouldMarkLastChunkWhenSourceEndsExactlyAtChunkEnd() throws IOException {
        List<Chunk> chunks = chunk("a\nb\nc\nd\n", "\n", 4, 1024);

        assertEquals(1, chunks.size(), "Four records must fit in one chunk");
        assertTrue(chunks.get(0).isFirst(), "The only chunk must be first");
        assertTrue(chunks.get(0).isLast(), "The only chunk must be marked last even though the count was reached");
    }

    @Test
    void shouldNotMarkLastWhenMoreRecordsFollow() throws IOException {
        List<Chunk> chunks = chunk("a\nb\nc\n", "\n", 1, 1024);

        assertEquals(Arrays.asList(false, false, true),
                     Arrays.asList(chunks.get(0).isLast(), chunks.get(1).isLast(), chunks.get(2).isLast()),
                     "Only the final chunk must be marked last");
        assertFalse(chunks.get(1).isFirst(), "Only the first chunk must be marked first");
    }

    @Test
    void shouldReportRecordCountOnEveryChunk() throws IOException {
        List<Chunk> chunks = chunk("1\n2\n3\n4\n5\n6\n7\n", "\n", 3, 6);

        assertEquals(Arrays.asList(3, 3, 1), recordCounts(chunks), "Every chunk must report the records it holds");
    }
}
