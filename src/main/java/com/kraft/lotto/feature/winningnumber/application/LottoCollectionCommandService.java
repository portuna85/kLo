package com.kraft.lotto.feature.winningnumber.application;

import com.kraft.lotto.feature.winningnumber.infrastructure.WinningNumberRepository;
import com.kraft.lotto.feature.winningnumber.web.dto.CollectResponse;
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

    LottoCollectionCommandService(WinningNumberRepository winningNumberRepository,
                                  LottoSingleDrawCollector singleDrawCollector,
                                  LottoRangeCollector rangeCollector) {
        this.winningNumberRepository = winningNumberRepository;
        this.singleDrawCollector = singleDrawCollector;
        this.rangeCollector = rangeCollector;
    }

    public CollectResponse collectNextIfNeeded() {
        int nextRound = winningNumberRepository.findMaxRound().orElse(0) + 1;
        return singleDrawCollector.collectOne(nextRound, false);
    }

    public CollectResponse collectMissingOnce() {
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
    }
}