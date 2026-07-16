package com.lanny.ailab.rag.infrastructure.ratelimit;

/**
 * Signals that the distributed rate-limiter backend is temporarily unavailable.
 */
public class RateLimitUnavailableException extends RuntimeException {

    public RateLimitUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
