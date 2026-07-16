package com.lanny.ailab.rag.infrastructure.ratelimit;

import com.lanny.ailab.rag.domain.valueobject.TenantId;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@Tag("integration")
class DistributedRateLimiterRedisIntegrationTest {

    private static final TenantId TENANT_A = TenantId.from("org-alpha");
    private static final TenantId TENANT_B = TenantId.from("org-beta");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    private RedisClient redisClient;
    private StatefulRedisConnection<String, byte[]> redisConnection;
    private RedisCommands<String, byte[]> redisCommands;
    private ProxyManager<String> proxyManager;

    @BeforeEach
    void setUp() {
        RedisURI redisUri = RedisURI.builder()
                .withHost(redis.getHost())
                .withPort(redis.getMappedPort(6379))
                .withTimeout(Duration.ofSeconds(2))
                .build();

        redisClient = RedisClient.create(redisUri);
        redisConnection = redisClient.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
        redisCommands = redisConnection.sync();
        redisCommands.flushall();
        proxyManager = Bucket4jLettuce.casBasedBuilder(redisConnection)
                .expirationAfterWrite(ExpirationAfterWriteStrategy
                        .basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(2)))
                .build();
    }

    @AfterEach
    void tearDown() {
        if (redisConnection != null) {
            redisConnection.close();
        }
        if (redisClient != null) {
            redisClient.shutdown();
        }
    }

    @Test
    void given_two_service_instances_when_query_limit_is_exhausted_in_one_then_other_instance_rejects() {
        RateLimiterService instanceA = new RateLimiterService(2, 1, "test:rate-limit", proxyManager);
        RateLimiterService instanceB = new RateLimiterService(2, 1, "test:rate-limit", proxyManager);

        assertThat(instanceA.tryConsumeQuery(TENANT_A)).isTrue();
        assertThat(instanceA.tryConsumeQuery(TENANT_A)).isTrue();
        assertThat(instanceB.tryConsumeQuery(TENANT_A)).isFalse();
    }

    @Test
    void given_query_limit_is_exhausted_when_consuming_ingest_bucket_then_ingest_remains_independent() {
        RateLimiterService instanceA = new RateLimiterService(2, 1, "test:rate-limit", proxyManager);
        RateLimiterService instanceB = new RateLimiterService(2, 1, "test:rate-limit", proxyManager);

        assertThat(instanceA.tryConsumeQuery(TENANT_A)).isTrue();
        assertThat(instanceA.tryConsumeQuery(TENANT_A)).isTrue();
        assertThat(instanceB.tryConsumeQuery(TENANT_A)).isFalse();

        assertThat(instanceB.tryConsumeIngest(TENANT_A)).isTrue();
        assertThat(instanceA.tryConsumeIngest(TENANT_A)).isFalse();
    }

    @Test
    void given_tenant_a_exhausts_limit_when_tenant_b_consumes_then_tenant_b_is_not_affected() {
        RateLimiterService instanceA = new RateLimiterService(2, 1, "test:rate-limit", proxyManager);
        RateLimiterService instanceB = new RateLimiterService(2, 1, "test:rate-limit", proxyManager);

        assertThat(instanceA.tryConsumeQuery(TENANT_A)).isTrue();
        assertThat(instanceA.tryConsumeQuery(TENANT_A)).isTrue();
        assertThat(instanceB.tryConsumeQuery(TENANT_A)).isFalse();

        assertThat(instanceB.tryConsumeQuery(TENANT_B)).isTrue();
    }
}
