package com.lanny.ailab.rag.domain.exception;

/**
 * Thrown when a tenant exceeds their configured request rate limit.
 *
 * <p>Caught by {@code GlobalExceptionHandler}, which maps it to HTTP 429 Too Many Requests.
 */
public class RateLimitExceededException extends RuntimeException {

    /**
     * @param tenantId the identifier of the tenant that exceeded the limit
     */
    public RateLimitExceededException(String tenantId) {
        super("Rate limit exceeded");
    }
}
