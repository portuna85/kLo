package com.kraft.lotto.feature.winningnumber.application;

import com.kraft.lotto.feature.winningnumber.infrastructure.WinningNumberRepository;
import com.kraft.lotto.feature.winningnumber.web.dto.CollectResponse;
import com.kraft.lotto.support.BusinessException;
import com.kraft.lotto.support.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class LottoCollectionCommandService {

    private final WinningNumberRepository winningNumberRepository;
    private final LottoSingleDrawCollector singleDrawCollector;
    private final LottoRangeCollector rangeCollector;
    private final LottoCollectionGate gate;

    LottoCollectionCommandService(WinningNumberRepository winningNumberRepository,
                                  LottoSingleDrawCollector singleDrawCollector,
                                  LottoRangeCollector rangeCollector,
                                  LottoCollectionGate gate) {
        this.winningNumberRepository = winningNumberRepository;
        this.singleDrawCollector = singleDrawCollector;
        this.rangeCollector = rangeCollector;
        this.gate = gate;
    }

    public CollectResponse collectDraw(int drwNo) {
        validateRound(drwNo);
        return gate.run(() -> singleDrawCollector.collectOne(drwNo, false));
    }

    public CollectResponse collect(Integer targetRound) {
        if (targetRound == null) {
            return collectNextDraw();
        }
        validateRound(targetRound);
        return gate.run(() -> {
            int latestRound = winningNumberRepository.findMaxRound().orElse(0);
            if (targetRound <= latestRound) {
                return CollectResponse.ofSkipped(1, latestRound);
            }
            List<Integer> rounds = new ArrayList<>();
            for (int round = latestRound + 1; round <= targetRound; round++) {
                rounds.add(round);
            }
            return rangeCollector.collectRange(rounds, false, true);
        });
    }

    public CollectResponse collectNextDraw() {
        return gate.run(() -> {
            int nextRound = winningNumberRepository.findMaxRound().orElse(0) + 1;
            return singleDrawCollector.collectOne(nextRound, false);
        });
    }

    public CollectResponse collectMissingDraws() {
        return gate.run(() -> {
            int maxRound = winningNumberRepository.findMaxRound().orElse(0);
            if (maxRound <= 0) {
                return CollectResponse.of(0, 0, 0, 0, List.of(), false, null, false);
            }
            Set<Integer> existingRounds = winningNumberRepository.findRoundsBetween(1, maxRound);
            List<Integer> missingRounds = new ArrayList<>();
            for (int round = 1; round <= maxRound; round++) {
                if (!existingRounds.contains(round)) {
                    missingRounds.add(round);
                }
            }
            return rangeCollector.collectRange(missingRounds, false, true);
        });
    }

    public CollectResponse backfill(int from, int to) {
        validateRange(from, to);
        return gate.run(() -> {
            List<Integer> rounds = new ArrayList<>();
            for (int round = from; round <= to; round++) {
                rounds.add(round);
            }
            return rangeCollector.collectRange(rounds, false, true);
        });
    }

    public CollectResponse refreshDraw(int drwNo) {
        validateRound(drwNo);
        return gate.run(() -> singleDrawCollector.collectOne(drwNo, true));
    }

    private void validateRange(int from, int to) {
        validateRound(from);
        validateRound(to);
        if (from > to) {
            throw new BusinessException(ErrorCode.LOTTO_INVALID_TARGET_ROUND,
                    "backfill start round must be less than or equal to end round");
        }
    }

    private void validateRound(int round) {
        if (round <= 0) {
            throw new BusinessException(ErrorCode.LOTTO_INVALID_TARGET_ROUND);
        }
    }
}
