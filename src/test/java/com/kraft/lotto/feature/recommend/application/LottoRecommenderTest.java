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

    @DisplayName("tests for LottoRecommenderTest")
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
    @DisplayName("recommends requested count")
    void recommendsRequestedCount() {
        List<LottoCombination> result = recommender(List.of(), 42L, LARGE_BUDGET).recommend(5);

        assertThat(result).hasSize(5);
        // ???브퀗?ч뜮??? 6?띠룇裕???筌먲퐣議????μ쪠?? ?뺢퀡???
        result.forEach(c -> {
            assertThat(c.numbers()).hasSize(6);
            assertThat(c.numbers()).isSorted();
            assertThat(c.numbers()).doesNotHaveDuplicates();
        });
    }

    @Test
    @DisplayName("recommendations are unique")
    void recommendationsAreUnique() {
        List<LottoCombination> result = recommender(List.of(), 7L, LARGE_BUDGET).recommend(10);

        assertThat(result).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("throws timeout when all combinations excluded")
    void throwsTimeoutWhenAllCombinationsExcluded() {
        LottoRecommender recommender = recommender(List.of(excludeAll()), 0L, 100);

        assertThatThrownBy(() -> recommender.recommend(1))
                .isInstanceOf(RecommendGenerationTimeoutException.class);
    }

    @Test
    @DisplayName("rules are applied and excluded combinations not included")
    void rulesAreAppliedAndExcludedCombinationsNotIncluded() {
        // 嶺뚮ㅄ維獄??뺢퀡???먯쾸? 31 ??袁⑤┃???브퀗?ч뜮?????戮곕뇶??濡ル츎 ?잙?裕????⑤챷??
        List<LottoCombination> result = recommender(List.of(new BirthdayBiasRule()), 123L, LARGE_BUDGET)
                .recommend(10);

        result.forEach(c -> assertThat(c.numbers().stream().anyMatch(n -> n > 31)).isTrue());
    }
}

