package com.lanny.ailab.rag.application.service;

import com.lanny.ailab.rag.application.command.QueryRagCommand;
import com.lanny.ailab.rag.application.metrics.RagMetrics;
import com.lanny.ailab.rag.application.policy.RelevancePolicy;
import com.lanny.ailab.rag.application.port.in.QueryRagUseCase;
import com.lanny.ailab.rag.application.port.out.LlmChatPort;
import com.lanny.ailab.rag.application.port.out.RetrievalPort;
import com.lanny.ailab.rag.application.result.QueryRagResult;
import org.springframework.stereotype.Service;

@Service
public class QueryRagService implements QueryRagUseCase {

    private static final String NO_EVIDENCE_TOKEN = "no_evidence";

    private final LlmChatPort llmChatPort;
    private final RetrievalPort retrievalPort;
    private final PromptBuilder promptBuilder;
    private final RelevancePolicy relevancePolicy;
    private final RagMetrics ragMetrics;

    public QueryRagService(
            LlmChatPort llmChatPort,
            RetrievalPort retrievalPort,
            PromptBuilder promptBuilder,
            RelevancePolicy relevancePolicy,
            RagMetrics ragMetrics) {

        this.llmChatPort = llmChatPort;
        this.retrievalPort = retrievalPort;
        this.promptBuilder = promptBuilder;
        this.relevancePolicy = relevancePolicy;
        this.ragMetrics = ragMetrics;
    }

    @Override
    public QueryRagResult execute(QueryRagCommand command) {

        ragMetrics.incrementTotal();

        int topK = command.topK() != null ? command.topK() : 3;

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
}
