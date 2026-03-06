package com.lanny.ailab.rag.domain.valueobject;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
class SimilarityScoreTest {

    @Test
    void accepts_valid_score() {
        var score = SimilarityScore.of(0.85);
        assertThat(score.value()).isEqualTo(0.85);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 0.5, 1.0})
    void accepts_boundary_values(double value) {
        assertThat(SimilarityScore.of(value).value()).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.1, 1.1, -1.0, 2.0})
    void rejects_out_of_range_values(double value) {
        assertThatThrownBy(() -> SimilarityScore.of(value))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void is_above_threshold_when_value_equals_threshold() {
        var score = SimilarityScore.of(0.75);
        assertThat(score.isAboveThreshold(0.75)).isTrue();
    }

    @Test
    void is_not_above_threshold_when_value_below_threshold() {
        var score = SimilarityScore.of(0.5);
        assertThat(score.isAboveThreshold(0.75)).isFalse();
    }
}
