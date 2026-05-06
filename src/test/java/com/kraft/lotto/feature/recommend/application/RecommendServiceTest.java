package com.kraft.lotto.feature.recommend.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.kraft.lotto.feature.recommend.domain.BirthdayBiasRule;
import com.kraft.lotto.feature.recommend.domain.ExclusionRule;
import com.kraft.lotto.feature.winningnumber.domain.LottoCombination;
import com.kraft.lotto.support.BusinessException;
import com.kraft.lotto.support.ErrorCode;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RecommendService")
class RecommendServiceTest {

    private static final long FIXED_SEED = 1L;

    private RecommendService service(List<ExclusionRule> rules) {
        return new RecommendService(rules, new LottoRecommender(rules, new Random(FIXED_SEED), 100_000));
    }

    private static ExclusionRule excludeAll() {
        return new ExclusionRule() {
            @Override public boolean shouldExclude(LottoCombination combination) { return true; }
            @Override public String reason() { return "always-exclude"; }
        };
    }

    @Test
    @DisplayName("count 가 0 이면 LOTTO_INVALID_COUNT 예외가 발생한다")
    void throwsInvalidCountWhenCountIsZero() {
        var service = service(List.of());

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.recommend(0))
                .extracting(BusinessException::getErrorCode)
                .isEqualTo(ErrorCode.LOTTO_INVALID_COUNT);
    }

    @Test
    @DisplayName("count 가 11 이면 LOTTO_INVALID_COUNT 예외가 발생한다")
    void throwsInvalidCountWhenCountIsEleven() {
        var service = service(List.of());

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.recommend(11))
                .extracting(BusinessException::getErrorCode)
                .isEqualTo(ErrorCode.LOTTO_INVALID_COUNT);
    }

    @Test
    @DisplayName("count 가 1 이면 조합 1개를 반환한다")
    void returnsOneCombinationWhenCountIsOne() {
        var service = service(List.of(new BirthdayBiasRule()));

        var response = service.recommend(1);

        assertThat(response.combinations()).hasSize(1);
    }

    @Test
    @DisplayName("count 가 10 이면 조합 10개를 반환한다")
    void returnsTenCombinationsWhenCountIsTen() {
        var service = service(List.of(new BirthdayBiasRule()));

        var response = service.recommend(10);

        assertThat(response.combinations()).hasSize(10);
    }

    @Test
    @DisplayName("정상 count 는 요청한 개수만큼 조합을 반환한다")
    void returnsRequestedNumberOfCombinations() {
        var service = service(List.of(new BirthdayBiasRule()));

        var response = service.recommend(5);

        assertThat(response.combinations()).hasSize(5);
    }

    @Test
    @DisplayName("모든 조합이 제외되면 LOTTO_GENERATION_TIMEOUT 예외가 발생한다")
    void throwsGenerationTimeoutWhenAllExcluded() {
        List<ExclusionRule> rules = List.of(excludeAll());
        var service = new RecommendService(rules, new LottoRecommender(rules, new Random(FIXED_SEED), 50));

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.recommend(1))
                .extracting(BusinessException::getErrorCode)
                .isEqualTo(ErrorCode.LOTTO_GENERATION_TIMEOUT);
    }

    @Test
    @DisplayName("rules 는 등록된 규칙의 이름과 사유를 반환한다")
    void rulesReturnsRegisteredRuleNamesAndReasons() {
        var service = service(List.of(new BirthdayBiasRule()));

        var rules = service.rules();

        assertThat(rules).hasSize(1);
        assertThat(rules.get(0).name()).isEqualTo("BirthdayBiasRule");
        assertThat(rules.get(0).reason()).contains("31 이하");
    }
}

