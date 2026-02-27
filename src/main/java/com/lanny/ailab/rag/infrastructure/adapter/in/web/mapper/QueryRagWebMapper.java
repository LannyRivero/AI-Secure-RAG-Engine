package com.lanny.ailab.rag.infrastructure.adapter.in.web.mapper;

import com.lanny.ailab.rag.application.command.QueryRagCommand;
import com.lanny.ailab.rag.application.result.QueryRagResult;
import com.lanny.ailab.rag.infrastructure.adapter.in.web.dto.QueryRagRequest;
import com.lanny.ailab.rag.infrastructure.adapter.in.web.dto.QueryRagResponse;

import org.springframework.stereotype.Component;

@Component
public class QueryRagWebMapper {

    public QueryRagCommand toCommand(QueryRagRequest request) {
        return new QueryRagCommand(
                request.query(),
                request.tenantId());
    }

    public QueryRagResponse toResponse(QueryRagResult result) {
        return new QueryRagResponse(
                result.answer());
    }
}
