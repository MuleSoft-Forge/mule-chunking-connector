package com.mulesoftforge.mule.extension.chunking.internal.operation;

import static org.mule.runtime.api.meta.ExpressionSupport.NOT_SUPPORTED;

import com.mulesoftforge.mule.extension.chunking.api.error.ChunkingError;
import com.mulesoftforge.mule.extension.chunking.api.mode.ByDelimiterStrategy;
import com.mulesoftforge.mule.extension.chunking.api.mode.BySizeStrategy;
import com.mulesoftforge.mule.extension.chunking.api.mode.ChunkingStrategy;
import com.mulesoftforge.mule.extension.chunking.api.streaming.ChunkingStreamingStrategy;
import com.mulesoftforge.mule.extension.chunking.api.streaming.NonRepeatableStrategy;
import com.mulesoftforge.mule.extension.chunking.internal.error.ChunkingErrorTypeProvider;
import com.mulesoftforge.mule.extension.chunking.internal.iterator.ByDelimiterChunkReader;
import com.mulesoftforge.mule.extension.chunking.internal.iterator.BySizeChunkReader;
import com.mulesoftforge.mule.extension.chunking.internal.iterator.ChunkIterator;
import com.mulesoftforge.mule.extension.chunking.internal.iterator.ChunkReader;
import com.mulesoftforge.mule.extension.chunking.internal.streaming.StreamingStrategies;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.extension.api.annotation.Alias;
import org.mule.runtime.extension.api.annotation.Expression;
import org.mule.runtime.extension.api.annotation.dsl.xml.ParameterDsl;
import org.mule.runtime.extension.api.annotation.error.Throws;
import org.mule.runtime.extension.api.annotation.param.Content;
import org.mule.runtime.extension.api.annotation.param.MediaType;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Placement;
import org.mule.runtime.extension.api.annotation.param.display.Summary;
import org.mule.runtime.extension.api.exception.ModuleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.Charset;

/**
 * The chunking connector's operation.
 *
 * @since 0.1.0
 */
