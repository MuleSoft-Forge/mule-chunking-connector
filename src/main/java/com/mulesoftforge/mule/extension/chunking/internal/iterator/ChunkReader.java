package com.mulesoftforge.mule.extension.chunking.internal.iterator;

import com.mulesoftforge.mule.extension.chunking.api.Chunk;

import java.io.Closeable;
import java.io.IOException;

/**
 * Reads chunks from a source, one at a time, in one of the connector's ways of chunking.
 */
public interface ChunkReader extends Closeable {

    /**
     * Reads the next chunk.
     *
     * @return the next chunk, or {@code null} when the source has no more data
     * @throws IOException if the source cannot be read
     * @throws com.mulesoftforge.mule.extension.chunking.internal.error.ChunkReadException if the data cannot be chunked as configured
     */
    Chunk read() throws IOException;
}
