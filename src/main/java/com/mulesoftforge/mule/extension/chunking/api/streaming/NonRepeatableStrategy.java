package com.mulesoftforge.mule.extension.chunking.api.streaming;

import java.util.Iterator;
import org.mule.runtime.extension.api.annotation.Alias;
import org.mule.runtime.extension.api.runtime.streaming.StreamingHelper;

/**
 * Non-repeatable streaming strategy with constant O(chunkSize) memory.
 *
 * <p>Chunks are processed once and cannot be re-read. The internal buffer
 * is reused between iterations for maximum memory efficiency.</p>
 *
 * <p><b>Use this strategy when:</b></p>
 * <ul>
 *   <li>Processing large files where memory is a concern</li>
 *   <li>Each chunk is processed immediately and not stored</li>
 *   <li>Repeatability (re-reading chunks) is not required</li>
 *   <li>You're using foreach or similar single-pass patterns</li>
 * </ul>
 *
 * <p><b>⚠️ WARNING: Cannot be used with:</b></p>
 * <ul>
 *   <li>scatter-gather (requires multiple cursors)</li>
 *   <li>Storing chunks in variables or collections</li>
 *   <li>Any operation that needs to re-read chunks</li>
 * </ul>
 *
 * <p><b>Memory Guarantee:</b> O(chunkSize) - only one chunk buffered at a time</p>
 *
 * <p><b>Example:</b></p>
 * <pre>
 * &lt;chunking:read-chunked path="${file}" chunkSize="104857600"&gt;
 *     &lt;chunking:non-repeatable /&gt;
 * &lt;/chunking:read-chunked&gt;
 * &lt;foreach&gt;
 *     &lt;!-- Process each chunk immediately --&gt;
 *     &lt;logger message="Chunk #[payload.index]: #[payload.length] bytes" /&gt;
 * &lt;/foreach&gt;
 * </pre>
 *
 * @since 0.2.0
 */
@Alias("non-repeatable")
public class NonRepeatableStrategy implements ChunkingStreamingStrategy {

    @Override
    public Object apply(Iterator<?> iterator, StreamingHelper streamingHelper) {
        // Return raw iterator - constant memory, single pass
        // No wrapping needed - this is the most memory-efficient mode
        return iterator;
    }
}
