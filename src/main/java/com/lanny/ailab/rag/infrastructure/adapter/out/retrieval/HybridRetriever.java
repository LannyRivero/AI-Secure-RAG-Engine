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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hybrid retriever combining vector search (semantic) and full-text search
 * (keyword).
 *
 * Uses Reciprocal Rank Fusion (RRF) to merge both result sets into a single
 * ranking.
 *
 * RRF formula: score(d) = Σ 1 / (k + rank(d))
 * where k=60 is a constant that reduces the impact of high rankings.
 * A document appearing in both result sets scores higher than one appearing in
 * only one.
 *
 * Trade-offs:
 * - Better recall than pure vector search for exact keyword queries
 * - Better semantic understanding than pure full-text search
 * - Slightly higher latency (two queries instead of one)
 * - Requires content_tsv column (V4 migration)
 */
public class HybridRetriever implements RetrievalPort {

    private static final Logger log = LoggerFactory.getLogger(HybridRetriever.class);
    private static final int RRF_K = 60;

    private final EmbeddingPort embeddingPort;
    private final JdbcTemplate jdbcTemplate;
    private final OperationMetrics operationMetrics;
    private final ObservationRegistry observationRegistry;

    public HybridRetriever(
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
        Observation observation = Observation.start("rag.retrieval.hybrid", observationRegistry)
                .lowCardinalityKeyValue("retriever", "hybrid")
                .lowCardinalityKeyValue("phase", "full")
                .highCardinalityKeyValue("tenant.id", tenantId.value());

        try (Observation.Scope scope = observation.openScope()) {
            float[] queryEmbedding = embeddingPort.embed(query);
            String pgVector = PgVectorUtils.toPgVector(queryEmbedding);
            String tenant = tenantId.value();
            MetadataSqlFilterBuilder.SqlFilterClause filterClause = MetadataSqlFilterBuilder.build(filters);

            List<RankedChunk> vectorResults = vectorSearch(pgVector, tenant, topK * 2, filterClause);
            List<RankedChunk> textResults = fullTextSearch(query, tenant, topK * 2, filterClause);

            log.debug("HYBRID_SEARCH tenantId={} vectorResults={} textResults={}",
                    tenant, vectorResults.size(), textResults.size());

            List<DocumentChunk> fused = reciprocalRankFusion(vectorResults, textResults, tenantId, topK);

            log.debug("HYBRID_SEARCH_COMPLETE tenantId={} fusedResults={}", tenant, fused.size());
            operationMetrics.recordRetrieval("hybrid", "full", outcome, fused.size(),
                    Duration.between(started, Instant.now()));
            return fused;
        } catch (RuntimeException ex) {
            outcome = "error";
            observation.error(ex);
            operationMetrics.recordRetrieval("hybrid", "full", outcome, 0, Duration.between(started, Instant.now()));
            throw ex;
        } finally {
            observation.lowCardinalityKeyValue("outcome", outcome);
            observation.stop();
        }
    }

    private List<RankedChunk> vectorSearch(String pgVector, String tenantId, int limit,
            MetadataSqlFilterBuilder.SqlFilterClause filterClause) {
        java.util.ArrayList<Object> args = new java.util.ArrayList<>();
        args.add(pgVector);
        args.add(tenantId);
        args.addAll(filterClause.args());
        args.add(pgVector);
        args.add(limit);

        return jdbcTemplate.query(("""
                SELECT document_id, content, metadata_document_type, metadata_document_date, metadata_source,
                       metadata_tags, metadata_owner, metadata_classification,
                       1 - (embedding <=> ?::vector) AS score
                FROM document_chunks
                WHERE tenant_id = ?
                %s
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """).formatted(filterClause.whereClause()),
                (rs, rowNum) -> new RankedChunk(
                        rs.getString("document_id"),
                        rs.getString("content"),
                        rs.getDouble("score"),
                        mapMetadata(rs.getString("metadata_document_type"),
                                rs.getObject("metadata_document_date", LocalDate.class),
                                rs.getString("metadata_source"),
                                extractTags(rs),
                                rs.getString("metadata_owner"),
                                rs.getString("metadata_classification"))),
                args.toArray());
    }

    private List<RankedChunk> fullTextSearch(String query, String tenantId, int limit,
            MetadataSqlFilterBuilder.SqlFilterClause filterClause) {
        // plainto_tsquery handles stopwords and stemming automatically
        java.util.ArrayList<Object> args = new java.util.ArrayList<>();
        args.add(query);
        args.add(tenantId);
        args.addAll(filterClause.args());
        args.add(query);
        args.add(limit);

        return jdbcTemplate.query(("""
                SELECT document_id, content, metadata_document_type, metadata_document_date, metadata_source,
                       metadata_tags, metadata_owner, metadata_classification,
                       ts_rank(content_tsv, plainto_tsquery('spanish', ?)) AS score
                FROM document_chunks
                WHERE tenant_id = ?
                  %s
                  AND content_tsv @@ plainto_tsquery('spanish', ?)
                ORDER BY score DESC
                LIMIT ?
                """).formatted(filterClause.whereClause()),
                (rs, rowNum) -> new RankedChunk(
                        rs.getString("document_id"),
                        rs.getString("content"),
                        rs.getDouble("score"),
                        mapMetadata(rs.getString("metadata_document_type"),
                                rs.getObject("metadata_document_date", LocalDate.class),
                                rs.getString("metadata_source"),
                                extractTags(rs),
                                rs.getString("metadata_owner"),
                                rs.getString("metadata_classification"))),
                args.toArray());
    }

    private List<DocumentChunk> reciprocalRankFusion(
            List<RankedChunk> vectorResults,
            List<RankedChunk> textResults,
            TenantId tenantId,
            int topK) {

        Map<String, Double> rrfScores = new HashMap<>();
        Map<String, RankedChunk> chunkByKey = new HashMap<>();

        // RRF score from vector search
        for (int i = 0; i < vectorResults.size(); i++) {
            RankedChunk chunk = vectorResults.get(i);
            String key = chunk.documentId() + ":" + chunk.content();
            rrfScores.merge(key, 1.0 / (RRF_K + i + 1), Double::sum);
            chunkByKey.put(key, chunk);
        }

        // RRF score from full-text search
        for (int i = 0; i < textResults.size(); i++) {
            RankedChunk chunk = textResults.get(i);
            String key = chunk.documentId() + ":" + chunk.content();
            rrfScores.merge(key, 1.0 / (RRF_K + i + 1), Double::sum);
            chunkByKey.put(key, chunk);
        }

        // Sort by RRF score descending, take topK
        return rrfScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(entry -> {
                    RankedChunk chunk = chunkByKey.get(entry.getKey());
                    return new DocumentChunk(
                            chunk.documentId(),
                            tenantId,
                            chunk.content(),
                            SimilarityScore.of(entry.getValue()),
                            chunk.metadata());
                })
                .collect(java.util.stream.Collectors.toList());
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

    private record RankedChunk(String documentId, String content, double score, DocumentMetadata metadata) {
    }
}
