package com.lanny.ailab.rag.application.service;

import com.lanny.ailab.rag.application.metrics.IngestionMetrics;
import com.lanny.ailab.rag.application.model.IngestionJob;
import com.lanny.ailab.rag.application.port.out.DocumentRepositoryPort;
import com.lanny.ailab.rag.application.port.out.EmbeddingPort;
import com.lanny.ailab.rag.application.port.out.IngestionJobRepositoryPort;
import com.lanny.ailab.rag.application.port.out.VectorStorePort;
import com.lanny.ailab.rag.domain.model.IngestionStatus;
import com.lanny.ailab.rag.domain.service.ChunkingService;
import com.lanny.ailab.shared.infrastructure.observability.OperationMetrics;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Executes one claimed ingestion job from chunking to final state transition.
 */
@Component
class IngestionJobProcessor {

    private static final Logger log = LoggerFactory.getLogger(IngestionJobProcessor.class);

    private final ChunkingService chunkingService;
    private final EmbeddingPort embeddingPort;
    private final VectorStorePort vectorStorePort;
    private final DocumentRepositoryPort documentRepositoryPort;
    private final IngestionJobRepositoryPort ingestionJobRepositoryPort;
    private final TransactionOperations transactionOperations;
    private final IngestionMetrics ingestionMetrics;
    private final OperationMetrics operationMetrics;
    private final ObservationRegistry observationRegistry;
    private final ScheduledExecutorService ingestionLeaseHeartbeatExecutor;
    private final long initialBackoffSeconds;
    private final long leaseHeartbeatIntervalMillis;

    IngestionJobProcessor(
            ChunkingService chunkingService,
            EmbeddingPort embeddingPort,
            VectorStorePort vectorStorePort,
            DocumentRepositoryPort documentRepositoryPort,
            IngestionJobRepositoryPort ingestionJobRepositoryPort,
            TransactionOperations transactionOperations,
            IngestionMetrics ingestionMetrics,
            OperationMetrics operationMetrics,
            ObservationRegistry observationRegistry,
            ScheduledExecutorService ingestionLeaseHeartbeatExecutor,
            @org.springframework.beans.factory.annotation.Value("${app.rag.ingestion.retry.initial-backoff-seconds:5}") long initialBackoffSeconds,
            @org.springframework.beans.factory.annotation.Value("${app.rag.ingestion.processing-timeout-seconds:300}") long processingTimeoutSeconds) {
        this.chunkingService = chunkingService;
        this.embeddingPort = embeddingPort;
        this.vectorStorePort = vectorStorePort;
        this.documentRepositoryPort = documentRepositoryPort;
        this.ingestionJobRepositoryPort = ingestionJobRepositoryPort;
        this.transactionOperations = transactionOperations;
        this.ingestionMetrics = ingestionMetrics;
        this.operationMetrics = operationMetrics;
        this.observationRegistry = observationRegistry;
        this.ingestionLeaseHeartbeatExecutor = ingestionLeaseHeartbeatExecutor;
        this.initialBackoffSeconds = initialBackoffSeconds;
        this.leaseHeartbeatIntervalMillis = Math.max(250L, TimeUnit.SECONDS.toMillis(processingTimeoutSeconds) / 3L);
    }

