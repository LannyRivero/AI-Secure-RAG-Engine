package com.lanny.ailab.rag.infrastructure.adapter.in.web.dto;

public record QueryRagRequest(
        String query,
        String tenantId) {
}