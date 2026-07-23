package com.lanny.ailab.rag.application.service;

import com.lanny.ailab.rag.application.metrics.IngestionMetrics;
import com.lanny.ailab.rag.application.model.IngestionJob;
import com.lanny.ailab.rag.application.port.out.IngestionJobRepositoryPort;
import com.lanny.ailab.rag.domain.model.DocumentMetadata;
import com.lanny.ailab.rag.domain.model.IngestionStatus;
import com.lanny.ailab.rag.domain.model.SourceType;
import com.lanny.ailab.rag.domain.valueobject.TenantId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class IngestionWorkerServiceTest {

    @Mock
    private IngestionJobRepositoryPort ingestionJobRepositoryPort;
    @Mock
    private IngestionMetrics ingestionMetrics;
    @Mock
    private IngestionJobProcessor ingestionJobProcessor;

    private IngestionWorkerService service;

    @BeforeEach
    void setUp() {
        Executor rejectingExecutor = task -> {
            throw new TaskRejectedException("executor shutting down");
        };

        service = new IngestionWorkerService(
                ingestionJobRepositoryPort,
                ingestionJobProcessor,
                rejectingExecutor,
                ingestionMetrics,
                1,
                1);
    }

    @Test
    @DisplayName("When executor rejects a claimed job, the worker releases the claim")
    void releases_claim_when_executor_rejects_submission() {
        IngestionJob claimedJob = new IngestionJob(
                TenantId.from("org-test"),
                "doc-1",
                "content",
                DocumentMetadata.empty(),
                SourceType.RAW_TEXT,
                null,
                IngestionStatus.PROCESSING,
                5L,
                2L,
                0,
                null,
                0,
                3,
                Instant.now(),
                Instant.now(),
                null,
                Instant.now(),
                Instant.now(),
                null,
                null);

        when(ingestionJobRepositoryPort.claimNextPending()).thenReturn(Optional.of(claimedJob));

        service.processPendingJobs();

        verify(ingestionJobRepositoryPort).releaseClaim(
                claimedJob.tenantId(),
                claimedJob.documentId(),
                claimedJob.requestVersion(),
                claimedJob.processingLeaseVersion());
    }
}
