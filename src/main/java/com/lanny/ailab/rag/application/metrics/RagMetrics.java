package com.lanny.ailab.rag.application.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import io.micrometer.core.instrument.Timer;

@Component
public class RagMetrics {

    private final Counter totalRequests;
    private final Counter thresholdRejected;
    private final Counter llmCalls;
    private final Counter noEvidenceResponses;
    private final Timer retrievalLatency;
    private final Timer llmLatency;

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

        this.retrievalLatency = Timer.builder("rag.retrieval.latency")
                .description("Time spent on vector retrieval")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        this.llmLatency = Timer.builder("rag.llm.latency")
                .description("Time spent waiting for LLM response")
                .publishPercentiles(0.5, 0.95, 0.99)
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

    public Timer retrievalLatency() {
        return retrievalLatency;
    }

    public Timer llmLatency() {
        return llmLatency;
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