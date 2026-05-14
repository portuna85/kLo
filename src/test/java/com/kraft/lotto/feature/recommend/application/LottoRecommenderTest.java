package com.kraft.lotto.feature.recommend.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kraft.lotto.feature.recommend.domain.BirthdayBiasRule;
import com.kraft.lotto.feature.recommend.domain.ExclusionRule;
import com.kraft.lotto.feature.winningnumber.domain.LottoCombination;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("로또 추천기 테스트")
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
    @DisplayName("요청된 개수만큼 추천한다")
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
    @DisplayName("추천된 번호 조합은 중복되지 않는다")
    void recommendationsAreUnique() {
        List<LottoCombination> result = recommender(List.of(), 7L, LARGE_BUDGET).recommend(10);

        assertThat(result).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("모든 조합이 제외되면 타임아웃 예외를 던진다")
    void throwsTimeoutWhenAllCombinationsExcluded() {
        LottoRecommender recommender = recommender(List.of(excludeAll()), 0L, 100);

        assertThatThrownBy(() -> recommender.recommend(1))
                .isInstanceOf(RecommendGenerationTimeoutException.class);
    }

    @Test
    @DisplayName("제외 규칙이 적용되어 제외된 조합은 포함되지 않는다")
    void rulesAreAppliedAndExcludedCombinationsNotIncluded() {
        // 嶺뚮ㅄ維獄??뺢퀡???먯쾸? 31 ??袁⑤┃???브퀗?ч뜮?????戮곕뇶??濡ル츎 ?잙?裕????⑤챷??
        List<LottoCombination> result = recommender(List.of(new BirthdayBiasRule()), 123L, LARGE_BUDGET)
                .recommend(10);

        result.forEach(c -> assertThat(c.numbers().stream().anyMatch(n -> n > 31)).isTrue());
    }

    @Test
    @DisplayName("추천 시 reject 메트릭을 기록한다")
    void recordsRejectionMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        LottoRecommender recommender = new LottoRecommender(
                List.of(),
                new RandomLottoNumberGenerator(new Random(42L)),
                LARGE_BUDGET,
                registry
        );

        recommender.recommend(5);

        assertThat(registry.find("kraft.recommend.rejection.rate").summary()).isNotNull();
        assertThat(registry.find("kraft.recommend.rejection.count").counter()).isNotNull();
        assertThat(registry.find("kraft.recommend.attempt.count").counter()).isNotNull();
    }
}

