package com.lanny.ailab.rag.infrastructure.adapter.out.pgvector;

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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.lanny.ailab.rag.domain.valueobject.TenantId;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@ActiveProfiles("integration-test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Tag("integration")
class PgDocumentRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("app.llm.provider", () -> "stub");
        registry.add("app.rag.ingestion.worker.enabled", () -> "false");
    }

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PgDocumentRepository repository;

    private static final float[] EMBEDDING = syntheticEmbedding(1536);

    @BeforeEach
    void clean() {
        jdbcTemplate.execute("DELETE FROM document_chunks");
        jdbcTemplate.execute("DELETE FROM document_ingestions");
    }

    @Test
    @DisplayName("When deleting a document, only the chunks for the given tenant and document are removed")
    void deletes_only_chunks_for_given_tenant_and_document() {
        insertChunk("tenant-a", "doc-1", "chunk A1");
        insertChunk("tenant-a", "doc-2", "chunk A2");
        insertChunk("tenant-b", "doc-1", "chunk B1");

        repository.deleteByTenantAndDocument(TenantId.from("tenant-a"), "doc-1");

        int remaining = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_chunks", Integer.class);

        assertThat(remaining).isEqualTo(2);

        int tenantADoc1 = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_chunks WHERE tenant_id = ? AND document_id = ?",
                Integer.class, "tenant-a", "doc-1");

        assertThat(tenantADoc1).isEqualTo(0);
    }

    @Test
    @DisplayName("When deleting an ingestion job, only the job for the given tenant and document is removed")
    void does_not_delete_chunks_of_other_tenant_with_same_document_id() {
        insertChunk("tenant-a", "doc-1", "chunk A");
        insertChunk("tenant-b", "doc-1", "chunk B");

        repository.deleteByTenantAndDocument(TenantId.from("tenant-a"), "doc-1");

        int tenantBChunks = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_chunks WHERE tenant_id = ?",
                Integer.class, "tenant-b");

        assertThat(tenantBChunks).isEqualTo(1);
    }

    @Test
    @DisplayName("Deleting a non-existent document does not throw an error and leaves the database unchanged")
    void delete_is_idempotent_when_no_chunks_exist() {
        repository.deleteByTenantAndDocument(TenantId.from("tenant-x"), "non-existent-doc");

        int count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_chunks", Integer.class);
        assertThat(count).isEqualTo(0);
    }

    @Test
    @DisplayName("Deletes all chunks for a document, not just the first one")
    void deletes_all_chunks_for_document_not_just_first() {
        insertChunk("tenant-a", "doc-1", "chunk 1");
        insertChunk("tenant-a", "doc-1", "chunk 2");
        insertChunk("tenant-a", "doc-1", "chunk 3");

        repository.deleteByTenantAndDocument(TenantId.from("tenant-a"), "doc-1");

        int remaining = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_chunks WHERE tenant_id = ? AND document_id = ?",
                Integer.class, "tenant-a", "doc-1");

        assertThat(remaining).isEqualTo(0);
    }

    @Test
    @DisplayName("Deletes the ingestion job for a given tenant and document")
    void deletes_ingestion_job_for_given_tenant_and_document() {
        insertIngestionJob("tenant-a", "doc-1", "raw content");
        insertIngestionJob("tenant-a", "doc-2", "other content");

        repository.deleteIngestionJobByTenantAndDocument(TenantId.from("tenant-a"), "doc-1");

        int remaining = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_ingestions WHERE tenant_id = ? AND document_id = ?",
                Integer.class,
                "tenant-a",
                "doc-1");

        int untouched = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_ingestions WHERE tenant_id = ? AND document_id = ?",
                Integer.class,
                "tenant-a",
                "doc-2");

        assertThat(remaining).isEqualTo(0);
        assertThat(untouched).isEqualTo(1);
    }

    @Test
    @DisplayName("Reports ingestion job existence independently from chunks")
    void reports_ingestion_job_existence_independently_from_chunks() {
        insertIngestionJob("tenant-a", "doc-1", "raw content");

        assertThat(repository.existsByTenantAndDocument(TenantId.from("tenant-a"), "doc-1")).isFalse();
        assertThat(repository.existsIngestionJobByTenantAndDocument(TenantId.from("tenant-a"), "doc-1")).isTrue();
    }

    private void insertChunk(String tenantId, String documentId, String content) {
        jdbcTemplate.update("""
                INSERT INTO document_chunks (id, tenant_id, document_id, content, embedding)
                VALUES (?, ?, ?, ?, ?::vector)
                """,
                UUID.randomUUID(), tenantId, documentId, content, toPgVector(EMBEDDING));
    }

    private void insertIngestionJob(String tenantId, String documentId, String content) {
        jdbcTemplate.update("""
                INSERT INTO document_ingestions (
                    tenant_id, document_id, content, status, request_version, chunks_indexed,
                    error_message, retry_count, max_attempts, requested_at, updated_at, next_attempt_at
                )
                VALUES (?, ?, ?, 'PENDING', 1, 0, NULL, 0, 3, now(), now(), now())
                """,
                tenantId,
                documentId,
                content);
    }

    private static float[] syntheticEmbedding(int dimensions) {
        float[] e = new float[dimensions];
        for (int i = 0; i < dimensions; i++) e[i] = 0.1f;
        return e;
    }

    private String toPgVector(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            sb.append(embedding[i]);
            if (i < embedding.length - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }
}
