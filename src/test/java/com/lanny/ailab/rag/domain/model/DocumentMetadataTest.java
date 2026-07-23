package com.lanny.ailab.rag.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
class DocumentMetadataTest {

    @Test
    @DisplayName("Normalizes valid metadata and removes duplicate tags preserving order")
    void normalizes_valid_metadata() {
        DocumentMetadata metadata = new DocumentMetadata(
                "  policy  ",
                LocalDate.parse("2026-07-22"),
                "  notion  ",
                List.of("security", "internal", "security"),
                "  alice  ",
                "  restricted  ");

        assertThat(metadata.documentType()).isEqualTo("policy");
        assertThat(metadata.source()).isEqualTo("notion");
        assertThat(metadata.owner()).isEqualTo("alice");
        assertThat(metadata.classification()).isEqualTo("restricted");
        assertThat(metadata.tags()).containsExactly("security", "internal");
    }

    @Test
    @DisplayName("Rejects blank metadata values when explicitly provided")
    void rejects_blank_metadata_values() {
        assertThatThrownBy(() -> new DocumentMetadata("   ", null, null, List.of(), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("documentType cannot be blank when provided");
    }

    @Test
    @DisplayName("Rejects oversized tag collections")
    void rejects_oversized_tag_collections() {
        assertThatThrownBy(() -> new DocumentMetadata(
                null,
                null,
                null,
                java.util.stream.IntStream.range(0, 21).mapToObj(i -> "tag-" + i).toList(),
                null,
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tags must contain at most 20 entries");
    }
}
