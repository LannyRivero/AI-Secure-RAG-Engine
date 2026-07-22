package com.lanny.ailab.rag.domain.exception;

/**
 * Raised when a remote source cannot be fetched because the upstream system is
 * unavailable or rejects the request.
 */
public class SourceUnavailableException extends RuntimeException {

    public SourceUnavailableException(String message) {
        super(message);
    }

    public SourceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
