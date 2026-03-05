package com.lanny.ailab.rag.domain.valueobject;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentIdTest {

    @Test
    void accepts_valid_value() {
        var id = DocumentId.from("doc-123");
        assertThat(id.value()).isEqualTo("doc-123");
    }

    @Test
    void rejects_null() {
        assertThatThrownBy(() -> DocumentId.from(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_blank() {
        assertThatThrownBy(() -> DocumentId.from("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void two_ids_with_same_value_are_equal() {
        assertThat(DocumentId.from("doc-1")).isEqualTo(DocumentId.from("doc-1"));
    }

    @Test
    void two_ids_with_different_values_are_not_equal() {
        assertThat(DocumentId.from("doc-1")).isNotEqualTo(DocumentId.from("doc-2"));
    }
}