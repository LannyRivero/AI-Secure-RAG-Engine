package com.lanny.ailab.rag.application.command;

import com.lanny.ailab.rag.domain.model.SourceType;
import com.lanny.ailab.rag.domain.valueobject.TenantId;

/**
 * Fully resolved ingestion payload that can be durably enqueued for background
 * chunking and embedding.
 *
 * @param documentId document identifier within the tenant scope
 * @param tenantId   owning tenant
 * @param content    normalized text extracted from the source
 * @param sourceType connector kind used to resolve the text
 * @param sourceUri  original source locator when the source was remote
 */
public record EnqueueIngestionJobCommand(
        String documentId,
        TenantId tenantId,
        String content,
        SourceType sourceType,
        String sourceUri) {
}
