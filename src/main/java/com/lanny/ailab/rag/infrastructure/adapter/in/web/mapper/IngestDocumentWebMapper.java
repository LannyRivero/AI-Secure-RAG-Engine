package com.lanny.ailab.rag.infrastructure.adapter.in.web.mapper;

import com.lanny.ailab.rag.application.command.IngestDocumentCommand;
import com.lanny.ailab.rag.application.command.IngestionSourceCommand;
import com.lanny.ailab.rag.application.result.IngestDocumentResult;
import com.lanny.ailab.rag.application.result.IngestionStatusResult;
import com.lanny.ailab.rag.domain.valueobject.TenantId;
import com.lanny.ailab.rag.infrastructure.adapter.in.web.dto.IngestDocumentRequest;
import com.lanny.ailab.rag.infrastructure.adapter.in.web.dto.IngestDocumentResponse;
import com.lanny.ailab.rag.infrastructure.adapter.in.web.dto.IngestionStatusResponse;

import org.springframework.stereotype.Component;

@Component
public class IngestDocumentWebMapper {

    public IngestDocumentCommand toCommand(IngestDocumentRequest request, TenantId tenantId) {
        if ((request.source() == null || request.source().type() == null)
                && (request.content() == null || request.content().isBlank())) {
            throw new IllegalArgumentException("content is required when source is not provided");
        }

        if (request.source() != null
                && request.source().type() == com.lanny.ailab.rag.domain.model.SourceType.RAW_TEXT
                && (request.content() == null || request.content().isBlank())) {
            throw new IllegalArgumentException("content is required for RAW_TEXT source");
        }

        return new IngestDocumentCommand(
                request.documentId(),
                tenantId,
                request.content(),
                request.source() == null
                        ? null
                        : new IngestionSourceCommand(
                                request.source().type(),
                                request.source().uri(),
                                request.source().base64Content(),
                                request.source().accessToken(),
                                request.source().maxPages()));
    }

    public IngestDocumentResponse toResponse(IngestDocumentResult result) {
        return new IngestDocumentResponse(
                result.documentId(),
                result.status());
    }

    public IngestionStatusResponse toStatusResponse(IngestionStatusResult result) {
        return new IngestionStatusResponse(
                result.documentId(),
                result.sourceType(),
                result.sourceUri(),
                result.status(),
                result.chunksIndexed(),
                result.errorMessage(),
                result.retryCount(),
                result.maxAttempts(),
                result.requestedAt(),
                result.startedAt(),
                result.completedAt(),
                result.updatedAt(),
                result.nextAttemptAt(),
                result.lastErrorAt(),
                result.deadLetteredAt());
    }
}
