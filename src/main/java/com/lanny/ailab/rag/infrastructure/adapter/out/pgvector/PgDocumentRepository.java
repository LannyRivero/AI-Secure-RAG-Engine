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

    @Override
    public boolean existsByTenantAndDocument(String tenantId, String documentId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1) FROM document_chunks
                WHERE tenant_id = ? AND document_id = ?
                LIMIT 1
                """,
                Integer.class,
                tenantId,
                documentId);
        return count != null && count > 0;
    }
}