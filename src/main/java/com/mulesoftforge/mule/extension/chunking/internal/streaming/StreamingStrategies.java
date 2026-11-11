package com.mulesoftforge.mule.extension.chunking.internal.streaming;

import com.mulesoftforge.mule.extension.chunking.api.Chunk;
import com.mulesoftforge.mule.extension.chunking.api.error.ChunkingError;
import com.mulesoftforge.mule.extension.chunking.api.streaming.ChunkingStreamingStrategy;
import com.mulesoftforge.mule.extension.chunking.api.streaming.FileStoreStrategy;
import com.mulesoftforge.mule.extension.chunking.api.streaming.InMemoryStrategy;
import com.mulesoftforge.mule.extension.chunking.api.streaming.SlidingWindowStrategy;
import org.mule.runtime.extension.api.exception.ModuleException;

import java.util.Iterator;

/**
 * Checks a streaming strategy's settings and applies the strategy to the chunks.
 */
public final class StreamingStrategies {

    private StreamingStrategies() {
    }

    /**
     * @throws ModuleException with {@code CHUNKING:INVALID_PARAMETER} for a setting out of range
     */
    public static void validate(ChunkingStreamingStrategy strategy) {
        if (strategy instanceof SlidingWindowStrategy) {
            SlidingWindowStrategy s = (SlidingWindowStrategy) strategy;
            positive("sliding-window maxInMemoryChunks", s.getMaxInMemoryChunks());
            if (s.getReaders() != null && s.getReaders() <= 0) {
                throw new ModuleException("sliding-window readers must be a positive number of readers, or not set "
                    + "(got: " + s.getReaders() + ")", ChunkingError.INVALID_PARAMETER);
            }
        } else if (strategy instanceof InMemoryStrategy) {
            positive("in-memory maxInMemoryChunks", ((InMemoryStrategy) strategy).getMaxInMemoryChunks());
        } else if (strategy instanceof FileStoreStrategy) {
            positive("file-store maxInMemoryChunks", ((FileStoreStrategy) strategy).getMaxInMemoryChunks());
        }
    }

    /**
     * @return the chunks as the strategy holds them: the iterator itself, or a provider of cursors over them
     */
    public static Object apply(ChunkingStreamingStrategy strategy, Iterator<Chunk> chunks) {
        if (strategy instanceof SlidingWindowStrategy) {
            SlidingWindowStrategy s = (SlidingWindowStrategy) strategy;
            return new SlidingWindowCursorIteratorProvider(chunks, s.getMaxInMemoryChunks(), s.getReaders());
        }
        if (strategy instanceof InMemoryStrategy) {
            return RepeatableCursorIteratorProvider.inMemory(chunks,
                                                             ((InMemoryStrategy) strategy).getMaxInMemoryChunks());
        }
        if (strategy instanceof FileStoreStrategy) {
            return RepeatableCursorIteratorProvider.fileStore(chunks,
                                                              ((FileStoreStrategy) strategy).getMaxInMemoryChunks());
        }
        return chunks;
    }

    private static void positive(String setting, int value) {
        if (value <= 0) {
            throw new ModuleException(setting + " must be a positive number of chunks (got: " + value + ")",
                                      ChunkingError.INVALID_PARAMETER);
        }
    }
}
