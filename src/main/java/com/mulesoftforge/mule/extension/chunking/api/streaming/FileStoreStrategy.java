package com.mulesoftforge.mule.extension.chunking.api.streaming;

import java.util.Iterator;
import org.mule.runtime.extension.api.annotation.Alias;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Summary;
import org.mule.runtime.extension.api.runtime.streaming.StreamingHelper;

/**
 * File-store repeatable streaming strategy using Mule's EE infrastructure.
 *
 * <p><b>NOTE: Requires Mule Enterprise Edition</b></p>
 *
 * <p>Buffers chunks to disk when memory threshold is exceeded. Provides
 * repeatability without unbounded memory growth, but accumulates temporary
 * files until the stream is closed.</p>
 *
 * <p><b>Use this strategy when:</b></p>
 * <ul>
 *   <li>You need repeatability (multiple reads/cursors)</li>
 *   <li>The data is too large to fit in memory</li>
 *   <li>You have Mule EE and sufficient disk space</li>
 *   <li>You can tolerate disk I/O performance impact</li>
 * </ul>
 *
 * <p><b>Memory Usage:</b> O(maxInMemoryInstances × chunkSize) - bounded</p>
 * <p><b>Disk Usage:</b> O(totalChunks × chunkSize) - grows until stream closes</p>
 *
 * <p><b>Trade-offs:</b></p>
 * <ul>
 *   <li>✅ Bounded memory usage</li>
 *   <li>✅ Supports repeatability and multiple cursors</li>
 *   <li>✅ Handles files larger than available memory</li>
 *   <li>⚠️ Requires disk space (same as file size)</li>
 *   <li>⚠️ Disk I/O impacts performance</li>
 *   <li>⚠️ Temp files persist until stream closes</li>
 *   <li>❌ Requires Mule EE license</li>
 * </ul>
 *
 * <p><b>Example:</b></p>
 * <pre>
 * &lt;chunking:read-chunked path="${file}" chunkSize="104857600"&gt;
 *     &lt;chunking:file-store maxInMemoryInstances="100" /&gt;
 * &lt;/chunking:read-chunked&gt;
 * &lt;scatter-gather&gt;
 *     &lt;route&gt;&lt;foreach&gt;...&lt;/foreach&gt;&lt;/route&gt;
 *     &lt;route&gt;&lt;foreach&gt;...&lt;/foreach&gt;&lt;/route&gt;
 * &lt;/scatter-gather&gt;
 * </pre>
 *
 * <p><b>Note:</b> The maxInMemoryInstances parameter is currently for
 * documentation only. Mule's streaming infrastructure uses settings from
 * the global streaming configuration. Future versions may support
 * programmatic configuration.</p>
 *
 * <p><b>Recommendation:</b> Consider {@code sliding-window} if your use case
 * allows bounded repeatability (cursors progress at similar rates).</p>
 *
 * @since 0.2.0
 * @see SlidingWindowStrategy
 */
@Alias("file-store")
public class FileStoreStrategy implements ChunkingStreamingStrategy {

    @Parameter
    @Optional(defaultValue = "100")
    @DisplayName("Max In-Memory Instances")
    @Summary("Maximum chunks to keep in memory before spilling to disk")
    private int maxInMemoryInstances;

    @Override
    public Object apply(Iterator<?> iterator, StreamingHelper streamingHelper) {
        // Delegate to Mule's file-store infrastructure (EE only)
        // Note: StreamingHelper uses the runtime's configured factory
        // Our parameter above is for documentation/future use when
        // programmatic configuration becomes available
        return streamingHelper.resolveCursorProvider(iterator);
    }

    public int getMaxInMemoryInstances() {
        return maxInMemoryInstances;
    }
}
