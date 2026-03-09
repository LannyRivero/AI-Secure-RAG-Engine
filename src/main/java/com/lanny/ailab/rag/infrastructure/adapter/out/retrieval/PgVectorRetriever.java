package com.lanny.ailab.rag.infrastructure.adapter.out.retrieval;

import com.lanny.ailab.rag.application.port.out.EmbeddingPort;
import com.lanny.ailab.rag.application.port.out.RetrievalPort;
import com.lanny.ailab.rag.domain.valueobject.DocumentChunk;
import com.lanny.ailab.rag.domain.valueobject.TenantId;
import com.lanny.ailab.rag.infrastructure.adapter.out.pgvector.PgVectorUtils;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

public class PgVectorRetriever implements RetrievalPort {

    private final EmbeddingPort embeddingPort;
    private final JdbcTemplate jdbcTemplate;

    public PgVectorRetriever(EmbeddingPort embeddingPort, JdbcTemplate jdbcTemplate) {
        this.embeddingPort = embeddingPort;
        this.jdbcTemplate  = jdbcTemplate;
    }

    @Override
    public List<DocumentChunk> retrieve(String query, TenantId tenantId, int topK) {
        float[] queryEmbedding = embeddingPort.embed(query);
        String pgVector        = PgVectorUtils.toPgVector(queryEmbedding);

        return jdbcTemplate.query("""
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
                        rs.getDouble("score")),
                pgVector,
                tenantId.value(),
                pgVector,
                topK);
    }
}