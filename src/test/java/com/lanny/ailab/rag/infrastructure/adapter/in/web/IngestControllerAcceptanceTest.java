package com.lanny.ailab.rag.infrastructure.adapter.in.web;

import com.lanny.ailab.rag.application.port.in.IngestDocumentUseCase;
import com.lanny.ailab.rag.application.result.IngestDocumentResult;
import com.lanny.ailab.rag.infrastructure.adapter.in.web.mapper.IngestDocumentWebMapper;
import com.lanny.ailab.security.application.TenantContext;
import com.lanny.ailab.security.infrastructure.SecurityConfig;
import com.lanny.ailab.shared.error.GlobalExceptionHandler;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.argThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(IngestController.class)
@Import({ SecurityConfig.class, IngestDocumentWebMapper.class, TenantContext.class, GlobalExceptionHandler.class })
class IngestControllerAcceptanceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IngestDocumentUseCase ingestDocumentUseCase;

    @Test
    void returns_401_when_request_has_no_jwt() throws Exception {
        mockMvc.perform(post("/rag/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "documentId": "doc-1", "content": "some content" }
                        """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returns_403_when_authenticated_as_org_member() throws Exception {
        mockMvc.perform(post("/rag/ingest")
                .with(jwtForTenant("org-test", "ORG_MEMBER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "documentId": "doc-1", "content": "some content" }
                        """))
                .andExpect(status().isForbidden());
    }

    @Test
    void returns_403_when_jwt_has_no_tenant_id() throws Exception {
        mockMvc.perform(post("/rag/ingest")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "documentId": "doc-1", "content": "some content" }
                        """))
                .andExpect(status().isForbidden());
    }

    @Test
    void returns_201_with_document_id_and_chunks_indexed() throws Exception {
        when(ingestDocumentUseCase.execute(any()))
                .thenReturn(new IngestDocumentResult("doc-1", 3));

        mockMvc.perform(post("/rag/ingest")
                .with(jwtForTenant("org-test", "PLATFORM_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "documentId": "doc-1", "content": "some relevant content" }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentId").value("doc-1"))
                .andExpect(jsonPath("$.chunksIndexed").value(3));
    }

    @Test
    void passes_correct_tenant_and_document_to_use_case() throws Exception {
        when(ingestDocumentUseCase.execute(any()))
                .thenReturn(new IngestDocumentResult("doc-42", 1));

        mockMvc.perform(post("/rag/ingest")
                .with(jwtForTenant("org-abc", "PLATFORM_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "documentId": "doc-42", "content": "content" }
                        """))
                .andExpect(status().isCreated());

        verify(ingestDocumentUseCase).execute(
                argThat(cmd -> cmd.documentId().equals("doc-42") &&
                        cmd.tenantId().value().equals("org-abc")));
    }

    @Test
    void returns_400_when_document_id_is_blank() throws Exception {
        mockMvc.perform(post("/rag/ingest")
                .with(jwtForTenant("org-test", "PLATFORM_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "documentId": "", "content": "some content" }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.documentId").exists());
    }

    @Test
    void returns_400_when_document_id_has_invalid_format() throws Exception {
        mockMvc.perform(post("/rag/ingest")
                .with(jwtForTenant("org-test", "PLATFORM_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "documentId": "invalid id!", "content": "some content" }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.documentId").exists());
    }

    @Test
    void returns_400_when_content_is_blank() throws Exception {
        mockMvc.perform(post("/rag/ingest")
                .with(jwtForTenant("org-test", "PLATFORM_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "documentId": "doc-1", "content": "" }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.content").exists());
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
