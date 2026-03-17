package com.lanny.ailab.rag.infrastructure.adapter.out.openai;

import com.lanny.ailab.rag.application.port.out.EmbeddingPort;
import com.lanny.ailab.rag.domain.exception.LlmProviderException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

/**
 * Adapter that delegates text embedding to the OpenAI embedding API via Spring AI.
 *
 * <p>Wraps any provider-level failure in a {@link LlmProviderException} so that
 * the application layer always deals with a single, well-known exception type.
 */
@Component
public class OpenAiEmbeddingAdapter implements EmbeddingPort {

    private static final Logger log = LoggerFactory.getLogger(OpenAiEmbeddingAdapter.class);

    private final EmbeddingModel embeddingModel;

    public OpenAiEmbeddingAdapter(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    /**
     * Generates a dense embedding vector for the given text.
     *
     * @param text the input text to embed
     * @return float array representing the embedding vector
     * @throws LlmProviderException if the embedding API call fails
     */
    @Override
    public float[] embed(String text) {
        try {
            return embeddingModel.embed(text);
        } catch (Exception ex) {
            log.error("EMBEDDING_ERROR message={}", ex.getMessage(), ex);
            throw new LlmProviderException("Embedding provider failed", ex);
        }
    }
}
