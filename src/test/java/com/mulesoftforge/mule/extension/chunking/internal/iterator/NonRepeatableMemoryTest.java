package com.mulesoftforge.mule.extension.chunking.internal.iterator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mulesoftforge.mule.extension.chunking.api.Chunk;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

/**
 * Evidence that reading chunks one at a time, as the non-repeatable strategy does, holds memory bounded by the
 * chunk size whatever the size of the source.
 *
 * <p>Two measures: how far the reader has read ahead of the chunks it has handed out, which bounds what the reader
 * holds; and the heap in use after a collection, sampled while a large source streams through.</p>
 */
class NonRepeatableMemoryTest {

    private static final long MB = 1024 * 1024;

    /** Generates {@code length} bytes on demand: lines of 99 letters and a newline. Counts what has been read. */
    private static final class GeneratedSource extends InputStream {
        private final long length;
        private long position = 0;

        GeneratedSource(long length) {
            this.length = length;
        }

        @Override
        public int read() {
            if (position >= length) {
                return -1;
            }
            return byteAt(position++);
        }

        @Override
        public int read(byte[] b, int off, int len) {
            if (position >= length) {
                return -1;
            }
            int n = (int) Math.min(len, length - position);
            for (int i = 0; i < n; i++) {
                b[off + i] = (byte) byteAt(position++);
            }
            return n;
        }

        private static int byteAt(long p) {
            return p % 100 == 99 ? '\n' : 'a' + (int) (p % 26);
        }

        long bytesRead() {
            return position;
        }
    }

    private static long usedHeapAfterCollection() {
        Runtime runtime = Runtime.getRuntime();
        System.gc();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static long[] stream(long sourceSize, ChunkReaderFactory factory, int bound) {
        GeneratedSource source = new GeneratedSource(sourceSize);
        ChunkIterator iterator = new ChunkIterator(factory.create(source));
        long baseline = usedHeapAfterCollection();
        long delivered = 0;
        long maxAhead = 0;
        long maxRetained = 0;
        int chunks = 0;
        while (iterator.hasNext()) {
            Chunk chunk = iterator.next();
            delivered += chunk.getLength();
            maxAhead = Math.max(maxAhead, source.bytesRead() - delivered);
            chunks++;
            if (chunks % 64 == 0) {
                maxRetained = Math.max(maxRetained, usedHeapAfterCollection() - baseline);
            }
        }
        assertEquals(sourceSize, delivered, "Every byte of the source must be delivered");
        assertTrue(maxAhead <= bound + 1L,
                   "The reader must never hold more than one maximum chunk ahead of what it handed out; was "
                       + maxAhead + " bytes");
        return new long[] {maxAhead, maxRetained};
    }

    @FunctionalInterface
    private interface ChunkReaderFactory {
        ChunkReader create(InputStream source);
    }

    @Test
    void shouldHoldMemoryBoundedByChunkSizeWhenChunkingBySizeWhateverTheSourceSize() {
        int chunkSize = (int) MB;
        long[] small = stream(32 * MB, s -> new BySizeChunkReader(s, chunkSize), chunkSize);
        long[] large = stream(512 * MB, s -> new BySizeChunkReader(s, chunkSize), chunkSize);

        System.out.printf("by-size, 1 MB chunks: read-ahead %d / %d bytes; retained heap %d / %d bytes "
                              + "(32 MB / 512 MB source)%n", small[0], large[0], small[1], large[1]);
        assertTrue(large[1] < 8 * MB, "Retained heap must stay within a few chunks for a 512 MB source; was "
            + large[1]);
    }

    @Test
    void shouldHoldMemoryBoundedByMaxChunkSizeWhenChunkingByDelimiterWhateverTheSourceSize() {
        int maxChunkSize = (int) MB;
        byte[] newline = {'\n'};
        long[] small = stream(32 * MB, s -> new ByDelimiterChunkReader(s, newline, 10_000, maxChunkSize),
                              maxChunkSize);
        long[] large = stream(512 * MB, s -> new ByDelimiterChunkReader(s, newline, 10_000, maxChunkSize),
                              maxChunkSize);

        System.out.printf("by-delimiter, 1 MB maximum, chunks cut short by the maximum: read-ahead %d / %d bytes; "
                              + "retained heap %d / %d bytes (32 MB / 512 MB source)%n",
                          small[0], large[0], small[1], large[1]);
        assertTrue(large[1] < 8 * MB, "Retained heap must stay within a few chunks for a 512 MB source; was "
            + large[1]);
    }
}
