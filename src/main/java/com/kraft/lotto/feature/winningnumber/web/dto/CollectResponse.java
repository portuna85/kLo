package com.kraft.lotto.feature.winningnumber.web.dto;

/**
 * 수집 결과 요약.
 * - {@code collected}: 새로 저장된 회차 수
 * - {@code skipped}: 이미 존재하여 건너뛴 회차 수
 * - {@code failed}: 데이터 검증/저장 실패 회차 수 (외부 API 미추첨 응답은 종료 신호로 처리되어 포함되지 않음)
 * - {@code latestRound}: 수집 완료 후 DB에 존재하는 최신 회차 (없으면 0)
 */
public record CollectResponse(int collected, int skipped, int failed, int latestRound) {
}
