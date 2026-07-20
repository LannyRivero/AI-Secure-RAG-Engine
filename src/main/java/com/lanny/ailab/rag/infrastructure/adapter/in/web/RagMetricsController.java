package com.lanny.ailab.rag.infrastructure.adapter.in.web;

import com.lanny.ailab.rag.application.metrics.RagMetrics;
import com.lanny.ailab.rag.application.metrics.IngestionMetrics;
import com.lanny.ailab.rag.infrastructure.adapter.in.web.dto.RagMetricsResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RagMetricsController {

    private final RagMetrics ragMetrics;
    private final IngestionMetrics ingestionMetrics;

    public RagMetricsController(RagMetrics ragMetrics, IngestionMetrics ingestionMetrics) {
        this.ragMetrics = ragMetrics;
        this.ingestionMetrics = ingestionMetrics;
    }

    @GetMapping("/rag/metrics")
    public RagMetricsResponse getMetrics() {

        return new RagMetricsResponse(
                ragMetrics.total(),
                ragMetrics.rejected(),
                ragMetrics.llmCalls(),
                ragMetrics.noEvidence(),
                ingestionMetrics.accepted(),
                ingestionMetrics.completed(),
                ingestionMetrics.retriesScheduled(),
                ingestionMetrics.failed(),
                ingestionMetrics.deadLettered(),
                ingestionMetrics.queued(),
                ingestionMetrics.processing(),
                ingestionMetrics.failedQueue(),
                ingestionMetrics.deadLetterQueue());
    }
}
