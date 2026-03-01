package com.lanny.ailab.rag.infrastructure.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lanny.ailab.rag.application.port.in.QueryRagUseCase;
import com.lanny.ailab.rag.application.result.QueryRagResult;
import com.lanny.ailab.rag.domain.valueobject.DocumentChunk;
import com.lanny.ailab.rag.infrastructure.adapter.in.web.dto.QueryRagRequest;
import com.lanny.ailab.rag.infrastructure.adapter.in.web.mapper.QueryRagWebMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RagController.class)
@Import(QueryRagWebMapper.class)
class RagControllerAcceptanceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private QueryRagUseCase queryRagUseCase;

    @Test
    void returns_200_with_answer_and_evidence_when_rag_finds_results() throws Exception {
        var chunk = new DocumentChunk("doc-1", "org-test", "contenido relevante", 0.9);
        when(queryRagUseCase.execute(any()))
                .thenReturn(QueryRagResult.withEvidence("Respuesta basada en evidencia", List.of(chunk)));

        var request = new QueryRagRequest(
                "¿qué servicios ofrece UNADA?",
                "org-test",
                null,
                3);

        mockMvc.perform(post("/rag/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Respuesta basada en evidencia"))
                .andExpect(jsonPath("$.hasEvidence").value(true))
                .andExpect(jsonPath("$.evidence").isArray())
                .andExpect(jsonPath("$.evidence[0].documentId").value("doc-1"));
    }

    @Test
    void returns_200_with_no_evidence_message_when_rag_finds_nothing() throws Exception {
        when(queryRagUseCase.execute(any()))
                .thenReturn(QueryRagResult.noEvidence());

        var request = new QueryRagRequest(
                "¿qué es algo que no existe?",
                "org-test",
                null,
                3);

        mockMvc.perform(post("/rag/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasEvidence").value(false))
                .andExpect(jsonPath("$.evidence").isEmpty());
    }

    @Test
    void returns_400_when_query_is_blank() throws Exception {
        var request = new QueryRagRequest(
                "",
                "org-test",
                null,
                3);

        mockMvc.perform(post("/rag/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.query").exists());
    }

    @Test
    void returns_400_when_tenantId_has_invalid_format() throws Exception {
        var request = new QueryRagRequest(
                "query válida",
                "invalid tenant!",
                null,
                3);

        mockMvc.perform(post("/rag/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.tenantId").exists());
    }

    @Test
    void returns_400_when_topK_exceeds_maximum() throws Exception {
        var request = new QueryRagRequest(
                "query válida",
                "org-test",
                null,
                99);

        mockMvc.perform(post("/rag/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.topK").exists());
    }
}