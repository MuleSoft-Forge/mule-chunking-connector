package com.mulesoftforge.mule.extension.chunking.internal.iterator;

import com.mulesoftforge.mule.extension.chunking.api.Chunk;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Sources and helpers shared by the reader tests. */
final class TestSources {

    private TestSources() {
    }

    static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    static String text(Chunk chunk) {
        return new String(chunk.getData(), StandardCharsets.UTF_8);
    }

    /** A source that returns at most {@code maxPerRead} bytes from each read call. */
    static InputStream trickle(byte[] data, int maxPerRead) {
        return new ByteArrayInputStream(data) {
            @Override
            public synchronized int read(byte[] b, int off, int len) {
                return super.read(b, off, Math.min(len, maxPerRead));
            }
        };
    }

    static List<Chunk> readAll(ChunkReader reader) throws IOException {
        List<Chunk> chunks = new ArrayList<>();
        Chunk chunk;
        while ((chunk = reader.read()) != null) {
            chunks.add(chunk);
        }
        return chunks;
    }

    static byte[] concatenate(List<Chunk> chunks) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (Chunk chunk : chunks) {
            out.write(chunk.getData(), 0, chunk.getData().length);
        }
        return out.toByteArray();
    }

    static List<String> texts(List<Chunk> chunks) {
        List<String> texts = new ArrayList<>();
        for (Chunk chunk : chunks) {
            texts.add(text(chunk));
        }
        return texts;
    }

    static List<Integer> recordCounts(List<Chunk> chunks) {
        List<Integer> counts = new ArrayList<>();
        for (Chunk chunk : chunks) {
            counts.add(chunk.getRecordCount());
        }
        return counts;
    }
}
