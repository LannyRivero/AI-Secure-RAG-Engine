package com.lanny.ailab.rag.application.port.in;

import com.lanny.ailab.rag.application.result.IngestionStatusResult;
import com.lanny.ailab.rag.domain.valueobject.TenantId;

import java.util.Optional;

/**
 * Input port for querying the current status of an asynchronous ingestion job.
 */
public interface GetIngestionStatusUseCase {

    /**
     * Returns the latest ingestion status for a document within the given tenant.
     *
     * @param tenantId   owning tenant
     * @param documentId document identifier
     * @return status snapshot when a job exists for the document
     */
    Optional<IngestionStatusResult> findStatus(TenantId tenantId, String documentId);
}
