package com.lanny.ailab.rag.application.command;

import java.util.Objects;

public record QueryRagCommand(
        String query,
        String tenantId) {

    public QueryRagCommand {
        Objects.requireNonNull(query, "query cannot be null");
        if (query.isBlank()) {
            throw new IllegalArgumentException("query cannot be blank");
        }
    }
}
