package com.kraft.lotto.feature.winningnumber.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LottoCombination 값 객체")
class LottoCombinationTest {

    @Test
    @DisplayName("입력 순서와 무관하게 정렬된 조합을 생성한다")
    void createsSortedCombination() {
        LottoCombination combo = new LottoCombination(List.of(7, 1, 45, 13, 22, 34));
        assertThat(combo.numbers()).containsExactly(1, 7, 13, 22, 34, 45);
    }

    @Test
    @DisplayName("팩토리 of() 가 조합을 생성한다")
    void factoryOfCreatesCombination() {
        LottoCombination combo = LottoCombination.of(1, 2, 3, 4, 5, 6);
        assertThat(combo.numbers()).containsExactly(1, 2, 3, 4, 5, 6);
    }

    @Test
    @DisplayName("null 리스트는 거부된다")
    void rejectsNullList() {
        assertThatThrownBy(() -> new LottoCombination(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("크기가 6이 아니면 거부된다")
    void rejectsWrongSize() {
        assertThatThrownBy(() -> new LottoCombination(List.of(1, 2, 3, 4, 5)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LottoCombination(List.of(1, 2, 3, 4, 5, 6, 7)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("범위(1~45)를 벗어난 번호는 거부된다")
    void rejectsOutOfRange() {
        assertThatThrownBy(() -> new LottoCombination(List.of(0, 2, 3, 4, 5, 6)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LottoCombination(List.of(1, 2, 3, 4, 5, 46)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("중복된 번호는 거부된다")
    void rejectsDuplicates() {
        assertThatThrownBy(() -> new LottoCombination(List.of(1, 1, 2, 3, 4, 5)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("null 요소가 포함되면 거부된다")
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
    @DisplayName("numbers 리스트는 불변이다")
    void numbersListIsImmutable() {
        LottoCombination combo = LottoCombination.of(1, 2, 3, 4, 5, 6);
        assertThatThrownBy(() -> combo.numbers().add(7))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("원본 리스트 변경이 조합에 영향을 주지 않는다")
    void mutationOfSourceListDoesNotAffectCombination() {
        List<Integer> source = new ArrayList<>(List.of(1, 2, 3, 4, 5, 6));
        LottoCombination combo = new LottoCombination(source);
        source.set(0, 45);
        assertThat(combo.numbers()).containsExactly(1, 2, 3, 4, 5, 6);
    }

    @Test
    @DisplayName("contains 는 포함 여부를 올바르게 반환한다")
    void containsReturnsTrueForPresentNumber() {
        LottoCombination combo = LottoCombination.of(1, 7, 13, 22, 34, 45);
        assertThat(combo.contains(13)).isTrue();
        assertThat(combo.contains(8)).isFalse();
    }

    @Test
    @DisplayName("경계값(1, 45)도 허용된다")
    void boundaryValuesAreAllowed() {
        LottoCombination combo = LottoCombination.of(1, 2, 3, 43, 44, 45);
        assertThat(combo.numbers()).containsExactly(1, 2, 3, 43, 44, 45);
    }
}

