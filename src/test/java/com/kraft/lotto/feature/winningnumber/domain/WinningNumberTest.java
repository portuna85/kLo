package com.kraft.lotto.feature.winningnumber.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("WinningNumber 도메인")
class WinningNumberTest {

    private static final LottoCombination COMBO = LottoCombination.of(1, 7, 13, 22, 34, 45);
    private static final LocalDate DRAW = LocalDate.of(2026, 5, 1);

    @Test
    @DisplayName("정상 입력으로 WinningNumber 를 생성한다")
    void createsValidWinningNumber() {
        WinningNumber wn = new WinningNumber(1100, DRAW, COMBO, 8, 1_500_000_000L, 5, 100_000_000_000L);
        assertThat(wn.round()).isEqualTo(1100);
        assertThat(wn.drawDate()).isEqualTo(DRAW);
        assertThat(wn.combination()).isEqualTo(COMBO);
        assertThat(wn.bonusNumber()).isEqualTo(8);
    }

    @Test
    @DisplayName("0 이하의 회차는 거부된다")
    void rejectsNonPositiveRound() {
        assertThatThrownBy(() -> new WinningNumber(0, DRAW, COMBO, 8, 0L, 0, 0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WinningNumber(-1, DRAW, COMBO, 8, 0L, 0, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("drawDate 가 null 이면 거부된다")
    void rejectsNullDrawDate() {
        assertThatThrownBy(() -> new WinningNumber(1, null, COMBO, 8, 0L, 0, 0L))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("combination 이 null 이면 거부된다")
    void rejectsNullCombination() {
        assertThatThrownBy(() -> new WinningNumber(1, DRAW, null, 8, 0L, 0, 0L))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("보너스 번호가 범위를 벗어나면 거부된다")
    void rejectsBonusOutOfRange() {
        assertThatThrownBy(() -> new WinningNumber(1, DRAW, COMBO, 0, 0L, 0, 0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WinningNumber(1, DRAW, COMBO, 46, 0L, 0, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("보너스 번호가 본번호와 중복되면 거부된다")
    void rejectsBonusDuplicatedWithMainNumbers() {
        assertThatThrownBy(() -> new WinningNumber(1, DRAW, COMBO, 13, 0L, 0, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("음수 1등 당첨금은 거부된다")
    void rejectsNegativeFirstPrize() {
        assertThatThrownBy(() -> new WinningNumber(1, DRAW, COMBO, 8, -1L, 0, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("음수 1등 당첨자 수는 거부된다")
    void rejectsNegativeFirstWinners() {
        assertThatThrownBy(() -> new WinningNumber(1, DRAW, COMBO, 8, 0L, -1, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("음수 총판매액은 거부된다")
    void rejectsNegativeTotalSales() {
        assertThatThrownBy(() -> new WinningNumber(1, DRAW, COMBO, 8, 0L, 0, -1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("0 인 금액 값은 허용된다")
    void allowsZeroFinancialValues() {
        WinningNumber wn = new WinningNumber(1, DRAW, COMBO, 8, 0L, 0, 0L);
        assertThat(wn.firstPrize()).isZero();
        assertThat(wn.firstWinners()).isZero();
        assertThat(wn.totalSales()).isZero();
    }
}

