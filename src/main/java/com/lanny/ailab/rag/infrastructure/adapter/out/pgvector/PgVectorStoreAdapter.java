package com.lanny.ailab.rag.infrastructure.adapter.out.pgvector;

import com.lanny.ailab.rag.application.port.out.VectorStorePort;
import com.lanny.ailab.rag.domain.model.DocumentMetadata;
import com.lanny.ailab.rag.domain.valueobject.TenantId;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PgVectorStoreAdapter implements VectorStorePort {

    private final JdbcTemplate jdbcTemplate;

    public PgVectorStoreAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void store(TenantId tenantId, String documentId, String content, float[] embedding,
            DocumentMetadata metadata) {
        jdbcTemplate.update("""
                INSERT INTO document_chunks (
                    id, tenant_id, document_id, content, embedding, metadata_document_type, metadata_document_date,
                    metadata_source, metadata_tags, metadata_owner, metadata_classification
                )
                VALUES (?, ?, ?, ?, ?::vector, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                tenantId.value(),
                documentId,
                content,
                PgVectorUtils.toPgVector(embedding),
                metadata.documentType(),
                metadata.documentDate(),
                metadata.source(),
                toTextArray(metadata.tags()),
                metadata.owner(),
                metadata.classification());
    }

    private org.springframework.jdbc.support.SqlValue toTextArray(java.util.List<String> values) {
        return new org.springframework.jdbc.support.SqlValue() {
            @Override
            public void setValue(java.sql.PreparedStatement ps, int paramIndex) throws java.sql.SQLException {
                ps.setArray(paramIndex, ps.getConnection().createArrayOf("text", values.toArray(String[]::new)));
            }

            @Override
            public void cleanup() {
            }
        };
    }
}
