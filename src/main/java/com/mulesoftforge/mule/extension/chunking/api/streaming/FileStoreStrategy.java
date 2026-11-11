package com.mulesoftforge.mule.extension.chunking.api.streaming;

import org.mule.runtime.extension.api.annotation.Alias;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Summary;

/**
 * The chunks can be read any number of times, by any number of readers, with at most {@code maxInMemoryChunks} of
 * them in memory; older chunks are written to a temporary file and read back when a reader needs them.
 *
 * <p>The most recently read chunks stay in memory. The file is created in the directory named by the
 * {@code java.io.tmpdir} system property when the first chunk is written to it, and deleted when the event that read
 * the chunks ends: Mule releases them then, after any asynchronous work the event started, and whether the flow
 * succeeded or failed. The disk space needed is the size of the content less what is held in memory. The connector
 * writes the file itself, so this strategy does not need the Mule Enterprise runtime.</p>
 *
 * <p>{@code maxInMemoryChunks} works like {@code inMemoryObjects} on Mule's {@code repeatable-file-store-iterable},
 * counted in chunks. It configures the connector's own store, not Mule's: the connector keeps the most recent
 * chunks in memory and writes each older chunk to disk as the next one arrives, and its default is 10 where
 * Mule's is 500.</p>
 *
 * <pre>
 * &lt;chunking:read-chunked&gt;
 *     &lt;chunking:streaming-strategy&gt;
 *         &lt;chunking:file-store maxInMemoryChunks="10"/&gt;
 *     &lt;/chunking:streaming-strategy&gt;
 * &lt;/chunking:read-chunked&gt;
 * </pre>
 *
 * @since 0.2.0
 */
@Alias("file-store")
public class FileStoreStrategy implements ChunkingStreamingStrategy {

    /**
     * The most chunks held in memory; older chunks are written to a temporary file and read back when needed.
     * A positive number. Default 10. Like {@code inMemoryObjects} on Mule's {@code repeatable-file-store-iterable}, for the
     * connector's own store.
     */
    @Parameter
    @Optional(defaultValue = "10")
    @DisplayName("Max In-Memory Chunks")
    @Summary("The most chunks held in memory; older chunks go to a temporary file. Default 10. Like "
        + "inMemoryObjects on Mule's repeatable-file-store-iterable, for the connector's own store.")
    private int maxInMemoryChunks = 10;

    /**
     * Returns the most chunks held in memory.
     *
     * @return the limit on chunks held in memory
     * @since 1.0.0
     */
    public int getMaxInMemoryChunks() {
        return maxInMemoryChunks;
    }

    @Override
    public String toString() {
        return "file-store (maxInMemoryChunks " + maxInMemoryChunks + ")";
    }
}
