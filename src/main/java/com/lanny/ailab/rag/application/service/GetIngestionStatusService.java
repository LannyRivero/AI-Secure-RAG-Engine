package com.lanny.ailab.rag.application.service;

import com.lanny.ailab.rag.application.port.in.GetIngestionStatusUseCase;
import com.lanny.ailab.rag.application.port.out.IngestionJobRepositoryPort;
import com.lanny.ailab.rag.application.result.IngestionStatusResult;
import com.lanny.ailab.rag.domain.valueobject.TenantId;

import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Application service for retrieving durable ingestion status snapshots.
 */
@Service
public class GetIngestionStatusService implements GetIngestionStatusUseCase {

    private final IngestionJobRepositoryPort ingestionJobRepositoryPort;

    public GetIngestionStatusService(IngestionJobRepositoryPort ingestionJobRepositoryPort) {
        this.ingestionJobRepositoryPort = ingestionJobRepositoryPort;
    }

    /**
     * Returns the latest known status for a document ingestion request.
     *
     * @param tenantId   owning tenant
     * @param documentId document identifier
     * @return status snapshot when present
     */
    @Override
    public Optional<IngestionStatusResult> findStatus(TenantId tenantId, String documentId) {
        return ingestionJobRepositoryPort.findByTenantAndDocument(tenantId, documentId)
                .map(job -> new IngestionStatusResult(
                        job.documentId(),
                        job.status(),
                        job.chunksIndexed(),
                        job.errorMessage(),
                        job.retryCount(),
                        job.maxAttempts(),
                        job.requestedAt(),
                        job.startedAt(),
                        job.completedAt(),
                        job.updatedAt(),
                        job.nextAttemptAt(),
                        job.lastErrorAt(),
                        job.deadLetteredAt()));
    }
}
