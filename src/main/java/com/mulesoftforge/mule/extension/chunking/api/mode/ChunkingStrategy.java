package com.mulesoftforge.mule.extension.chunking.api.mode;

/**
 * How {@code read-chunked} cuts the content into chunks, written inside the operation's
 * {@code chunking-strategy} element as {@code by-size} or {@code by-delimiter}. Without one, the content is cut
 * by size into chunks of 1 MB.
 *
 * @since 1.0.0
 */
public interface ChunkingStrategy {
}
