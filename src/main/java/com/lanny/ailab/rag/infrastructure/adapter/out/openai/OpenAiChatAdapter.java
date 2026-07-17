package com.lanny.ailab.rag.infrastructure.adapter.out.openai;

import com.lanny.ailab.rag.application.port.out.LlmChatPort;
import com.lanny.ailab.rag.domain.exception.LlmProviderException;
import com.lanny.ailab.shared.infrastructure.observability.OperationMetrics;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.ai.chat.client.ChatClient;

import java.time.Duration;

public class OpenAiChatAdapter implements LlmChatPort {

    private static final Logger log = LoggerFactory.getLogger(OpenAiChatAdapter.class);

    private final ChatClient chatClient;
    private final OperationMetrics operationMetrics;
    private final ObservationRegistry observationRegistry;

    public OpenAiChatAdapter(
            ChatClient.Builder builder,
            OperationMetrics operationMetrics,
            ObservationRegistry observationRegistry) {
        this.chatClient = builder.build();
        this.operationMetrics = operationMetrics;
        this.observationRegistry = observationRegistry;
    }

    @Override
    @Retry(name = "llmRetry", fallbackMethod = "fallback")
    @CircuitBreaker(name = "llmCircuitBreaker", fallbackMethod = "fallback")
    public String generateAnswer(String userQuery) {

        long start = System.nanoTime();
        String outcome = "success";
        Observation observation = Observation.start("rag.provider.chat", observationRegistry)
                .lowCardinalityKeyValue("provider", "openai")
                .lowCardinalityKeyValue("operation", "chat")
                .highCardinalityKeyValue("query.id", String.valueOf(MDC.get("queryId")));

        try (Observation.Scope scope = observation.openScope()) {

            String response = chatClient.prompt()
                    .user(userQuery)
                    .call()
                    .content();

            long duration = (System.nanoTime() - start) / 1_000_000;

            log.info("LLM_CALL_SUCCESS model=openai latencyMs={} queryLength={}",
                    duration,
                    userQuery.length());

            operationMetrics.recordProviderCall("openai", "chat", outcome, Duration.ofMillis(duration));
            return response;

        } catch (Exception ex) {
            outcome = "error";

            long duration = (System.nanoTime() - start) / 1_000_000;

            log.error("LLM_CALL_ERROR model=openai latencyMs={} errorType={}",
                    duration,
                    ex.getClass().getSimpleName());

            operationMetrics.recordProviderCall("openai", "chat", outcome, Duration.ofMillis(duration));
            observation.error(ex);
            throw new LlmProviderException("LLM provider failed", ex);
        } finally {
            observation.lowCardinalityKeyValue("outcome", outcome);
            observation.stop();
        }
    }

    @SuppressWarnings("unused")
    private String fallback(String userQuery, Exception ex) {
        log.warn("LLM_FALLBACK_TRIGGERED reason={} queryLength={}",
                ex.getClass().getSimpleName(), userQuery.length());
        throw new LlmProviderException("LLM provider unavailable after retries", ex);
    }
}
