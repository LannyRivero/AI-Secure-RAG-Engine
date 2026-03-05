package com.lanny.ailab.rag.domain.model;

import com.lanny.ailab.rag.domain.valueobject.DocumentId;
import com.lanny.ailab.rag.domain.valueobject.TenantId;

/**
 * Aggregate root representing a document in the RAG system.
 *
 * A Document is the unit of ingestion: it has an identity (documentId),
 * belongs to a tenant, and contains raw text content that will be
 * chunked and embedded by the ingestion pipeline.
 *
 * Note: Document does not store embeddings directly.
 * Embeddings are managed at the infrastructure level (pgvector).
 */
public class Document {

    private final DocumentId id;
    private final TenantId tenantId;
    private final String content;

    public Document(DocumentId id, TenantId tenantId, String content) {
        if (id == null)
            throw new IllegalArgumentException("DocumentId cannot be null");
        if (tenantId == null)
            throw new IllegalArgumentException("TenantId cannot be null");
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Content cannot be blank");
        }
        this.id = id;
        this.tenantId = tenantId;
        this.content = content;
    }

    public DocumentId id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public String content() {
        return content;
    }
}
