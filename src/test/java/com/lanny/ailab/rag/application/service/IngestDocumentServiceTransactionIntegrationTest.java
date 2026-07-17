package com.lanny.ailab.rag.application.service;

import com.lanny.ailab.rag.application.command.IngestDocumentCommand;
import com.lanny.ailab.rag.application.port.in.IngestDocumentUseCase;
import com.lanny.ailab.rag.application.port.out.EmbeddingPort;
import com.lanny.ailab.rag.application.port.out.VectorStorePort;
import com.lanny.ailab.rag.domain.exception.LlmProviderException;
import com.lanny.ailab.rag.domain.valueobject.TenantId;
import com.lanny.ailab.testutil.EmbeddingTestUtils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
@ActiveProfiles("integration-test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Tag("integration")
class IngestDocumentServiceTransactionIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("app.llm.provider", () -> "stub");
    }

    @MockitoBean
    private EmbeddingPort embeddingPort;

    @MockitoBean
    private VectorStorePort vectorStorePort;

    @Autowired
    private IngestDocumentUseCase ingestDocumentUseCase;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final float[] EMBEDDING = EmbeddingTestUtils.syntheticEmbedding(1536);

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM document_chunks");
    }

    @Test
    void keeps_existing_document_when_embedding_generation_fails_before_transaction_starts() {
        insertChunk("org-alpha", "doc-1", "original chunk");
        when(embeddingPort.embed(anyString()))
                .thenThrow(new LlmProviderException("embedding failed", new RuntimeException("boom")));

        assertThatThrownBy(() -> ingestDocumentUseCase.execute(new IngestDocumentCommand(
                "doc-1", TenantId.from("org-alpha"), "updated content for reingestion")))
                .isInstanceOf(LlmProviderException.class)
                .hasMessage("embedding failed");

        assertThat(countChunks("org-alpha", "doc-1")).isEqualTo(1);
        assertThat(loadContents("org-alpha", "doc-1")).containsExactly("original chunk");
        verify(vectorStorePort, never()).store(any(TenantId.class), anyString(), anyString(), any());
    }

    @Test
    void rolls_back_delete_when_transactional_store_fails() {
        insertChunk("org-alpha", "doc-1", "original chunk");
        when(embeddingPort.embed(anyString())).thenReturn(EMBEDDING);
        doThrow(new RuntimeException("db write failed"))
                .when(vectorStorePort)
                .store(any(TenantId.class), anyString(), anyString(), any());

        assertThatThrownBy(() -> ingestDocumentUseCase.execute(new IngestDocumentCommand(
                "doc-1", TenantId.from("org-alpha"), "updated content for reingestion")))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("db write failed");

        assertThat(countChunks("org-alpha", "doc-1")).isEqualTo(1);
        assertThat(loadContents("org-alpha", "doc-1")).containsExactly("original chunk");
    }

    private void insertChunk(String tenantId, String documentId, String content) {
        jdbcTemplate.update("""
                INSERT INTO document_chunks (id, tenant_id, document_id, content, embedding)
                VALUES (?, ?, ?, ?, ?::vector)
                """,
                UUID.randomUUID(),
                tenantId,
                documentId,
                content,
                EmbeddingTestUtils.toPgVector(EMBEDDING));
    }

    private int countChunks(String tenantId, String documentId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_chunks WHERE tenant_id = ? AND document_id = ?",
                Integer.class,
                tenantId,
                documentId);
    }

    private java.util.List<String> loadContents(String tenantId, String documentId) {
        return jdbcTemplate.queryForList(
                "SELECT content FROM document_chunks WHERE tenant_id = ? AND document_id = ? ORDER BY content",
                String.class,
                tenantId,
                documentId);
    }
}
