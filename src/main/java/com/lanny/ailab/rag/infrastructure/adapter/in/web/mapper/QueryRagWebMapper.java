package com.lanny.ailab.rag.infrastructure.adapter.in.web.mapper;

import com.lanny.ailab.rag.application.command.QueryRagCommand;
import com.lanny.ailab.rag.application.model.RetrievalFilter;
import com.lanny.ailab.rag.application.result.QueryRagResult;
import com.lanny.ailab.rag.domain.model.FacetValidationException;
import com.lanny.ailab.rag.domain.valueobject.DocumentChunk;
import com.lanny.ailab.rag.domain.valueobject.TenantId;
import com.lanny.ailab.rag.infrastructure.adapter.in.web.dto.EvidenceDto;
import com.lanny.ailab.rag.infrastructure.adapter.in.web.dto.QueryRagRequest;
import com.lanny.ailab.rag.infrastructure.adapter.in.web.dto.QueryRagResponse;
import com.lanny.ailab.shared.error.RequestValidationException;

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
                request.topK(),
                toFilters(request));
    }

    /**
     * Converts HTTP filters into the normalized application model.
     *
     * @param request inbound HTTP query request
     * @return normalized filters or an empty instance when absent
     */
    public RetrievalFilter toFilters(QueryRagRequest request) {
        if (request.filters() == null) {
            return RetrievalFilter.empty();
        }

        try {
            return new RetrievalFilter(
                    request.filters().documentType(),
                    request.filters().source(),
                    request.filters().tags(),
                    request.filters().owner(),
                    request.filters().classification(),
                    request.filters().dateFrom(),
                    request.filters().dateTo());
        } catch (FacetValidationException ex) {
            String field = ex.field().equals("dateFrom") ? "filters.dateFrom" : "filters." + ex.field();
            throw new RequestValidationException(field, ex.getMessage());
        }
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
