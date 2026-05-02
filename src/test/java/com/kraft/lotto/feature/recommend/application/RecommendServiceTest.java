package com.kraft.lotto.feature.recommend.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kraft.lotto.feature.recommend.domain.BirthdayBiasRule;
import com.kraft.lotto.feature.recommend.domain.ExclusionRule;
import com.kraft.lotto.infra.config.KraftRecommendProperties;
import com.kraft.lotto.support.BusinessException;
import com.kraft.lotto.support.ErrorCode;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class RecommendServiceTest {

    private final KraftRecommendProperties props = new KraftRecommendProperties(100_000);

    private RecommendService service(List<ExclusionRule> rules) {
        return new RecommendService(rules, props, new Random(1L));
    }

    @Test
    void count가_0이면_LOTTO_INVALID_COUNT() {
        var service = service(List.of());
        assertThatThrownBy(() -> service.recommend(0))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LOTTO_INVALID_COUNT);
    }

    @Test
    void count가_11이면_LOTTO_INVALID_COUNT() {
        var service = service(List.of());
        assertThatThrownBy(() -> service.recommend(11))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LOTTO_INVALID_COUNT);
    }

    @Test
    void 정상_count는_요청한_개수만큼_조합을_반환한다() {
        var service = service(List.of(new BirthdayBiasRule()));
        var response = service.recommend(5);
        assertThat(response.combinations()).hasSize(5);
    }

    @Test
    void 모든_조합이_제외되면_LOTTO_GENERATION_TIMEOUT() {
        ExclusionRule excludeAll = new ExclusionRule() {
            @Override public boolean shouldExclude(com.kraft.lotto.feature.winningnumber.domain.LottoCombination c) { return true; }
            @Override public String reason() { return "x"; }
        };
        var service = new RecommendService(List.of(excludeAll), new KraftRecommendProperties(50), new Random(1L));
        assertThatThrownBy(() -> service.recommend(1))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LOTTO_GENERATION_TIMEOUT);
    }

    @Test
    void rules는_등록된_규칙의_이름과_사유를_반환한다() {
        var service = service(List.of(new BirthdayBiasRule()));
        var rules = service.rules();
        assertThat(rules).hasSize(1);
        assertThat(rules.get(0).name()).isEqualTo("BirthdayBiasRule");
        assertThat(rules.get(0).reason()).contains("31 이하");
    }
}
