package com.lanny.ailab.rag.infrastructure.adapter.in.web.dto;

public record RagMetricsResponse(
        double total,
        double rejected,
        double llmCalls,
        double noEvidence,
        double ingestionAccepted,
        double ingestionCompleted,
        double ingestionRetriesScheduled,
        double ingestionFailed,
        double ingestionDeadLettered,
        double ingestionQueued,
        double ingestionProcessing,
        double ingestionFailedQueue,
        double ingestionDeadLetterQueue) {
}
