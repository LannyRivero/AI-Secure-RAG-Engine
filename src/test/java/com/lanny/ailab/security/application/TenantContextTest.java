package com.lanny.ailab.security.application;

import com.lanny.ailab.rag.domain.valueobject.TenantId;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
class TenantContextTest {

    private TenantContext tenantContext;

    @BeforeEach
    void setUp() {
        tenantContext = new TenantContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returns_tenant_id_when_jwt_contains_valid_tenant() {
        setJwtAuthentication(Map.of("attributes", Map.of("tenant_id", List.of("org-test"))));

        TenantId result = tenantContext.getCurrentTenantId();

        assertThat(result.value()).isEqualTo("org-test");
    }

    @Test
    void throws_401_when_no_authentication_present() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> tenantContext.getCurrentTenantId())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401");
    }

    @Test
    void throws_403_when_tenant_id_claim_is_absent() {
        setJwtAuthentication(Map.of());

        assertThatThrownBy(() -> tenantContext.getCurrentTenantId())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
    }

    @Test
    void throws_403_when_tenant_id_has_invalid_format() {
        setJwtAuthentication(Map.of("attributes", Map.of("tenant_id", List.of("!invalid tenant!"))));

        assertThatThrownBy(() -> tenantContext.getCurrentTenantId())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
    }

    @Test
    void returns_tenant_id_when_claim_value_is_plain_string_not_list() {
        setJwtAuthentication(Map.of("attributes", Map.of("tenant_id", "org-direct")));

        TenantId result = tenantContext.getCurrentTenantId();

        assertThat(result.value()).isEqualTo("org-direct");
    }

    private void setJwtAuthentication(Map<String, Object> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600));
        claims.forEach(builder::claim);
        Jwt jwt = builder.build();

        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
