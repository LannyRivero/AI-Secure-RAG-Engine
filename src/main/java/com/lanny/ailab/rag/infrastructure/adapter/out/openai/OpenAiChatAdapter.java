package com.lanny.ailab.rag.infrastructure.adapter.out.openai;

import com.lanny.ailab.rag.application.port.out.LlmChatPort;
import com.lanny.ailab.rag.domain.exception.LlmProviderException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;

public class OpenAiChatAdapter implements LlmChatPort {

    private static final Logger log = LoggerFactory.getLogger(OpenAiChatAdapter.class);

    private final ChatClient chatClient;

    public OpenAiChatAdapter(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @Override
    public String generateAnswer(String userQuery) {

        long start = System.nanoTime();

        try {

            String response = chatClient.prompt()
                    .user(userQuery)
                    .call()
                    .content();

            long duration = (System.nanoTime() - start) / 1_000_000;

            log.info("LLM_CALL_SUCCESS model=openai latencyMs={} queryLength={}",
                    duration,
                    userQuery.length());

            return response;

        } catch (Exception ex) {

            long duration = System.currentTimeMillis() - start;

            log.error("LLM_CALL_ERROR model=openai latencyMs={} errorType={}",
                    duration,
                    ex.getClass().getSimpleName());

            throw new LlmProviderException("LLM provider failed", ex);
        }
    }
}