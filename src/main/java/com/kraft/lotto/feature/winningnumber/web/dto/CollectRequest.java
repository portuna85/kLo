package com.kraft.lotto.feature.winningnumber.web.dto;

import jakarta.validation.constraints.Min;

/**
 * POST /api/admin/winning-numbers/collect 요청 본문.
 * <ul>
 *   <li>{@code targetRound}이 지정되면 latestStored+1 ~ targetRound 범위까지 수집한다.</li>
 *   <li>지정되지 않으면 외부 API가 미추첨 응답을 줄 때까지 순차 수집한다.</li>
 * </ul>
 */
public record CollectRequest(
        @Min(value = 1, message = "targetRound는 1 이상이어야 합니다.")
        Integer targetRound
) {
}
