package com.lanny.ailab.rag.infrastructure.ratelimit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Micrometer counters dedicated to rate-limiter behavior and backend health.
 */
@Component
public class RateLimitMetrics {

    private final Counter queryAllowed;
    private final Counter queryRejected;
    private final Counter ingestAllowed;
    private final Counter ingestRejected;
    private final Counter backendUnavailable;

    public RateLimitMetrics(MeterRegistry registry) {
        this.queryAllowed = counter(registry, "query", "allowed");
        this.queryRejected = counter(registry, "query", "rejected");
        this.ingestAllowed = counter(registry, "ingest", "allowed");
        this.ingestRejected = counter(registry, "ingest", "rejected");
        this.backendUnavailable = Counter.builder("rag.rate_limit.backend_unavailable")
                .description("Rate-limit backend failures while Redis-backed quotas are configured")
                .register(registry);
    }

    public void incrementAllowed(String operation) {
        counterFor(operation, true).increment();
    }

    public void incrementRejected(String operation) {
        counterFor(operation, false).increment();
    }

    public void incrementBackendUnavailable() {
        backendUnavailable.increment();
    }

    private Counter counterFor(String operation, boolean allowed) {
        if ("query".equals(operation)) {
            return allowed ? queryAllowed : queryRejected;
        }
        return allowed ? ingestAllowed : ingestRejected;
    }

    private Counter counter(MeterRegistry registry, String operation, String outcome) {
        return Counter.builder("rag.rate_limit.requests")
                .description("Rate-limit decisions by operation and outcome")
                .tag("operation", operation)
                .tag("outcome", outcome)
                .register(registry);
    }
}
