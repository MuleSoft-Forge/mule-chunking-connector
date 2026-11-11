package com.mulesoftforge.mule.extension.chunking.api.error;

import java.util.Optional;

import org.mule.runtime.extension.api.error.ErrorTypeDefinition;

/**
 * Errors raised by the chunking connector, in the {@code CHUNKING} namespace.
 *
 * <p>The errors for invalid values form a hierarchy: {@code CHUNKING:INVALID_PARAMETER} is the parent of
 * {@code CHUNKING:INVALID_CHUNK_SIZE}, {@code CHUNKING:INVALID_RECORDS_PER_CHUNK} and {@code CHUNKING:INVALID_DELIMITER},
 * so a flow can handle the parent to catch all of them.</p>
 *
 * @since 0.1.0
 */
public enum ChunkingError implements ErrorTypeDefinition<ChunkingError> {

    /**
     * There is no content to read.
     *
     * @since 0.1.0
     */
    READ_ERROR,

    /**
     * A value given to the operation is not valid. Parent of the more specific invalid-value errors, and raised
     * itself for an encoding the runtime does not recognise and for an invalid streaming strategy setting.
     *
     * @since 1.0.0
     */
    INVALID_PARAMETER,

    /**
     * A chunk size or a maximum chunk size is not a positive number of bytes.
     *
     * @since 0.1.0
     */
    INVALID_CHUNK_SIZE(INVALID_PARAMETER),

    /**
     * The number of records per chunk is not positive.
     *
     * @since 1.0.0
     */
    INVALID_RECORDS_PER_CHUNK(INVALID_PARAMETER),

    /**
     * The delimiter is empty.
     *
     * @since 1.0.0
     */
    INVALID_DELIMITER(INVALID_PARAMETER);

    private final ChunkingError parent;

    ChunkingError() {
        this.parent = null;
    }

    ChunkingError(ChunkingError parent) {
        this.parent = parent;
    }

    @Override
    public Optional<ErrorTypeDefinition<? extends Enum<?>>> getParent() {
        return Optional.ofNullable(parent);
    }
}
