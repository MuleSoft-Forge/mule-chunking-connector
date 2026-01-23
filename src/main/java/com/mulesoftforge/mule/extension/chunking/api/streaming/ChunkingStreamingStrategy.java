package com.mulesoftforge.mule.extension.chunking.api.streaming;

import java.util.Iterator;
import org.mule.runtime.extension.api.runtime.streaming.StreamingHelper;

/**
 * Defines a streaming strategy for chunk iteration.
 *
 * <p>Implementations control how chunks are buffered and whether they can be
 * re-read (repeatability). Different strategies offer trade-offs between
 * memory usage and functionality:</p>
 *
 * <ul>
 *   <li><b>non-repeatable</b> - Constant O(chunkSize) memory, single pass</li>
 *   <li><b>in-memory</b> - Mule's repeatable buffer, O(totalChunks × chunkSize) memory</li>
 *   <li><b>file-store</b> - Mule EE file-backed buffer, bounded memory but disk I/O</li>
 *   <li><b>sliding-window</b> - Custom bounded repeatability, O(maxCachedChunks × chunkSize)</li>
 * </ul>
 *
 * @since 0.2.0
 */
public interface ChunkingStreamingStrategy {

    /**
     * Wraps the chunk iterator with the appropriate streaming behavior.
     *
     * @param iterator the raw chunk iterator
     * @param streamingHelper Mule's streaming helper for delegation to runtime infrastructure
     * @return the wrapped iterator or cursor provider
     */
    Object apply(Iterator<?> iterator, StreamingHelper streamingHelper);
}
