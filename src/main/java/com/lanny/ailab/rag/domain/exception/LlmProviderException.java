package com.lanny.ailab.rag.domain.exception;

/**
 * Thrown when an LLM or embedding provider call fails after all retry attempts.
 *
 * <p>This is a domain exception — it is declared in the domain layer and caught by
 * {@code GlobalExceptionHandler}, which maps it to HTTP 502 Bad Gateway. Infrastructure
 * adapters must wrap provider-specific exceptions in this type before propagating.
 */
public class LlmProviderException extends RuntimeException {

    /**
     * @param message a description of the failure, suitable for logging
     * @param cause   the original provider exception
     */
    public LlmProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
