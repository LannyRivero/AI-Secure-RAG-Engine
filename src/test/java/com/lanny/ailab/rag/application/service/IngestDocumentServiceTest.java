package com.lanny.ailab.rag.application.service;

import com.lanny.ailab.rag.application.command.IngestDocumentCommand;
import com.lanny.ailab.rag.application.metrics.IngestionMetrics;
import com.lanny.ailab.rag.application.model.IngestionJob;
import com.lanny.ailab.rag.application.port.out.IngestionJobRepositoryPort;
import com.lanny.ailab.rag.application.port.out.IngestionSourceResolverPort;
import com.lanny.ailab.rag.application.model.ResolvedIngestionSource;
import com.lanny.ailab.rag.domain.model.IngestionStatus;
import com.lanny.ailab.rag.domain.model.SourceType;
import com.lanny.ailab.rag.domain.valueobject.TenantId;
import com.lanny.ailab.shared.infrastructure.observability.OperationMetrics;
import io.micrometer.observation.ObservationRegistry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the ingestion request acceptance service.
 */
@ExtendWith(MockitoExtension.class)
@Tag("unit")
class IngestDocumentServiceTest {

        @Mock
        private IngestionJobRepositoryPort ingestionJobRepositoryPort;
        @Mock
        private IngestionMetrics ingestionMetrics;
        @Mock
        private IngestionSourceResolverPort ingestionSourceResolverPort;
        @Mock
        private OperationMetrics operationMetrics;

        private IngestDocumentService service;

        @BeforeEach
        void setUp() {
                service = new IngestDocumentService(
                                ingestionJobRepositoryPort,
                                ingestionSourceResolverPort,
                                ingestionMetrics,
                                operationMetrics,
                                ObservationRegistry.NOOP);
        }

        @Test
        @DisplayName("When a document is enqueued, the service returns a PENDING status")
        void returns_pending_when_document_is_enqueued() {
                when(ingestionSourceResolverPort.resolve(command("doc-1", "content")))
                                .thenReturn(new ResolvedIngestionSource("content", SourceType.RAW_TEXT, null));
                when(ingestionJobRepositoryPort
                                .enqueue(new com.lanny.ailab.rag.application.command.EnqueueIngestionJobCommand(
                                                "doc-1", TenantId.from("org-test"), "content", SourceType.RAW_TEXT,
                                                null)))
                                .thenReturn(job("doc-1", IngestionStatus.PENDING, 0, null, 1L));

                var result = service.execute(command("doc-1", "content"));

                assertThat(result.documentId()).isEqualTo("doc-1");
                assertThat(result.status()).isEqualTo(IngestionStatus.PENDING);
        }

        @Test
        @DisplayName("When a document is enqueued, the service delegates to the durable repository")
        void delegates_enqueue_to_durable_repository() {
                var command = command("doc-42", "new content");
                when(ingestionSourceResolverPort.resolve(command))
                                .thenReturn(new ResolvedIngestionSource("new content", SourceType.RAW_TEXT, null));
                var enqueue = new com.lanny.ailab.rag.application.command.EnqueueIngestionJobCommand(
                                "doc-42", TenantId.from("org-test"), "new content", SourceType.RAW_TEXT, null);
                when(ingestionJobRepositoryPort.enqueue(enqueue))
                                .thenReturn(job("doc-42", IngestionStatus.PENDING, 0, null, 3L));

                service.execute(command);

                verify(ingestionSourceResolverPort).resolve(command);
                verify(ingestionJobRepositoryPort).enqueue(enqueue);
                verify(ingestionMetrics).incrementAccepted();
        }

        private IngestDocumentCommand command(String documentId, String content) {
                return new IngestDocumentCommand(documentId, TenantId.from("org-test"), content, null);
        }

        private IngestionJob job(String documentId, IngestionStatus status, int chunksIndexed, String errorMessage,
                        long version) {
                return new IngestionJob(
                                TenantId.from("org-test"),
                                documentId,
                                "content",
                                SourceType.RAW_TEXT,
                                null,
                                status,
                                version,
                                chunksIndexed,
                                errorMessage,
                                0,
                                3,
                                java.time.Instant.now(),
                                null,
                                null,
                                java.time.Instant.now(),
                                java.time.Instant.now(),
                                null,
                                null);
        }
}
