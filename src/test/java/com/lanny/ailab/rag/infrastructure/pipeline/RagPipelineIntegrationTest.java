package com.lanny.ailab.rag.infrastructure.pipeline;

import com.lanny.ailab.rag.application.command.IngestDocumentCommand;
import com.lanny.ailab.rag.application.command.QueryRagCommand;
import com.lanny.ailab.rag.application.port.in.GetIngestionStatusUseCase;
import com.lanny.ailab.rag.application.port.in.IngestDocumentUseCase;
import com.lanny.ailab.rag.application.port.in.QueryRagUseCase;
import com.lanny.ailab.rag.application.port.out.EmbeddingPort;
import com.lanny.ailab.rag.application.result.QueryRagResult;
import com.lanny.ailab.rag.domain.model.IngestionStatus;
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

import java.time.Duration;
import java.time.Instant;

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
                registry.add("app.rag.ingestion.poll-delay-ms", () -> "50");
        }

        @MockitoBean
        private EmbeddingPort embeddingPort;

        @Autowired
        private IngestDocumentUseCase ingestDocumentUseCase;

        @Autowired
        private GetIngestionStatusUseCase getIngestionStatusUseCase;

        @Autowired
        private QueryRagUseCase queryRagUseCase;

        @Autowired
        private JdbcTemplate jdbcTemplate;

        private static final float[] FIXED_EMBEDDING = syntheticEmbedding(1536, 0.1f);

        @BeforeEach
        void setUp() {
                when(embeddingPort.embed(anyString())).thenReturn(FIXED_EMBEDDING);
                jdbcTemplate.execute("DELETE FROM document_chunks");
                jdbcTemplate.execute("DELETE FROM document_ingestions");
        }

        @Test
        @DisplayName("The pipeline should return evidence after ingesting a document for the same tenant")
        void query_returns_evidence_after_ingesting_document_for_same_tenant() {
                TenantId tenant = TenantId.from("org-alpha");

                ingestDocumentUseCase.execute(new IngestDocumentCommand(
                                "doc-1",
                                tenant,
                                "UNADA es una plataforma de recursos sociales para técnicas de organizaciones."));
                awaitStatus(tenant, "doc-1", IngestionStatus.COMPLETED);

                QueryRagResult result = queryRagUseCase.execute(new QueryRagCommand(
                                "Qué es UNADA", tenant, null, 5));

                assertThat(result.hasEvidence())
                                .as("El pipeline debe recuperar evidencia del documento ingestionado")
                                .isTrue();
                assertThat(result.evidence()).isNotEmpty();
                assertThat(result.evidence().get(0).documentId()).isEqualTo("doc-1");
        }

        @Test
        @DisplayName("The pipeline should not return evidence for a document belonging to a different tenant")
        void query_returns_no_evidence_when_document_belongs_to_different_tenant() {
                TenantId tenantA = TenantId.from("org-alpha");
                TenantId tenantB = TenantId.from("org-beta");

                ingestDocumentUseCase.execute(new IngestDocumentCommand(
                                "doc-confidencial",
                                tenantA,
                                "Información confidencial exclusiva de org-alpha."));
                awaitStatus(tenantA, "doc-confidencial", IngestionStatus.COMPLETED);

                QueryRagResult result = queryRagUseCase.execute(new QueryRagCommand(
                                "Información confidencial", tenantB, null, 5));

                assertThat(result.hasEvidence())
                                .as("tenantB NO debe ver documentos de tenantA — fuga de datos")
                                .isFalse();
                assertThat(result.evidence()).isEmpty();
        }

        @Test
        @DisplayName("The pipeline should return only chunks belonging to the querying tenant")
        void query_returns_only_chunks_belonging_to_querying_tenant() {
                TenantId tenantA = TenantId.from("org-alpha");
                TenantId tenantB = TenantId.from("org-beta");

                ingestDocumentUseCase.execute(new IngestDocumentCommand(
                                "doc-alpha", tenantA, "Recurso exclusivo de org-alpha."));
                ingestDocumentUseCase.execute(new IngestDocumentCommand(
                                "doc-beta", tenantB, "Recurso exclusivo de org-beta."));
                awaitStatus(tenantA, "doc-alpha", IngestionStatus.COMPLETED);
                awaitStatus(tenantB, "doc-beta", IngestionStatus.COMPLETED);

                QueryRagResult resultA = queryRagUseCase.execute(
                                new QueryRagCommand("recurso", tenantA, null, 10));

                assertThat(resultA.evidence())
                                .as("tenantA solo debe ver sus propios documentos")
                                .allMatch(chunk -> chunk.documentId().equals("doc-alpha"));
                assertThat(resultA.evidence())
                                .noneMatch(chunk -> chunk.documentId().equals("doc-beta"));
        }

        @Test
        @DisplayName("The pipeline should return no evidence when no documents have been ingested")
        void query_returns_no_evidence_when_no_documents_ingested() {
                QueryRagResult result = queryRagUseCase.execute(new QueryRagCommand(
                                "cualquier consulta", TenantId.from("org-nueva"), null, 5));

                assertThat(result.hasEvidence()).isFalse();
                assertThat(result.evidence()).isEmpty();
        }

        @Test
        @DisplayName("Re-ingesting the same document should replace existing chunks, not accumulate them")
        void re_ingesting_same_document_replaces_existing_chunks() {
                TenantId tenant = TenantId.from("org-alpha");

                ingestDocumentUseCase.execute(new IngestDocumentCommand(
                                "doc-1", tenant, "Versión original del documento."));
                awaitStatus(tenant, "doc-1", IngestionStatus.COMPLETED);
                ingestDocumentUseCase.execute(new IngestDocumentCommand(
                                "doc-1", tenant, "Versión actualizada del documento."));
                awaitStatus(tenant, "doc-1", IngestionStatus.COMPLETED);

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

        private void awaitStatus(TenantId tenantId, String documentId, IngestionStatus expectedStatus) {
                Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
                while (Instant.now().isBefore(deadline)) {
                        var status = getIngestionStatusUseCase.findStatus(tenantId, documentId);
                        if (status.isPresent() && status.get().status() == expectedStatus) {
                                return;
                        }

                        try {
                                Thread.sleep(50);
                        } catch (InterruptedException ex) {
                                Thread.currentThread().interrupt();
                                throw new IllegalStateException("Interrupted while waiting for ingestion completion",
                                                ex);
                        }
                }

                throw new AssertionError("Timed out waiting for ingestion status " + expectedStatus);
        }
}
