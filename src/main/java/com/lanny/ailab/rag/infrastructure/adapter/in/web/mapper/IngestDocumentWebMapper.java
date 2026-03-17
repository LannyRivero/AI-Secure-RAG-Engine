package com.lanny.ailab.rag.infrastructure.adapter.in.web.mapper;

import com.lanny.ailab.rag.application.command.IngestDocumentCommand;
import com.lanny.ailab.rag.application.result.IngestDocumentResult;
import com.lanny.ailab.rag.domain.valueobject.TenantId;
import com.lanny.ailab.rag.infrastructure.adapter.in.web.dto.IngestDocumentRequest;
import com.lanny.ailab.rag.infrastructure.adapter.in.web.dto.IngestDocumentResponse;

import org.springframework.stereotype.Component;

@Component
public class IngestDocumentWebMapper {

    public IngestDocumentCommand toCommand(IngestDocumentRequest request, TenantId tenantId) {
        return new IngestDocumentCommand(
                request.documentId(),
                tenantId,
                request.content());
    }

    public IngestDocumentResponse toResponse(IngestDocumentResult result) {
        return new IngestDocumentResponse(
                result.documentId(),
                result.chunksIndexed());
    }
}