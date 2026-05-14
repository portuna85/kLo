package com.kraft.lotto.feature.recommend.application;

import com.kraft.lotto.feature.recommend.domain.ExclusionRule;
import com.kraft.lotto.infra.config.KraftRecommendProperties;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Random;
import java.util.SplittableRandom;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RecommendConfiguration {

    @Bean
    Random lottoRandom() {
        return Random.from(new SplittableRandom());
    }

    @Bean
    LottoNumberGenerator lottoNumberGenerator(Random lottoRandom, KraftRecommendProperties properties) {
        KraftRecommendProperties.Rules rules = properties.rules();
        return new ConstraintAwareLottoNumberGenerator(
                lottoRandom,
                rules.birthdayThreshold(),
                rules.longRunThreshold(),
                rules.decadeThreshold()
        );
    }

    @Bean
    LottoRecommender lottoRecommender(List<ExclusionRule> rules,
                                      LottoNumberGenerator numberGenerator,
                                      KraftRecommendProperties properties,
                                      ObjectProvider<MeterRegistry> meterRegistryProvider) {
        return new LottoRecommender(
                rules,
                numberGenerator,
                properties.maxAttempts(),
                meterRegistryProvider.getIfAvailable()
        );
    }
}
