package com.kraft.lotto.feature.recommend.application;

/**
 * LottoRecommender가 최대 시도 횟수 안에 충분한 추천 조합을 생성하지 못했을 때 발생.
 * application 계층에서 BusinessException(LOTTO_GENERATION_TIMEOUT)으로 변환된다.
 */
public class RecommendGenerationTimeoutException extends RuntimeException {

    public RecommendGenerationTimeoutException(String message) {
        super(message);
    }
}
