package com.lanny.ailab.rag.infrastructure.adapter.in.web.dto;

public record RagMetricsResponse(
        long totalRequests,
        long thresholdRejected,
        long llmCalls,
        long noEvidenceResponses) {
}
