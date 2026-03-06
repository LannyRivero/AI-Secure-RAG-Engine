package com.lanny.ailab.rag.application.service;

import com.lanny.ailab.rag.application.command.QueryRagCommand;
import com.lanny.ailab.rag.application.metrics.RagMetrics;
import com.lanny.ailab.rag.application.policy.RelevancePolicy;
import com.lanny.ailab.rag.application.port.in.QueryRagUseCase;
import com.lanny.ailab.rag.application.port.out.LlmChatPort;
import com.lanny.ailab.rag.application.port.out.RetrievalPort;
import com.lanny.ailab.rag.application.result.QueryRagResult;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class QueryRagService implements QueryRagUseCase {

    private static final String NO_EVIDENCE_TOKEN = "no_evidence";

    private final LlmChatPort llmChatPort;
    private final RetrievalPort retrievalPort;
    private final PromptBuilder promptBuilder;
    private final RelevancePolicy relevancePolicy;
    private final RagMetrics ragMetrics;
    private final int defaultTopK;
    private final int maxTopK;

    public QueryRagService(
            LlmChatPort llmChatPort,
            RetrievalPort retrievalPort,
            PromptBuilder promptBuilder,
            RelevancePolicy relevancePolicy,
            RagMetrics ragMetrics,
            @Value("${app.rag.default-top-k:3}") int defaultTopK,
            @Value("${app.rag.max-top-k:20}") int maxTopK) {

        this.llmChatPort = llmChatPort;
        this.retrievalPort = retrievalPort;
        this.promptBuilder = promptBuilder;
        this.relevancePolicy = relevancePolicy;
        this.ragMetrics = ragMetrics;
        this.defaultTopK = defaultTopK;
        this.maxTopK = maxTopK;
    }

    @Override
    public QueryRagResult execute(QueryRagCommand command) {

        ragMetrics.incrementTotal();

        int topK = resolveTopK(command.topK());

        var chunks = retrievalPort.retrieve(
                command.query(),
                command.tenantId().value(),
                topK);

        if (chunks.isEmpty()) {
            ragMetrics.incrementNoEvidence();
            return QueryRagResult.noEvidence();
        }

        if (!relevancePolicy.isRelevant(chunks)) {
            ragMetrics.incrementThresholdRejected();
            ragMetrics.incrementNoEvidence();
            return QueryRagResult.noEvidence();
        }

        String prompt = promptBuilder.build(command.query(), chunks);
        ragMetrics.incrementLlmCalls();
        String answer = llmChatPort.generateAnswer(prompt);

        if (answer == null || answer.isBlank()) {
            ragMetrics.incrementNoEvidence();
            return QueryRagResult.noEvidence();
        }

        String normalized = answer.trim().toLowerCase();

        if (normalized.equals(NO_EVIDENCE_TOKEN)) {
            ragMetrics.incrementNoEvidence();
            return QueryRagResult.noEvidence();
        }

        return QueryRagResult.withEvidence(answer, chunks);
    }

    /**
     * Resolves the effective topK value.
     * - If caller provides a value: clamp to [1, maxTopK]
     * - If caller provides null: use defaultTopK
     *
     * The DTO already validates topK <= 20 via @Max.
     * The clamp here is a second line of defence for non-HTTP callers.
     */
    int resolveTopK(Integer requested) {
        if (requested == null)
            return defaultTopK;
        return Math.max(1, Math.min(requested, maxTopK));
    }
}