    void process(IngestionJob job) {
        Instant started = Instant.now();
        String outcome = "completed";
        AtomicBoolean leaseLost = new AtomicBoolean(false);
        ScheduledFuture<?> heartbeat = startLeaseHeartbeat(job, leaseLost);
        Observation observation = Observation.start("rag.ingestion.process", observationRegistry)
                .lowCardinalityKeyValue("operation", "ingestion_worker")
                .highCardinalityKeyValue("tenant.id", job.tenantId().value())
                .highCardinalityKeyValue("document.id", job.documentId())
                .highCardinalityKeyValue("request.version", String.valueOf(job.requestVersion()));

        try (Observation.Scope scope = observation.openScope()) {
            MDC.put("operation", "ingestion_worker");
            MDC.put("documentId", job.documentId());

            log.info("INGEST_ASYNC_START tenantId={} documentId={} version={}",
                    job.tenantId().value(), job.documentId(), job.requestVersion());

            abortIfLeaseLost(job, leaseLost);
            List<String> chunks = chunkingService.chunk(job.content());
            List<ChunkEmbedding> preparedChunks = new ArrayList<>(chunks.size());
            for (String chunkContent : chunks) {
                abortIfLeaseLost(job, leaseLost);
                preparedChunks.add(new ChunkEmbedding(chunkContent, embeddingPort.embed(chunkContent)));
            }

            abortIfLeaseLost(job, leaseLost);
            boolean written = transactionOperations.execute(status -> replaceDocumentChunks(job, preparedChunks));
            if (!Boolean.TRUE.equals(written)) {
                outcome = "skipped_stale";
                log.info("INGEST_ASYNC_SKIPPED_STALE tenantId={} documentId={} version={}",
                        job.tenantId().value(), job.documentId(), job.requestVersion());
                return;
            }

            ingestionMetrics.incrementCompleted();
            ingestionMetrics.processingLatency().record(Duration.between(started, Instant.now()));
            log.info("INGEST_ASYNC_COMPLETE tenantId={} documentId={} version={} chunksIndexed={}",
                    job.tenantId().value(), job.documentId(), job.requestVersion(), preparedChunks.size());
        } catch (RuntimeException ex) {
            outcome = "failed";
            ingestionMetrics.incrementFailed();
            var failedJob = ingestionJobRepositoryPort.markFailed(
                    job.tenantId(),
                    job.documentId(),
                    job.requestVersion(),
                    job.processingLeaseVersion(),
                    truncate(ex.getMessage()),
                    backoffSeconds(job.retryCount()));

            failedJob.ifPresent(updatedJob -> {
                if (updatedJob.status() == IngestionStatus.PENDING) {
                    ingestionMetrics.incrementRetriesScheduled();
                } else if (updatedJob.status() == IngestionStatus.DEAD_LETTER) {
                    ingestionMetrics.incrementDeadLettered();
                }
            });

            log.error("INGEST_ASYNC_FAILED tenantId={} documentId={} version={} message={}",
                    job.tenantId().value(), job.documentId(), job.requestVersion(), ex.getMessage(), ex);
            observation.error(ex);
        } finally {
            heartbeat.cancel(true);
            observation.lowCardinalityKeyValue("outcome", outcome);
            observation.stop();
            operationMetrics.recordOperation("ingestion_worker", outcome, Duration.between(started, Instant.now()));
            MDC.remove("operation");
            MDC.remove("documentId");
        }
    }

    private boolean replaceDocumentChunks(IngestionJob job, List<ChunkEmbedding> preparedChunks) {
        var lockedJob = ingestionJobRepositoryPort
                .findByTenantAndDocumentForUpdate(job.tenantId(), job.documentId())
                .orElse(null);

        if (lockedJob == null
                || lockedJob.status() != IngestionStatus.PROCESSING
                || lockedJob.requestVersion() != job.requestVersion()
                || lockedJob.processingLeaseVersion() != job.processingLeaseVersion()) {
            return false;
        }

        documentRepositoryPort.deleteByTenantAndDocument(job.tenantId(), job.documentId());
        for (ChunkEmbedding preparedChunk : preparedChunks) {
            vectorStorePort.store(
                    job.tenantId(),
                    job.documentId(),
                    preparedChunk.content(),
                    preparedChunk.embedding(),
                    job.metadata());
        }

        return ingestionJobRepositoryPort.markCompleted(
                job.tenantId(),
                job.documentId(),
                job.requestVersion(),
                job.processingLeaseVersion(),
                preparedChunks.size());
    }

    private String truncate(String message) {
        if (message == null || message.length() <= 500) {
            return message;
        }
        return message.substring(0, 500);
    }

    private long backoffSeconds(int retryCount) {
        long multiplier = 1L << Math.min(retryCount, 10);
        return initialBackoffSeconds * multiplier;
    }

    private ScheduledFuture<?> startLeaseHeartbeat(IngestionJob job, AtomicBoolean leaseLost) {
        return ingestionLeaseHeartbeatExecutor.scheduleAtFixedRate(() -> {
            if (leaseLost.get()) {
                return;
            }

            boolean renewed = ingestionJobRepositoryPort.renewClaimLease(
                    job.tenantId(),
                    job.documentId(),
                    job.requestVersion(),
                    job.processingLeaseVersion());

            if (!renewed) {
                leaseLost.set(true);
                log.warn("INGEST_ASYNC_LEASE_LOST tenantId={} documentId={} version={} leaseVersion={}",
                        job.tenantId().value(), job.documentId(), job.requestVersion(), job.processingLeaseVersion());
            }
        }, leaseHeartbeatIntervalMillis, leaseHeartbeatIntervalMillis, TimeUnit.MILLISECONDS);
    }

    private void abortIfLeaseLost(IngestionJob job, AtomicBoolean leaseLost) {
        if (leaseLost.get()) {
            throw new IllegalStateException("Processing lease lost for document %s version %s"
                    .formatted(job.documentId(), job.requestVersion()));
        }
    }

    private record ChunkEmbedding(String content, float[] embedding) {
    }
}
