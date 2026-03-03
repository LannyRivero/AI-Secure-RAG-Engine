package com.lanny.ailab.rag.infrastructure.adapter.in.web;

import com.lanny.ailab.rag.application.metrics.RagMetrics;
import com.lanny.ailab.rag.infrastructure.adapter.in.web.dto.RagMetricsResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RagMetricsController {

    private final RagMetrics ragMetrics;

    public RagMetricsController(RagMetrics ragMetrics) {
        this.ragMetrics = ragMetrics;
    }

    @GetMapping("/rag/metrics")
    public RagMetricsResponse getMetrics() {

        return new RagMetricsResponse(
                ragMetrics.total(),
                ragMetrics.rejected(),
                ragMetrics.llmCalls(),
                ragMetrics.noEvidence());
    }
}
