package com.mulesoftforge.mule.extension.chunking.api.streaming;

import org.mule.runtime.extension.api.annotation.Alias;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Summary;

/**
 * The chunks can be read any number of times, by any number of readers, because every chunk read is kept in
 * memory, up to {@code maxInMemoryChunks}.
 *
 * <p>The connector keeps the chunks in its own buffer, counted in chunks. When the next chunk would be one more than
 * {@code maxInMemoryChunks}, the read fails with a message giving the limit; no chunk is dropped. With the default of
 * 10 and 1 MB chunks, up to about 10 MB is held. What is held is released when the event that read the chunks
 * ends.</p>
 *
 * <p>{@code maxInMemoryChunks} works like {@code maxBufferSize} on Mule's {@code repeatable-in-memory-iterable},
 * counted in chunks. It configures the connector's own buffer, not Mule's; the buffer grows as chunks arrive, so
 * there is nothing like Mule's {@code initialBufferSize} or {@code bufferSizeIncrement} to set. A failure at the
 * limit reaches the flow as {@code MULE:UNKNOWN} inside {@code foreach}, with the connector's message.</p>
 *
 * <pre>
 * &lt;chunking:read-chunked&gt;
 *     &lt;chunking:streaming-strategy&gt;
 *         &lt;chunking:in-memory maxInMemoryChunks="100"/&gt;
 *     &lt;/chunking:streaming-strategy&gt;
 * &lt;/chunking:read-chunked&gt;
 * </pre>
 *
 * @since 0.2.0
 */
@Alias("in-memory")
public class InMemoryStrategy implements ChunkingStreamingStrategy {

    /**
     * The most chunks held in memory, a positive number; reading one more fails the read. Default 10. Like {@code
     * maxBufferSize} on Mule's {@code repeatable-in-memory-iterable}, for the connector's own buffer.
     */
    @Parameter
    @Optional(defaultValue = "10")
    @DisplayName("Max In-Memory Chunks")
    @Summary("The most chunks held in memory; reading one more fails the read. Default 10. Like maxBufferSize on "
        + "Mule's repeatable-in-memory-iterable, for the connector's own buffer.")
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
        return "in-memory (maxInMemoryChunks " + maxInMemoryChunks + ")";
    }
}
