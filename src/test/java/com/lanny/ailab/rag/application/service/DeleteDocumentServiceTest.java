package com.lanny.ailab.rag.application.service;

import com.lanny.ailab.rag.application.command.DeleteDocumentCommand;
import com.lanny.ailab.rag.application.port.out.DocumentRepositoryPort;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class DeleteDocumentServiceTest {

        @Mock
        private DocumentRepositoryPort documentRepositoryPort;

        @Mock
        private OperationMetrics operationMetrics;

        private DeleteDocumentService service;

        @BeforeEach
        void setUp() {
                service = new DeleteDocumentService(documentRepositoryPort, operationMetrics, ObservationRegistry.NOOP);
        }

        @Test
        @DisplayName("When a document exists, the service deletes it and returns success")
        void returns_success_when_document_exists() {
                when(documentRepositoryPort.existsByTenantAndDocument(TenantId.from("org-test"), "doc-1"))
                                .thenReturn(true);
                when(documentRepositoryPort.existsIngestionJobByTenantAndDocument(TenantId.from("org-test"), "doc-1"))
                                .thenReturn(false);

                var result = service.execute(command("doc-1"));

                assertThat(result.deleted()).isTrue();
                assertThat(result.documentId()).isEqualTo("doc-1");
                verify(documentRepositoryPort).deleteByTenantAndDocument(TenantId.from("org-test"), "doc-1");
                verify(documentRepositoryPort).deleteIngestionJobByTenantAndDocument(TenantId.from("org-test"),
                                "doc-1");
        }

        @Test
        @DisplayName("When a document does not exist, the service returns not-found and does not delete")
        void returns_not_found_when_document_does_not_exist() {
                when(documentRepositoryPort.existsByTenantAndDocument(TenantId.from("org-test"), "doc-1"))
                                .thenReturn(false);
                when(documentRepositoryPort.existsIngestionJobByTenantAndDocument(TenantId.from("org-test"), "doc-1"))
                                .thenReturn(false);

                var result = service.execute(command("doc-1"));

                assertThat(result.deleted()).isFalse();
                verify(documentRepositoryPort, never()).deleteByTenantAndDocument(any(TenantId.class), anyString());
                verify(documentRepositoryPort, never()).deleteIngestionJobByTenantAndDocument(any(TenantId.class),
                                anyString());
        }

        @Test
        @DisplayName("When a document is not found, the service does not delete anything")
        void does_not_delete_when_document_not_found() {
                when(documentRepositoryPort.existsByTenantAndDocument(any(TenantId.class), anyString()))
                                .thenReturn(false);
                when(documentRepositoryPort.existsIngestionJobByTenantAndDocument(any(TenantId.class), anyString()))
                                .thenReturn(false);

                service.execute(command("doc-99"));

                verify(documentRepositoryPort, never()).deleteByTenantAndDocument(any(TenantId.class), anyString());
                verify(documentRepositoryPort, never()).deleteIngestionJobByTenantAndDocument(any(TenantId.class),
                                anyString());
        }

        @Test
        @DisplayName("When only an ingestion job exists, the service deletes it and returns success")
        void returns_success_and_purges_ingestion_job_when_only_queued_job_exists() {
                when(documentRepositoryPort.existsByTenantAndDocument(TenantId.from("org-test"), "doc-1"))
                                .thenReturn(false);
                when(documentRepositoryPort.existsIngestionJobByTenantAndDocument(TenantId.from("org-test"), "doc-1"))
                                .thenReturn(true);

                var result = service.execute(command("doc-1"));

                assertThat(result.deleted()).isTrue();
                verify(documentRepositoryPort).deleteByTenantAndDocument(TenantId.from("org-test"), "doc-1");
                verify(documentRepositoryPort).deleteIngestionJobByTenantAndDocument(TenantId.from("org-test"),
                                "doc-1");
        }

        private DeleteDocumentCommand command(String documentId) {
                return new DeleteDocumentCommand(documentId, TenantId.from("org-test"));
        }
}
