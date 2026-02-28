package com.lanny.ailab.rag.application.service;

import com.lanny.ailab.rag.application.command.QueryRagCommand;
import com.lanny.ailab.rag.application.port.in.QueryRagUseCase;
import com.lanny.ailab.rag.application.port.out.LlmChatPort;
import com.lanny.ailab.rag.application.port.out.RetrievalPort;
import com.lanny.ailab.rag.application.result.QueryRagResult;
import org.springframework.stereotype.Service;

@Service
public class QueryRagService implements QueryRagUseCase {

    private final LlmChatPort llmChatPort;
    private final RetrievalPort retrievalPort;
    private final PromptBuilder promptBuilder = new PromptBuilder();

    public QueryRagService(
            LlmChatPort llmChatPort,
            RetrievalPort retrievalPort) {
        this.llmChatPort = llmChatPort;
        this.retrievalPort = retrievalPort;
    }

    @Override
    public QueryRagResult execute(QueryRagCommand command) {

        int topK = command.topK() != null ? command.topK() : 3;

        var chunks = retrievalPort.retrieve(
                command.query(),
                command.tenantId(),
                topK
        );

        if (chunks.isEmpty()) {
            return QueryRagResult.noEvidence();
        }

        String prompt = promptBuilder.build(command.query(), chunks);

        String answer = llmChatPort.generateAnswer(prompt);

        return QueryRagResult.withEvidence(answer, chunks);
    }
}
