package com.lanny.ailab.rag.application.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
class RetrievalFilterTest {

    @Test
    @DisplayName("Normalizes valid retrieval filters")
    void normalizes_valid_filters() {
        RetrievalFilter filter = new RetrievalFilter(
                "  policy  ",
                "  notion  ",
                List.of("security", "internal", "security"),
                "  alice  ",
                "  restricted  ",
                LocalDate.parse("2026-01-01"),
                LocalDate.parse("2026-12-31"));

        assertThat(filter.documentType()).isEqualTo("policy");
        assertThat(filter.source()).isEqualTo("notion");
        assertThat(filter.tags()).containsExactly("security", "internal");
        assertThat(filter.owner()).isEqualTo("alice");
        assertThat(filter.classification()).isEqualTo("restricted");
    }

    @Test
    @DisplayName("Rejects blank filters instead of silently dropping them")
    void rejects_blank_filters() {
        assertThatThrownBy(() -> new RetrievalFilter("   ", null, List.of(), null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("documentType cannot be blank when provided");
    }

    @Test
    @DisplayName("Rejects invalid date ranges")
    void rejects_invalid_date_ranges() {
        assertThatThrownBy(() -> new RetrievalFilter(
                null,
                null,
                List.of(),
                null,
                null,
                LocalDate.parse("2026-12-31"),
                LocalDate.parse("2026-01-01")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("dateFrom must be before or equal to dateTo");
    }
}
