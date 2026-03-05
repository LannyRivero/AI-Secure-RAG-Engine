package com.lanny.ailab.rag.domain.valueobject;

/**
 * Value object representing a cosine similarity score between two vectors.
 *
 * Range: [0.0, 1.0]
 * - 1.0 = identical vectors
 * - 0.0 = completely dissimilar
 *
 * Negative values are theoretically possible with cosine distance
 * but pgvector normalizes to [0, 1] with the <=> operator.
 * We enforce [0, 1] at construction time.
 */
public record SimilarityScore(double value) {

    public SimilarityScore {
        if (value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(
                    "SimilarityScore must be between 0.0 and 1.0, got: " + value);
        }
    }

    public static SimilarityScore of(double value) {
        return new SimilarityScore(value);
    }

    public boolean isAboveThreshold(double threshold) {
        return value >= threshold;
    }
}
