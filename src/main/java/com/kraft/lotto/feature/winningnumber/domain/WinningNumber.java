package com.kraft.lotto.feature.winningnumber.domain;

import java.time.LocalDate;
import java.util.Objects;

public record WinningNumber(
        int round,
        LocalDate drawDate,
        LottoCombination combination,
        int bonusNumber,
        long firstPrize,
        int firstWinners,
        long totalSales
) {

    public WinningNumber {
        if (round <= 0) {
            throw new IllegalArgumentException("회차는 0보다 커야 합니다: " + round);
        }
        Objects.requireNonNull(drawDate, "추첨일(drawDate)은 null일 수 없습니다.");
        Objects.requireNonNull(combination, "당첨 조합(combination)은 null일 수 없습니다.");
        if (bonusNumber < LottoCombination.MIN_NUMBER || bonusNumber > LottoCombination.MAX_NUMBER) {
            throw new IllegalArgumentException(
                    "보너스 번호는 " + LottoCombination.MIN_NUMBER + "~" + LottoCombination.MAX_NUMBER + " 범위여야 합니다: " + bonusNumber);
        }
        if (combination.contains(bonusNumber)) {
            throw new IllegalArgumentException("보너스 번호는 본번호와 중복될 수 없습니다: " + bonusNumber);
        }
        if (firstPrize < 0) {
            throw new IllegalArgumentException("1등 당첨금(firstPrize)은 음수일 수 없습니다: " + firstPrize);
        }
        if (firstWinners < 0) {
            throw new IllegalArgumentException("1등 당첨자수(firstWinners)는 음수일 수 없습니다: " + firstWinners);
        }
        if (totalSales < 0) {
            throw new IllegalArgumentException("총 판매액(totalSales)은 음수일 수 없습니다: " + totalSales);
        }
    }
}
