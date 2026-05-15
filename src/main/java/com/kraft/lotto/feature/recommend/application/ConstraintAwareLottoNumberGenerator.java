package com.kraft.lotto.feature.recommend.application;

import com.kraft.lotto.feature.winningnumber.domain.LottoCombination;
import java.util.ArrayList;
import java.util.Collections;
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
    private final int initialPickMaxAttempts;
    private final int fixupMaxAttempts;

    ConstraintAwareLottoNumberGenerator(Random random, int birthdayThreshold, int longRunThreshold, int decadeThreshold) {
        this(random, birthdayThreshold, longRunThreshold, decadeThreshold, 10_000, 1_000);
    }

    ConstraintAwareLottoNumberGenerator(Random random,
                                        int birthdayThreshold,
                                        int longRunThreshold,
                                        int decadeThreshold,
                                        int initialPickMaxAttempts,
                                        int fixupMaxAttempts) {
        this.random = random;
        this.birthdayThreshold = birthdayThreshold;
        this.longRunThreshold = longRunThreshold;
        this.decadeThreshold = decadeThreshold;
        this.initialPickMaxAttempts = initialPickMaxAttempts;
        this.fixupMaxAttempts = fixupMaxAttempts;
    }

    @Override
    public LottoCombination generate() {
        Set<Integer> selected = new TreeSet<>();
        int birthdayAboveCount = 0;
        int[] decadeBuckets = new int[5];
        int initialPickAttempts = 0;

        while (selected.size() < SIZE) {
            if (++initialPickAttempts > initialPickMaxAttempts) {
                throw new RecommendGenerationTimeoutException("initial pick exceeded max attempts");
            }
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
        int fixupAttempts = 0;
        while (hasLongRun(numbers, longRunThreshold)) {
            if (++fixupAttempts > fixupMaxAttempts) {
                throw new RecommendGenerationTimeoutException("fixup exceeded max attempts");
            }
            List<Integer> longRunIndices = findLongRunIndices(numbers, longRunThreshold);
            int replaceIndex;
            if (!longRunIndices.isEmpty()) {
                replaceIndex = longRunIndices.get(random.nextInt(longRunIndices.size()));
            } else {
                replaceIndex = random.nextInt(SIZE);
            }
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

    private static List<Integer> findLongRunIndices(List<Integer> numbers, int threshold) {
        if (threshold <= 1 || numbers.size() < threshold) {
            return Collections.emptyList();
        }
        List<Integer> indices = new ArrayList<>();
        int runStart = 0;
        int runLength = 1;
        for (int i = 1; i < numbers.size(); i++) {
            if (numbers.get(i) - numbers.get(i - 1) == 1) {
                runLength++;
                if (runLength >= threshold) {
                    for (int idx = runStart; idx <= i; idx++) {
                        if (!indices.contains(idx)) {
                            indices.add(idx);
                        }
                    }
                }
            } else {
                runStart = i;
                runLength = 1;
            }
        }
        return indices;
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
