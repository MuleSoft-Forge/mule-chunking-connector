package com.mulesoftforge.mule.extension.chunking.api.streaming;

/**
 * How {@code read-chunked} holds the chunks it reads, written inside the operation's {@code streaming-strategy}
 * element as {@code non-repeatable}, {@code sliding-window}, {@code in-memory} or {@code file-store}. Without one,
 * the chunks are non-repeatable.
 *
 * <p>Memory is counted in chunks; a chunk is at most the chunk size, or the maximum chunk size when chunking by
 * delimiter.</p>
 *
 * <table>
 *   <caption>The four strategies</caption>
 *   <tr><th>Strategy</th><th>Chunks can be read</th><th>Chunks held in memory</th></tr>
 *   <tr><td>non-repeatable</td><td>once, in order</td><td>one between reads; up to about three while one is
 *   read</td></tr>
 *   <tr><td>sliding-window</td><td>by several readers at the same time</td><td>at most maxInMemoryChunks</td></tr>
 *   <tr><td>in-memory</td><td>any number of times</td><td>every chunk, at most maxInMemoryChunks</td></tr>
 *   <tr><td>file-store</td><td>any number of times</td><td>at most maxInMemoryChunks; the rest on disk</td></tr>
 * </table>
 *
 * <p>What a repeatable strategy holds is released when the event that read the chunks ends.</p>
 *
 * @since 0.2.0
 */
public interface ChunkingStreamingStrategy {
}
