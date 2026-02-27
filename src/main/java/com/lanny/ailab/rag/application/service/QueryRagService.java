package com.lanny.ailab.rag.application.service;

import com.lanny.ailab.rag.application.command.QueryRagCommand;
import com.lanny.ailab.rag.application.port.in.QueryRagUseCase;
import com.lanny.ailab.rag.application.port.out.LlmChatPort;
import com.lanny.ailab.rag.application.result.QueryRagResult;
import org.springframework.stereotype.Service;

@Service
public class QueryRagService implements QueryRagUseCase {

    private final LlmChatPort llmChatPort;

    public QueryRagService(LlmChatPort llmChatPort) {
        this.llmChatPort = llmChatPort;
    }

    @Override
    public QueryRagResult execute(QueryRagCommand command) {

        // En Sprint 1 no hacemos retrieval.
        // Solo enviamos la query directamente al LLM.

        String answer = llmChatPort.generateAnswer(command.query());

        return new QueryRagResult(answer);
    }
}
