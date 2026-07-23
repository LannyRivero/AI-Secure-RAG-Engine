package com.lanny.ailab.rag.infrastructure.adapter.out.retrieval;

import com.lanny.ailab.rag.application.port.out.EmbeddingPort;
import com.lanny.ailab.rag.application.port.out.RetrievalPort;
import com.lanny.ailab.rag.application.model.RetrievalFilter;
import com.lanny.ailab.rag.domain.valueobject.DocumentChunk;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
@ActiveProfiles("integration-test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Tag("integration")
class HybridRetrieverIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("app.llm.provider", () -> "stub");
        registry.add("app.rag.retriever", () -> "hybrid");
        registry.add("app.rag.ingestion.worker.enabled", () -> "false");
    }

    @MockitoBean
    private EmbeddingPort embeddingPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RetrievalPort retriever;

    private static final float[] EMBEDDING = syntheticEmbedding(1536, 0.1f);
    private static final TenantId TENANT = TenantId.from("org-test");

    @BeforeEach
    void setUp() {
        when(embeddingPort.embed(anyString())).thenReturn(EMBEDDING);
        jdbcTemplate.execute("DELETE FROM document_chunks");
    }

    @Test
    @DisplayName("given chunk with keyword match when retrieve then returns chunk")
    void given_chunk_with_keyword_match_when_retrieve_then_returns_chunk() {
        insertChunk("doc-1", "UNADA Conecta Dona is a social resources platform.");

        List<DocumentChunk> results = retriever.retrieve("UNADA Conecta Dona", TENANT, 5, RetrievalFilter.empty());

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).documentId()).isEqualTo("doc-1");
    }

    @Test
    @DisplayName("given chunk semantically similar when retrieve then returns chunk")
    void given_chunk_semantically_similar_when_retrieve_then_returns_chunk() {
        insertChunk("doc-1", "The platform helps social organisations manage their resources.");

        List<DocumentChunk> results = retriever.retrieve("tool for NGOs", TENANT, 5, RetrievalFilter.empty());

        // Vector search recovers by semantic similarity even without an exact keyword
        // match
        assertThat(results).isNotEmpty();
    }

    @Test
    @DisplayName("given chunk matching both searches when retrieve then ranks first")
    void given_chunk_matching_both_searches_when_retrieve_then_ranks_first() {
        // doc-1 matches both by keyword and semantically
        insertChunk("doc-1", "UNADA is a digital platform for social resources.");
        // doc-2 only semantically similar
        insertChunk("doc-2", "Tool for managing non-profit organisations.");

        List<DocumentChunk> results = retriever.retrieve("UNADA platform", TENANT, 5, RetrievalFilter.empty());

        assertThat(results).isNotEmpty();
        // doc-1 should rank first due to higher combined RRF score
        assertThat(results.get(0).documentId()).isEqualTo("doc-1");
    }

    @Test
    @DisplayName("given no chunks for tenant when retrieve then returns empty")
    void given_no_chunks_for_tenant_when_retrieve_then_returns_empty() {
        insertChunk("doc-1", "Some content.");
        jdbcTemplate.update("UPDATE document_chunks SET tenant_id = 'other-tenant'");

        List<DocumentChunk> results = retriever.retrieve("content", TENANT, 5, RetrievalFilter.empty());

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("given chunks from two tenants when retrieve for own tenant then returns only own chunks")
    void given_chunks_from_two_tenants_when_retrieve_for_own_tenant_then_returns_only_own_chunks() {
        insertChunk("doc-alpha", "Confidential resource for org-test.");

        jdbcTemplate.update("""
                INSERT INTO document_chunks (id, tenant_id, document_id, content, embedding)
                VALUES (?, 'other-org', 'doc-beta', 'Resource from another tenant.', ?::vector)
                """,
                UUID.randomUUID(), toPgVector(EMBEDDING));

        List<DocumentChunk> results = retriever.retrieve("confidential resource", TENANT, 10, RetrievalFilter.empty());

        assertThat(results)
                .allMatch(chunk -> chunk.tenantId().equals(TENANT));
        assertThat(results)
                .noneMatch(chunk -> chunk.documentId().equals("doc-beta"));
    }

    @Test
    @DisplayName("given metadata filter when retrieve then only matching chunks are returned")
    void given_metadata_filter_when_retrieve_then_only_matching_chunks_are_returned() {
        insertChunk("doc-policy", "Security policy for org-test.", "policy", "security");
        insertChunk("doc-guide", "Onboarding guide for org-test.", "guide", "hr");

        List<DocumentChunk> results = retriever.retrieve(
                "org-test",
                TENANT,
                10,
                new RetrievalFilter("policy", null, java.util.List.of("security"), null, null, null, null));

        assertThat(results)
                .allMatch(chunk -> "policy".equals(chunk.metadata().documentType()));
    }

    private void insertChunk(String documentId, String content) {
        jdbcTemplate.update("""
                INSERT INTO document_chunks (id, tenant_id, document_id, content, embedding, metadata_tags)
                VALUES (?, ?, ?, ?, ?::vector, ARRAY[]::TEXT[])
                """,
                UUID.randomUUID(), TENANT.value(), documentId, content,
                toPgVector(EMBEDDING));
    }

    private void insertChunk(String documentId, String content, String documentType, String tag) {
        jdbcTemplate.update("""
                INSERT INTO document_chunks (id, tenant_id, document_id, content, embedding, metadata_document_type, metadata_tags)
                VALUES (?, ?, ?, ?, ?::vector, ?, ARRAY[?]::TEXT[])
                """,
                UUID.randomUUID(), TENANT.value(), documentId, content,
                toPgVector(EMBEDDING), documentType, tag);
    }

    private static float[] syntheticEmbedding(int dimensions, float value) {
        float[] e = new float[dimensions];
        for (int i = 0; i < dimensions; i++)
            e[i] = value;
        return e;
    }

    private String toPgVector(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            sb.append(embedding[i]);
            if (i < embedding.length - 1)
                sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }
}
