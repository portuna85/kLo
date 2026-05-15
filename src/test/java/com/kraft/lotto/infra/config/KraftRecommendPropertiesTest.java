package com.kraft.lotto.infra.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("KraftRecommendProperties")
class KraftRecommendPropertiesTest {

    @Test
    @DisplayName("유효한 규칙 범위면 생성에 성공한다")
    void acceptsValidRules() {
        assertThatCode(() -> new KraftRecommendProperties(
                5000,
                10_000,
                1_000,
                new KraftRecommendProperties.Rules(31, 5, 5)
        )).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("decade-threshold가 1이면 생성에 실패한다")
    void rejectsTooSmallDecadeThreshold() {
        assertThatThrownBy(() -> new KraftRecommendProperties(
                5000,
                10_000,
                1_000,
                new KraftRecommendProperties.Rules(31, 5, 1)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("max-attempts가 0 이하이면 생성에 실패한다")
    void rejectsNonPositiveMaxAttempts() {
        assertThatThrownBy(() -> new KraftRecommendProperties(
                0,
                10_000,
                1_000,
                new KraftRecommendProperties.Rules(31, 5, 5)
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
