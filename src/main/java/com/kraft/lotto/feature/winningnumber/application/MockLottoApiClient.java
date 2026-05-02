package com.kraft.lotto.feature.winningnumber.application;

import com.kraft.lotto.feature.winningnumber.domain.LottoCombination;
import com.kraft.lotto.feature.winningnumber.domain.WinningNumber;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * 테스트/로컬용 결정적 Mock 구현. 외부 호출 없이 round 기반 시드로 합법 조합을 생성한다.
 * round &gt; {@link #latestRound}이면 아직 추첨 전으로 간주하여 {@link Optional#empty()}를 반환한다.
 */
public class MockLottoApiClient implements LottoApiClient {

    private static final LocalDate EPOCH = LocalDate.of(2002, 12, 7);

    private final int latestRound;

    public MockLottoApiClient(int latestRound) {
        this.latestRound = latestRound;
    }

    @Override
    public Optional<WinningNumber> fetch(int round) {
        if (round <= 0 || round > latestRound) {
            return Optional.empty();
        }
        Random rnd = new Random(round);
        List<Integer> pool = new ArrayList<>(LottoCombination.MAX_NUMBER);
        for (int i = LottoCombination.MIN_NUMBER; i <= LottoCombination.MAX_NUMBER; i++) {
            pool.add(i);
        }
        Collections.shuffle(pool, rnd);
        List<Integer> mains = new ArrayList<>(pool.subList(0, LottoCombination.SIZE));
        int bonus = pool.get(LottoCombination.SIZE);
        LottoCombination combination = new LottoCombination(mains);
        WinningNumber wn = new WinningNumber(
                round,
                EPOCH.plusWeeks(round - 1L),
                combination,
                bonus,
                1_000_000_000L + (long) round * 1_000_000L,
                1 + (round % 20),
                50_000_000_000L + (long) round * 10_000_000L
        );
        return Optional.of(wn);
    }
}
