package com.lanny.ailab.rag.infrastructure.ratelimit;

/**
 * Signals that the distributed rate-limiter backend is temporarily unavailable.
 *
 * <p>The application treats this as a fail-closed condition and returns HTTP 503
 * instead of falling back to per-instance buckets.
 */
public class RateLimitUnavailableException extends RuntimeException {

    public RateLimitUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
