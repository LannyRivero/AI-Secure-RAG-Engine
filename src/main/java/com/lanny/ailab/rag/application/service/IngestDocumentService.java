package com.lanny.ailab.rag.application.service;

import com.lanny.ailab.rag.application.command.IngestDocumentCommand;
import com.lanny.ailab.rag.application.metrics.IngestionMetrics;
import com.lanny.ailab.rag.application.port.in.IngestDocumentUseCase;
import com.lanny.ailab.rag.application.port.out.IngestionJobRepositoryPort;
import com.lanny.ailab.rag.application.result.IngestDocumentResult;

import org.springframework.stereotype.Service;

/**
 * Application service that durably accepts document ingestion requests.
 *
 * <p>
 * The service does not call embedding providers or write chunks during the
 * request. Instead it upserts a persisted ingestion job that a background
 * worker
 * will process later. This keeps HTTP latency short and isolates provider
 * slowness
 * from the request/transaction path.
 */
@Service
public class IngestDocumentService implements IngestDocumentUseCase {

    private final IngestionJobRepositoryPort ingestionJobRepositoryPort;
    private final IngestionMetrics ingestionMetrics;

    public IngestDocumentService(
            IngestionJobRepositoryPort ingestionJobRepositoryPort,
            IngestionMetrics ingestionMetrics) {
        this.ingestionJobRepositoryPort = ingestionJobRepositoryPort;
        this.ingestionMetrics = ingestionMetrics;
    }

    /**
     * Persists the latest ingestion request for a document.
     *
     * @param command accepted ingest parameters
     * @return accepted request state for the document
     */
    @Override
    public IngestDocumentResult execute(IngestDocumentCommand command) {
        var job = ingestionJobRepositoryPort.enqueue(command);
        ingestionMetrics.incrementAccepted();
        return new IngestDocumentResult(job.documentId(), job.status());
    }
}
