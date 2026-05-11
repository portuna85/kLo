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

    @DisplayName("테스트")
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
    @DisplayName("테스트")
    void throwsInvalidCountWhenCountIsZero() {
        var service = service(List.of());

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.recommend(0))
                .extracting(BusinessException::getErrorCode)
                .isEqualTo(ErrorCode.LOTTO_INVALID_COUNT);
    }

    @Test
    @DisplayName("테스트")
    void throwsInvalidCountWhenCountIsEleven() {
        var service = service(List.of());

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.recommend(11))
                .extracting(BusinessException::getErrorCode)
                .isEqualTo(ErrorCode.LOTTO_INVALID_COUNT);
    }

    @Test
    @DisplayName("테스트")
    void returnsOneCombinationWhenCountIsOne() {
        var service = service(List.of(new BirthdayBiasRule()));

        var response = service.recommend(1);

        assertThat(response.combinations()).hasSize(1);
    }

    @Test
    @DisplayName("테스트")
    void returnsTenCombinationsWhenCountIsTen() {
        var service = service(List.of(new BirthdayBiasRule()));

        var response = service.recommend(10);

        assertThat(response.combinations()).hasSize(10);
    }

    @Test
    @DisplayName("테스트")
    void returnsRequestedNumberOfCombinations() {
        var service = service(List.of(new BirthdayBiasRule()));

        var response = service.recommend(5);

        assertThat(response.combinations()).hasSize(5);
    }

    @Test
    @DisplayName("테스트")
    void throwsGenerationTimeoutWhenAllExcluded() {
        List<ExclusionRule> rules = List.of(excludeAll());
        var service = new RecommendService(rules, new LottoRecommender(rules, new Random(FIXED_SEED), 50));

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.recommend(1))
                .extracting(BusinessException::getErrorCode)
                .isEqualTo(ErrorCode.LOTTO_GENERATION_TIMEOUT);
    }

    @Test
    @DisplayName("테스트")
    void rulesReturnsRegisteredRuleNamesAndReasons() {
        var service = service(List.of(new BirthdayBiasRule()));

        var rules = service.rules();

        assertThat(rules).hasSize(1);
        assertThat(rules.get(0).name()).isEqualTo("BirthdayBiasRule");
        assertThat(rules.get(0).reason()).isNotBlank();
    }
}

