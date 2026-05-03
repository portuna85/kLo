package com.kraft.lotto.feature.recommend.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kraft.lotto.feature.recommend.domain.BirthdayBiasRule;
import com.kraft.lotto.feature.recommend.domain.ExclusionRule;
import com.kraft.lotto.feature.winningnumber.domain.LottoCombination;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LottoRecommender")
class LottoRecommenderTest {

    private static final int LARGE_BUDGET = 100_000;

    private static LottoRecommender recommender(List<ExclusionRule> rules, long seed, int maxAttempts) {
        return new LottoRecommender(rules, new Random(seed), maxAttempts);
    }

    private static ExclusionRule excludeAll() {
        return new ExclusionRule() {
            @Override public boolean shouldExclude(LottoCombination combination) { return true; }
            @Override public String reason() { return "always"; }
        };
    }

    @Test
    @DisplayName("정상적으로 요청한 개수만큼 추천한다")
    void recommendsRequestedCount() {
        List<LottoCombination> result = recommender(List.of(), 42L, LARGE_BUDGET).recommend(5);

        assertThat(result).hasSize(5);
        // 각 조합은 6개의 정렬된 고유 번호
        result.forEach(c -> {
            assertThat(c.numbers()).hasSize(6);
            assertThat(c.numbers()).isSorted();
            assertThat(c.numbers()).doesNotHaveDuplicates();
        });
    }

    @Test
    @DisplayName("추천 결과는 서로 중복되지 않는다")
    void recommendationsAreUnique() {
        List<LottoCombination> result = recommender(List.of(), 7L, LARGE_BUDGET).recommend(10);

        assertThat(result).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("모든 조합을 제외하는 규칙이 있으면 타임아웃 예외를 던진다")
    void throwsTimeoutWhenAllCombinationsExcluded() {
        LottoRecommender recommender = recommender(List.of(excludeAll()), 0L, 100);

        assertThatThrownBy(() -> recommender.recommend(1))
                .isInstanceOf(RecommendGenerationTimeoutException.class);
    }

    @Test
    @DisplayName("규칙은 순서대로 적용되며 제외된 조합은 결과에 포함되지 않는다")
    void rulesAreAppliedAndExcludedCombinationsNotIncluded() {
        // 모든 번호가 31 이하인 조합을 제외하는 규칙 적용
        List<LottoCombination> result = recommender(List.of(new BirthdayBiasRule()), 123L, LARGE_BUDGET)
                .recommend(10);

        result.forEach(c -> assertThat(c.numbers().stream().anyMatch(n -> n > 31)).isTrue());
    }
}

