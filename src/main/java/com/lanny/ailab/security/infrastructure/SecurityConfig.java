package com.lanny.ailab.security.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security configuration for the RAG API.
 *
 * <p>This is a stateless OAuth2 resource server. CSRF protection is disabled because the API
 * relies exclusively on JWT bearer tokens — no session cookies are used. Security headers are
 * added to mitigate common web vulnerabilities even for API consumers.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Configures the security filter chain with:
     * <ul>
     *   <li>Stateless session management (no HTTP session)</li>
     *   <li>CSRF disabled (stateless JWT-based API)</li>
     *   <li>Defensive HTTP response headers</li>
     *   <li>Explicit CORS policy (deny all cross-origin by default)</li>
     *   <li>Role-based authorization per endpoint</li>
     *   <li>OAuth2 JWT resource server with Keycloak role mapping</li>
     * </ul>
     *
     * @param http the {@link HttpSecurity} to configure
     * @return the configured {@link SecurityFilterChain}
     * @throws Exception if configuration fails
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(headers -> headers
                .contentTypeOptions(contentTypeOptions -> {})
                .frameOptions(frameOptions -> frameOptions.deny())
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000))
                .referrerPolicy(referrer -> referrer
                    .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**").hasRole("PLATFORM_ADMIN")
                .requestMatchers("/rag/metrics").hasRole("PLATFORM_ADMIN")
                .requestMatchers("/rag/ingest").hasRole("PLATFORM_ADMIN")
                .requestMatchers("/rag/query").hasAnyRole("ORG_MEMBER", "PLATFORM_ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/rag/documents/**").hasRole("PLATFORM_ADMIN")
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(
                    new KeycloakJwtAuthenticationConverter())));

        return http.build();
    }

    /**
     * Defines the CORS policy. By default all cross-origin requests are denied.
     * Override {@code app.cors.allowed-origins} in environment-specific configuration
     * to open access to trusted frontends.
     *
     * @return the configured {@link CorsConfigurationSource}
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of());   // deny all by default — override per environment
        config.setAllowedMethods(List.of("GET", "POST", "DELETE"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
