package com.mulesoftforge.mule.extension.chunking.api.error;

import org.mule.runtime.extension.api.error.ErrorTypeDefinition;

public enum ChunkingError implements ErrorTypeDefinition<ChunkingError> {
    READ_ERROR,
    INVALID_CHUNK_SIZE
}
