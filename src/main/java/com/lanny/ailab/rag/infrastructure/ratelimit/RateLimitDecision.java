package com.lanny.ailab.rag.infrastructure.ratelimit;

import java.util.concurrent.TimeUnit;

/**
 * Result of attempting to consume a token from a tenant rate-limit bucket.
 */
public record RateLimitDecision(boolean allowed, long remainingTokens, long nanosToWaitForRefill) {

    public long retryAfterSeconds() {
        if (nanosToWaitForRefill <= 0) {
            return 0;
        }
        long nanosPerSecond = TimeUnit.SECONDS.toNanos(1);
        return (nanosToWaitForRefill + nanosPerSecond - 1) / nanosPerSecond;
    }
}
