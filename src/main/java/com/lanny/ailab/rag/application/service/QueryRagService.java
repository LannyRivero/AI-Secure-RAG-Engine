package com.lanny.ailab.rag.application.service;

import com.lanny.ailab.rag.application.command.QueryRagCommand;
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

    public QueryRagService(
            LlmChatPort llmChatPort,
            RetrievalPort retrievalPort,
            PromptBuilder promptBuilder,
            RelevancePolicy relevancePolicy) {

        this.llmChatPort = llmChatPort;
        this.retrievalPort = retrievalPort;
        this.promptBuilder = promptBuilder;
        this.relevancePolicy = relevancePolicy;
    }

    @Override
    public QueryRagResult execute(QueryRagCommand command) {

        int topK = command.topK() != null ? command.topK() : 3;

        var chunks = retrievalPort.retrieve(
                command.query(),
                command.tenantId(),
                topK);

        if (chunks.isEmpty()) {
            return QueryRagResult.noEvidence();
        }

        if (!relevancePolicy.isRelevant(chunks)) {
            return QueryRagResult.noEvidence();
        }

        String prompt = promptBuilder.build(command.query(), chunks);

        String answer = llmChatPort.generateAnswer(prompt);

        if (answer == null || answer.isBlank()) {
            return QueryRagResult.noEvidence();
        }

        String normalized = answer.trim().toLowerCase();

        if (normalized.equals(NO_EVIDENCE_TOKEN)) {
            return QueryRagResult.noEvidence();
        }

        return QueryRagResult.withEvidence(answer, chunks);
    }
}
