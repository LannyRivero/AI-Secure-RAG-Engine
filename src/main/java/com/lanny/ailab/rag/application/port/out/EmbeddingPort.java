package com.lanny.ailab.rag.application.port.out;

/**
 * Outbound port for generating dense vector embeddings from text.
 *
 * <p>Implementations delegate to an external embedding model (e.g., OpenAI
 * {@code text-embedding-ada-002}). The returned vector dimensionality must be
 * consistent with the vectors already stored in the pgvector index.
 */
public interface EmbeddingPort {

    /**
     * Generates a dense embedding vector for the given text.
     *
     * @param text the input text to embed; must not be null or blank
     * @return a float array representing the embedding vector
     * @throws com.lanny.ailab.rag.domain.exception.LlmProviderException if the embedding call fails
     */
    float[] embed(String text);
}
