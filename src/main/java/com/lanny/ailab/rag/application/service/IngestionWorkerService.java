package com.lanny.ailab.rag.application.service;

import com.lanny.ailab.rag.application.metrics.IngestionMetrics;
import com.lanny.ailab.rag.application.port.out.IngestionJobRepositoryPort;
import com.lanny.ailab.rag.domain.model.IngestionStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;

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
@ConditionalOnProperty(name = "app.rag.ingestion.worker.enabled", havingValue = "true", matchIfMissing = true)
public class IngestionWorkerService {

    private static final Logger log = LoggerFactory.getLogger(IngestionWorkerService.class);

    private final IngestionJobRepositoryPort ingestionJobRepositoryPort;
    private final IngestionJobProcessor ingestionJobProcessor;
    private final Executor ingestionTaskExecutor;
    private final IngestionMetrics ingestionMetrics;
    private final AtomicInteger inFlightJobs = new AtomicInteger();
    private volatile boolean shuttingDown;
    private final int maxParallelJobs;
    private final int claimBatchSize;

    public IngestionWorkerService(
            IngestionJobRepositoryPort ingestionJobRepositoryPort,
            IngestionJobProcessor ingestionJobProcessor,
            @Qualifier("ingestionTaskExecutor") Executor ingestionTaskExecutor,
            IngestionMetrics ingestionMetrics,
            @org.springframework.beans.factory.annotation.Value("${app.rag.ingestion.worker-concurrency:2}") int maxParallelJobs,
            @org.springframework.beans.factory.annotation.Value("${app.rag.ingestion.claim-batch-size:10}") int claimBatchSize) {

        this.ingestionJobRepositoryPort = ingestionJobRepositoryPort;
        this.ingestionJobProcessor = ingestionJobProcessor;
        this.ingestionTaskExecutor = ingestionTaskExecutor;
        this.ingestionMetrics = ingestionMetrics;
        this.maxParallelJobs = maxParallelJobs;
        this.claimBatchSize = claimBatchSize;
    }

    /**
     * Polls and processes a small batch of pending ingestion jobs.
     */
    @Scheduled(fixedDelayString = "${app.rag.ingestion.poll-delay-ms:250}")
    public void processPendingJobs() {
        if (shuttingDown) {
            return;
        }

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
                try {
                    ingestionTaskExecutor.execute(() -> {
                        try {
                            ingestionJobProcessor.process(nextJob.get());
                        } finally {
                            inFlightJobs.decrementAndGet();
                            refreshQueueMetrics();
                        }
                    });
                } catch (TaskRejectedException ex) {
                    inFlightJobs.decrementAndGet();
                    ingestionJobRepositoryPort.releaseClaim(
                            nextJob.get().tenantId(),
                            nextJob.get().documentId(),
                            nextJob.get().requestVersion(),
                            nextJob.get().processingLeaseVersion());
                    if (!shuttingDown) {
                        log.warn("INGEST_ASYNC_SUBMIT_REJECTED tenantId={} documentId={} version={} message={}",
                                nextJob.get().tenantId().value(), nextJob.get().documentId(),
                                nextJob.get().requestVersion(), ex.getMessage());
                    }
                    return;
                }
            }
        } catch (DataAccessException ex) {
            if (shuttingDown) {
                log.debug("INGEST_ASYNC_POLL_SKIPPED_SHUTDOWN message={}", ex.getMessage());
            } else {
                log.warn("INGEST_ASYNC_POLL_UNAVAILABLE message={}", ex.getMessage());
            }
        }
    }

    @PreDestroy
    void onShutdown() {
        shuttingDown = true;
    }

    private void refreshQueueMetrics() {
        if (shuttingDown) {
            return;
        }
        try {
            ingestionMetrics.updateQueueDepths(
                    ingestionJobRepositoryPort.countByStatus(IngestionStatus.PENDING),
                    ingestionJobRepositoryPort.countByStatus(IngestionStatus.PROCESSING),
                    ingestionJobRepositoryPort.countByStatus(IngestionStatus.FAILED),
                    ingestionJobRepositoryPort.countByStatus(IngestionStatus.DEAD_LETTER));
        } catch (DataAccessException ex) {
            if (shuttingDown) {
                log.debug("INGEST_ASYNC_METRICS_REFRESH_SKIPPED_SHUTDOWN message={}", ex.getMessage());
            } else {
                log.debug("INGEST_ASYNC_METRICS_REFRESH_SKIPPED message={}", ex.getMessage());
            }
        }
    }
}
