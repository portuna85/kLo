package com.kraft.lotto.feature.recommend.domain;

import com.kraft.lotto.feature.winningnumber.domain.LottoCombination;
import java.util.List;

/**
 * 5개 이상 연속번호가 포함된 조합을 제외한다.
 * 예: 1,2,3,4,5,20 또는 10,11,12,13,14,40.
 */
public class LongRunRule implements ExclusionRule {

    public static final int DEFAULT_LONG_RUN_THRESHOLD = 5;

    private final int longRunThreshold;

    public LongRunRule() {
        this(DEFAULT_LONG_RUN_THRESHOLD);
    }

    public LongRunRule(int longRunThreshold) {
        this.longRunThreshold = longRunThreshold;
    }

    @Override
    public boolean shouldExclude(LottoCombination combination) {
        List<Integer> nums = combination.numbers();
        int run = 1;
        for (int i = 1; i < nums.size(); i++) {
            if (nums.get(i) - nums.get(i - 1) == 1) {
                run++;
                if (run >= longRunThreshold) {
                    return true;
                }
            } else {
                run = 1;
            }
        }
        return false;
    }

    @Override
    public String reason() {
        return "5개 이상 연속된 번호가 포함된 긴 연속 조합은 제외합니다.";
    }
}
