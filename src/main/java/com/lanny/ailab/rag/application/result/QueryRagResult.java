package com.lanny.ailab.rag.application.result;

import java.util.List;
import com.lanny.ailab.rag.domain.valueobject.DocumentChunk;

public record QueryRagResult(
                String answer,
                List<DocumentChunk> evidence,
                boolean hasEvidence) {

        private static final String NO_EVIDENCE_TOKEN = "NO_EVIDENCE";

        public static QueryRagResult noEvidence() {
                return new QueryRagResult(
                                NO_EVIDENCE_TOKEN,
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
