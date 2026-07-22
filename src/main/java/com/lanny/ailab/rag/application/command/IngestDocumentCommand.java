package com.lanny.ailab.rag.application.command;

import com.lanny.ailab.rag.domain.model.DocumentMetadata;
import com.lanny.ailab.rag.domain.valueobject.TenantId;

public record IngestDocumentCommand(
        String documentId,
        TenantId tenantId,
        String content,
        IngestionSourceCommand source,
        DocumentMetadata metadata) {

    public IngestDocumentCommand {
        metadata = metadata == null ? DocumentMetadata.empty() : metadata;
    }
}
