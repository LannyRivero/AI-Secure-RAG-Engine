package com.lanny.ailab.rag.infrastructure.adapter.out.openai;

import com.lanny.ailab.rag.application.port.out.EmbeddingPort;
import com.lanny.ailab.rag.domain.exception.LlmProviderException;
import com.lanny.ailab.shared.infrastructure.observability.OperationMetrics;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.time.Duration;

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
    private final OperationMetrics operationMetrics;
    private final ObservationRegistry observationRegistry;

    public OpenAiEmbeddingAdapter(
            EmbeddingModel embeddingModel,
            OperationMetrics operationMetrics,
            ObservationRegistry observationRegistry) {
        this.embeddingModel = embeddingModel;
        this.operationMetrics = operationMetrics;
        this.observationRegistry = observationRegistry;
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
        long start = System.nanoTime();
        String outcome = "success";
        Observation observation = Observation.start("rag.provider.embedding", observationRegistry)
                .lowCardinalityKeyValue("provider", "openai")
                .lowCardinalityKeyValue("operation", "embedding");

        try (Observation.Scope scope = observation.openScope()) {
            float[] response = embeddingModel.embed(text);
            operationMetrics.recordProviderCall(
                    "openai",
                    "embedding",
                    outcome,
                    Duration.ofNanos(System.nanoTime() - start));
            return response;
        } catch (Exception ex) {
            outcome = "error";
            log.error("EMBEDDING_ERROR message={}", ex.getMessage(), ex);
            operationMetrics.recordProviderCall(
                    "openai",
                    "embedding",
                    outcome,
                    Duration.ofNanos(System.nanoTime() - start));
            observation.error(ex);
            throw new LlmProviderException("Embedding provider failed", ex);
        } finally {
            observation.lowCardinalityKeyValue("outcome", outcome);
            observation.stop();
        }
    }
}
