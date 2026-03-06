package com.lanny.ailab.rag.infrastructure.ratelimit;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class RateLimiterServiceTest {

    @Test
    void allows_requests_within_limit() {
        var service = new RateLimiterService(5, 5);

        for (int i = 0; i < 5; i++) {
            assertThat(service.tryConsumeQuery("tenant-a")).isTrue();
        }
    }

    @Test
    void blocks_request_when_limit_exceeded() {
        var service = new RateLimiterService(3, 3);

        for (int i = 0; i < 3; i++) {
            service.tryConsumeQuery("tenant-a");
        }

        assertThat(service.tryConsumeQuery("tenant-a")).isFalse();
    }

    @Test
    void buckets_are_independent_per_tenant() {
        var service = new RateLimiterService(2, 2);

        service.tryConsumeQuery("tenant-a");
        service.tryConsumeQuery("tenant-a");

        assertThat(service.tryConsumeQuery("tenant-a")).isFalse();
        assertThat(service.tryConsumeQuery("tenant-b")).isTrue();
    }

    @Test
    void query_and_ingest_buckets_are_independent() {
        var service = new RateLimiterService(2, 5);

        service.tryConsumeQuery("tenant-a");
        service.tryConsumeQuery("tenant-a");

        assertThat(service.tryConsumeQuery("tenant-a")).isFalse();
        assertThat(service.tryConsumeIngest("tenant-a")).isTrue();
    }
}
