package com.kraft.lotto.feature.winningnumber.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class WinningNumberTest {

    private static final LottoCombination COMBO = LottoCombination.of(1, 7, 13, 22, 34, 45);
    private static final LocalDate DRAW = LocalDate.of(2026, 5, 1);

    @Test
    void createsValidWinningNumber() {
        WinningNumber wn = new WinningNumber(1100, DRAW, COMBO, 8, 1_500_000_000L, 5, 100_000_000_000L);
        assertThat(wn.round()).isEqualTo(1100);
        assertThat(wn.drawDate()).isEqualTo(DRAW);
        assertThat(wn.combination()).isEqualTo(COMBO);
        assertThat(wn.bonusNumber()).isEqualTo(8);
    }

    @Test
    void rejectsNonPositiveRound() {
        assertThatThrownBy(() -> new WinningNumber(0, DRAW, COMBO, 8, 0L, 0, 0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WinningNumber(-1, DRAW, COMBO, 8, 0L, 0, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullDrawDate() {
        assertThatThrownBy(() -> new WinningNumber(1, null, COMBO, 8, 0L, 0, 0L))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullCombination() {
        assertThatThrownBy(() -> new WinningNumber(1, DRAW, null, 8, 0L, 0, 0L))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsBonusOutOfRange() {
        assertThatThrownBy(() -> new WinningNumber(1, DRAW, COMBO, 0, 0L, 0, 0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WinningNumber(1, DRAW, COMBO, 46, 0L, 0, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBonusDuplicatedWithMainNumbers() {
        assertThatThrownBy(() -> new WinningNumber(1, DRAW, COMBO, 13, 0L, 0, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeFirstPrize() {
        assertThatThrownBy(() -> new WinningNumber(1, DRAW, COMBO, 8, -1L, 0, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeFirstWinners() {
        assertThatThrownBy(() -> new WinningNumber(1, DRAW, COMBO, 8, 0L, -1, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeTotalSales() {
        assertThatThrownBy(() -> new WinningNumber(1, DRAW, COMBO, 8, 0L, 0, -1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allowsZeroFinancialValues() {
        WinningNumber wn = new WinningNumber(1, DRAW, COMBO, 8, 0L, 0, 0L);
        assertThat(wn.firstPrize()).isZero();
        assertThat(wn.firstWinners()).isZero();
        assertThat(wn.totalSales()).isZero();
    }
}
