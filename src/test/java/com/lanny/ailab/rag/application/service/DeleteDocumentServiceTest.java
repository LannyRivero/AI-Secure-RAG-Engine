package com.lanny.ailab.rag.application.service;

import com.lanny.ailab.rag.application.command.DeleteDocumentCommand;
import com.lanny.ailab.rag.application.port.out.DocumentRepositoryPort;
import com.lanny.ailab.rag.domain.valueobject.TenantId;

import org.junit.jupiter.api.BeforeEach;
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

    @Mock private DocumentRepositoryPort documentRepositoryPort;

    private DeleteDocumentService service;

    @BeforeEach
    void setUp() {
        service = new DeleteDocumentService(documentRepositoryPort);
    }

    @Test
    void returns_success_when_document_exists() {
        when(documentRepositoryPort.existsByTenantAndDocument(TenantId.from("org-test"), "doc-1"))
                .thenReturn(true);

        var result = service.execute(command("doc-1"));

        assertThat(result.deleted()).isTrue();
        assertThat(result.documentId()).isEqualTo("doc-1");
        verify(documentRepositoryPort).deleteByTenantAndDocument(TenantId.from("org-test"), "doc-1");
    }

    @Test
    void returns_not_found_when_document_does_not_exist() {
        when(documentRepositoryPort.existsByTenantAndDocument(TenantId.from("org-test"), "doc-1"))
                .thenReturn(false);

        var result = service.execute(command("doc-1"));

        assertThat(result.deleted()).isFalse();
        verify(documentRepositoryPort, never()).deleteByTenantAndDocument(any(TenantId.class), anyString());
    }

    @Test
    void does_not_delete_when_document_not_found() {
        when(documentRepositoryPort.existsByTenantAndDocument(any(TenantId.class), anyString()))
                .thenReturn(false);

        service.execute(command("doc-99"));

        verify(documentRepositoryPort, never()).deleteByTenantAndDocument(any(TenantId.class), anyString());
    }

    private DeleteDocumentCommand command(String documentId) {
        return new DeleteDocumentCommand(documentId, TenantId.from("org-test"));
    }
}
