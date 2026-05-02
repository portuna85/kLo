package com.kraft.lotto.feature.recommend.application;

import com.kraft.lotto.feature.recommend.domain.ExclusionRule;
import com.kraft.lotto.feature.recommend.web.dto.CombinationDto;
import com.kraft.lotto.feature.recommend.web.dto.RecommendResponse;
import com.kraft.lotto.feature.recommend.web.dto.RuleDto;
import com.kraft.lotto.infra.config.KraftRecommendProperties;
import com.kraft.lotto.support.BusinessException;
import com.kraft.lotto.support.ErrorCode;
import java.security.SecureRandom;
import java.util.List;
import java.util.Random;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 추천 API의 application 서비스.
 * - count 입력 검증 (1~10) 후 BusinessException 변환
 * - LottoRecommender 호출 및 도메인 예외 → BusinessException 변환
 * - 규칙 목록 DTO 변환
 *
 * 본 서비스는 당첨 확률을 높이지 않으며, 편향된 조합 회피만을 목적으로 한다.
 */
@Service
public class RecommendService {

    static final int MIN_COUNT = 1;
    static final int MAX_COUNT = 10;

    private final List<ExclusionRule> rules;
    private final int maxAttempts;
    private final Random random;

    @Autowired
    public RecommendService(List<ExclusionRule> rules, KraftRecommendProperties properties) {
        this(rules, properties, new SecureRandom());
    }

    // package-private constructor for tests to inject deterministic Random.
    RecommendService(List<ExclusionRule> rules, KraftRecommendProperties properties, Random random) {
        this.rules = List.copyOf(rules);
        this.maxAttempts = properties.maxAttempts();
        this.random = random;
    }

    public RecommendResponse recommend(int count) {
        if (count < MIN_COUNT || count > MAX_COUNT) {
            throw new BusinessException(ErrorCode.LOTTO_INVALID_COUNT);
        }
        LottoRecommender recommender = new LottoRecommender(rules, random, maxAttempts);
        try {
            var combinations = recommender.recommend(count).stream()
                    .map(c -> new CombinationDto(c.numbers()))
                    .toList();
            return new RecommendResponse(combinations);
        } catch (RecommendGenerationTimeoutException ex) {
            throw new BusinessException(ErrorCode.LOTTO_GENERATION_TIMEOUT, ex.getMessage(), ex);
        }
    }

    public List<RuleDto> rules() {
        return rules.stream()
                .map(r -> new RuleDto(r.name(), r.reason()))
                .toList();
    }
}
