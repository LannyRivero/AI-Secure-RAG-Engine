package com.lanny.ailab.rag.infrastructure.ratelimit;

import com.lanny.ailab.rag.domain.valueobject.TenantId;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory per-tenant rate limiter using Bucket4j token bucket algorithm.
 *
 * <p>Each tenant gets an independent bucket with a fixed refill rate.
 * Buckets are created lazily on first request and kept in memory for the lifetime
 * of the application instance.
 *
 * <p>Trade-off: in-memory means limits reset on restart and are not shared
 * across multiple application instances. Acceptable for MVP single-instance deployment.
 * For multi-instance: replace {@link ConcurrentHashMap} with a Redis-backed Bucket4j
 * distributed store.
 *
 * <p>Accepts {@link TenantId} value objects rather than raw {@code String} to enforce
 * the project contract that tenant identity is always validated before use.
 */
@Component
public class RateLimiterService {

    private final ConcurrentHashMap<String, Bucket> queryBuckets  = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> ingestBuckets = new ConcurrentHashMap<>();

    private final int queryRequestsPerMinute;
    private final int ingestRequestsPerMinute;

    public RateLimiterService(
            @Value("${app.rag.rate-limit.query-requests-per-minute:20}") int queryRequestsPerMinute,
            @Value("${app.rag.rate-limit.ingest-requests-per-minute:10}") int ingestRequestsPerMinute) {

        this.queryRequestsPerMinute  = queryRequestsPerMinute;
        this.ingestRequestsPerMinute = ingestRequestsPerMinute;
    }

    /**
     * Attempts to consume one token from the query bucket for the given tenant.
     *
     * @param tenantId the validated tenant identifier
     * @return {@code true} if the request is allowed, {@code false} if rate limit is exceeded
     */
    public boolean tryConsumeQuery(TenantId tenantId) {
        return queryBuckets
                .computeIfAbsent(tenantId.value(), id -> buildBucket(queryRequestsPerMinute))
                .tryConsume(1);
    }

    /**
     * Attempts to consume one token from the ingest bucket for the given tenant.
     *
     * @param tenantId the validated tenant identifier
     * @return {@code true} if the request is allowed, {@code false} if rate limit is exceeded
     */
    public boolean tryConsumeIngest(TenantId tenantId) {
        return ingestBuckets
                .computeIfAbsent(tenantId.value(), id -> buildBucket(ingestRequestsPerMinute))
                .tryConsume(1);
    }

    private Bucket buildBucket(int requestsPerMinute) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(requestsPerMinute)
                .refillGreedy(requestsPerMinute, Duration.ofMinutes(1))
                .build();
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }
}