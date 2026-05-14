package com.kraft.lotto.feature.recommend.application;

import com.kraft.lotto.feature.winningnumber.domain.LottoCombination;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

class ConstraintAwareLottoNumberGenerator implements LottoNumberGenerator {

    private static final int MAX_NUMBER = 45;
    private static final int SIZE = 6;

    private final Random random;
    private final int birthdayThreshold;
    private final int longRunThreshold;
    private final int decadeThreshold;

    ConstraintAwareLottoNumberGenerator(Random random, int birthdayThreshold, int longRunThreshold, int decadeThreshold) {
        this.random = random;
        this.birthdayThreshold = birthdayThreshold;
        this.longRunThreshold = longRunThreshold;
        this.decadeThreshold = decadeThreshold;
    }

    @Override
    public LottoCombination generate() {
        Set<Integer> selected = new TreeSet<>();
        int birthdayAboveCount = 0;
        int[] decadeBuckets = new int[5];

        while (selected.size() < SIZE) {
            int n = random.nextInt(MAX_NUMBER) + 1;
            if (selected.contains(n)) {
                continue;
            }
            if (birthdayAboveCount == 0 && selected.size() == SIZE - 1 && n <= birthdayThreshold) {
                continue;
            }
            int bucket = bucketIndex(n);
            if (decadeBuckets[bucket] >= Math.max(1, decadeThreshold - 1)) {
                continue;
            }

            selected.add(n);
            decadeBuckets[bucket]++;
            if (n > birthdayThreshold) {
                birthdayAboveCount++;
            }
        }

        List<Integer> numbers = new ArrayList<>(selected);
        while (hasLongRun(numbers, longRunThreshold)) {
            int replaceIndex = random.nextInt(SIZE);
            int old = numbers.get(replaceIndex);
            int newNumber = random.nextInt(MAX_NUMBER) + 1;
            if (numbers.contains(newNumber)) {
                continue;
            }
            int oldBucket = bucketIndex(old);
            int newBucket = bucketIndex(newNumber);
            if (decadeBuckets[newBucket] >= Math.max(1, decadeThreshold - 1)) {
                continue;
            }
            numbers.set(replaceIndex, newNumber);
            decadeBuckets[oldBucket]--;
            decadeBuckets[newBucket]++;
            numbers.sort(Integer::compareTo);
        }
        return new LottoCombination(numbers);
    }

    private static int bucketIndex(int n) {
        if (n <= 9) return 0;
        if (n <= 19) return 1;
        if (n <= 29) return 2;
        if (n <= 39) return 3;
        return 4;
    }

    private static boolean hasLongRun(List<Integer> numbers, int threshold) {
        int run = 1;
        for (int i = 1; i < numbers.size(); i++) {
            if (numbers.get(i) - numbers.get(i - 1) == 1) {
                run++;
                if (run >= threshold) {
                    return true;
                }
            } else {
                run = 1;
            }
        }
        return false;
    }
}
