package com.lanny.ailab.rag.infrastructure.adapter.out.pgvector;

import org.junit.jupiter.api.BeforeEach;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@ActiveProfiles("integration-test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
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
    }

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PgDocumentRepository repository;

    private static final float[] EMBEDDING = syntheticEmbedding(1536);

    @BeforeEach
    void clean() {
        jdbcTemplate.execute("DELETE FROM document_chunks");
    }

    @Test
    void deletes_only_chunks_for_given_tenant_and_document() {
        insertChunk("tenant-a", "doc-1", "chunk A1");
        insertChunk("tenant-a", "doc-2", "chunk A2");
        insertChunk("tenant-b", "doc-1", "chunk B1");

        repository.deleteByTenantAndDocument("tenant-a", "doc-1");

        int remaining = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_chunks", Integer.class);

        assertThat(remaining).isEqualTo(2);

        int tenantADoc1 = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_chunks WHERE tenant_id = ? AND document_id = ?",
                Integer.class, "tenant-a", "doc-1");

        assertThat(tenantADoc1).isEqualTo(0);
    }

    @Test
    void does_not_delete_chunks_of_other_tenant_with_same_document_id() {
        insertChunk("tenant-a", "doc-1", "chunk A");
        insertChunk("tenant-b", "doc-1", "chunk B");

        repository.deleteByTenantAndDocument("tenant-a", "doc-1");

        int tenantBChunks = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_chunks WHERE tenant_id = ?",
                Integer.class, "tenant-b");

        assertThat(tenantBChunks).isEqualTo(1);
    }

    @Test
    void delete_is_idempotent_when_no_chunks_exist() {
        repository.deleteByTenantAndDocument("tenant-x", "non-existent-doc");

        int count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_chunks", Integer.class);
        assertThat(count).isEqualTo(0);
    }

    @Test
    void deletes_all_chunks_for_document_not_just_first() {
        insertChunk("tenant-a", "doc-1", "chunk 1");
        insertChunk("tenant-a", "doc-1", "chunk 2");
        insertChunk("tenant-a", "doc-1", "chunk 3");

        repository.deleteByTenantAndDocument("tenant-a", "doc-1");

        int remaining = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_chunks WHERE tenant_id = ? AND document_id = ?",
                Integer.class, "tenant-a", "doc-1");

        assertThat(remaining).isEqualTo(0);
    }

    private void insertChunk(String tenantId, String documentId, String content) {
        jdbcTemplate.update("""
                INSERT INTO document_chunks (id, tenant_id, document_id, content, embedding)
                VALUES (?, ?, ?, ?, ?::vector)
                """,
                UUID.randomUUID(), tenantId, documentId, content, toPgVector(EMBEDDING));
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
