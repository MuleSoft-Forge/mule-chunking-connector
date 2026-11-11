package com.mulesoftforge.mule.extension.chunking.api.streaming;

import org.mule.runtime.extension.api.annotation.Alias;

/**
 * The chunks can be read once, in order. This is the default.
 *
 * <p>Use it to process each chunk once, as {@code foreach} does. A second pass over the chunks finds none, and a
 * chunk kept in a variable stays valid but costs its memory for as long as it is kept.</p>
 *
 * <p>Memory does not grow with the size of the content. Between reads the connector holds one chunk; while it reads
 * a chunk it needs up to about three times the maximum chunk size (the chunk size, or the maximum chunk size when
 * chunking by delimiter).</p>
 *
 * <pre>
 * &lt;chunking:read-chunked&gt;
 *     &lt;chunking:streaming-strategy&gt;
 *         &lt;chunking:non-repeatable/&gt;
 *     &lt;/chunking:streaming-strategy&gt;
 * &lt;/chunking:read-chunked&gt;
 * </pre>
 *
 * @since 0.2.0
 */
@Alias("non-repeatable")
public class NonRepeatableStrategy implements ChunkingStreamingStrategy {

    @Override
    public String toString() {
        return "non-repeatable";
    }
}
