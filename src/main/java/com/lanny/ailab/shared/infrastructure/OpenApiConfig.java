package com.lanny.ailab.shared.infrastructure;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI/Swagger configuration for the AI Secure RAG Engine.
 *
 * <p>Registers two security schemes:
 * <ul>
 *   <li><b>bearerAuth</b> — paste a raw JWT token directly.</li>
 *   <li><b>keycloak</b> — OAuth2 Resource Owner Password flow that fetches a
 *       token from Keycloak automatically when you click Authorize in the UI.</li>
 * </ul>
 * Both schemes are applied globally so every endpoint shows the lock icon.</p>
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "AI Secure RAG Engine",
                version = "0.0.1",
                description = "Multi-tenant Retrieval-Augmented Generation API secured with Keycloak JWT"
        )
)
public class OpenApiConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    /**
     * Builds the OpenAPI definition with Bearer and Keycloak OAuth2 security schemes.
     *
     * @return the configured {@link OpenAPI} instance
     */
    @Bean
    public OpenAPI openAPI() {
        String tokenUrl = issuerUri + "/protocol/openid-connect/token";

        OAuthFlow passwordFlow = new OAuthFlow()
                .tokenUrl(tokenUrl);

        SecurityScheme keycloakScheme = new SecurityScheme()
                .type(SecurityScheme.Type.OAUTH2)
                .flows(new OAuthFlows().password(passwordFlow));

        SecurityScheme bearerScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT");

        return new OpenAPI()
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", bearerScheme)
                        .addSecuritySchemes("keycloak", keycloakScheme))
                .addSecurityItem(new SecurityRequirement()
                        .addList("bearerAuth")
                        .addList("keycloak"));
    }
}
