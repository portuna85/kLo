package com.kraft.lotto.feature.recommend.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * POST /api/recommend 요청 본문.
 * count 미지정 시 5를 기본값으로 사용한다.
 */
public record RecommendRequest(
        @Min(value = 1, message = "추천 개수는 1~10 사이여야 합니다.")
        @Max(value = 10, message = "추천 개수는 1~10 사이여야 합니다.")
        Integer count
) {
    public static final int DEFAULT_COUNT = 5;

    public int countOrDefault() {
        return count == null ? DEFAULT_COUNT : count;
    }
}
