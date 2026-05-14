package com.kraft.lotto.feature.winningnumber.web.dto;

import java.util.List;

/**
 * 수집 결과 요약.
 * - {@code collected}: 새로 저장된 회차 수
 * - {@code skipped}: 이미 최신 저장 회차 이하이거나 이미 존재하여 건너뛴 회차 수
 * - {@code failed}: 데이터 검증/저장 실패 회차 수 (외부 API 미추첨 응답은 종료 신호로 처리되어 포함되지 않음)
 * - {@code latestRound}: 수집 완료 후 DB에 존재하는 최신 회차 (없으면 0)
 * - {@code failedRounds}: 저장 실패가 발생한 회차 목록
 * - {@code truncated}: ABSOLUTE_MAX_ROUNDS_PER_CALL 제한에 걸려 중단되었는지 여부
 * - {@code nextRound}: 제한에 걸린 경우 다음 수집 시작 가능 회차(없으면 null)
 */
public record CollectResponse(
        int collected,
        int updated,
        int skipped,
        int failed,
        int latestRound,
        List<Integer> failedRounds,
        boolean truncated,
        Integer nextRound,
        boolean notDrawn,
        boolean dataChanged
) {
    public CollectResponse {
        failedRounds = List.copyOf(failedRounds);
    }

    public CollectResponse(int collected, int skipped, int failed, int latestRound) {
        this(collected, 0, skipped, failed, latestRound, List.of(), false, null, false, collected > 0);
    }

    public CollectResponse(int collected, int skipped, int failed, int latestRound, List<Integer> failedRounds) {
        this(collected, 0, skipped, failed, latestRound, failedRounds, false, null, false, collected > 0);
    }

    public CollectResponse(int collected,
                           int skipped,
                           int failed,
                           int latestRound,
                           List<Integer> failedRounds,
                           boolean truncated,
                           Integer nextRound,
                           boolean notDrawn) {
        this(collected, 0, skipped, failed, latestRound, failedRounds, truncated, nextRound, notDrawn, collected > 0);
    }
}
