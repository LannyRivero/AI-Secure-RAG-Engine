package com.lanny.ailab.rag.infrastructure.adapter.out.openai;

import com.lanny.ailab.rag.domain.exception.LlmProviderException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class OpenAiChatAdapterResilienceTest {

    @Mock
    private ChatClient.Builder builder;

    @Mock
    private ChatClient chatClient;

    @Test
    void throws_LlmProviderException_when_openai_fails() {
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);

        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenThrow(new RuntimeException("OpenAI timeout"));

        OpenAiChatAdapter adapter = new OpenAiChatAdapter(builder);

        assertThatThrownBy(() -> adapter.generateAnswer("test query"))
                .isInstanceOf(LlmProviderException.class)
                .hasMessageContaining("LLM provider failed");
    }

    @Test
    void circuit_breaker_registry_contains_llm_instance() {
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();
        CircuitBreaker cb = registry.circuitBreaker("llmCircuitBreaker");

        assertThat(cb).isNotNull();
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }
}