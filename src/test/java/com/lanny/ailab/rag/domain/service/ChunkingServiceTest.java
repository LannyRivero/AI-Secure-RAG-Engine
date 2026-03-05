package com.lanny.ailab.rag.domain.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChunkingServiceTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "   ", "\t", "\n" })
    void returns_empty_when_text_is_blank_or_null(String text) {
        var service = new ChunkingService(10, 2);
        assertThat(service.chunk(text)).isEmpty();
    }

    @Test
    void single_chunk_when_text_fits_within_chunk_size() {
        var service = new ChunkingService(10, 2);
        String text = "one two three";

        List<String> chunks = service.chunk(text);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).isEqualTo("one two three");
    }

    @Test
    void splits_into_multiple_chunks_when_text_exceeds_chunk_size() {
        var service = new ChunkingService(3, 1);
        String text = "a b c d e f g";

        List<String> chunks = service.chunk(text);

        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0)).isEqualTo("a b c");
        assertThat(chunks.get(1)).isEqualTo("c d e");
        assertThat(chunks.get(2)).isEqualTo("e f g");
    }

    @Test
    void consecutive_chunks_share_overlap_words() {
        var service = new ChunkingService(4, 2);
        String text = "w1 w2 w3 w4 w5 w6";

        List<String> chunks = service.chunk(text);

        // chunk 0 ends with w3 w4, chunk 1 starts with w3 w4
        assertThat(chunks.get(0)).endsWith("w3 w4");
        assertThat(chunks.get(1)).startsWith("w3 w4");
    }

    @Test
    void last_chunk_contains_remaining_words_even_if_smaller_than_chunk_size() {
        var service = new ChunkingService(4, 1);
        // words: a b c d e (5 words), step=3
        // chunk 0: a b c d
        // chunk 1: d e (only 2 words)
        String text = "a b c d e";

        List<String> chunks = service.chunk(text);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(1)).isEqualTo("d e");
    }

    @Test
    void result_is_immutable() {
        var service = new ChunkingService(3, 1);
        List<String> chunks = service.chunk("a b c d e");

        assertThatThrownBy(() -> chunks.add("extra"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void throws_when_chunk_size_is_zero() {
        assertThatThrownBy(() -> new ChunkingService(0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chunkSize");
    }

    @Test
    void throws_when_overlap_equals_chunk_size() {
        assertThatThrownBy(() -> new ChunkingService(5, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overlap");
    }

    @Test
    void throws_when_overlap_is_negative() {
        assertThatThrownBy(() -> new ChunkingService(5, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overlap");
    }
}
