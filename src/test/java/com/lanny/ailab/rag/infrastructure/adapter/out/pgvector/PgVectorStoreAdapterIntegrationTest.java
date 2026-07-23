package com.lanny.ailab.rag.infrastructure.adapter.out.pgvector;

import com.lanny.ailab.rag.domain.model.DocumentMetadata;
import com.lanny.ailab.rag.domain.valueobject.TenantId;

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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@ActiveProfiles("integration-test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Tag("integration")
class PgVectorStoreAdapterIntegrationTest {

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
    @Autowired private PgVectorStoreAdapter adapter;

    private static final float[] EMBEDDING = syntheticEmbedding(1536);

    @BeforeEach
    void clean() {
        jdbcTemplate.execute("DELETE FROM document_chunks");
    }

    @Test
    @DisplayName("given valid chunk when store then persists row with correct tenant and document")
    void given_valid_chunk_when_store_then_persists_row_with_correct_tenant_and_document() {
        adapter.store(TenantId.from("tenant-a"), "doc-1", "chunk content", EMBEDDING, DocumentMetadata.empty());

        int count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_chunks WHERE tenant_id = ? AND document_id = ?",
                Integer.class, "tenant-a", "doc-1");

        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("given multiple chunks when store each then all are persisted")
    void given_multiple_chunks_when_store_each_then_all_are_persisted() {
        adapter.store(TenantId.from("tenant-a"), "doc-1", "chunk 1", EMBEDDING, DocumentMetadata.empty());
        adapter.store(TenantId.from("tenant-a"), "doc-1", "chunk 2", EMBEDDING, DocumentMetadata.empty());
        adapter.store(TenantId.from("tenant-a"), "doc-1", "chunk 3", EMBEDDING, DocumentMetadata.empty());

        int count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_chunks WHERE tenant_id = ? AND document_id = ?",
                Integer.class, "tenant-a", "doc-1");

        assertThat(count).isEqualTo(3);
    }

    @Test
    @DisplayName("given same document different tenants when store then rows are isolated")
    void given_same_document_different_tenants_when_store_then_rows_are_isolated() {
        adapter.store(TenantId.from("tenant-a"), "doc-1", "chunk for A", EMBEDDING, DocumentMetadata.empty());
        adapter.store(TenantId.from("tenant-b"), "doc-1", "chunk for B", EMBEDDING, DocumentMetadata.empty());

        int countA = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_chunks WHERE tenant_id = ?",
                Integer.class, "tenant-a");
        int countB = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_chunks WHERE tenant_id = ?",
                Integer.class, "tenant-b");

        assertThat(countA).isEqualTo(1);
        assertThat(countB).isEqualTo(1);
    }

    @Test
    @DisplayName("given chunk when store then content is persisted correctly")
    void given_chunk_when_store_then_content_is_persisted_correctly() {
        String expectedContent = "this is the chunk text";
        adapter.store(TenantId.from("tenant-a"), "doc-1", expectedContent, EMBEDDING, DocumentMetadata.empty());

        String content = jdbcTemplate.queryForObject(
                "SELECT content FROM document_chunks WHERE tenant_id = ? AND document_id = ?",
                String.class, "tenant-a", "doc-1");

        assertThat(content).isEqualTo(expectedContent);
    }

    @Test
    @DisplayName("given chunk metadata when store then persists structured metadata")
    void given_chunk_metadata_when_store_then_persists_structured_metadata() {
        DocumentMetadata metadata = new DocumentMetadata(
                "policy",
                java.time.LocalDate.parse("2026-07-20"),
                "notion",
                java.util.List.of("security", "internal"),
                "alice",
                "restricted");

        adapter.store(TenantId.from("tenant-a"), "doc-1", "chunk content", EMBEDDING, metadata);

        java.util.Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT metadata_document_type, metadata_document_date, metadata_source, metadata_tags, metadata_owner, metadata_classification FROM document_chunks WHERE tenant_id = ? AND document_id = ?",
                "tenant-a", "doc-1");

        assertThat(row.get("metadata_document_type")).isEqualTo("policy");
        assertThat(row.get("metadata_source")).isEqualTo("notion");
        assertThat(row.get("metadata_owner")).isEqualTo("alice");
        assertThat(row.get("metadata_classification")).isEqualTo("restricted");
    }

    private static float[] syntheticEmbedding(int dimensions) {
        float[] e = new float[dimensions];
        for (int i = 0; i < dimensions; i++) e[i] = 0.1f;
        return e;
    }
}
