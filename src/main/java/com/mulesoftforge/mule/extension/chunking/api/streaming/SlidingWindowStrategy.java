package com.mulesoftforge.mule.extension.chunking.api.streaming;

import org.mule.runtime.extension.api.annotation.Alias;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Placement;
import org.mule.runtime.extension.api.annotation.param.display.Summary;

/**
 * Several readers read the chunks, and each chunk is let go once the readers no longer need it, with never more than
 * {@code maxInMemoryChunks} chunks held in memory. Nothing is written to disk.
 *
 * <p>A reader is anything that reads at least one chunk: a {@code foreach}, a {@code scatter-gather} route that reads
 * them, a DataWeave expression such as {@code sizeOf(payload)}.</p>
 *
 * <ul>
 *   <li><b>{@code readers} set</b>: a chunk is let go as soon as that many readers have read it, and never before.
 *   Readers that run at the same time, such as two {@code foreach} routes of a {@code scatter-gather}, keep memory
 *   to the few chunks between the slowest and the fastest.</li>
 *   <li><b>{@code readers} not set</b>: chunks are kept until {@code maxInMemoryChunks} are held; then the oldest that
 *   every reader seen so far has read is let go to make room for the next. A reader that starts within that many
 *   chunks of the first finds every chunk.</li>
 * </ul>
 *
 * <p>The read fails, with a message saying what to change, when the next chunk would be one more than
 * {@code maxInMemoryChunks} because the chunks held are still owed to a reader; when a reader needs a chunk already
 * let go and {@code readers} is not set; and when a reader needs a chunk already let go because more readers read
 * the chunks than {@code readers} says. Readers one after another, such as a {@code foreach} after a
 * {@code foreach}, therefore work while the content is no more than {@code maxInMemoryChunks} chunks. To read the
 * chunks any number of times, use {@code in-memory} or {@code file-store}.</p>
 *
 * <p>What is held is released when the event that read the chunks ends. This strategy has no counterpart among
 * Mule's streaming strategies.</p>
 *
 * <pre>
 * &lt;chunking:read-chunked&gt;
 *     &lt;chunking:streaming-strategy&gt;
 *         &lt;chunking:sliding-window maxInMemoryChunks="5" readers="2"/&gt;
 *     &lt;/chunking:streaming-strategy&gt;
 * &lt;/chunking:read-chunked&gt;
 * </pre>
 *
 * @since 0.2.0
 */
@Alias("sliding-window")
public class SlidingWindowStrategy implements ChunkingStreamingStrategy {

    /**
     * The most chunks held in memory, a hard limit. A read that would hold more fails. Default 3.
     */
    @Parameter
    @Optional(defaultValue = "3")
    @DisplayName("Max In-Memory Chunks")
    @Summary("The most chunks held in memory; a read that would hold more fails. Default 3.")
    @Placement(order = 1)
    private int maxInMemoryChunks = 3;

    /**
     * How many readers read the chunks: each {@code foreach}, {@code scatter-gather} route or expression that reads
     * them. Set, a chunk is let go as soon as that many readers have read it. Not set, chunks are kept until
     * {@code maxInMemoryChunks} are held, and the oldest that every reader so far has read is then let go.
     */
    @Parameter
    @Optional
    @DisplayName("Readers")
    @Summary("How many readers read the chunks (each foreach, route or expression that reads them). Set: a chunk is "
        + "let go once that many have read it. Not set: chunks are kept up to Max In-Memory Chunks, then the oldest "
        + "every reader so far has read is let go.")
    @Placement(order = 2)
    private Integer readers;

    /**
     * Returns the most chunks held in memory.
     *
     * @return the limit on chunks held in memory
     * @since 1.0.0
     */
    public int getMaxInMemoryChunks() {
        return maxInMemoryChunks;
    }

    /**
     * Returns how many readers read the chunks.
     *
     * @return the number of readers, or {@code null} when not set
     * @since 1.0.0
     */
    public Integer getReaders() {
        return readers;
    }

    @Override
    public String toString() {
        return "sliding-window (maxInMemoryChunks " + maxInMemoryChunks
            + (readers == null ? ", readers not set)" : ", readers " + readers + ")");
    }
}
