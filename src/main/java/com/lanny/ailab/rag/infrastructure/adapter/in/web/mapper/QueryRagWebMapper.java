package com.lanny.ailab.rag.infrastructure.adapter.in.web.mapper;

import com.lanny.ailab.rag.application.command.QueryRagCommand;
import com.lanny.ailab.rag.application.result.QueryRagResult;
import com.lanny.ailab.rag.domain.valueobject.DocumentChunk;
import com.lanny.ailab.rag.infrastructure.adapter.in.web.dto.EvidenceDto;
import com.lanny.ailab.rag.infrastructure.adapter.in.web.dto.QueryRagRequest;
import com.lanny.ailab.rag.infrastructure.adapter.in.web.dto.QueryRagResponse;

import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class QueryRagWebMapper {

    public QueryRagCommand toCommand(QueryRagRequest request) {
        return new QueryRagCommand(
                request.query(),
                request.tenantId(),
                request.conversationId(),
                request.topK());
    }

    public QueryRagResponse toResponse(QueryRagResult result) {

        List<EvidenceDto> evidence = result.evidence().stream()
                .map(this::toEvidenceDto)
                .toList();

        return new QueryRagResponse(
                result.answer(),
                evidence,
                result.hasEvidence());
    }

    private EvidenceDto toEvidenceDto(DocumentChunk chunk) {
        return new EvidenceDto(
                chunk.documentId(),
                chunk.content());
    }
}
