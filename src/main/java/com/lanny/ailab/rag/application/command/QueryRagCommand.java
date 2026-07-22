package com.lanny.ailab.rag.application.command;

import com.lanny.ailab.rag.application.model.RetrievalFilter;
import com.lanny.ailab.rag.domain.valueobject.TenantId;

public record QueryRagCommand(
                String query,
                TenantId tenantId,
                String conversationId,
                Integer topK,
                RetrievalFilter filters) {

        public QueryRagCommand {
                filters = filters == null ? RetrievalFilter.empty() : filters;
        }
}
