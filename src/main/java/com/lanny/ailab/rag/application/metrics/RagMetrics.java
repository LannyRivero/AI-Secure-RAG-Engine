package com.lanny.ailab.rag.application.metrics;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class RagMetrics {

    private final AtomicLong totalRequests = new AtomicLong();
    private final AtomicLong thresholdRejected = new AtomicLong();
    private final AtomicLong llmCalls = new AtomicLong();
    private final AtomicLong noEvidenceResponses = new AtomicLong();

    public void incrementTotal() {
        totalRequests.incrementAndGet();
    }

    public void incrementThresholdRejected() {
        thresholdRejected.incrementAndGet();
    }

    public void incrementLlmCalls() {
        llmCalls.incrementAndGet();
    }

    public void incrementNoEvidence() {
        noEvidenceResponses.incrementAndGet();
    }

    public long total() {
        return totalRequests.get();
    }

    public long rejected() {
        return thresholdRejected.get();
    }

    public long llmCalls() {
        return llmCalls.get();
    }

    public long noEvidence() {
        return noEvidenceResponses.get();
    }
}