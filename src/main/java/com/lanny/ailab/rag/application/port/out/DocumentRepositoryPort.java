package com.lanny.ailab.rag.application.port.out;

/**
 * Outbound port for document-level operations in the vector store.
 *
 * Separated from VectorStorePort intentionally:
 * - VectorStorePort handles chunk-level writes (store single chunk)
 * - DocumentRepositoryPort handles document-level operations (delete all chunks
 * of a document)
 *
 * This separation keeps each port with a single responsibility.
 */
public interface DocumentRepositoryPort {

    /**
     * Deletes all chunks associated with the given documentId within a tenant.
     * Used as the first step of upsert: delete existing → store new.
     *
     * @param tenantId   owning tenant
     * @param documentId document whose chunks will be deleted
     */
    void deleteByTenantAndDocument(String tenantId, String documentId);

    /**
     * Returns true if at least one chunk exists for the given documentId within a
     * tenant.
     */
    boolean existsByTenantAndDocument(String tenantId, String documentId);
}
