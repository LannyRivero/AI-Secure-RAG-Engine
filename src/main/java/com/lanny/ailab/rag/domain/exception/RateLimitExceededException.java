package com.lanny.ailab.rag.domain.exception;

/**
 * Thrown when a tenant exceeds their configured request rate limit.
 *
 * <p>Caught by {@code GlobalExceptionHandler}, which maps it to HTTP 429 Too Many Requests.
 */
public class RateLimitExceededException extends RuntimeException {

    private final long remainingTokens;
    private final long retryAfterSeconds;

    /**
     * @param tenantId the identifier of the tenant that exceeded the limit
     */
    public RateLimitExceededException(String tenantId) {
        this(tenantId, 0, 0);
    }

    public RateLimitExceededException(String tenantId, long remainingTokens, long retryAfterSeconds) {
        super("Rate limit exceeded");
        this.remainingTokens = remainingTokens;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long remainingTokens() {
        return remainingTokens;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
