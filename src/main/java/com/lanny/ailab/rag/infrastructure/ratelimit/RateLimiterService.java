package com.lanny.ailab.rag.infrastructure.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory per-tenant rate limiter using Bucket4j token bucket algorithm.
 *
 * Each tenant gets an independent bucket with a fixed refill rate.
 * Buckets are created lazily on first request and kept in memory.
 *
 * Trade-off: in-memory means limits reset on restart and are not shared
 * across multiple instances. Acceptable for MVP single-instance deployment.
 * For multi-instance: replace ConcurrentHashMap with Redis-backed Bucket4j.
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
     * @param tenantId tenant identifier
     * @return true if the request is allowed, false if rate limit exceeded
     */
    public boolean tryConsumeQuery(String tenantId) {
        return queryBuckets
                .computeIfAbsent(tenantId, id -> buildBucket(queryRequestsPerMinute))
                .tryConsume(1);
    }

    /**
     * Attempts to consume one token from the ingest bucket for the given tenant.
     *
     * @param tenantId tenant identifier
     * @return true if the request is allowed, false if rate limit exceeded
     */
    public boolean tryConsumeIngest(String tenantId) {
        return ingestBuckets
                .computeIfAbsent(tenantId, id -> buildBucket(ingestRequestsPerMinute))
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