package com.lanny.ailab.rag.infrastructure.adapter.out.retrieval;

import com.lanny.ailab.rag.application.model.RetrievalFilter;
import com.lanny.ailab.rag.application.port.out.EmbeddingPort;
import com.lanny.ailab.rag.application.port.out.RetrievalPort;
import com.lanny.ailab.rag.domain.model.DocumentMetadata;
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
import java.time.LocalDate;
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
        this.jdbcTemplate = jdbcTemplate;
        this.operationMetrics = operationMetrics;
        this.observationRegistry = observationRegistry;
    }

    @Override
    public List<DocumentChunk> retrieve(String query, TenantId tenantId, int topK, RetrievalFilter filters) {
        Instant started = Instant.now();
        String outcome = "success";
        Observation observation = Observation.start("rag.retrieval.vector", observationRegistry)
                .lowCardinalityKeyValue("retriever", "vector")
                .lowCardinalityKeyValue("phase", "full")
                .highCardinalityKeyValue("tenant.id", tenantId.value());

        try (Observation.Scope scope = observation.openScope()) {
            float[] queryEmbedding = embeddingPort.embed(query);
            String pgVector = PgVectorUtils.toPgVector(queryEmbedding);
            MetadataSqlFilterBuilder.SqlFilterClause filterClause = MetadataSqlFilterBuilder.build(filters);

            Object[] args = buildArgs(pgVector, tenantId, topK, filterClause);

            List<DocumentChunk> results = jdbcTemplate.query(("""
                    SELECT document_id, content, metadata_document_type, metadata_document_date, metadata_source,
                           metadata_tags, metadata_owner, metadata_classification,
                           1 - (embedding <=> ?::vector) AS score
                    FROM document_chunks
                    WHERE tenant_id = ?
                    %s
                    ORDER BY embedding <=> ?::vector
                    LIMIT ?
                    """).formatted(filterClause.whereClause()),
                    (rs, rowNum) -> new DocumentChunk(
                            rs.getString("document_id"),
                            tenantId,
                            rs.getString("content"),
                            SimilarityScore.of(rs.getDouble("score")),
                            mapMetadata(rs.getString("metadata_document_type"),
                                    rs.getObject("metadata_document_date", LocalDate.class),
                                    rs.getString("metadata_source"),
                                    extractTags(rs),
                                    rs.getString("metadata_owner"),
                                    rs.getString("metadata_classification"))),
                    args);

            operationMetrics.recordRetrieval("vector", "full", outcome, results.size(),
                    Duration.between(started, Instant.now()));
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

    private Object[] buildArgs(String pgVector, TenantId tenantId, int topK,
            MetadataSqlFilterBuilder.SqlFilterClause filterClause) {
        java.util.ArrayList<Object> args = new java.util.ArrayList<>();
        args.add(pgVector);
        args.add(tenantId.value());
        args.addAll(filterClause.args());
        args.add(pgVector);
        args.add(topK);
        return args.toArray();
    }

    private DocumentMetadata mapMetadata(String documentType, LocalDate documentDate, String source,
            String[] tags, String owner, String classification) {
        return new DocumentMetadata(documentType, documentDate, source,
                tags == null ? List.of() : List.of(tags), owner, classification);
    }

    private String[] extractTags(java.sql.ResultSet rs) throws java.sql.SQLException {
        java.sql.Array tags = rs.getArray("metadata_tags");
        return tags != null ? (String[]) tags.getArray() : null;
    }
}
