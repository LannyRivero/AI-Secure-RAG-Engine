package com.lanny.ailab.rag.application.policy;

import com.lanny.ailab.rag.domain.valueobject.DocumentChunk;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RelevancePolicy {

    private static final double MIN_SCORE_THRESHOLD = 0.5;

    public boolean isRelevant(List<DocumentChunk> chunks) {
        return chunks.stream()
                .anyMatch(chunk -> chunk.score() >= MIN_SCORE_THRESHOLD);
    }
}
