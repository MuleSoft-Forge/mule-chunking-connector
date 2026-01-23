package com.mulesoftforge.mule.extension.chunking.api.streaming;

import java.util.Iterator;

import com.mulesoftforge.mule.extension.chunking.internal.streaming.SlidingWindowCursorIteratorProvider;
import org.mule.runtime.extension.api.annotation.Alias;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Summary;
import org.mule.runtime.extension.api.runtime.streaming.StreamingHelper;

/**
 * Sliding window repeatable streaming strategy with bounded memory.
 *
 * <p>Provides repeatability within a sliding window of cached chunks.
 * As all cursors advance past a chunk, it is evicted from memory.
 * This enables multiple cursors (scatter-gather) without unbounded memory growth.</p>
 *
 * <p><b>Use this strategy when:</b></p>
 * <ul>
 *   <li>You need repeatability (multiple reads/cursors)</li>
 *   <li>Cursors process at roughly similar rates (don't diverge widely)</li>
 *   <li>You want bounded memory usage</li>
 *   <li>You can tolerate "evicted data" errors if cursors lag too far</li>
 * </ul>
 *
 * <p><b>Memory Usage:</b> O(maxCachedChunks × chunkSize) - bounded and predictable</p>
 *
 * <p><b>How It Works:</b></p>
 * <ol>
 *   <li>Chunks are cached as they're read from the source</li>
 *   <li>Multiple cursors can read from the cache</li>
 *   <li>When ALL cursors advance past a chunk, it's evicted</li>
 *   <li>If a cursor tries to seek to evicted data, an error is thrown</li>
 * </ol>
 *
 * <p><b>Limitations:</b></p>
 * <ul>
 *   <li>Cannot seek backward to evicted chunks (throws IOException)</li>
 *   <li>If cursors diverge widely, more chunks stay cached</li>
 *   <li>Best when cursors process at similar rates</li>
 *   <li>Not suitable if cursors need random access across entire dataset</li>
 * </ul>
 *
 * <p><b>Example (Two foreach loops in scatter-gather):</b></p>
 * <pre>
 * &lt;chunking:read-chunked path="${file}" chunkSize="104857600"&gt;
 *     &lt;chunking:sliding-window maxCachedChunks="5" /&gt;
 * &lt;/chunking:read-chunked&gt;
 * &lt;scatter-gather&gt;
 *     &lt;route&gt;
 *         &lt;foreach&gt;
 *             &lt;!-- Both routes can read same chunks --&gt;
 *             &lt;logger message="Route 1: #[payload.index]" /&gt;
 *         &lt;/foreach&gt;
 *     &lt;/route&gt;
 *     &lt;route&gt;
 *         &lt;foreach&gt;
 *             &lt;logger message="Route 2: #[payload.index]" /&gt;
 *         &lt;/foreach&gt;
 *     &lt;/route&gt;
 * &lt;/scatter-gather&gt;
 * </pre>
 *
 * <p><b>Sizing Guidelines:</b></p>
 * <ul>
 *   <li>maxCachedChunks = 3-5: Conservative, for well-synchronized cursors</li>
 *   <li>maxCachedChunks = 10-20: Moderate, allows some cursor divergence</li>
 *   <li>maxCachedChunks = 50+: Liberal, but defeats bounded memory purpose</li>
 * </ul>
 *
 * <p><b>Comparison to Alternatives:</b></p>
 * <table>
 *   <tr>
 *     <th>Strategy</th>
 *     <th>Memory</th>
 *     <th>Repeatability</th>
 *     <th>Trade-off</th>
 *   </tr>
 *   <tr>
 *     <td>non-repeatable</td>
 *     <td>O(chunkSize)</td>
 *     <td>None</td>
 *     <td>Constant memory, no scatter-gather</td>
 *   </tr>
 *   <tr>
 *     <td>in-memory</td>
 *     <td>O(totalChunks × chunkSize)</td>
 *     <td>Full</td>
 *     <td>OOM risk for large files</td>
 *   </tr>
 *   <tr>
 *     <td>file-store</td>
 *     <td>O(maxInMemory × chunkSize)</td>
 *     <td>Full</td>
 *     <td>Disk I/O, requires EE</td>
 *   </tr>
 *   <tr>
 *     <td><b>sliding-window</b></td>
 *     <td><b>O(maxCached × chunkSize)</b></td>
 *     <td><b>Bounded</b></td>
 *     <td><b>Eviction errors if cursors diverge</b></td>
 *   </tr>
 * </table>
 *
 * @since 0.2.0
 */
@Alias("sliding-window")
public class SlidingWindowStrategy implements ChunkingStreamingStrategy {

    @Parameter
    @Optional(defaultValue = "3")
    @DisplayName("Max Cached Chunks")
    @Summary("Maximum chunks to keep in the sliding window cache")
    private int maxCachedChunks;

    @Override
    public Object apply(Iterator<?> iterator, StreamingHelper streamingHelper) {
        // Return our custom sliding window cursor provider
        return new SlidingWindowCursorIteratorProvider<>(iterator, maxCachedChunks);
    }

    public int getMaxCachedChunks() {
        return maxCachedChunks;
    }
}
