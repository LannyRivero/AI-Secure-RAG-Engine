package com.lanny.ailab.rag.infrastructure.adapter.out.retrieval;

import com.lanny.ailab.rag.domain.valueobject.DocumentChunk;
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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@ActiveProfiles("integration-test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PgVectorRetrieverIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("app.llm.provider", () -> "stub");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PgVectorRetriever retriever;

    private static final float[] EMBEDDING = syntheticEmbedding(1536, 0.1f);

    @BeforeEach
    void cleanBetweenTests() {
        jdbcTemplate.execute("DELETE FROM document_chunks");
    }

    @Test
    void retrieve_returns_only_chunks_for_given_tenant() {
        insertChunk("tenant-a", "doc-1", "Recurso de tenant A", EMBEDDING);
        insertChunk("tenant-b", "doc-2", "Recurso de tenant B", EMBEDDING);

        List<DocumentChunk> results = retriever.retrieve("query", "tenant-a", 10);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).tenantId()).isEqualTo("tenant-a");
        assertThat(results.get(0).documentId()).isEqualTo("doc-1");
    }

    @Test
    void retrieve_respects_topK_limit() {
        insertChunk("tenant-a", "doc-1", "Primer recurso", EMBEDDING);
        insertChunk("tenant-a", "doc-2", "Segundo recurso", EMBEDDING);
        insertChunk("tenant-a", "doc-3", "Tercer recurso", EMBEDDING);

        List<DocumentChunk> results = retriever.retrieve("query", "tenant-a", 2);

        assertThat(results).hasSize(2);
    }

    @Test
    void retrieve_returns_empty_when_no_chunks_for_tenant() {
        insertChunk("tenant-b", "doc-1", "Recurso de otro tenant", EMBEDDING);

        List<DocumentChunk> results = retriever.retrieve("query", "tenant-a", 10);

        assertThat(results).isEmpty();
    }

    @Test
    void retrieve_returns_empty_when_table_is_empty() {
        List<DocumentChunk> results = retriever.retrieve("query", "tenant-a", 10);

        assertThat(results).isEmpty();
    }

    private void insertChunk(String tenantId, String documentId,
                              String content, float[] embedding) {
        jdbcTemplate.update("""
                INSERT INTO document_chunks (id, tenant_id, document_id, content, embedding)
                VALUES (?, ?, ?, ?, ?::vector)
                """,
                UUID.randomUUID(),
                tenantId,
                documentId,
                content,
                toPgVector(embedding));
    }

    private static float[] syntheticEmbedding(int dimensions, float value) {
        float[] embedding = new float[dimensions];
        for (int i = 0; i < dimensions; i++) {
            embedding[i] = value;
        }
        return embedding;
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