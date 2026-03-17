package com.lanny.ailab.rag.domain.valueobject;

/**
 * Value object representing a retrieved document chunk with its relevance score.
 *
 * <p>The {@code score} is a validated {@link SimilarityScore} in range [0.0, 1.0],
 * ensuring that invalid scores never propagate through the system.
 */
public record DocumentChunk(
                String documentId,
                TenantId tenantId,
                String content,
                SimilarityScore score) {
}