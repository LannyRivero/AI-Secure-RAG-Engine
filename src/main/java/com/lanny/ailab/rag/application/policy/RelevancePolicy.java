package com.lanny.ailab.rag.application.policy;

import com.lanny.ailab.rag.domain.valueobject.DocumentChunk;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RelevancePolicy {

    private final double minScoreThreshold;

    public RelevancePolicy(
            @Value("${app.rag.min-score-threshold:0.75}") double minScoreThreshold) {
        this.minScoreThreshold = minScoreThreshold;
    }

    public boolean isRelevant(List<DocumentChunk> chunks) {
        return chunks.stream()
                .anyMatch(chunk -> chunk.score() >= minScoreThreshold);
    }

    double getMinScoreThreshold() {
        return minScoreThreshold;
    }
}
