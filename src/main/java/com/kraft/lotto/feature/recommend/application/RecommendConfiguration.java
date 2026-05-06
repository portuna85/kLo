package com.kraft.lotto.feature.recommend.application;

import com.kraft.lotto.feature.recommend.domain.ExclusionRule;
import com.kraft.lotto.infra.config.KraftRecommendProperties;
import java.security.SecureRandom;
import java.util.List;
import java.util.Random;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RecommendConfiguration {

    @Bean
    Random lottoRandom() {
        return new SecureRandom();
    }

    @Bean
    LottoNumberGenerator lottoNumberGenerator(Random lottoRandom) {
        return new RandomLottoNumberGenerator(lottoRandom);
    }

    @Bean
    LottoRecommender lottoRecommender(List<ExclusionRule> rules,
                                      LottoNumberGenerator numberGenerator,
                                      KraftRecommendProperties properties) {
        return new LottoRecommender(rules, numberGenerator, properties.maxAttempts());
    }
}
