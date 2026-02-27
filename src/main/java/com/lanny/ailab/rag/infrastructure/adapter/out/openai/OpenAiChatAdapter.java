package com.lanny.ailab.rag.infrastructure.adapter.out.openai;

import com.lanny.ailab.rag.application.port.out.LlmChatPort;
import org.springframework.ai.chat.client.ChatClient;


public class OpenAiChatAdapter implements LlmChatPort {

    private final ChatClient chatClient;

    public OpenAiChatAdapter(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @Override
    public String generateAnswer(String userQuery) {
        return chatClient.prompt()
                .user(userQuery)
                .call()
                .content();
    }
}