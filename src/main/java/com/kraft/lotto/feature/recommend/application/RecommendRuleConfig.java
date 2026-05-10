package com.kraft.lotto.feature.recommend.application;

import com.kraft.lotto.feature.recommend.domain.ArithmeticSequenceRule;
import com.kraft.lotto.feature.recommend.domain.BirthdayBiasRule;
import com.kraft.lotto.feature.recommend.domain.ExclusionRule;
import com.kraft.lotto.feature.recommend.domain.LongRunRule;
import com.kraft.lotto.feature.recommend.domain.PastWinningCache;
import com.kraft.lotto.feature.recommend.domain.PastWinningRule;
import com.kraft.lotto.feature.recommend.domain.SingleDecadeRule;
import com.kraft.lotto.infra.config.KraftRecommendProperties;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 추천 규칙 도메인 객체들을 Spring 빈으로 등록한다.
 * domain 계층은 Spring 의존성을 가지지 않으므로,
 * 본 설정 클래스에서 명시적으로 빈을 선언한다.
 *
 * 규칙 적용 순서는 {@link #exclusionRules}의 List 순서를 따른다.
 */
@Configuration
public class RecommendRuleConfig {

    @Bean
    public PastWinningCache pastWinningCache() {
        return new PastWinningCache();
    }

    @Bean
    public PastWinningRule pastWinningRule(PastWinningCache cache) {
        return new PastWinningRule(cache);
    }

    @Bean
    public BirthdayBiasRule birthdayBiasRule(KraftRecommendProperties properties) {
        return new BirthdayBiasRule(properties.rules().birthdayThreshold());
    }

    @Bean
    public ArithmeticSequenceRule arithmeticSequenceRule() {
        return new ArithmeticSequenceRule();
    }

    @Bean
    public LongRunRule longRunRule(KraftRecommendProperties properties) {
        return new LongRunRule(properties.rules().longRunThreshold());
    }

    @Bean
    public SingleDecadeRule singleDecadeRule(KraftRecommendProperties properties) {
        return new SingleDecadeRule(properties.rules().decadeThreshold());
    }

    /**
     * 추천 규칙 적용 순서를 명시적으로 정의한다.
     * 가벼운 패턴 규칙 → 비용이 큰 캐시 검색(PastWinningRule) 순으로 배치한다.
     */
    @Bean
    public List<ExclusionRule> exclusionRules(BirthdayBiasRule birthdayBiasRule,
                                              ArithmeticSequenceRule arithmeticSequenceRule,
                                              LongRunRule longRunRule,
                                              SingleDecadeRule singleDecadeRule,
                                              PastWinningRule pastWinningRule) {
        return List.of(
                birthdayBiasRule,
                arithmeticSequenceRule,
                longRunRule,
                singleDecadeRule,
                pastWinningRule
        );
    }
}
