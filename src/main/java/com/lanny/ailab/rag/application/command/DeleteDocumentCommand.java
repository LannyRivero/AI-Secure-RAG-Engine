package com.lanny.ailab.rag.application.command;

import com.lanny.ailab.rag.domain.valueobject.TenantId;

public record DeleteDocumentCommand(
        String documentId,
        TenantId tenantId) {
}
