package com.kraft.lotto.feature.winningnumber.event;

import java.time.Instant;

/**
 * 당첨번호 수집 완료 후 발행되는 이벤트.
 * PastWinningCache 등 후속 컴포넌트의 갱신 트리거로 사용된다.
 */
public record WinningNumbersCollectedEvent(int collected, int skipped, int failed, Instant occurredAt) {

    public static WinningNumbersCollectedEvent of(int collected, int skipped, int failed) {
        return new WinningNumbersCollectedEvent(collected, skipped, failed, Instant.now());
    }
}
