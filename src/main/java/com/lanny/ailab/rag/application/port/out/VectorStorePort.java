package com.lanny.ailab.rag.application.port.out;

import com.lanny.ailab.rag.domain.valueobject.TenantId;

/**
 * Outbound port for writing document chunks to the vector store.
 *
 * <p>Handles chunk-level writes. For document-level operations such as
 * deletion, see {@link DocumentRepositoryPort}.
 */
public interface VectorStorePort {

    /**
     * Persists a single chunk with its embedding vector.
     *
     * @param tenantId   owning tenant — must be a validated {@link TenantId}
     * @param documentId source document identifier
     * @param content    raw text of the chunk
     * @param embedding  dense vector representation of {@code content}
     */
    void store(TenantId tenantId, String documentId, String content, float[] embedding);

}
