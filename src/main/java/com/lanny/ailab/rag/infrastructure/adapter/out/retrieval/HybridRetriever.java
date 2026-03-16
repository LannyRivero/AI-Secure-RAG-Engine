package com.lanny.ailab.rag.infrastructure.adapter.out.retrieval;

import com.lanny.ailab.rag.application.port.out.EmbeddingPort;
import com.lanny.ailab.rag.application.port.out.RetrievalPort;
import com.lanny.ailab.rag.domain.valueobject.DocumentChunk;
import com.lanny.ailab.rag.domain.valueobject.SimilarityScore;
import com.lanny.ailab.rag.domain.valueobject.TenantId;
import com.lanny.ailab.rag.infrastructure.adapter.out.pgvector.PgVectorUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

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

    public HybridRetriever(EmbeddingPort embeddingPort, JdbcTemplate jdbcTemplate) {
        this.embeddingPort = embeddingPort;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<DocumentChunk> retrieve(String query, TenantId tenantId, int topK) {

        float[] queryEmbedding = embeddingPort.embed(query);
        String pgVector = PgVectorUtils.toPgVector(queryEmbedding);
        String tenant = tenantId.value();

        // 1. Vector search — fetch 2*topK to have enough candidates for fusion
        List<RankedChunk> vectorResults = vectorSearch(pgVector, tenant, topK * 2);

        // 2. Full-text search — fetch 2*topK
        List<RankedChunk> textResults = fullTextSearch(query, tenant, topK * 2);

        log.debug("HYBRID_SEARCH tenantId={} vectorResults={} textResults={}",
                tenant, vectorResults.size(), textResults.size());

        // 3. RRF fusion
        List<DocumentChunk> fused = reciprocalRankFusion(vectorResults, textResults, tenantId, topK);

        log.debug("HYBRID_SEARCH_COMPLETE tenantId={} fusedResults={}", tenant, fused.size());

        return fused;
    }

    private List<RankedChunk> vectorSearch(String pgVector, String tenantId, int limit) {
        return jdbcTemplate.query("""
                SELECT document_id, content,
                       1 - (embedding <=> ?::vector) AS score
                FROM document_chunks
                WHERE tenant_id = ?
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """,
                (rs, rowNum) -> new RankedChunk(
                        rs.getString("document_id"),
                        rs.getString("content"),
                        rs.getDouble("score")),
                pgVector, tenantId, pgVector, limit);
    }

    private List<RankedChunk> fullTextSearch(String query, String tenantId, int limit) {
        // plainto_tsquery handles stopwords and stemming automatically
        return jdbcTemplate.query("""
                SELECT document_id, content,
                       ts_rank(content_tsv, plainto_tsquery('spanish', ?)) AS score
                FROM document_chunks
                WHERE tenant_id = ?
                  AND content_tsv @@ plainto_tsquery('spanish', ?)
                ORDER BY score DESC
                LIMIT ?
                """,
                (rs, rowNum) -> new RankedChunk(
                        rs.getString("document_id"),
                        rs.getString("content"),
                        rs.getDouble("score")),
                query, tenantId, query, limit);
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
                            SimilarityScore.of(entry.getValue()));
                })
                .collect(java.util.stream.Collectors.toList());
    }

    private record RankedChunk(String documentId, String content, double score) {
    }
}