public class ChunkOperations {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChunkOperations.class);

    /** The chunk size used when no chunking strategy is given, 1 MB. */
    private static final int DEFAULT_CHUNK_SIZE = 1048576;

    /**
     * Reads content as a sequence of chunks, for processing one at a time with {@code foreach}.
     *
     * <p>The chunking strategy says how the content is cut: {@code by-size} or {@code by-delimiter} (a number of
     * delimited records per chunk). With none chosen, the content is cut by size into chunks of 1 MB. The streaming
     * strategy says how the chunks are held: {@code non-repeatable} can be read once, in order; {@code sliding-window}
     * by several readers that read at the same time; {@code in-memory} and {@code file-store} any number of times.
     * With none chosen, the chunks are non-repeatable.</p>
     *
     * <pre>
     * &lt;chunking:read-chunked&gt;
     *     &lt;chunking:chunking-strategy&gt;
     *         &lt;chunking:by-delimiter recordsPerChunk="1000"/&gt;
     *     &lt;/chunking:chunking-strategy&gt;
     * &lt;/chunking:read-chunked&gt;
     * &lt;foreach&gt;
     *     &lt;logger message="#['Chunk ' ++ payload.index ++ ': ' ++ payload.recordCount ++ ' records']"/&gt;
     * &lt;/foreach&gt;
     * </pre>
     *
     * @param content the content to chunk; the message payload by default
     * @param chunkingStrategy how the content is cut into chunks: by-size or by-delimiter; when none is given, by size,
     *        in chunks of 1 MB
     * @param streamingStrategy how the chunks are held: non-repeatable, sliding-window, in-memory or file-store; when
     *        none is given, non-repeatable, so the chunks can be read once, in order
     * @return the chunks, as an iterator or as a repeatable provider of iterators, depending on the streaming
     *         strategy
     * @throws ModuleException if a value given to the operation is invalid, or the content is missing
     * @since 0.1.0
     */
    @DisplayName("Read Chunked")
    @Alias("read-chunked")
    @Throws(ChunkingErrorTypeProvider.class)
    @Summary("Reads content as a sequence of chunks, by size or by delimited records")
    @MediaType(value = MediaType.ANY, strict = false)
    public Object readChunked(
        @Content TypedValue<InputStream> content,

        @Optional
        @ParameterDsl(allowReferences = false)
        @Expression(NOT_SUPPORTED)
        @DisplayName("Chunking Strategy")
        @Summary("How the content is cut into chunks: by-size or by-delimiter. Nothing chosen (None, or the box "
            + "left unticked) means by size, in chunks of 1 MB.")
        @Placement(order = 1)
        ChunkingStrategy chunkingStrategy,

        @Optional
        @ParameterDsl(allowReferences = false)
        @Expression(NOT_SUPPORTED)
        @DisplayName("Streaming Strategy")
        @Summary("How the chunks are held: non-repeatable, sliding-window, in-memory or file-store. Nothing chosen "
            + "(None, or the box left unticked) means non-repeatable: the chunks can be read once, in order.")
        @Placement(order = 2)
        ChunkingStreamingStrategy streamingStrategy) {

        InputStream inputStream = content == null ? null : content.getValue();
        if (inputStream == null) {
            throw new ModuleException("There is no content to chunk (got: null)", ChunkingError.READ_ERROR);
        }
        ChunkingStreamingStrategy strategy = streamingStrategy != null ? streamingStrategy : new NonRepeatableStrategy();
        ChunkReader reader;
        String description;
        if (chunkingStrategy instanceof ByDelimiterStrategy) {
            ByDelimiterStrategy byDelimiter = (ByDelimiterStrategy) chunkingStrategy;
            byte[] delimiter = delimiterBytes(byDelimiter);
            if (byDelimiter.getRecordsPerChunk() <= 0) {
                throw new ModuleException("recordsPerChunk must be a positive number of records (got: "
                    + byDelimiter.getRecordsPerChunk() + ")", ChunkingError.INVALID_RECORDS_PER_CHUNK);
            }
            if (byDelimiter.getMaxChunkSize() <= 0) {
                throw new ModuleException("maxChunkSize must be a positive number of bytes (got: "
                    + byDelimiter.getMaxChunkSize() + ")", ChunkingError.INVALID_CHUNK_SIZE);
            }
            reader = new ByDelimiterChunkReader(inputStream, delimiter, byDelimiter.getRecordsPerChunk(),
                                                byDelimiter.getMaxChunkSize());
            description = "by-delimiter (delimiter " + visible(byDelimiter.getDelimiter()) + " in "
                + byDelimiter.getEncoding() + ", recordsPerChunk " + byDelimiter.getRecordsPerChunk()
                + ", maxChunkSize " + byDelimiter.getMaxChunkSize() + " bytes)";
        } else {
            int chunkSize = chunkingStrategy instanceof BySizeStrategy
                ? ((BySizeStrategy) chunkingStrategy).getChunkSize()
                : DEFAULT_CHUNK_SIZE;
            if (chunkSize <= 0) {
                throw new ModuleException("chunkSize must be a positive number of bytes (got: " + chunkSize + ")",
                                          ChunkingError.INVALID_CHUNK_SIZE);
            }
            reader = new BySizeChunkReader(inputStream, chunkSize);
            description = "by-size (chunkSize " + chunkSize + " bytes)";
        }

        StreamingStrategies.validate(strategy);
        LOGGER.debug("read-chunked: {}, streaming strategy {}", description, strategy);
        return StreamingStrategies.apply(strategy, new ChunkIterator(reader));
    }

    private static byte[] delimiterBytes(ByDelimiterStrategy byDelimiter) {
        String encoding = byDelimiter.getEncoding();
        Charset charset;
        try {
            charset = Charset.forName(encoding);
        } catch (IllegalArgumentException e) { // includes IllegalCharsetNameException and UnsupportedCharsetException
            throw new ModuleException("encoding is not a character encoding this runtime recognises (got: "
                + encoding + ")", ChunkingError.INVALID_PARAMETER);
        }
        String delimiter = byDelimiter.getDelimiter();
        byte[] bytes = delimiter == null ? new byte[0] : delimiter.getBytes(charset);
        if (bytes.length == 0) {
            throw new ModuleException("delimiter must not be empty (got: " + visible(delimiter) + ")",
                                      ChunkingError.INVALID_DELIMITER);
        }
        return bytes;
    }

    /** Renders text with control characters made visible, for messages and the log. */
    static String visible(String text) {
        if (text == null) {
            return "null";
        }
        StringBuilder b = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (Character.isISOControl(c)) {
                        b.append(String.format("\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
            }
        }
        return b.append('"').toString();
    }
}
