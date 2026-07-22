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
import org.springframework.dao.DataAccessException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Background worker that claims persisted ingestion jobs and processes them.
 *
 * <p>
 * Jobs are claimed from the database, embeddings are computed outside the write
 * transaction, and chunk replacement remains atomic at the document level.
 */
@Service
public class IngestionWorkerService {

    private static final Logger log = LoggerFactory.getLogger(IngestionWorkerService.class);

    private final IngestionJobRepositoryPort ingestionJobRepositoryPort;
    private final ChunkingService chunkingService;
    private final EmbeddingPort embeddingPort;
    private final VectorStorePort vectorStorePort;
    private final DocumentRepositoryPort documentRepositoryPort;
    private final TransactionOperations transactionOperations;
    private final Executor ingestionTaskExecutor;
    private final IngestionMetrics ingestionMetrics;
    private final OperationMetrics operationMetrics;
    private final ObservationRegistry observationRegistry;
    private final AtomicInteger inFlightJobs = new AtomicInteger();
    private final int maxParallelJobs;
    private final int claimBatchSize;
    private final long initialBackoffSeconds;

    public IngestionWorkerService(
            IngestionJobRepositoryPort ingestionJobRepositoryPort,
            ChunkingService chunkingService,
            EmbeddingPort embeddingPort,
            VectorStorePort vectorStorePort,
            DocumentRepositoryPort documentRepositoryPort,
            TransactionOperations transactionOperations,
            @Qualifier("ingestionTaskExecutor") Executor ingestionTaskExecutor,
            IngestionMetrics ingestionMetrics,
            OperationMetrics operationMetrics,
            ObservationRegistry observationRegistry,
            @org.springframework.beans.factory.annotation.Value("${app.rag.ingestion.worker-concurrency:2}") int maxParallelJobs,
            @org.springframework.beans.factory.annotation.Value("${app.rag.ingestion.claim-batch-size:10}") int claimBatchSize,
            @org.springframework.beans.factory.annotation.Value("${app.rag.ingestion.retry.initial-backoff-seconds:5}") long initialBackoffSeconds) {

        this.ingestionJobRepositoryPort = ingestionJobRepositoryPort;
        this.chunkingService = chunkingService;
        this.embeddingPort = embeddingPort;
        this.vectorStorePort = vectorStorePort;
        this.documentRepositoryPort = documentRepositoryPort;
        this.transactionOperations = transactionOperations;
        this.ingestionTaskExecutor = ingestionTaskExecutor;
        this.ingestionMetrics = ingestionMetrics;
        this.operationMetrics = operationMetrics;
        this.observationRegistry = observationRegistry;
        this.maxParallelJobs = maxParallelJobs;
        this.claimBatchSize = claimBatchSize;
        this.initialBackoffSeconds = initialBackoffSeconds;
    }

    /**
     * Polls and processes a small batch of pending ingestion jobs.
     */
    @Scheduled(fixedDelayString = "${app.rag.ingestion.poll-delay-ms:250}")
    public void processPendingJobs() {
        try {
            refreshQueueMetrics();
            int availableSlots = Math.max(0, maxParallelJobs - inFlightJobs.get());
            int claims = Math.min(claimBatchSize, availableSlots);

            for (int i = 0; i < claims; i++) {
                var nextJob = ingestionJobRepositoryPort.claimNextPending();
                if (nextJob.isEmpty()) {
                    return;
                }
                inFlightJobs.incrementAndGet();
                ingestionTaskExecutor.execute(() -> {
                    try {
                        process(nextJob.get());
                    } finally {
                        inFlightJobs.decrementAndGet();
                        refreshQueueMetrics();
                    }
                });
            }
        } catch (DataAccessException ex) {
            log.warn("INGEST_ASYNC_POLL_UNAVAILABLE message={}", ex.getMessage());
        }
    }

    private void process(IngestionJob job) {
        Instant started = Instant.now();
        String outcome = "completed";
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

            List<String> chunks = chunkingService.chunk(job.content());
            List<ChunkEmbedding> preparedChunks = new ArrayList<>(chunks.size());

            for (String chunkContent : chunks) {
                preparedChunks.add(new ChunkEmbedding(chunkContent, embeddingPort.embed(chunkContent)));
            }

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
                || lockedJob.requestVersion() != job.requestVersion()) {
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

    private void refreshQueueMetrics() {
        try {
            ingestionMetrics.updateQueueDepths(
                    ingestionJobRepositoryPort.countByStatus(IngestionStatus.PENDING),
                    ingestionJobRepositoryPort.countByStatus(IngestionStatus.PROCESSING),
                    ingestionJobRepositoryPort.countByStatus(IngestionStatus.FAILED),
                    ingestionJobRepositoryPort.countByStatus(IngestionStatus.DEAD_LETTER));
        } catch (DataAccessException ex) {
            log.debug("INGEST_ASYNC_METRICS_REFRESH_SKIPPED message={}", ex.getMessage());
        }
    }

    private record ChunkEmbedding(String content, float[] embedding) {
    }
}
