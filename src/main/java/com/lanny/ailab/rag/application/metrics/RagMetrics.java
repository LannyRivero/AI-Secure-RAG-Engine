package com.lanny.ailab.rag.application.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class RagMetrics {

    private final Counter totalRequests;
    private final Counter thresholdRejected;
    private final Counter llmCalls;
    private final Counter noEvidenceResponses;

    public RagMetrics(MeterRegistry registry) {
        this.totalRequests = Counter.builder("rag.requests.total")
                .description("Total RAG requests received")
                .register(registry);

        this.thresholdRejected = Counter.builder("rag.requests.threshold_rejected")
                .description("Requests rejected by relevance threshold")
                .register(registry);

        this.llmCalls = Counter.builder("rag.llm.calls")
                .description("Total calls made to the LLM provider")
                .register(registry);

        this.noEvidenceResponses = Counter.builder("rag.responses.no_evidence")
                .description("Responses returned with no evidence")
                .register(registry);
    }

    public void incrementTotal() {
        totalRequests.increment();
    }

    public void incrementThresholdRejected() {
        thresholdRejected.increment();
    }

    public void incrementLlmCalls() {
        llmCalls.increment();
    }

    public void incrementNoEvidence() {
        noEvidenceResponses.increment();
    }

    public double total() {
        return totalRequests.count();
    }

    public double rejected() {
        return thresholdRejected.count();
    }

    public double llmCalls() {
        return llmCalls.count();
    }

    public double noEvidence() {
        return noEvidenceResponses.count();
    }
}