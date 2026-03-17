package com.lanny.ailab.rag.application.service;

import com.lanny.ailab.rag.application.command.DeleteDocumentCommand;
import com.lanny.ailab.rag.application.port.in.DeleteDocumentUseCase;
import com.lanny.ailab.rag.application.port.out.DocumentRepositoryPort;
import com.lanny.ailab.rag.application.result.DeleteDocumentResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Application service that implements the document deletion use case.
 *
 * <p>The operation is idempotent: if the document does not exist, the service
 * returns a not-found result without throwing an exception. Deletion removes all
 * indexed chunks associated with the given document ID within the tenant.
 */
@Service
public class DeleteDocumentService implements DeleteDocumentUseCase {

    private static final Logger log = LoggerFactory.getLogger(DeleteDocumentService.class);

    private final DocumentRepositoryPort documentRepositoryPort;

    public DeleteDocumentService(DocumentRepositoryPort documentRepositoryPort) {
        this.documentRepositoryPort = documentRepositoryPort;
    }

    @Override
    public DeleteDocumentResult execute(DeleteDocumentCommand command) {
        var tenantId      = command.tenantId();
        String documentId = command.documentId();

        log.info("DELETE_DOCUMENT_START tenantId={} documentId={}", tenantId.value(), documentId);

        boolean existed = documentRepositoryPort.existsByTenantAndDocument(tenantId, documentId);

        if (!existed) {
            log.warn("DELETE_DOCUMENT_NOT_FOUND tenantId={} documentId={}", tenantId.value(), documentId);
            return DeleteDocumentResult.notFound(documentId);
        }

        documentRepositoryPort.deleteByTenantAndDocument(tenantId, documentId);

        log.info("DELETE_DOCUMENT_COMPLETE tenantId={} documentId={}", tenantId.value(), documentId);

        return DeleteDocumentResult.success(documentId);
    }
}