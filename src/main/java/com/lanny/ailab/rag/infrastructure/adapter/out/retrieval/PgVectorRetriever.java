package com.lanny.ailab.rag.infrastructure.adapter.out.retrieval;

import com.lanny.ailab.rag.application.port.out.EmbeddingPort;
import com.lanny.ailab.rag.application.port.out.RetrievalPort;
import com.lanny.ailab.rag.domain.valueobject.DocumentChunk;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PgVectorRetriever implements RetrievalPort {

    private final EmbeddingPort embeddingPort;
    private final JdbcTemplate jdbcTemplate;

    public PgVectorRetriever(EmbeddingPort embeddingPort,
            JdbcTemplate jdbcTemplate) {
        this.embeddingPort = embeddingPort;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<DocumentChunk> retrieve(String query, String tenantId, int topK) {

        float[] queryEmbedding = embeddingPort.embed(query);

        return jdbcTemplate.query("""
                SELECT id, document_id, content,
                       1 - (embedding <=> ?) AS score
                FROM document_chunks
                WHERE tenant_id = ?
                ORDER BY embedding <=> ?
                LIMIT ?
                """,
                (rs, rowNum) -> new DocumentChunk(
                        rs.getString("document_id"),
                        tenantId,
                        rs.getString("content"),
                        rs.getDouble("score")),
                queryEmbedding,
                tenantId,
                queryEmbedding,
                topK);
    }
}
