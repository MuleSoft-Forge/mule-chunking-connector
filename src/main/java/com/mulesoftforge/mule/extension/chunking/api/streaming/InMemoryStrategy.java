package com.mulesoftforge.mule.extension.chunking.api.streaming;

import java.util.Iterator;
import org.mule.runtime.extension.api.annotation.Alias;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Summary;
import org.mule.runtime.extension.api.runtime.streaming.StreamingHelper;

/**
 * In-memory repeatable streaming strategy using Mule's infrastructure.
 *
 * <p><b>⚠️ CRITICAL WARNING: UNBOUNDED MEMORY GROWTH</b></p>
 * <p>This strategy buffers ALL chunks in memory. For large files, this WILL
 * cause OutOfMemoryError and application crashes.</p>
 *
 * <p><b>Only use this strategy when:</b></p>
 * <ul>
 *   <li>The total data size is small and fits comfortably in heap memory</li>
 *   <li>You need to re-read chunks multiple times</li>
 *   <li>You need multiple concurrent cursors (e.g., scatter-gather)</li>
 *   <li>You understand and accept the OOM risk</li>
 * </ul>
 *
 * <p><b>Memory Usage:</b> O(totalChunks × chunkSize) - grows unbounded until
 * maxBufferSize is reached (if configured). After maxBufferSize, subsequent
 * reads may fail.</p>
 *
 * <p><b>Example (1GB file, 100MB chunks = 1GB in memory!):</b></p>
 * <pre>
 * &lt;chunking:read-chunked path="${file}" chunkSize="104857600"&gt;
 *     &lt;chunking:in-memory
 *         initialBufferSize="500"
 *         bufferSizeIncrement="100"
 *         maxBufferSize="1000" /&gt;
 * &lt;/chunking:read-chunked&gt;
 * &lt;scatter-gather&gt;
 *     &lt;route&gt;&lt;foreach&gt;...&lt;/foreach&gt;&lt;/route&gt;
 *     &lt;route&gt;&lt;foreach&gt;...&lt;/foreach&gt;&lt;/route&gt;
 * &lt;/scatter-gather&gt;
 * </pre>
 *
 * <p><b>Note:</b> The configuration parameters (initialBufferSize, etc.) are
 * currently for documentation only. Mule's streaming infrastructure uses
 * settings from the global streaming configuration. Future versions may
 * support programmatic configuration.</p>
 *
 * <p><b>Recommendation:</b> Use {@code sliding-window} instead for bounded
 * memory with repeatability.</p>
 *
 * @since 0.2.0
 * @see SlidingWindowStrategy
 */
@Alias("in-memory")
public class InMemoryStrategy implements ChunkingStreamingStrategy {

    @Parameter
    @Optional(defaultValue = "500")
    @DisplayName("Initial Buffer Size")
    @Summary("Initial number of chunks to buffer in memory")
    private int initialBufferSize;

    @Parameter
    @Optional(defaultValue = "100")
    @DisplayName("Buffer Size Increment")
    @Summary("Number of chunks to add when buffer needs to expand")
    private int bufferSizeIncrement;

    @Parameter
    @Optional(defaultValue = "1000")
    @DisplayName("Max Buffer Size")
    @Summary("Maximum number of chunks to buffer (0 = unlimited, EXTREME OOM RISK)")
    private int maxBufferSize;

    @Override
    public Object apply(Iterator<?> iterator, StreamingHelper streamingHelper) {
        // Delegate to Mule's in-memory repeatable infrastructure
        // Note: StreamingHelper uses the runtime's configured factory
        // Our parameters above are for documentation/future use when
        // programmatic configuration becomes available
        return streamingHelper.resolveCursorProvider(iterator);
    }

    // Getters for potential future programmatic configuration

    public int getInitialBufferSize() {
        return initialBufferSize;
    }

    public int getBufferSizeIncrement() {
        return bufferSizeIncrement;
    }

    public int getMaxBufferSize() {
        return maxBufferSize;
    }
}
