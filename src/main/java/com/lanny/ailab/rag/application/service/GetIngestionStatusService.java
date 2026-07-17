package com.lanny.ailab.rag.application.service;

import com.lanny.ailab.rag.application.port.in.GetIngestionStatusUseCase;
import com.lanny.ailab.rag.application.port.out.IngestionJobRepositoryPort;
import com.lanny.ailab.rag.application.result.IngestionStatusResult;
import com.lanny.ailab.rag.domain.valueobject.TenantId;
import com.lanny.ailab.shared.infrastructure.observability.OperationMetrics;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

import org.slf4j.MDC;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Application service for retrieving durable ingestion status snapshots.
 */
@Service
public class GetIngestionStatusService implements GetIngestionStatusUseCase {

    private final IngestionJobRepositoryPort ingestionJobRepositoryPort;
    private final OperationMetrics operationMetrics;
    private final ObservationRegistry observationRegistry;

    public GetIngestionStatusService(
            IngestionJobRepositoryPort ingestionJobRepositoryPort,
            OperationMetrics operationMetrics,
            ObservationRegistry observationRegistry) {
        this.ingestionJobRepositoryPort = ingestionJobRepositoryPort;
        this.operationMetrics = operationMetrics;
        this.observationRegistry = observationRegistry;
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
        Instant started = Instant.now();
        String outcome = "found";
        Observation observation = Observation.start("rag.ingest.status", observationRegistry)
                .lowCardinalityKeyValue("operation", "ingest_status")
                .highCardinalityKeyValue("tenant.id", tenantId.value())
                .highCardinalityKeyValue("document.id", documentId);

        try (Observation.Scope scope = observation.openScope()) {
            MDC.put("operation", "ingest_status");
            MDC.put("documentId", documentId);

            Optional<IngestionStatusResult> result = ingestionJobRepositoryPort
                    .findByTenantAndDocument(tenantId, documentId)
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

            if (result.isEmpty()) {
                outcome = "not_found";
            }
            return result;
        } catch (RuntimeException ex) {
            outcome = "error";
            observation.error(ex);
            throw ex;
        } finally {
            observation.lowCardinalityKeyValue("outcome", outcome);
            observation.stop();
            operationMetrics.recordOperation("ingest_status", outcome, Duration.between(started, Instant.now()));
            MDC.remove("operation");
            MDC.remove("documentId");
        }
    }
}
