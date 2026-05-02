package com.kraft.lotto.feature.recommend.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kraft.lotto.feature.recommend.domain.ExclusionRule;
import com.kraft.lotto.feature.winningnumber.domain.LottoCombination;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class LottoRecommenderTest {

    @Test
    void 정상적으로_요청한_개수만큼_추천한다() {
        LottoRecommender recommender = new LottoRecommender(List.of(), new Random(42L), 100_000);
        List<LottoCombination> result = recommender.recommend(5);
        assertThat(result).hasSize(5);
        // 각 조합은 6개의 정렬된 고유 번호
        result.forEach(c -> {
            assertThat(c.numbers()).hasSize(6);
            assertThat(c.numbers()).isSorted();
            assertThat(c.numbers()).doesNotHaveDuplicates();
        });
    }

    @Test
    void 추천_결과는_서로_중복되지_않는다() {
        LottoRecommender recommender = new LottoRecommender(List.of(), new Random(7L), 100_000);
        List<LottoCombination> result = recommender.recommend(10);
        assertThat(result).doesNotHaveDuplicates();
    }

    @Test
    void 모든_조합을_제외하는_규칙이_있으면_타임아웃_예외를_던진다() {
        ExclusionRule excludeAll = new ExclusionRule() {
            @Override public boolean shouldExclude(LottoCombination combination) { return true; }
            @Override public String reason() { return "always"; }
        };
        LottoRecommender recommender = new LottoRecommender(List.of(excludeAll), new Random(0L), 100);
        assertThatThrownBy(() -> recommender.recommend(1))
                .isInstanceOf(RecommendGenerationTimeoutException.class);
    }

    @Test
    void 규칙은_순서대로_적용되며_제외된_조합은_결과에_포함되지_않는다() {
        // 모든 번호가 31 이하인 조합을 제외하는 규칙 적용
        ExclusionRule birthday = new com.kraft.lotto.feature.recommend.domain.BirthdayBiasRule();
        LottoRecommender recommender = new LottoRecommender(List.of(birthday), new Random(123L), 100_000);
        List<LottoCombination> result = recommender.recommend(10);
        result.forEach(c -> assertThat(c.numbers().stream().anyMatch(n -> n > 31)).isTrue());
    }
}
