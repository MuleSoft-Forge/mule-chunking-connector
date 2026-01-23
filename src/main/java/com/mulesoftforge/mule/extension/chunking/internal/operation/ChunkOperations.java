package com.mulesoftforge.mule.extension.chunking.internal.operation;

import com.mulesoftforge.mule.extension.chunking.api.Chunk;
import com.mulesoftforge.mule.extension.chunking.api.error.ChunkingError;
import com.mulesoftforge.mule.extension.chunking.api.streaming.ChunkingStreamingStrategy;
import com.mulesoftforge.mule.extension.chunking.api.streaming.NonRepeatableStrategy;
import com.mulesoftforge.mule.extension.chunking.internal.error.ChunkingErrorTypeProvider;
import com.mulesoftforge.mule.extension.chunking.internal.iterator.ChunkIterator;
import org.mule.runtime.api.meta.ExpressionSupport;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.extension.api.annotation.Alias;
import org.mule.runtime.extension.api.annotation.Expression;
import org.mule.runtime.extension.api.annotation.dsl.xml.ParameterDsl;
import org.mule.runtime.extension.api.annotation.error.Throws;
import org.mule.runtime.extension.api.annotation.param.Content;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Summary;
import org.mule.runtime.extension.api.exception.ModuleException;
import org.mule.runtime.extension.api.runtime.streaming.StreamingHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Iterator;

/**
 * Operations for chunking binary streams.
 *
 * <p><b>v0.2.0 Architecture:</b> TRUE constant memory streaming using buffer reuse.</p>
 *
 * @since 0.1.0
 */
public class ChunkOperations {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChunkOperations.class);

    /**
     * Reads binary content and returns chunks with configurable streaming strategy.
     *
     * <p><b>v0.2.0: Streaming Strategy Support</b></p>
     * <p>Choose between four streaming strategies based on your memory and repeatability needs:</p>
     * <ul>
     *   <li><b>non-repeatable</b> - O(chunkSize) memory, single pass only</li>
     *   <li><b>in-memory</b> - Mule's repeatable buffer, ⚠️ O(totalChunks × chunkSize) memory</li>
     *   <li><b>file-store</b> - Mule EE file-backed buffer, bounded memory with disk I/O</li>
     *   <li><b>sliding-window</b> - Custom bounded repeatability, O(maxCachedChunks × chunkSize)</li>
     * </ul>
     *
     * <h3>Example: Non-repeatable (Constant Memory)</h3>
     * <pre>
     * &lt;chunking:read-chunked chunkSize="104857600"&gt;
     *     &lt;chunking:non-repeatable /&gt;
     * &lt;/chunking:read-chunked&gt;
     * &lt;foreach&gt;
     *     &lt;logger message="Chunk #[payload.index]: #[payload.length] bytes"/&gt;
     * &lt;/foreach&gt;
     * </pre>
     *
     * <h3>Example: Sliding Window (Bounded Repeatable)</h3>
     * <pre>
     * &lt;chunking:read-chunked chunkSize="104857600"&gt;
     *     &lt;chunking:sliding-window maxCachedChunks="5" /&gt;
     * &lt;/chunking:read-chunked&gt;
     * &lt;scatter-gather&gt;
     *     &lt;route&gt;&lt;foreach&gt;...&lt;/foreach&gt;&lt;/route&gt;
     *     &lt;route&gt;&lt;foreach&gt;...&lt;/foreach&gt;&lt;/route&gt;
     * &lt;/scatter-gather&gt;
     * </pre>
     *
     * <h3>Example: In-Memory (⚠️ OOM Risk for Large Files)</h3>
     * <pre>
     * &lt;chunking:read-chunked chunkSize="104857600"&gt;
     *     &lt;chunking:in-memory initialBufferSize="500" maxBufferSize="1000" /&gt;
     * &lt;/chunking:read-chunked&gt;
     * </pre>
     *
     * @param content The input stream to chunk
     * @param chunkSize Size of each chunk in bytes (default: 1MB)
     * @param streamingStrategy Controls memory usage and repeatability (default: non-repeatable)
     * @param streamingHelper Mule's streaming helper for infrastructure delegation
     * @return Iterator or CursorIteratorProvider depending on strategy
     * @since 0.1.0
     */
    @DisplayName("Read Chunked")
    @Alias("read-chunked")
    @Throws(ChunkingErrorTypeProvider.class)
    @Summary("Reads an input stream in chunks with configurable streaming strategy")
    @org.mule.runtime.extension.api.annotation.param.MediaType(value = org.mule.runtime.extension.api.annotation.param.MediaType.ANY, strict = false)
    public Object readChunked(
            @Content TypedValue<InputStream> content,

            @Optional(defaultValue = "1048576")
            @DisplayName("Chunk Size")
            @Summary("Size of each chunk in bytes (default: 1MB = 1048576 bytes)")
            Integer chunkSize,

            @Optional
            @ParameterDsl(allowReferences = false)
            @Expression(ExpressionSupport.NOT_SUPPORTED)
            @DisplayName("Streaming Strategy")
            @Summary("Controls memory usage and repeatability behavior (default: non-repeatable)")
            ChunkingStreamingStrategy streamingStrategy,

            StreamingHelper streamingHelper) {

        // Use provided chunk size or default
        int effectiveChunkSize = (chunkSize != null && chunkSize > 0)
            ? chunkSize
            : 1048576;

        if (effectiveChunkSize <= 0) {
            throw new ModuleException(
                ChunkingError.INVALID_CHUNK_SIZE,
                new IllegalArgumentException("Chunk size must be positive: " + effectiveChunkSize)
            );
        }

        InputStream inputStream = content.getValue();

        if (inputStream == null) {
            throw new ModuleException(
                ChunkingError.READ_ERROR,
                new IllegalArgumentException("Input stream is null")
            );
        }

        // Default to non-repeatable if not specified
        ChunkingStreamingStrategy strategy = streamingStrategy != null
            ? streamingStrategy
            : new NonRepeatableStrategy();

        LOGGER.info("Creating ChunkIterator: chunkSize={} bytes ({} MB), strategy={}",
            effectiveChunkSize, effectiveChunkSize / 1024 / 1024, strategy.getClass().getSimpleName());

        // Determine if strategy needs immutable chunks (caching)
        // Only NonRepeatableStrategy can use buffer reuse for constant memory
        boolean reuseBuffer = strategy instanceof NonRepeatableStrategy;

        LOGGER.debug("Iterator mode: reuseBuffer={} (constant memory: {})",
            reuseBuffer, reuseBuffer);

        // Create the chunk iterator with appropriate mode
        Iterator<Chunk> iterator = new ChunkIterator(inputStream, effectiveChunkSize, reuseBuffer);

        // Apply the streaming strategy
        return strategy.apply(iterator, streamingHelper);
    }
}
