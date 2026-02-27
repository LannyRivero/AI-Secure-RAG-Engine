package com.lanny.ailab.rag.infrastructure.adapter.out.openai;

import com.lanny.ailab.rag.application.port.out.LlmChatPort;
import org.springframework.stereotype.Component;

@Component
public class OpenAiChatAdapter implements LlmChatPort {

    @Override
    public String generateAnswer(String prompt) {

        // STUB enterprise-style
        return """
                [MOCK RESPONSE]
                You asked: %s

                This is a simulated LLM response.
                """.formatted(prompt);
    }
}