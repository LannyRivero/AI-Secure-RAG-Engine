package com.lanny.ailab.rag.infrastructure.adapter.out.pgvector;

import com.lanny.ailab.rag.domain.valueobject.TenantId;

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
    }

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PgVectorStoreAdapter adapter;

    private static final float[] EMBEDDING = syntheticEmbedding(1536);

    @BeforeEach
    void clean() {
        jdbcTemplate.execute("DELETE FROM document_chunks");
    }

    @Test
    void given_valid_chunk_when_store_then_persists_row_with_correct_tenant_and_document() {
        adapter.store(TenantId.from("tenant-a"), "doc-1", "chunk content", EMBEDDING);

        int count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_chunks WHERE tenant_id = ? AND document_id = ?",
                Integer.class, "tenant-a", "doc-1");

        assertThat(count).isEqualTo(1);
    }

    @Test
    void given_multiple_chunks_when_store_each_then_all_are_persisted() {
        adapter.store(TenantId.from("tenant-a"), "doc-1", "chunk 1", EMBEDDING);
        adapter.store(TenantId.from("tenant-a"), "doc-1", "chunk 2", EMBEDDING);
        adapter.store(TenantId.from("tenant-a"), "doc-1", "chunk 3", EMBEDDING);

        int count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_chunks WHERE tenant_id = ? AND document_id = ?",
                Integer.class, "tenant-a", "doc-1");

        assertThat(count).isEqualTo(3);
    }

    @Test
    void given_same_document_different_tenants_when_store_then_rows_are_isolated() {
        adapter.store(TenantId.from("tenant-a"), "doc-1", "chunk for A", EMBEDDING);
        adapter.store(TenantId.from("tenant-b"), "doc-1", "chunk for B", EMBEDDING);

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
    void given_chunk_when_store_then_content_is_persisted_correctly() {
        String expectedContent = "this is the chunk text";
        adapter.store(TenantId.from("tenant-a"), "doc-1", expectedContent, EMBEDDING);

        String content = jdbcTemplate.queryForObject(
                "SELECT content FROM document_chunks WHERE tenant_id = ? AND document_id = ?",
                String.class, "tenant-a", "doc-1");

        assertThat(content).isEqualTo(expectedContent);
    }

    private static float[] syntheticEmbedding(int dimensions) {
        float[] e = new float[dimensions];
        for (int i = 0; i < dimensions; i++) e[i] = 0.1f;
        return e;
    }
}
