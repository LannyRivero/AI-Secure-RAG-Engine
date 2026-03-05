package com.lanny.ailab.rag.application.service;

import com.lanny.ailab.rag.application.command.DeleteDocumentCommand;
import com.lanny.ailab.rag.application.port.in.DeleteDocumentUseCase;
import com.lanny.ailab.rag.application.port.out.DocumentRepositoryPort;
import com.lanny.ailab.rag.application.result.DeleteDocumentResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DeleteDocumentService implements DeleteDocumentUseCase {

    private static final Logger log = LoggerFactory.getLogger(DeleteDocumentService.class);

    private final DocumentRepositoryPort documentRepositoryPort;

    public DeleteDocumentService(DocumentRepositoryPort documentRepositoryPort) {
        this.documentRepositoryPort = documentRepositoryPort;
    }

    @Override
    public DeleteDocumentResult execute(DeleteDocumentCommand command) {
        String tenantId   = command.tenantId().value();
        String documentId = command.documentId();

        log.info("DELETE_DOCUMENT_START tenantId={} documentId={}", tenantId, documentId);

        boolean existed = documentRepositoryPort.existsByTenantAndDocument(tenantId, documentId);

        if (!existed) {
            log.warn("DELETE_DOCUMENT_NOT_FOUND tenantId={} documentId={}", tenantId, documentId);
            return DeleteDocumentResult.notFound(documentId);
        }

        documentRepositoryPort.deleteByTenantAndDocument(tenantId, documentId);

        log.info("DELETE_DOCUMENT_COMPLETE tenantId={} documentId={}", tenantId, documentId);

        return DeleteDocumentResult.success(documentId);
    }
}