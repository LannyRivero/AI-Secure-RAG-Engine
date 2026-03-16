package com.lanny.ailab.security.infrastructure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class KeycloakJwtAuthenticationConverterTest {

    private KeycloakJwtAuthenticationConverter converter;

    @BeforeEach
    void setUp() {
        converter = new KeycloakJwtAuthenticationConverter();
    }

    @Test
    void extracts_roles_from_realm_access_claim() {
        Jwt jwt = jwt(Map.of("realm_access", Map.of("roles", List.of("ORG_MEMBER", "PLATFORM_ADMIN"))));

        AbstractAuthenticationToken token = converter.convert(jwt);

        Collection<GrantedAuthority> authorities = token.getAuthorities();
        assertThat(authorities).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_ORG_MEMBER", "ROLE_PLATFORM_ADMIN");
    }

    @Test
    void prefixes_roles_with_ROLE_() {
        Jwt jwt = jwt(Map.of("realm_access", Map.of("roles", List.of("ORG_MEMBER"))));

        AbstractAuthenticationToken token = converter.convert(jwt);

        assertThat(token.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ORG_MEMBER");
    }

    @Test
    void returns_empty_authorities_when_realm_access_claim_is_absent() {
        Jwt jwt = jwt(Map.of());

        AbstractAuthenticationToken token = converter.convert(jwt);

        assertThat(token.getAuthorities()).isEmpty();
    }

    @Test
    void returns_empty_authorities_when_roles_list_is_absent() {
        Jwt jwt = jwt(Map.of("realm_access", Map.of()));

        AbstractAuthenticationToken token = converter.convert(jwt);

        assertThat(token.getAuthorities()).isEmpty();
    }

    @Test
    void preserves_jwt_principal_in_returned_token() {
        Jwt jwt = jwt(Map.of("realm_access", Map.of("roles", List.of("ORG_MEMBER"))));

        AbstractAuthenticationToken token = converter.convert(jwt);

        assertThat(token.getPrincipal()).isEqualTo(jwt);
    }

    private Jwt jwt(Map<String, Object> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600));
        claims.forEach(builder::claim);
        return builder.build();
    }
}
