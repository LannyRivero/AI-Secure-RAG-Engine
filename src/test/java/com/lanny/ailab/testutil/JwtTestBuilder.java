package com.lanny.ailab.testutil;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * Shared test utility for building JWT-based {@link JwtRequestPostProcessor}s in acceptance tests.
 *
 * <p>Eliminates duplication of Keycloak-style JWT construction across
 * {@code *AcceptanceTest} classes.
 */
public final class JwtTestBuilder {

    private JwtTestBuilder() {}

    /**
     * Creates a JWT post-processor for a tenant with the given role.
     *
     * @param tenantId the tenant ID to embed in the {@code attributes} claim
     * @param role     the Spring Security role name (without the {@code ROLE_} prefix)
     * @return a configured {@link JwtRequestPostProcessor}
     */
    public static JwtRequestPostProcessor jwtForTenant(String tenantId, String role) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("attributes", Map.of("tenant_id", List.of(tenantId)))
                .build();

        return jwt().jwt(jwt).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    /**
     * Creates a JWT post-processor for a tenant with the {@code ORG_MEMBER} role.
     *
     * @param tenantId the tenant ID to embed in the {@code attributes} claim
     * @return a configured {@link JwtRequestPostProcessor}
     */
    public static JwtRequestPostProcessor jwtForTenant(String tenantId) {
        return jwtForTenant(tenantId, "ORG_MEMBER");
    }
}
