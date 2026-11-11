package com.mulesoftforge.mule.extension.chunking.internal.iterator;

import com.mulesoftforge.mule.extension.chunking.api.Chunk;
import com.mulesoftforge.mule.extension.chunking.internal.chunk.ChunkFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.Arrays;

/**
 * Cuts the source into chunks of a fixed number of bytes; the last chunk holds what is left.
 */
public final class BySizeChunkReader implements ChunkReader {

    private final PushbackInputStream source;
    private final int chunkSize;
    private int index = 0;
    private long offset = 0;
    private boolean ended = false;

    /**
     * @param source the content to chunk
     * @param chunkSize the number of bytes in each chunk but the last; must be positive
     */
    public BySizeChunkReader(InputStream source, int chunkSize) {
        this.source = new PushbackInputStream(source, 1);
        this.chunkSize = chunkSize;
    }

    @Override
    public Chunk read() throws IOException {
        if (ended) {
            return null;
        }
        byte[] buffer = new byte[chunkSize];
        int read = Sources.readFully(source, buffer, 0, chunkSize);
        if (read == 0) {
            ended = true;
            return null;
        }
        byte[] data;
        boolean last;
        if (read < chunkSize) {
            data = Arrays.copyOf(buffer, read);
            last = true;
        } else {
            data = buffer;
            last = Sources.atEnd(source);
        }
        ended = last;
        Chunk chunk = ChunkFactory.create(data, index, offset, index == 0, last, null);
        index++;
        offset += read;
        return chunk;
    }

    @Override
    public void close() throws IOException {
        ended = true;
        source.close();
    }
}
