package com.mulesoftforge.mule.extension.chunking.api.mode;

import org.mule.runtime.extension.api.annotation.Alias;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Placement;
import org.mule.runtime.extension.api.annotation.param.display.Summary;

/**
 * Cuts the content into chunks of a number of records, where a record ends with the delimiter.
 *
 * <p>Every chunk is an exact slice of the content: the delimiters stay in the data, a chunk ends with the delimiter
 * that ends its last record, and the chunks in order reproduce the content byte for byte. Two delimiters in a row
 * are an empty record, which counts. Content that ends with a delimiter does not produce an extra empty chunk, and
 * content that ends without one still has its final record. The last chunk holds whatever records are left.</p>
 *
 * <p><b>The maximum chunk size never cuts a record.</b> When the next record would take a chunk past
 * {@code maxChunkSize} bytes, counting the delimiter that ends it, the chunk ends before that record and holds
 * fewer records than {@code recordsPerChunk}; nothing fails. Each chunk's {@code recordCount} says how many records
 * it holds. A single record larger than {@code maxChunkSize} fails when it is reached, with a message saying so.
 * The failure arrives while the flow reads the chunks, so Mule reports it with its own error type:
 * {@code MULE:UNKNOWN} inside {@code foreach}.</p>
 *
 * <pre>
 * &lt;chunking:read-chunked&gt;
 *     &lt;chunking:chunking-strategy&gt;
 *         &lt;chunking:by-delimiter recordsPerChunk="1000"/&gt;
 *     &lt;/chunking:chunking-strategy&gt;
 * &lt;/chunking:read-chunked&gt;
 * </pre>
 *
 * @since 1.0.0
 */
@Alias("by-delimiter")
public class ByDelimiterStrategy implements ChunkingStrategy {

    /**
     * The exact sequence that ends a record, as text or as a DataWeave expression. Default a newline. Escape
     * sequences are not interpreted; a character that cannot be typed is written as an expression, such as {@code
     * #['\r\n']}.
     */
    @Parameter
    @Optional(defaultValue = "#['\\n']")
    @DisplayName("Delimiter")
    @Summary("The exact sequence that ends a record, as text or as an expression. Default a newline. "
        + "Escape sequences are not interpreted; write a character that cannot be typed as an expression.")
    @Placement(order = 1)
    private String delimiter;

    /**
     * The character encoding that turns the delimiter into bytes. Default UTF-8.
     */
    @Parameter
    @Optional(defaultValue = "UTF-8")
    @DisplayName("Encoding")
    @Summary("The character encoding that turns the delimiter into bytes. Default UTF-8.")
    @Placement(order = 2)
    private String encoding;

    /**
     * The number of records in each chunk. A chunk holds fewer when the content ends, or when the next record would
     * take it past the maximum chunk size; each chunk's {@code recordCount} says how many it holds.
     */
    @Parameter
    @DisplayName("Records Per Chunk")
    @Summary("The number of records in each chunk. A chunk holds fewer when the content ends or when the next "
        + "record would take it past the maximum chunk size.")
    @Placement(order = 3)
    private int recordsPerChunk;

    /**
     * The most bytes a chunk may hold, counting the delimiter that ends each record. It never cuts a record: a chunk
     * ends early instead, and a single record larger than this fails. Default 1048576 (1 MB).
     */
    @Parameter
    @Optional(defaultValue = "1048576")
    @DisplayName("Max Chunk Size")
    @Summary("The most bytes a chunk may hold. It never cuts a record: a chunk ends early instead, and a single "
        + "record larger than this fails. Default 1048576 (1 MB).")
    @Placement(order = 4)
    private int maxChunkSize = 1048576;

    /**
     * Returns the sequence that ends a record, as text.
     *
     * @return the delimiter
     * @since 1.0.0
     */
    public String getDelimiter() {
        return delimiter;
    }

    /**
     * Returns the name of the character encoding that turns the delimiter into bytes.
     *
     * @return the encoding name
     * @since 1.0.0
     */
    public String getEncoding() {
        return encoding;
    }

    /**
     * Returns the number of records asked for in each chunk.
     *
     * @return the records per chunk
     * @since 1.0.0
     */
    public int getRecordsPerChunk() {
        return recordsPerChunk;
    }

    /**
     * Returns the most bytes a chunk may hold.
     *
     * @return the maximum chunk size in bytes
     * @since 1.0.0
     */
    public int getMaxChunkSize() {
        return maxChunkSize;
    }
}
