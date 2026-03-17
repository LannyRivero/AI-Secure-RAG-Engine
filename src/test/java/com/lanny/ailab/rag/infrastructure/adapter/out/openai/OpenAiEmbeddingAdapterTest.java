package com.lanny.ailab.rag.infrastructure.adapter.out.openai;

import com.lanny.ailab.rag.domain.exception.LlmProviderException;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class OpenAiEmbeddingAdapterTest {

    @Mock
    private EmbeddingModel embeddingModel;

    @InjectMocks
    private OpenAiEmbeddingAdapter adapter;

    @Test
    void given_valid_text_when_embed_then_returns_vector_from_model() {
        float[] expected = new float[]{0.1f, 0.2f, 0.3f};
        when(embeddingModel.embed("hello world")).thenReturn(expected);

        float[] result = adapter.embed("hello world");

        assertThat(result).isEqualTo(expected);
        verify(embeddingModel).embed("hello world");
    }

    @Test
    void given_provider_failure_when_embed_then_throws_LlmProviderException() {
        when(embeddingModel.embed(anyString()))
                .thenThrow(new RuntimeException("OpenAI timeout"));

        assertThatThrownBy(() -> adapter.embed("any text"))
                .isInstanceOf(LlmProviderException.class)
                .hasMessageContaining("Embedding provider failed")
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void given_provider_failure_when_embed_then_wraps_original_cause() {
        RuntimeException cause = new RuntimeException("Connection refused");
        when(embeddingModel.embed(anyString())).thenThrow(cause);

        assertThatThrownBy(() -> adapter.embed("text"))
                .isInstanceOf(LlmProviderException.class)
                .cause()
                .isSameAs(cause);
    }
}
