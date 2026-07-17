package com.lanny.ailab.rag.application.service;

import com.lanny.ailab.rag.application.command.DeleteDocumentCommand;
import com.lanny.ailab.rag.application.port.in.DeleteDocumentUseCase;
import com.lanny.ailab.rag.application.port.out.DocumentRepositoryPort;
import com.lanny.ailab.rag.application.result.DeleteDocumentResult;
import com.lanny.ailab.shared.infrastructure.observability.OperationMetrics;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Application service that implements the document deletion use case.
 *
 * <p>
 * The operation is idempotent: if the document does not exist, the service
 * returns a not-found result without throwing an exception. Deletion removes
 * all
 * indexed chunks and any persisted ingestion job associated with the given
 * document ID within the tenant.
 */
@Service
public class DeleteDocumentService implements DeleteDocumentUseCase {

    private static final Logger log = LoggerFactory.getLogger(DeleteDocumentService.class);

    private final DocumentRepositoryPort documentRepositoryPort;
    private final OperationMetrics operationMetrics;
    private final ObservationRegistry observationRegistry;

    public DeleteDocumentService(
            DocumentRepositoryPort documentRepositoryPort,
            OperationMetrics operationMetrics,
            ObservationRegistry observationRegistry) {
        this.documentRepositoryPort = documentRepositoryPort;
        this.operationMetrics = operationMetrics;
        this.observationRegistry = observationRegistry;
    }

    @Override
    public DeleteDocumentResult execute(DeleteDocumentCommand command) {
        var tenantId = command.tenantId();
        String documentId = command.documentId();
        Instant started = Instant.now();
        String outcome = "deleted";
        Observation observation = Observation.start("rag.document.delete", observationRegistry)
                .lowCardinalityKeyValue("operation", "delete")
                .highCardinalityKeyValue("tenant.id", tenantId.value())
                .highCardinalityKeyValue("document.id", documentId);

        try (Observation.Scope scope = observation.openScope()) {
            MDC.put("operation", "delete");
            MDC.put("documentId", documentId);

            log.info("DELETE_DOCUMENT_START tenantId={} documentId={}", tenantId.value(), documentId);

            boolean chunkExisted = documentRepositoryPort.existsByTenantAndDocument(tenantId, documentId);
            boolean ingestionJobExisted = documentRepositoryPort.existsIngestionJobByTenantAndDocument(tenantId,
                    documentId);
            boolean existed = chunkExisted || ingestionJobExisted;

            if (!existed) {
                outcome = "not_found";
                log.warn("DELETE_DOCUMENT_NOT_FOUND tenantId={} documentId={}", tenantId.value(), documentId);
                return DeleteDocumentResult.notFound(documentId);
            }

            documentRepositoryPort.deleteByTenantAndDocument(tenantId, documentId);
            documentRepositoryPort.deleteIngestionJobByTenantAndDocument(tenantId, documentId);

            log.info("DELETE_DOCUMENT_COMPLETE tenantId={} documentId={}", tenantId.value(), documentId);

            return DeleteDocumentResult.success(documentId);
        } catch (RuntimeException ex) {
            outcome = "error";
            observation.error(ex);
            throw ex;
        } finally {
            observation.lowCardinalityKeyValue("outcome", outcome);
            observation.stop();
            operationMetrics.recordOperation("delete", outcome, Duration.between(started, Instant.now()));
            MDC.remove("operation");
            MDC.remove("documentId");
        }
    }
}
