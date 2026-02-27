package com.lanny.ailab.rag.application.command;

import com.lanny.ailab.rag.domain.valueobject.TenantId;

public record QueryRagCommand(
        String query,
        TenantId tenantId) {
}
