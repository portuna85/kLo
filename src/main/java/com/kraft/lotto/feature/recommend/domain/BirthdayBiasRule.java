package com.kraft.lotto.feature.recommend.domain;

import com.kraft.lotto.feature.winningnumber.domain.LottoCombination;

/**
 * 6개 번호가 모두 31 이하인 조합을 제외한다.
 * 생일 번호에 편향된 조합을 회피하기 위한 규칙이다.
 */
public class BirthdayBiasRule implements ExclusionRule {

    public static final int DEFAULT_BIRTHDAY_THRESHOLD = 31;

    private final int birthdayThreshold;

    public BirthdayBiasRule() {
        this(DEFAULT_BIRTHDAY_THRESHOLD);
    }

    public BirthdayBiasRule(int birthdayThreshold) {
        this.birthdayThreshold = birthdayThreshold;
    }

    @Override
    public boolean shouldExclude(LottoCombination combination) {
        for (Integer n : combination.numbers()) {
            if (n > birthdayThreshold) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String reason() {
        return "6개 번호가 모두 31 이하인 생일 번호 편향 조합은 제외합니다.";
    }
}
