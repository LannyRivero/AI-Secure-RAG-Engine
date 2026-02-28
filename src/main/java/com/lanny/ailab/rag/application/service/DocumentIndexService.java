package com.lanny.ailab.rag.application.service;

import com.lanny.ailab.rag.application.port.out.EmbeddingPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class DocumentIndexService {

    private final EmbeddingPort embeddingPort;
    private final JdbcTemplate jdbcTemplate;

    public DocumentIndexService(EmbeddingPort embeddingPort,
            JdbcTemplate jdbcTemplate) {
        this.embeddingPort = embeddingPort;
        this.jdbcTemplate = jdbcTemplate;
    }

    public void index(String tenantId, String documentId, String content) {

        float[] embedding = embeddingPort.embed(content);

        UUID id = UUID.randomUUID();

        jdbcTemplate.update("""
                INSERT INTO document_chunks (id, tenant_id, document_id, content, embedding)
                VALUES (?, ?, ?, ?, ?)
                """,
                id,
                tenantId,
                documentId,
                content,
                embedding);
    }
}