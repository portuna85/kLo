package com.kraft.lotto.feature.recommend.domain;

import com.kraft.lotto.feature.winningnumber.domain.LottoCombination;
import java.util.Collection;
import java.util.Set;

/**
 * 과거 1등 당첨 조합의 인메모리 스냅샷.
 * 도메인 계층의 순수 POJO로, Spring/JPA 의존성을 가지지 않는다.
 *
 * 외부에서는 {@link #replace(Collection)}로 스냅샷을 통째로 교체한다.
 * 동시성 안전을 위해 내부 스냅샷은 volatile 불변 Set으로 유지된다.
 *
 * 본 캐시는 PastWinningRule 등 추천 규칙이 DB/Repository에 직접 접근하지 않도록
 * 분리된 의존성 경계를 제공하기 위한 도구이다.
 */
public class PastWinningCache {

    private volatile Set<LottoCombination> snapshot = Set.of();

    public void replace(Collection<LottoCombination> combinations) {
        this.snapshot = Set.copyOf(combinations);
    }

    public boolean contains(LottoCombination combination) {
        return snapshot.contains(combination);
    }

    public int size() {
        return snapshot.size();
    }
}
