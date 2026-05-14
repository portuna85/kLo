package com.kraft.lotto.feature.recommend.application;

import com.kraft.lotto.feature.recommend.domain.ExclusionRule;
import com.kraft.lotto.feature.winningnumber.domain.LottoCombination;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class LottoRecommender {

    private final List<ExclusionRule> rules;
    private final LottoNumberGenerator numberGenerator;
    private final int maxAttempts;
    private final MeterRegistry meterRegistry;

    public LottoRecommender(List<ExclusionRule> rules, LottoNumberGenerator numberGenerator, int maxAttempts) {
        this(rules, numberGenerator, maxAttempts, null);
    }

    public LottoRecommender(List<ExclusionRule> rules,
                            LottoNumberGenerator numberGenerator,
                            int maxAttempts,
                            MeterRegistry meterRegistry) {
        if (rules == null) {
            throw new IllegalArgumentException("rules must not be null");
        }
        if (numberGenerator == null) {
            throw new IllegalArgumentException("numberGenerator must not be null");
        }
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be positive: " + maxAttempts);
        }
        this.rules = List.copyOf(rules);
        this.numberGenerator = numberGenerator;
        this.maxAttempts = maxAttempts;
        this.meterRegistry = meterRegistry;
    }

    public LottoRecommender(List<ExclusionRule> rules, Random random, int maxAttempts) {
        this(rules, new RandomLottoNumberGenerator(random), maxAttempts, null);
    }

    public List<LottoCombination> recommend(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive: " + count);
        }
        List<LottoCombination> result = new ArrayList<>(count);
        Set<LottoCombination> emitted = new LinkedHashSet<>();
        int attempts = 0;
        int rejected = 0;
        while (result.size() < count) {
            if (attempts >= maxAttempts) {
                recordRejectionRate(attempts, rejected);
                throw new RecommendGenerationTimeoutException(
                        "recommend generation attempts exceeded (max=" + maxAttempts + ")");
            }
            attempts++;
            LottoCombination candidate = numberGenerator.generate();
            if (emitted.contains(candidate)) {
                rejected++;
                continue;
            }
            if (isExcluded(candidate)) {
                rejected++;
                continue;
            }
            emitted.add(candidate);
            result.add(candidate);
        }
        recordRejectionRate(attempts, rejected);
        return List.copyOf(result);
    }

    private boolean isExcluded(LottoCombination candidate) {
        for (ExclusionRule rule : rules) {
            if (rule.shouldExclude(candidate)) {
                return true;
            }
        }
        return false;
    }

    private void recordRejectionRate(int attempts, int rejected) {
        if (meterRegistry == null || attempts <= 0) {
            return;
        }
        meterRegistry.summary("kraft.recommend.rejection.rate")
                .record((double) rejected / attempts);
        meterRegistry.counter("kraft.recommend.rejection.count").increment(rejected);
        meterRegistry.counter("kraft.recommend.attempt.count").increment(attempts);
    }
}
