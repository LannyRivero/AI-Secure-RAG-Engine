package com.lanny.ailab.rag.application.policy;

import com.lanny.ailab.rag.domain.valueobject.DocumentChunk;
import com.lanny.ailab.rag.domain.valueobject.SimilarityScore;
import com.lanny.ailab.rag.domain.valueobject.TenantId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class RelevancePolicyTest {

    private RelevancePolicy policy;

    @BeforeEach
    void setUp() {
        policy = new RelevancePolicy(0.7);
    }

    @Test
    void is_relevant_when_at_least_one_chunk_meets_threshold() {
        var chunks = List.of(
                chunk("doc-1", 0.5),
                chunk("doc-2", 0.8));

        assertThat(policy.isRelevant(chunks)).isTrue();
    }

    @Test
    void is_not_relevant_when_all_chunks_below_threshold() {
        var chunks = List.of(
                chunk("doc-1", 0.3),
                chunk("doc-2", 0.6));

        assertThat(policy.isRelevant(chunks)).isFalse();
    }

    @Test
    void is_not_relevant_when_chunk_list_is_empty() {
        assertThat(policy.isRelevant(List.of())).isFalse();
    }

    @Test
    void is_relevant_when_chunk_score_equals_threshold_exactly() {
        var chunks = List.of(chunk("doc-1", 0.7));

        assertThat(policy.isRelevant(chunks)).isTrue();
    }

    @Test
    void threshold_is_loaded_from_constructor() {
        var strictPolicy = new RelevancePolicy(0.95);
        var permissivePolicy = new RelevancePolicy(0.3);

        var chunks = List.of(chunk("doc-1", 0.6));

        assertThat(strictPolicy.isRelevant(chunks)).isFalse();
        assertThat(permissivePolicy.isRelevant(chunks)).isTrue();
    }

    private DocumentChunk chunk(String documentId, double score) {
        return new DocumentChunk(documentId, TenantId.from("org-test"), "contenido de prueba", SimilarityScore.of(score));
    }
}
