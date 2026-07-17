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
        long seconds = TimeUnit.NANOSECONDS.toSeconds(nanosToWaitForRefill);
        return seconds > 0 ? seconds : 1;
    }
}
