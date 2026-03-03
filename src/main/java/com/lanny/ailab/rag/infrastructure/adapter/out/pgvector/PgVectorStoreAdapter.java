package com.lanny.ailab.rag.infrastructure.adapter.out.pgvector;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.lanny.ailab.rag.application.port.out.VectorStorePort;

@Component
public class PgVectorStoreAdapter implements VectorStorePort {

    private final JdbcTemplate jdbcTemplate;

    public PgVectorStoreAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void store(String tenantId, String documentId, String content, float[] embedding) {

        UUID id = UUID.randomUUID();
        String pgVector = toPgVector(embedding);

        jdbcTemplate.update("""
                INSERT INTO document_chunks (id, tenant_id, document_id, content, embedding)
                VALUES (?, ?, ?, ?, ?::vector)
                """,
                id,
                tenantId,
                documentId,
                content,
                pgVector);
    }

    private String toPgVector(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            sb.append(embedding[i]);
            if (i < embedding.length - 1) {
                sb.append(',');
            }
        }
        sb.append(']');
        return sb.toString();

    }

}
