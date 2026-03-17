package com.lanny.ailab.rag.infrastructure.adapter.in.web.mapper;

import com.lanny.ailab.rag.application.command.QueryRagCommand;
import com.lanny.ailab.rag.application.result.QueryRagResult;
import com.lanny.ailab.rag.domain.valueobject.DocumentChunk;
import com.lanny.ailab.rag.domain.valueobject.TenantId;
import com.lanny.ailab.rag.infrastructure.adapter.in.web.dto.EvidenceDto;
import com.lanny.ailab.rag.infrastructure.adapter.in.web.dto.QueryRagRequest;
import com.lanny.ailab.rag.infrastructure.adapter.in.web.dto.QueryRagResponse;

import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class QueryRagWebMapper {

    private static final String NO_EVIDENCE_MESSAGE = "No relevant information found.";

    public QueryRagCommand toCommand(QueryRagRequest request, TenantId tenantId) {
        return new QueryRagCommand(
                request.query(),
                tenantId,
                request.conversationId(),
                request.topK());
    }

    public QueryRagResponse toResponse(QueryRagResult result) {

        if (!result.hasEvidence()) {
            return new QueryRagResponse(
                    NO_EVIDENCE_MESSAGE,
                    List.of(),
                    false);
        }

        List<EvidenceDto> evidence = result.evidence().stream()
                .map(this::toEvidenceDto)
                .toList();

        return new QueryRagResponse(
                result.answer(),
                evidence,
                true);
    }

    private EvidenceDto toEvidenceDto(DocumentChunk chunk) {
        return new EvidenceDto(
                chunk.documentId(),
                chunk.score().value());
    }
}
