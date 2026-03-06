package com.lanny.ailab.rag.infrastructure.adapter.in.web.dto;

public record RagMetricsResponse(
                double total,
                double rejected,
                double llmCalls,
                double noEvidence) {
}
