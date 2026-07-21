package com.lanny.ailab.rag.infrastructure.adapter.in.web;

import com.lanny.ailab.rag.application.port.in.IngestDocumentUseCase;
import com.lanny.ailab.rag.application.port.in.GetIngestionStatusUseCase;
import com.lanny.ailab.rag.application.result.IngestDocumentResult;
import com.lanny.ailab.rag.application.result.IngestionStatusResult;
import com.lanny.ailab.rag.domain.model.IngestionStatus;
import com.lanny.ailab.rag.domain.model.SourceType;
import com.lanny.ailab.rag.infrastructure.adapter.in.web.mapper.IngestDocumentWebMapper;
import com.lanny.ailab.rag.infrastructure.ratelimit.RateLimiterService;
import com.lanny.ailab.security.infrastructure.AuditAccessDeniedHandler;
import com.lanny.ailab.security.infrastructure.AuditAuthenticationEntryPoint;
import com.lanny.ailab.security.infrastructure.SecurityConfig;
import com.lanny.ailab.security.infrastructure.TenantContext;
import com.lanny.ailab.security.infrastructure.audit.SecurityAuditService;
import com.lanny.ailab.shared.error.GlobalExceptionHandler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static com.lanny.ailab.testutil.JwtTestBuilder.jwtForTenant;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(IngestController.class)
@Import({ SecurityConfig.class, IngestDocumentWebMapper.class, TenantContext.class, GlobalExceptionHandler.class,
                RateLimiterService.class, SecurityAuditService.class, AuditAuthenticationEntryPoint.class,
                AuditAccessDeniedHandler.class })
@Tag("acceptance")
class IngestControllerAcceptanceTest {

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private IngestDocumentUseCase ingestDocumentUseCase;

        @MockitoBean
        private GetIngestionStatusUseCase getIngestionStatusUseCase;

        @Test
        @DisplayName("Should return 401 Unauthorized when no JWT is provided")
        void returns_401_when_request_has_no_jwt() throws Exception {
                mockMvc.perform(post("/rag/ingest")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                { "documentId": "doc-1", "content": "some content" }
                                                """))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should return 403 Forbidden when authenticated as an organization member")
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
        @DisplayName("Should return 403 Forbidden when JWT has no tenant ID")
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
        @DisplayName("Should return 202 Accepted with document ID and pending status when ingestion is initiated")
        void returns_202_with_document_id_and_pending_status() throws Exception {
                when(ingestDocumentUseCase.execute(any()))
                                .thenReturn(new IngestDocumentResult("doc-1", IngestionStatus.PENDING));

                mockMvc.perform(post("/rag/ingest")
                                .with(jwtForTenant("org-test", "PLATFORM_ADMIN"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                { "documentId": "doc-1", "content": "some relevant content" }
                                                """))
                                .andExpect(status().isAccepted())
                                .andExpect(header().string("Location", "/rag/ingest/doc-1"))
                                .andExpect(jsonPath("$.documentId").value("doc-1"))
                                .andExpect(jsonPath("$.status").value("PENDING"));
        }

        @Test
        @DisplayName("Should pass the correct tenant and document ID to the use case when ingestion is initiated")
        void passes_correct_tenant_and_document_to_use_case() throws Exception {
                when(ingestDocumentUseCase.execute(any()))
                                .thenReturn(new IngestDocumentResult("doc-42", IngestionStatus.PENDING));

                mockMvc.perform(post("/rag/ingest")
                                .with(jwtForTenant("org-abc", "PLATFORM_ADMIN"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                { "documentId": "doc-42", "content": "content" }
                                                """))
                                .andExpect(status().isAccepted());

                verify(ingestDocumentUseCase).execute(
                                argThat(cmd -> cmd.documentId().equals("doc-42") &&
                                                cmd.tenantId().value().equals("org-abc")));
        }

        @Test
        @DisplayName("Should return 200 OK with ingestion status for existing document")
        void returns_200_with_ingestion_status_for_existing_document() throws Exception {
                when(getIngestionStatusUseCase.findStatus(
                                eq(com.lanny.ailab.rag.domain.valueobject.TenantId.from("org-test")), eq("doc-1")))
                                .thenReturn(java.util.Optional.of(new IngestionStatusResult(
                                                "doc-1",
                                                SourceType.RAW_TEXT,
                                                null,
                                                IngestionStatus.COMPLETED,
                                                3,
                                                null,
                                                0,
                                                3,
                                                java.time.Instant.parse("2026-07-17T10:00:00Z"),
                                                java.time.Instant.parse("2026-07-17T10:00:01Z"),
                                                java.time.Instant.parse("2026-07-17T10:00:05Z"),
                                                java.time.Instant.parse("2026-07-17T10:00:05Z"),
                                                java.time.Instant.parse("2026-07-17T10:00:05Z"),
                                                null,
                                                null)));

                mockMvc.perform(get("/rag/ingest/doc-1")
                                .with(jwtForTenant("org-test", "PLATFORM_ADMIN")))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.documentId").value("doc-1"))
                                .andExpect(jsonPath("$.status").value("COMPLETED"))
                                .andExpect(jsonPath("$.chunksIndexed").value(3))
                                .andExpect(jsonPath("$.retryCount").value(0))
                                .andExpect(jsonPath("$.maxAttempts").value(3))
                                .andExpect(jsonPath("$.requestedAt").value("2026-07-17T10:00:00Z"));
        }

        @Test
        @DisplayName("Should return 404 Not Found when ingestion status does not exist for the document")
        void returns_404_when_ingestion_status_does_not_exist() throws Exception {
                when(getIngestionStatusUseCase.findStatus(
                                eq(com.lanny.ailab.rag.domain.valueobject.TenantId.from("org-test")),
                                eq("missing-doc")))
                                .thenReturn(java.util.Optional.empty());

                mockMvc.perform(get("/rag/ingest/missing-doc")
                                .with(jwtForTenant("org-test", "PLATFORM_ADMIN")))
                                .andExpect(status().isNotFound());

                verify(ingestDocumentUseCase, never()).execute(any());
        }

        @Test
        @DisplayName("Should return 400 Bad Request when document ID is blank")
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
        @DisplayName("Should return 400 Bad Request when document ID has invalid format")
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
        @DisplayName("Should return 400 Bad Request when content is blank")
        void returns_400_when_content_is_blank() throws Exception {
                mockMvc.perform(post("/rag/ingest")
                                .with(jwtForTenant("org-test", "PLATFORM_ADMIN"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                { "documentId": "doc-1", "content": "" }
                                                """))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.title").value("Bad request"))
                                .andExpect(jsonPath("$.detail")
                                                .value("content is required when source is not provided"));
        }

}
