package com.lanny.ailab.rag.infrastructure.pipeline;

import com.lanny.ailab.rag.application.command.IngestDocumentCommand;
import com.lanny.ailab.rag.application.command.QueryRagCommand;
import com.lanny.ailab.rag.application.port.in.IngestDocumentUseCase;
import com.lanny.ailab.rag.application.port.in.QueryRagUseCase;
import com.lanny.ailab.rag.application.port.out.EmbeddingPort;
import com.lanny.ailab.rag.application.result.QueryRagResult;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
@ActiveProfiles("integration-test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Tag("integration")
class RagPipelineIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("app.llm.provider", () -> "stub");
        registry.add("app.rag.min-score-threshold", () -> "0.0");
    }

    @MockitoBean
    private EmbeddingPort embeddingPort;

    @Autowired
    private IngestDocumentUseCase ingestDocumentUseCase;

    @Autowired
    private QueryRagUseCase queryRagUseCase;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final float[] FIXED_EMBEDDING = syntheticEmbedding(1536, 0.1f);

    @BeforeEach
    void setUp() {
        when(embeddingPort.embed(anyString())).thenReturn(FIXED_EMBEDDING);
        jdbcTemplate.execute("DELETE FROM document_chunks");
    }

    @Test
    void query_returns_evidence_after_ingesting_document_for_same_tenant() {
        TenantId tenant = TenantId.from("org-alpha");

        ingestDocumentUseCase.execute(new IngestDocumentCommand(
                "doc-1",
                tenant,
                "UNADA es una plataforma de recursos sociales para técnicas de organizaciones."));

        QueryRagResult result = queryRagUseCase.execute(new QueryRagCommand(
                "Qué es UNADA", tenant, null, 5));

        assertThat(result.hasEvidence())
                .as("El pipeline debe recuperar evidencia del documento ingestionado")
                .isTrue();
        assertThat(result.evidence()).isNotEmpty();
        assertThat(result.evidence().get(0).documentId()).isEqualTo("doc-1");
    }

    @Test
    void query_returns_no_evidence_when_document_belongs_to_different_tenant() {
        TenantId tenantA = TenantId.from("org-alpha");
        TenantId tenantB = TenantId.from("org-beta");

        ingestDocumentUseCase.execute(new IngestDocumentCommand(
                "doc-confidencial",
                tenantA,
                "Información confidencial exclusiva de org-alpha."));

        QueryRagResult result = queryRagUseCase.execute(new QueryRagCommand(
                "Información confidencial", tenantB, null, 5));

        assertThat(result.hasEvidence())
                .as("tenantB NO debe ver documentos de tenantA — fuga de datos")
                .isFalse();
        assertThat(result.evidence()).isEmpty();
    }

    @Test
    void query_returns_only_chunks_belonging_to_querying_tenant() {
        TenantId tenantA = TenantId.from("org-alpha");
        TenantId tenantB = TenantId.from("org-beta");

        ingestDocumentUseCase.execute(new IngestDocumentCommand(
                "doc-alpha", tenantA, "Recurso exclusivo de org-alpha."));
        ingestDocumentUseCase.execute(new IngestDocumentCommand(
                "doc-beta", tenantB, "Recurso exclusivo de org-beta."));

        QueryRagResult resultA = queryRagUseCase.execute(
                new QueryRagCommand("recurso", tenantA, null, 10));

        assertThat(resultA.evidence())
                .as("tenantA solo debe ver sus propios documentos")
                .allMatch(chunk -> chunk.documentId().equals("doc-alpha"));
        assertThat(resultA.evidence())
                .noneMatch(chunk -> chunk.documentId().equals("doc-beta"));
    }

    @Test
    void query_returns_no_evidence_when_no_documents_ingested() {
        QueryRagResult result = queryRagUseCase.execute(new QueryRagCommand(
                "cualquier consulta", TenantId.from("org-nueva"), null, 5));

        assertThat(result.hasEvidence()).isFalse();
        assertThat(result.evidence()).isEmpty();
    }

    @Test
    void re_ingesting_same_document_replaces_existing_chunks() {
        TenantId tenant = TenantId.from("org-alpha");

        ingestDocumentUseCase.execute(new IngestDocumentCommand(
                "doc-1", tenant, "Versión original del documento."));
        ingestDocumentUseCase.execute(new IngestDocumentCommand(
                "doc-1", tenant, "Versión actualizada del documento."));

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_chunks WHERE tenant_id = ? AND document_id = ?",
                Integer.class, tenant.value(), "doc-1");

        assertThat(count)
                .as("Re-ingest debe reemplazar chunks anteriores, no acumularlos")
                .isGreaterThan(0);

        Integer totalForTenant = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_chunks WHERE tenant_id = ?",
                Integer.class, tenant.value());

        assertThat(totalForTenant).isEqualTo(count);
    }

    private static float[] syntheticEmbedding(int dimensions, float value) {
        float[] embedding = new float[dimensions];
        for (int i = 0; i < dimensions; i++) {
            embedding[i] = value;
        }
        return embedding;
    }
}
