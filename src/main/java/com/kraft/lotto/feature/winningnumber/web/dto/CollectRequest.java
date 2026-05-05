package com.kraft.lotto.feature.winningnumber.web.dto;

import com.kraft.lotto.feature.winningnumber.web.validation.ValidRound;

/**
 * POST /api/admin/winning-numbers/refresh 요청 본문.
 * <ul>
 *   <li>{@code targetRound}이 지정되면 latestStored+1 ~ targetRound 범위까지 수집한다.</li>
 *   <li>지정되지 않으면 외부 API가 미추첨 응답을 줄 때까지 순차 수집한다.</li>
 * </ul>
 */
public record CollectRequest(
        @ValidRound(allowNull = true, message = "targetRound는 숫자 형식의 유효한 회차 범위여야 합니다.")
        String targetRound
) {
}
