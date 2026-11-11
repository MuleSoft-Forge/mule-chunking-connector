package com.mulesoftforge.mule.extension.chunking.internal.error;

import com.mulesoftforge.mule.extension.chunking.api.error.ChunkingError;
import org.mule.runtime.extension.api.annotation.error.ErrorTypeProvider;
import org.mule.runtime.extension.api.error.ErrorTypeDefinition;

import java.util.HashSet;
import java.util.Set;

public class ChunkingErrorTypeProvider implements ErrorTypeProvider {

    @Override
    public Set<ErrorTypeDefinition> getErrorTypes() {
        Set<ErrorTypeDefinition> errors = new HashSet<>();
        errors.add(ChunkingError.READ_ERROR);
        errors.add(ChunkingError.INVALID_PARAMETER);
        errors.add(ChunkingError.INVALID_CHUNK_SIZE);
        errors.add(ChunkingError.INVALID_RECORDS_PER_CHUNK);
        errors.add(ChunkingError.INVALID_DELIMITER);
        return errors;
    }
}
