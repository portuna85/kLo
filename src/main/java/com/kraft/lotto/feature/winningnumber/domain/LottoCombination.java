package com.kraft.lotto.feature.winningnumber.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record LottoCombination(List<Integer> numbers) {

    public static final int SIZE = 6;
    public static final int MIN_NUMBER = 1;
    public static final int MAX_NUMBER = 45;

    public LottoCombination {
        Objects.requireNonNull(numbers, "numbers must not be null");
        if (numbers.size() != SIZE) {
            throw new IllegalArgumentException("로또 조합은 정확히 " + SIZE + "개 번호여야 합니다.");
        }
        Set<Integer> seen = new HashSet<>(SIZE);
        for (Integer n : numbers) {
            if (n == null) {
                throw new IllegalArgumentException("로또 번호는 null일 수 없습니다.");
            }
            if (n < MIN_NUMBER || n > MAX_NUMBER) {
                throw new IllegalArgumentException(
                        "로또 번호는 " + MIN_NUMBER + "~" + MAX_NUMBER + " 범위여야 합니다: " + n);
            }
            if (!seen.add(n)) {
                throw new IllegalArgumentException("로또 번호는 중복될 수 없습니다: " + n);
            }
        }
        numbers = List.copyOf(numbers.stream().sorted().toList());
    }

    public static LottoCombination of(Integer... numbers) {
        return new LottoCombination(List.of(numbers));
    }

    public boolean contains(int number) {
        return numbers.contains(number);
    }

    public boolean sameNumbers(LottoCombination other) {
        return other != null && this.numbers.equals(other.numbers);
    }
}
