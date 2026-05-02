package com.kraft.lotto.feature.winningnumber.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LottoCombinationTest {

    @Test
    void createsSortedCombination() {
        LottoCombination combo = new LottoCombination(List.of(7, 1, 45, 13, 22, 34));
        assertThat(combo.numbers()).containsExactly(1, 7, 13, 22, 34, 45);
    }

    @Test
    void factoryOfCreatesCombination() {
        LottoCombination combo = LottoCombination.of(1, 2, 3, 4, 5, 6);
        assertThat(combo.numbers()).containsExactly(1, 2, 3, 4, 5, 6);
    }

    @Test
    void rejectsNullList() {
        assertThatThrownBy(() -> new LottoCombination(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsWrongSize() {
        assertThatThrownBy(() -> new LottoCombination(List.of(1, 2, 3, 4, 5)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LottoCombination(List.of(1, 2, 3, 4, 5, 6, 7)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsOutOfRange() {
        assertThatThrownBy(() -> new LottoCombination(List.of(0, 2, 3, 4, 5, 6)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LottoCombination(List.of(1, 2, 3, 4, 5, 46)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsDuplicates() {
        assertThatThrownBy(() -> new LottoCombination(List.of(1, 1, 2, 3, 4, 5)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullElement() {
        List<Integer> withNull = new ArrayList<>();
        withNull.add(1);
        withNull.add(null);
        withNull.add(3);
        withNull.add(4);
        withNull.add(5);
        withNull.add(6);
        assertThatThrownBy(() -> new LottoCombination(withNull))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void numbersListIsImmutable() {
        LottoCombination combo = LottoCombination.of(1, 2, 3, 4, 5, 6);
        assertThatThrownBy(() -> combo.numbers().add(7))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void mutationOfSourceListDoesNotAffectCombination() {
        List<Integer> source = new ArrayList<>(List.of(1, 2, 3, 4, 5, 6));
        LottoCombination combo = new LottoCombination(source);
        source.set(0, 45);
        assertThat(combo.numbers()).containsExactly(1, 2, 3, 4, 5, 6);
    }

    @Test
    void containsReturnsTrueForPresentNumber() {
        LottoCombination combo = LottoCombination.of(1, 7, 13, 22, 34, 45);
        assertThat(combo.contains(13)).isTrue();
        assertThat(combo.contains(8)).isFalse();
    }

    @Test
    void boundaryValuesAreAllowed() {
        LottoCombination combo = LottoCombination.of(1, 2, 3, 43, 44, 45);
        assertThat(combo.numbers()).containsExactly(1, 2, 3, 43, 44, 45);
    }
}
