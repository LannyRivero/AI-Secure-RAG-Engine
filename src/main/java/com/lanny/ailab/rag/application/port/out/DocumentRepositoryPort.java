package com.lanny.ailab.rag.application.port.out;

import com.lanny.ailab.rag.domain.valueobject.TenantId;

/**
 * Outbound port for document-level operations in the vector store.
 *
 * <p>Separated from {@link VectorStorePort} intentionally:
 * <ul>
 *   <li>{@link VectorStorePort} handles chunk-level writes (store a single chunk)</li>
 *   <li>{@link DocumentRepositoryPort} handles document-level operations (delete all chunks
 *       of a document, check existence)</li>
 * </ul>
 * This separation keeps each port with a single responsibility.
 */
public interface DocumentRepositoryPort {

    /**
     * Deletes all chunks associated with the given documentId within a tenant.
     * Used as the first step of upsert: delete existing → store new.
     *
     * @param tenantId   owning tenant — must be a validated {@link TenantId}
     * @param documentId document whose chunks will be deleted
     */
    void deleteByTenantAndDocument(TenantId tenantId, String documentId);

    /**
     * Returns {@code true} if at least one chunk exists for the given documentId
     * within the specified tenant.
     *
     * @param tenantId   owning tenant — must be a validated {@link TenantId}
     * @param documentId document to check
     * @return {@code true} if the document has indexed chunks, {@code false} otherwise
     */
    boolean existsByTenantAndDocument(TenantId tenantId, String documentId);
}
