package com.mulesoftforge.mule.extension.chunking.internal.error;

/**
 * A failure while chunks are being read: the source failed, a record is larger than the maximum chunk size, or a
 * streaming strategy's limit on chunks held in memory was reached.
 *
 * <p>These failures happen after {@code read-chunked} has returned, while a flow reads the chunks. Mule reports them
 * to the flow with its own error type ({@code MULE:UNKNOWN} inside {@code foreach}, {@code MULE:EXPRESSION} inside a
 * DataWeave expression), and the error's description ends with this exception's message. That is why they carry no
 * {@code CHUNKING} error type.</p>
 */
public class ChunkReadException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ChunkReadException(String message) {
        super(message);
    }

    public ChunkReadException(String message, Throwable cause) {
        super(message, cause);
    }
}
