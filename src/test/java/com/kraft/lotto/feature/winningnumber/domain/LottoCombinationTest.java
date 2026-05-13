package com.kraft.lotto.feature.winningnumber.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("로또 번호 조합 테스트")
class LottoCombinationTest {

    @Test
    @DisplayName("정렬된 번호 조합을 생성한다")
    void createsSortedCombination() {
        LottoCombination combo = new LottoCombination(List.of(7, 1, 45, 13, 22, 34));
        assertThat(combo.numbers()).containsExactly(1, 7, 13, 22, 34, 45);
    }

    @Test
    @DisplayName("정적 팩토리 메소드로 조합을 생성한다")
    void factoryOfCreatesCombination() {
        LottoCombination combo = LottoCombination.of(1, 2, 3, 4, 5, 6);
        assertThat(combo.numbers()).containsExactly(1, 2, 3, 4, 5, 6);
    }

    @Test
    @DisplayName("null 리스트는 거부한다")
    void rejectsNullList() {
        assertThatThrownBy(() -> new LottoCombination(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("번호 개수가 6개가 아니면 거부한다")
    void rejectsWrongSize() {
        assertThatThrownBy(() -> new LottoCombination(List.of(1, 2, 3, 4, 5)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LottoCombination(List.of(1, 2, 3, 4, 5, 6, 7)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("번호 범위를 벗어나면 거부한다")
    void rejectsOutOfRange() {
        assertThatThrownBy(() -> new LottoCombination(List.of(0, 2, 3, 4, 5, 6)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LottoCombination(List.of(1, 2, 3, 4, 5, 46)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("중복된 번호는 거부한다")
    void rejectsDuplicates() {
        assertThatThrownBy(() -> new LottoCombination(List.of(1, 1, 2, 3, 4, 5)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("null 엘리먼트를 포함하면 거부한다")
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
    @DisplayName("반환된 번호 리스트는 수정할 수 없다")
    void numbersListIsImmutable() {
        LottoCombination combo = LottoCombination.of(1, 2, 3, 4, 5, 6);
        assertThatThrownBy(() -> combo.numbers().add(7))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("원본 리스트를 수정해도 조합은 영향을 받지 않는다")
    void mutationOfSourceListDoesNotAffectCombination() {
        List<Integer> source = new ArrayList<>(List.of(1, 2, 3, 4, 5, 6));
        LottoCombination combo = new LottoCombination(source);
        source.set(0, 45);
        assertThat(combo.numbers()).containsExactly(1, 2, 3, 4, 5, 6);
    }

    @Test
    @DisplayName("번호 포함 여부를 확인한다")
    void containsReturnsTrueForPresentNumber() {
        LottoCombination combo = LottoCombination.of(1, 7, 13, 22, 34, 45);
        assertThat(combo.contains(13)).isTrue();
        assertThat(combo.contains(8)).isFalse();
    }

    @Test
    @DisplayName("경계값 번호를 허용한다")
    void boundaryValuesAreAllowed() {
        LottoCombination combo = LottoCombination.of(1, 2, 3, 43, 44, 45);
        assertThat(combo.numbers()).containsExactly(1, 2, 3, 43, 44, 45);
    }
}

