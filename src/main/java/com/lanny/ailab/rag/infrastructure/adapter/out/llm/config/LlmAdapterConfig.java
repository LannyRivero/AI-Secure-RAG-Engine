package com.lanny.ailab.rag.infrastructure.adapter.out.llm.config;

import com.lanny.ailab.rag.application.port.out.LlmChatPort;
import com.lanny.ailab.rag.infrastructure.adapter.out.llm.StubChatAdapter;
import com.lanny.ailab.rag.infrastructure.adapter.out.openai.OpenAiChatAdapter;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LlmAdapterConfig {

    @Bean
    @ConditionalOnProperty(name = "app.llm.provider", havingValue = "stub")
    public LlmChatPort stubAdapter() {
        return new StubChatAdapter();
    }

    @Bean
    @ConditionalOnProperty(name = "app.llm.provider", havingValue = "openai")
    public LlmChatPort openAiAdapter(ChatClient.Builder builder) {
        return new OpenAiChatAdapter(builder);
    }
}