package com.lanny.ailab.rag.application.result;

import java.util.List;

import com.lanny.ailab.rag.domain.valueobject.DocumentChunk;

public record QueryRagResult(
                String answer,
                List<DocumentChunk> evidence,
                boolean hasEvidence) {

        public static QueryRagResult noEvidence() {
                return new QueryRagResult(
                                "No tengo evidencia suficiente.",
                                List.of(),
                                false);
        }

        public static QueryRagResult withEvidence(
                        String answer,
                        List<DocumentChunk> evidence) {
                return new QueryRagResult(
                                answer,
                                evidence,
                                true);
        }
}
