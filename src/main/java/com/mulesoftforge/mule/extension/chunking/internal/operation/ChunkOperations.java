package com.mulesoftforge.mule.extension.chunking.internal.operation;

import com.mulesoftforge.mule.extension.chunking.api.Chunk;
import com.mulesoftforge.mule.extension.chunking.api.error.ChunkingError;
import com.mulesoftforge.mule.extension.chunking.internal.connection.ChunkingConnection;
import com.mulesoftforge.mule.extension.chunking.internal.error.ChunkingErrorTypeProvider;
import com.mulesoftforge.mule.extension.chunking.internal.paging.ChunkPagingProvider;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.extension.api.annotation.Alias;
import org.mule.runtime.extension.api.annotation.error.Throws;
import org.mule.runtime.extension.api.annotation.param.Content;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Summary;
import org.mule.runtime.extension.api.exception.ModuleException;
import org.mule.runtime.extension.api.runtime.streaming.PagingProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

public class ChunkOperations {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChunkOperations.class);

    /**
     * Reads binary content and returns a PagingProvider of chunks.
     *
     * Use with <foreach> to process chunks one at a time with constant memory.
     * Memory usage is O(chunkSize), not O(fileSize).
     *
     * Example:
     * <pre>
     * &lt;binary-streaming:read-chunked chunkSize="5242880"/&gt;
     * &lt;foreach&gt;
     *     &lt;logger message="Chunk #[payload.index]: #[payload.length] bytes"/&gt;
     *     &lt;s3:upload-part partNumber="#[payload.index + 1]" content="#[payload.data]"/&gt;
     * &lt;/foreach&gt;
     * </pre>
     */
    @DisplayName("Read Chunked")
    @Alias("read-chunked")
    @Throws(ChunkingErrorTypeProvider.class)
    public PagingProvider<ChunkingConnection, Chunk> readChunked(
            @Content TypedValue<InputStream> content,
            @Optional(defaultValue = "65536") @DisplayName("Chunk Size")
            @Summary("Chunk size in bytes (default: 65536 = 64KB).")
            Integer chunkSize) {

        // Use provided chunk size or default
        int effectiveChunkSize = (chunkSize != null && chunkSize > 0)
            ? chunkSize
            : 65536;

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

        LOGGER.debug("Creating ChunkPagingProvider: chunkSize={}", effectiveChunkSize);

        // Create paging provider (lazy - no reading happens yet)
        return new ChunkPagingProvider(inputStream, effectiveChunkSize);
    }
}
