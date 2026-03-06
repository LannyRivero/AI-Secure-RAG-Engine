package com.lanny.ailab.rag.infrastructure.adapter.in.web;

import com.lanny.ailab.rag.application.port.in.DeleteDocumentUseCase;
import com.lanny.ailab.rag.application.result.DeleteDocumentResult;
import com.lanny.ailab.rag.infrastructure.ratelimit.RateLimiterService;
import com.lanny.ailab.security.application.TenantContext;
import com.lanny.ailab.security.infrastructure.SecurityConfig;
import com.lanny.ailab.shared.error.GlobalExceptionHandler;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DeleteController.class)
@Import({SecurityConfig.class, TenantContext.class, GlobalExceptionHandler.class, RateLimiterService.class})
@Tag("acceptance")
class DeleteControllerAcceptanceTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private DeleteDocumentUseCase deleteDocumentUseCase;

    @Test
    void returns_401_when_no_jwt() throws Exception {
        mockMvc.perform(delete("/rag/documents/doc-1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returns_403_when_authenticated_as_org_member() throws Exception {
        mockMvc.perform(delete("/rag/documents/doc-1")
                        .with(jwtForTenant("org-test", "ORG_MEMBER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void returns_204_when_document_deleted() throws Exception {
        when(deleteDocumentUseCase.execute(any()))
                .thenReturn(DeleteDocumentResult.success("doc-1"));

        mockMvc.perform(delete("/rag/documents/doc-1")
                        .with(jwtForTenant("org-test", "PLATFORM_ADMIN")))
                .andExpect(status().isNoContent());
    }

    @Test
    void returns_404_when_document_not_found() throws Exception {
        when(deleteDocumentUseCase.execute(any()))
                .thenReturn(DeleteDocumentResult.notFound("doc-1"));

        mockMvc.perform(delete("/rag/documents/doc-1")
                        .with(jwtForTenant("org-test", "PLATFORM_ADMIN")))
                .andExpect(status().isNotFound());
    }

    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor jwtForTenant(
            String tenantId, String role) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("attributes", Map.of("tenant_id", List.of(tenantId)))
                .build();
        return jwt().jwt(jwt).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
