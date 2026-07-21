package com.lanny.ailab.rag.application.service;

import com.lanny.ailab.rag.application.command.IngestDocumentCommand;
import com.lanny.ailab.rag.application.port.in.GetIngestionStatusUseCase;
import com.lanny.ailab.rag.application.port.in.IngestDocumentUseCase;
import com.lanny.ailab.rag.application.port.out.EmbeddingPort;
import com.lanny.ailab.rag.application.port.out.VectorStorePort;
import com.lanny.ailab.rag.domain.exception.LlmProviderException;
import com.lanny.ailab.rag.domain.model.IngestionStatus;
import com.lanny.ailab.rag.domain.valueobject.TenantId;
import com.lanny.ailab.testutil.EmbeddingTestUtils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration tests for the asynchronous ingestion worker and durable job
 * state.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("integration-test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Tag("integration")
class IngestionWorkerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("app.llm.provider", () -> "stub");
        registry.add("app.rag.ingestion.poll-delay-ms", () -> "50");
        registry.add("app.rag.ingestion.retry.initial-backoff-seconds", () -> "1");
        registry.add("app.rag.ingestion.retry.max-attempts", () -> "2");
        registry.add("app.rag.ingestion.processing-timeout-seconds", () -> "1");
    }

    @MockitoBean
    private EmbeddingPort embeddingPort;

    @MockitoBean
    private VectorStorePort vectorStorePort;

    @Autowired
    private IngestDocumentUseCase ingestDocumentUseCase;

    @Autowired
    private GetIngestionStatusUseCase getIngestionStatusUseCase;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final float[] EMBEDDING = EmbeddingTestUtils.syntheticEmbedding(1536);

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM document_chunks");
        jdbcTemplate.execute("DELETE FROM document_ingestions");
    }

    @Test
    @DisplayName("When embedding generation fails twice, the existing document chunks are preserved and the job is dead-lettered")
    void keeps_existing_document_when_embedding_generation_fails() {
        insertChunk("org-alpha", "doc-1", "original chunk");
        when(embeddingPort.embed(anyString()))
                .thenThrow(new LlmProviderException("embedding failed", new RuntimeException("boom")));

        var accepted = ingestDocumentUseCase.execute(new IngestDocumentCommand(
                "doc-1", TenantId.from("org-alpha"), "updated content for reingestion", null));

        assertThat(accepted.status()).isEqualTo(IngestionStatus.PENDING);

        var status = awaitStatus("org-alpha", "doc-1", IngestionStatus.DEAD_LETTER);

        assertThat(countChunks("org-alpha", "doc-1")).isEqualTo(1);
        assertThat(loadContents("org-alpha", "doc-1")).containsExactly("original chunk");
        assertThat(status.retryCount()).isEqualTo(2);
        assertThat(status.deadLetteredAt()).isNotNull();
        verify(vectorStorePort, never()).store(any(TenantId.class), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("When transactional store fails, the delete is rolled back and original chunks are preserved")
    void rolls_back_delete_when_transactional_store_fails() {
        insertChunk("org-alpha", "doc-1", "original chunk");
        when(embeddingPort.embed(anyString())).thenReturn(EMBEDDING);
        doThrow(new RuntimeException("db write failed"))
                .when(vectorStorePort)
                .store(any(TenantId.class), anyString(), anyString(), any());

        ingestDocumentUseCase.execute(new IngestDocumentCommand(
                "doc-1", TenantId.from("org-alpha"), "updated content for reingestion", null));

        var status = awaitStatus("org-alpha", "doc-1", IngestionStatus.DEAD_LETTER);

        assertThat(countChunks("org-alpha", "doc-1")).isEqualTo(1);
        assertThat(loadContents("org-alpha", "doc-1")).containsExactly("original chunk");
        assertThat(status.retryCount()).isEqualTo(2);
        assertThat(status.deadLetteredAt()).isNotNull();
    }

    @Test
    @DisplayName("When a processing job is stale after a crash, the worker reclaims and completes it")
    void reclaims_stale_processing_job_and_completes_it() {
        when(embeddingPort.embed(anyString())).thenReturn(EMBEDDING);
        insertStaleProcessingJob("org-alpha", "doc-stale", "recovered content");

        var status = awaitStatus("org-alpha", "doc-stale", IngestionStatus.COMPLETED);

        assertThat(status.retryCount()).isEqualTo(0);
        verify(vectorStorePort).store(any(TenantId.class), anyString(), anyString(), any());
    }

    private com.lanny.ailab.rag.application.result.IngestionStatusResult awaitStatus(
            String tenantId,
            String documentId,
            IngestionStatus expectedStatus) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (Instant.now().isBefore(deadline)) {
            var status = getIngestionStatusUseCase.findStatus(TenantId.from(tenantId), documentId);
            if (status.isPresent() && status.get().status() == expectedStatus) {
                return status.get();
            }

            try {
                Thread.sleep(50);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for ingestion status", ex);
            }
        }

        throw new AssertionError("Timed out waiting for ingestion status " + expectedStatus);
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

    private void insertStaleProcessingJob(String tenantId, String documentId, String content) {
        jdbcTemplate.update("""
                INSERT INTO document_ingestions (
                    tenant_id, document_id, content, status, request_version, chunks_indexed,
                    error_message, retry_count, max_attempts, requested_at, started_at,
                    updated_at, next_attempt_at
                )
                VALUES (?, ?, ?, 'PROCESSING', 1, 0, NULL, 0, 2, now() - interval '5 minutes',
                        now() - interval '5 minutes', now() - interval '5 minutes', now())
                """,
                tenantId,
                documentId,
                content);
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
