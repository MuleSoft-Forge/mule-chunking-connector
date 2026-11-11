package com.mulesoftforge.mule.extension.chunking.api.mode;

import org.mule.runtime.extension.api.annotation.Alias;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Summary;

/**
 * Cuts the content into chunks of a fixed number of bytes. The last chunk holds what is left.
 *
 * <pre>
 * &lt;chunking:read-chunked&gt;
 *     &lt;chunking:chunking-strategy&gt;
 *         &lt;chunking:by-size chunkSize="5242880"/&gt;
 *     &lt;/chunking:chunking-strategy&gt;
 * &lt;/chunking:read-chunked&gt;
 * </pre>
 *
 * @since 1.0.0
 */
@Alias("by-size")
public class BySizeStrategy implements ChunkingStrategy {

    /**
     * The number of bytes in each chunk but the last, which holds what is left. Default 1048576 (1 MB).
     */
    @Parameter
    @Optional(defaultValue = "1048576")
    @DisplayName("Chunk Size")
    @Summary("Bytes in each chunk; the last chunk holds what is left. Default 1048576 (1 MB).")
    private int chunkSize = 1048576;

    /**
     * Returns the number of bytes in each chunk but the last.
     *
     * @return the chunk size in bytes
     * @since 1.0.0
     */
    public int getChunkSize() {
        return chunkSize;
    }
}
