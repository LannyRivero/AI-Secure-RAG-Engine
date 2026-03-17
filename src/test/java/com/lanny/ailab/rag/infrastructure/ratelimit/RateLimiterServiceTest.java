package com.lanny.ailab.rag.infrastructure.ratelimit;

import com.lanny.ailab.rag.domain.valueobject.TenantId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class RateLimiterServiceTest {

    private static final TenantId TENANT_A = TenantId.from("org-alpha");
    private static final TenantId TENANT_B = TenantId.from("org-beta");

    private RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        rateLimiterService = new RateLimiterService(2, 1);
    }

    @Test
    void given_requests_within_limit_when_tryConsumeQuery_then_returns_true() {
        assertThat(rateLimiterService.tryConsumeQuery(TENANT_A)).isTrue();
        assertThat(rateLimiterService.tryConsumeQuery(TENANT_A)).isTrue();
    }

    @Test
    void given_requests_exceeding_limit_when_tryConsumeQuery_then_returns_false() {
        rateLimiterService.tryConsumeQuery(TENANT_A);
        rateLimiterService.tryConsumeQuery(TENANT_A);

        assertThat(rateLimiterService.tryConsumeQuery(TENANT_A)).isFalse();
    }

    @Test
    void given_requests_within_ingest_limit_when_tryConsumeIngest_then_returns_true() {
        assertThat(rateLimiterService.tryConsumeIngest(TENANT_A)).isTrue();
    }

    @Test
    void given_requests_exceeding_ingest_limit_when_tryConsumeIngest_then_returns_false() {
        rateLimiterService.tryConsumeIngest(TENANT_A);

        assertThat(rateLimiterService.tryConsumeIngest(TENANT_A)).isFalse();
    }

    @Test
    void given_tenant_a_exceeds_limit_when_tryConsumeQuery_for_tenant_b_then_returns_true() {
        rateLimiterService.tryConsumeQuery(TENANT_A);
        rateLimiterService.tryConsumeQuery(TENANT_A);
        assertThat(rateLimiterService.tryConsumeQuery(TENANT_A)).isFalse();

        assertThat(rateLimiterService.tryConsumeQuery(TENANT_B)).isTrue();
    }

    @Test
    void given_query_bucket_exhausted_when_tryConsumeIngest_then_returns_true() {
        rateLimiterService.tryConsumeQuery(TENANT_A);
        rateLimiterService.tryConsumeQuery(TENANT_A);
        assertThat(rateLimiterService.tryConsumeQuery(TENANT_A)).isFalse();

        assertThat(rateLimiterService.tryConsumeIngest(TENANT_A)).isTrue();
    }
}