package com.lanny.ailab.rag.infrastructure.ratelimit;

import com.lanny.ailab.rag.domain.valueobject.TenantId;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-tenant rate limiter using Bucket4j token bucket algorithm.
 *
 * <p>In distributed environments the service uses a Redis-backed {@link ProxyManager}
 * so all application instances share the same bucket state. When no proxy manager is
 * configured, it falls back to in-memory buckets so slice tests can stay lightweight.
 *
 * <p>Accepts {@link TenantId} value objects rather than raw {@code String} to enforce
 * the project contract that tenant identity is always validated before use.
 */
@Component
public class RateLimiterService {

    private final ConcurrentHashMap<String, Bucket> queryBuckets  = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> ingestBuckets = new ConcurrentHashMap<>();

    private final ProxyManager<String> proxyManager;
    private final String keyPrefix;
    private final int queryRequestsPerMinute;
    private final int ingestRequestsPerMinute;
    private final BucketConfiguration queryBucketConfiguration;
    private final BucketConfiguration ingestBucketConfiguration;

    @Autowired
    public RateLimiterService(
            @Value("${app.rag.rate-limit.query-requests-per-minute:20}") int queryRequestsPerMinute,
            @Value("${app.rag.rate-limit.ingest-requests-per-minute:10}") int ingestRequestsPerMinute,
            @Value("${app.rag.rate-limit.redis.key-prefix:ai-secure-rag-engine:rate-limit}") String keyPrefix,
            ObjectProvider<ProxyManager<String>> proxyManagerProvider) {

        this(queryRequestsPerMinute, ingestRequestsPerMinute, keyPrefix, proxyManagerProvider.getIfAvailable());
    }

    RateLimiterService(int queryRequestsPerMinute, int ingestRequestsPerMinute) {
        this(queryRequestsPerMinute, ingestRequestsPerMinute, "ai-secure-rag-engine:rate-limit", (ProxyManager<String>) null);
    }

    RateLimiterService(
            int queryRequestsPerMinute,
            int ingestRequestsPerMinute,
            String keyPrefix,
            ProxyManager<String> proxyManager) {

        this.proxyManager = proxyManager;
        this.keyPrefix = keyPrefix;
        this.queryRequestsPerMinute  = queryRequestsPerMinute;
        this.ingestRequestsPerMinute = ingestRequestsPerMinute;
        this.queryBucketConfiguration = buildDistributedConfiguration(queryRequestsPerMinute);
        this.ingestBucketConfiguration = buildDistributedConfiguration(ingestRequestsPerMinute);
    }

    /**
     * Attempts to consume one token from the query bucket for the given tenant.
     *
     * @param tenantId the validated tenant identifier
     * @return {@code true} if the request is allowed, {@code false} if rate limit is exceeded
     */
    public boolean tryConsumeQuery(TenantId tenantId) {
        return tryConsume(
                keyPrefix + ":query:tenant:" + tenantId.value(),
                queryBucketConfiguration,
                queryBuckets,
                queryRequestsPerMinute);
    }

    /**
     * Attempts to consume one token from the ingest bucket for the given tenant.
     *
     * @param tenantId the validated tenant identifier
     * @return {@code true} if the request is allowed, {@code false} if rate limit is exceeded
     */
    public boolean tryConsumeIngest(TenantId tenantId) {
        return tryConsume(
                keyPrefix + ":ingest:tenant:" + tenantId.value(),
                ingestBucketConfiguration,
                ingestBuckets,
                ingestRequestsPerMinute);
    }

    private boolean tryConsume(
            String bucketKey,
            BucketConfiguration distributedConfiguration,
            ConcurrentHashMap<String, Bucket> localBuckets,
            int requestsPerMinute) {

        if (proxyManager == null) {
            return localBuckets
                    .computeIfAbsent(bucketKey, id -> buildLocalBucket(requestsPerMinute))
                    .tryConsume(1);
        }

        try {
            return proxyManager
                    .getProxy(bucketKey, () -> distributedConfiguration)
                    .tryConsume(1);
        } catch (RuntimeException ex) {
            throw new RateLimitUnavailableException("Distributed rate limiter is unavailable", ex);
        }
    }

    private BucketConfiguration buildDistributedConfiguration(int requestsPerMinute) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(requestsPerMinute)
                .refillGreedy(requestsPerMinute, Duration.ofMinutes(1))
                .build();
        return BucketConfiguration.builder()
                .addLimit(limit)
                .build();
    }

    private Bucket buildLocalBucket(int requestsPerMinute) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(requestsPerMinute)
                .refillGreedy(requestsPerMinute, Duration.ofMinutes(1))
                .build();
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }
}
