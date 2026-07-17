package com.lanny.ailab.rag.infrastructure.adapter.out.retrieval;

import com.lanny.ailab.rag.application.port.out.EmbeddingPort;
import com.lanny.ailab.rag.application.port.out.RetrievalPort;
import com.lanny.ailab.rag.domain.valueobject.DocumentChunk;
import com.lanny.ailab.rag.domain.valueobject.SimilarityScore;
import com.lanny.ailab.rag.domain.valueobject.TenantId;
import com.lanny.ailab.rag.infrastructure.adapter.out.pgvector.PgVectorUtils;
import com.lanny.ailab.shared.infrastructure.observability.OperationMetrics;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public class PgVectorRetriever implements RetrievalPort {

    private final EmbeddingPort embeddingPort;
    private final JdbcTemplate jdbcTemplate;
    private final OperationMetrics operationMetrics;
    private final ObservationRegistry observationRegistry;

    public PgVectorRetriever(
            EmbeddingPort embeddingPort,
            JdbcTemplate jdbcTemplate,
            OperationMetrics operationMetrics,
            ObservationRegistry observationRegistry) {
        this.embeddingPort = embeddingPort;
        this.jdbcTemplate  = jdbcTemplate;
        this.operationMetrics = operationMetrics;
        this.observationRegistry = observationRegistry;
    }

    @Override
    public List<DocumentChunk> retrieve(String query, TenantId tenantId, int topK) {
        Instant started = Instant.now();
        String outcome = "success";
        Observation observation = Observation.start("rag.retrieval.vector", observationRegistry)
                .lowCardinalityKeyValue("retriever", "vector")
                .lowCardinalityKeyValue("phase", "full")
                .highCardinalityKeyValue("tenant.id", tenantId.value());

        try (Observation.Scope scope = observation.openScope()) {
            float[] queryEmbedding = embeddingPort.embed(query);
            String pgVector = PgVectorUtils.toPgVector(queryEmbedding);

            List<DocumentChunk> results = jdbcTemplate.query("""
                    SELECT document_id, content,
                           1 - (embedding <=> ?::vector) AS score
                    FROM document_chunks
                    WHERE tenant_id = ?
                    ORDER BY embedding <=> ?::vector
                    LIMIT ?
                    """,
                    (rs, rowNum) -> new DocumentChunk(
                            rs.getString("document_id"),
                            tenantId,
                            rs.getString("content"),
                            SimilarityScore.of(rs.getDouble("score"))),
                    pgVector,
                    tenantId.value(),
                    pgVector,
                    topK);

            operationMetrics.recordRetrieval("vector", "full", outcome, results.size(), Duration.between(started, Instant.now()));
            return results;
        } catch (RuntimeException ex) {
            outcome = "error";
            observation.error(ex);
            operationMetrics.recordRetrieval("vector", "full", outcome, 0, Duration.between(started, Instant.now()));
            throw ex;
        } finally {
            observation.lowCardinalityKeyValue("outcome", outcome);
            observation.stop();
        }
    }
}
