package com.lanny.ailab.rag.infrastructure.adapter.in.web;

import com.lanny.ailab.rag.application.port.in.QueryRagUseCase;
import com.lanny.ailab.rag.application.result.QueryRagResult;
import com.lanny.ailab.rag.domain.valueobject.DocumentChunk;
import com.lanny.ailab.rag.domain.valueobject.TenantId;
import com.lanny.ailab.rag.infrastructure.adapter.in.web.mapper.QueryRagWebMapper;
import com.lanny.ailab.rag.infrastructure.ratelimit.RateLimiterService;
import com.lanny.ailab.security.application.TenantContext;
import com.lanny.ailab.security.infrastructure.SecurityConfig;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.lanny.ailab.rag.domain.exception.LlmProviderException;
import com.lanny.ailab.rag.domain.exception.RateLimitExceededException;
import com.lanny.ailab.shared.error.GlobalExceptionHandler;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RagController.class)
@Import({SecurityConfig.class, QueryRagWebMapper.class, TenantContext.class, GlobalExceptionHandler.class, RateLimiterService.class})
@Tag("acceptance")
class RagControllerAcceptanceTest {

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private QueryRagUseCase queryRagUseCase;

        @Test
        void returns_401_when_request_has_no_jwt() throws Exception {
                mockMvc.perform(post("/rag/query")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                { "query": "What is UNADA?" }
                                                """))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        void returns_403_when_jwt_has_no_tenant_id() throws Exception {
                mockMvc.perform(post("/rag/query")
                                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                { "query": "What is UNADA?" }
                                                """))
                                .andExpect(status().isForbidden());
        }

        @Test
        void returns_403_when_jwt_has_invalid_tenant_id_format() throws Exception {
                Jwt invalidJwt = Jwt.withTokenValue("token")
                                .header("alg", "none")
                                .issuedAt(Instant.now())
                                .expiresAt(Instant.now().plusSeconds(3600))
                                .claim("attributes", Map.of("tenant_id", List.of("!invalid tenant!")))
                                .build();

                mockMvc.perform(post("/rag/query")
                                .with(jwt().jwt(invalidJwt).authorities(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                { "query": "What is UNADA?" }
                                                """))
                                .andExpect(status().isForbidden());
        }

        @Test
        void returns_200_with_answer_and_evidence_when_rag_finds_results() throws Exception {
                var chunk = new DocumentChunk("doc-1", TenantId.from("org-test"), "relevant content", 0.9);
                when(queryRagUseCase.execute(any()))
                                .thenReturn(QueryRagResult.withEvidence("Answer based on evidence", List.of(chunk)));

                mockMvc.perform(post("/rag/query")
                                .with(jwtForTenant("org-test"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                { "query": "What is UNADA?" }
                                                """))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.answer").value("Answer based on evidence"))
                                .andExpect(jsonPath("$.hasEvidence").value(true))
                                .andExpect(jsonPath("$.evidence").isArray())
                                .andExpect(jsonPath("$.evidence[0].documentId").value("doc-1"))
                                .andExpect(jsonPath("$.evidence[0].score").value(0.9))
                                .andExpect(jsonPath("$.evidence[0].content").doesNotExist());
        }

        @Test
        void returns_200_with_no_evidence_when_rag_finds_nothing() throws Exception {
                when(queryRagUseCase.execute(any()))
                                .thenReturn(QueryRagResult.noEvidence());

                mockMvc.perform(post("/rag/query")
                                .with(jwtForTenant("org-test"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {
                                                  "query": "What is UNADA?",
                                                  "topK": 5
                                                }
                                                """))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.hasEvidence").value(false))
                                .andExpect(jsonPath("$.evidence").isEmpty());
        }

        @Test
        void returns_400_when_query_is_blank() throws Exception {
                mockMvc.perform(post("/rag/query")
                                .with(jwtForTenant("org-test"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                { "query": "" }
                                                """))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.title").value("Validation failed"))
                                .andExpect(jsonPath("$.errors.query").exists());
        }

        @Test
        void returns_400_when_conversationId_has_invalid_format() throws Exception {
                mockMvc.perform(post("/rag/query")
                                .with(jwtForTenant("org-test"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {
                                                  "query": "What is UNADA?",
                                                  "conversationId": "not-a-uuid"
                                                }
                                                """))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.title").value("Validation failed"))
                                .andExpect(jsonPath("$.errors.conversationId").exists());
        }

        @Test
        void returns_400_when_topK_exceeds_maximum() throws Exception {
                mockMvc.perform(post("/rag/query")
                                .with(jwtForTenant("org-test"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {
                                                  "query": "What is UNADA?",
                                                  "topK": 100
                                                }
                                                """))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.title").value("Validation failed"))
                                .andExpect(jsonPath("$.errors.topK").exists());
        }

        @Test
        void returns_502_when_llm_provider_fails() throws Exception {
                when(queryRagUseCase.execute(any()))
                                .thenThrow(new LlmProviderException("LLM provider failed",
                                                new RuntimeException("OpenAI timeout")));

                mockMvc.perform(post("/rag/query")
                                .with(jwtForTenant("org-test"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                { "query": "What is UNADA?" }
                                                """))
                                .andExpect(status().isBadGateway())
                                .andExpect(jsonPath("$.title").value("AI service unavailable"));
        }

        @Test
        void returns_429_when_rate_limit_exceeded() throws Exception {
                when(queryRagUseCase.execute(any()))
                                .thenThrow(new RateLimitExceededException("org-test"));

                mockMvc.perform(post("/rag/query")
                                .with(jwtForTenant("org-test"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                { "query": "What is UNADA?" }
                                                """))
                                .andExpect(status().isTooManyRequests())
                                .andExpect(jsonPath("$.title").value("Rate limit exceeded"));
        }

        private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor jwtForTenant(
                        String tenantId) {
                Jwt jwt = Jwt.withTokenValue("token")
                                .header("alg", "none")
                                .issuedAt(Instant.now())
                                .expiresAt(Instant.now().plusSeconds(3600))
                                .claim("attributes", Map.of("tenant_id", List.of(tenantId)))
                                .build();

                return jwt().jwt(jwt).authorities(new SimpleGrantedAuthority("ROLE_ORG_MEMBER"));
        }
}