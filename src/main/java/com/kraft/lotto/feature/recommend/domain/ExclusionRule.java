package com.kraft.lotto.feature.recommend.domain;

import com.kraft.lotto.feature.winningnumber.domain.LottoCombination;

/**
 * 추천 후보 조합에서 인기/중복 패턴 등 편향된 조합을 제외하기 위한 규칙.
 * 본 규칙은 당첨 확률을 높이기 위한 것이 아니라, 편향된 조합을 회피하기 위한 도구이다.
 */
public interface ExclusionRule {

    /**
     * 주어진 조합이 제외 대상인지 판단한다.
     */
    boolean shouldExclude(LottoCombination combination);

    /**
     * 사람이 읽을 수 있는 제외 사유 설명.
     */
    String reason();

    /**
     * 규칙 식별자. 기본 구현은 단순 클래스명을 사용한다.
     */
    default String name() {
        return getClass().getSimpleName();
    }
}
