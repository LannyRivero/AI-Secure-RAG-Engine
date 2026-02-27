package com.lanny.ailab.rag.infrastructure.adapter.out.llm;

import com.lanny.ailab.rag.application.port.out.LlmChatPort;

public class StubChatAdapter implements LlmChatPort {

    @Override
    public String generateAnswer(String userQuery) {
        return """
                [MOCK RESPONSE]
                You asked: %s

                This is a simulated LLM response (Sprint 1).
                """.formatted(userQuery);
    }
}
