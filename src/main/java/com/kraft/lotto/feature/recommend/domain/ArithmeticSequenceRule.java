package com.kraft.lotto.feature.recommend.domain;

import com.kraft.lotto.feature.winningnumber.domain.LottoCombination;
import java.util.List;

/**
 * 정렬된 6개 번호가 동일한 공차를 가지는 완전 등차수열인 경우 제외한다.
 * 예: 1,8,15,22,29,36 또는 3,6,9,12,15,18.
 */
public class ArithmeticSequenceRule implements ExclusionRule {

    @Override
    public boolean shouldExclude(LottoCombination combination) {
        List<Integer> nums = combination.numbers();
        int diff = nums.get(1) - nums.get(0);
        if (diff <= 0) {
            return false;
        }
        for (int i = 2; i < nums.size(); i++) {
            if (nums.get(i) - nums.get(i - 1) != diff) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String reason() {
        return "동일한 공차를 가지는 완전 등차수열 조합은 제외합니다.";
    }
}
