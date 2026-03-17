package com.lanny.ailab.rag.application.port.out;

/**
 * Outbound port for generating natural language answers via a large language model.
 *
 * <p>The prompt passed to this port is expected to be pre-sanitized and grounded
 * with retrieved context chunks by the application layer ({@code PromptBuilder}).
 * Implementations must wrap provider failures in
 * {@link com.lanny.ailab.rag.domain.exception.LlmProviderException}.
 */
public interface LlmChatPort {

    /**
     * Sends the given prompt to the LLM and returns the generated answer.
     *
     * @param prompt the full, sanitized prompt including retrieved context
     * @return the LLM-generated answer string; never null
     * @throws com.lanny.ailab.rag.domain.exception.LlmProviderException if the LLM call fails after retries
     */
    String generateAnswer(String prompt);
}
