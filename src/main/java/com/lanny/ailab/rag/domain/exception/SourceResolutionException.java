package com.lanny.ailab.rag.domain.exception;

/**
 * Raised when a source connector can reach a source but cannot transform it
 * into valid ingestion text.
 */
public class SourceResolutionException extends RuntimeException {

    public SourceResolutionException(String message) {
        super(message);
    }

    public SourceResolutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
