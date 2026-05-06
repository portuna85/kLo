package com.kraft.lotto.feature.recommend.application;

import com.kraft.lotto.feature.recommend.domain.ExclusionRule;
import com.kraft.lotto.feature.winningnumber.domain.LottoCombination;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 무작위 6개 번호 조합을 생성하고 ExclusionRule을 적용해 유효한 조합을 반환한다.
 *
 * - 추천 결과는 1~10개 범위를 호출자가 보장한다(검증은 RecommendService에서 수행).
 * - 최대 시도 횟수를 초과하면 {@link RecommendGenerationTimeoutException}을 던진다.
 * - 본 클래스는 Spring/Web/JPA 의존성 없이 단위 테스트 가능하도록 설계되었다.
 *
 * 본 추천기는 당첨 확률을 높이는 도구가 아니라, 인기/중복 패턴 등 편향된
 * 조합을 회피하기 위한 도구이다.
 */
public class LottoRecommender {

    private final List<ExclusionRule> rules;
    private final LottoNumberGenerator numberGenerator;
    private final int maxAttempts;

    public LottoRecommender(List<ExclusionRule> rules, LottoNumberGenerator numberGenerator, int maxAttempts) {
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
    }

    public LottoRecommender(List<ExclusionRule> rules, Random random, int maxAttempts) {
        this(rules, new RandomLottoNumberGenerator(random), maxAttempts);
    }

    public List<LottoCombination> recommend(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive: " + count);
        }
        List<LottoCombination> result = new ArrayList<>(count);
        Set<LottoCombination> emitted = new LinkedHashSet<>();
        int attempts = 0;
        while (result.size() < count) {
            if (attempts >= maxAttempts) {
                throw new RecommendGenerationTimeoutException(
                        "추천 조합 생성 시도 한도(" + maxAttempts + ")를 초과했습니다.");
            }
            attempts++;
            LottoCombination candidate = numberGenerator.generate();
            if (emitted.contains(candidate)) {
                continue;
            }
            if (isExcluded(candidate)) {
                continue;
            }
            emitted.add(candidate);
            result.add(candidate);
        }
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

}
