package com.lanny.ailab.rag.infrastructure.adapter.out.pgvector;

import com.lanny.ailab.rag.application.port.out.DocumentRepositoryPort;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PgDocumentRepository implements DocumentRepositoryPort {

    private final JdbcTemplate jdbcTemplate;

    public PgDocumentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void deleteByTenantAndDocument(String tenantId, String documentId) {
        jdbcTemplate.update("""
                DELETE FROM document_chunks
                WHERE tenant_id = ? AND document_id = ?
                """,
                tenantId,
                documentId);
    }
}