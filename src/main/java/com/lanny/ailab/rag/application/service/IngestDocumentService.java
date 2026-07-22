package com.lanny.ailab.rag.application.service;

import com.lanny.ailab.rag.application.command.IngestDocumentCommand;
import com.lanny.ailab.rag.application.command.EnqueueIngestionJobCommand;
import com.lanny.ailab.rag.application.metrics.IngestionMetrics;
import com.lanny.ailab.rag.application.port.in.IngestDocumentUseCase;
import com.lanny.ailab.rag.application.port.out.IngestionJobRepositoryPort;
import com.lanny.ailab.rag.application.port.out.IngestionSourceResolverPort;
import com.lanny.ailab.rag.application.result.IngestDocumentResult;
import com.lanny.ailab.shared.infrastructure.observability.OperationMetrics;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

import org.slf4j.MDC;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

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
    private final IngestionSourceResolverPort ingestionSourceResolverPort;
    private final IngestionMetrics ingestionMetrics;
    private final OperationMetrics operationMetrics;
    private final ObservationRegistry observationRegistry;

    public IngestDocumentService(
            IngestionJobRepositoryPort ingestionJobRepositoryPort,
            IngestionSourceResolverPort ingestionSourceResolverPort,
            IngestionMetrics ingestionMetrics,
            OperationMetrics operationMetrics,
            ObservationRegistry observationRegistry) {
        this.ingestionJobRepositoryPort = ingestionJobRepositoryPort;
        this.ingestionSourceResolverPort = ingestionSourceResolverPort;
        this.ingestionMetrics = ingestionMetrics;
        this.operationMetrics = operationMetrics;
        this.observationRegistry = observationRegistry;
    }

    /**
     * Persists the latest ingestion request for a document.
     *
     * @param command accepted ingest parameters
     * @return accepted request state for the document
     */
    @Override
    public IngestDocumentResult execute(IngestDocumentCommand command) {
        Instant started = Instant.now();
        String outcome = "accepted";
        Observation observation = Observation.start("rag.ingest.accept", observationRegistry)
                .lowCardinalityKeyValue("operation", "ingest")
                .highCardinalityKeyValue("tenant.id", command.tenantId().value())
                .highCardinalityKeyValue("document.id", command.documentId());

        try (Observation.Scope scope = observation.openScope()) {
            MDC.put("operation", "ingest");
            MDC.put("documentId", command.documentId());

            var resolvedSource = ingestionSourceResolverPort.resolve(command);
            observation.lowCardinalityKeyValue("source.type", resolvedSource.sourceType().name());

            var job = ingestionJobRepositoryPort.enqueue(new EnqueueIngestionJobCommand(
                    command.documentId(),
                    command.tenantId(),
                    resolvedSource.content(),
                    resolvedSource.sourceType(),
                    resolvedSource.sourceUri()));
            ingestionMetrics.incrementAccepted();
            return new IngestDocumentResult(job.documentId(), job.status());
        } catch (RuntimeException ex) {
            outcome = "error";
            observation.error(ex);
            throw ex;
        } finally {
            observation.lowCardinalityKeyValue("outcome", outcome);
            observation.stop();
            operationMetrics.recordOperation("ingest", outcome, Duration.between(started, Instant.now()));
            MDC.remove("operation");
            MDC.remove("documentId");
        }
    }
}
