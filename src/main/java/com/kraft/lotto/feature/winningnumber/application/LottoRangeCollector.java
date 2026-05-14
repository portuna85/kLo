package com.kraft.lotto.feature.winningnumber.application;

import com.kraft.lotto.feature.winningnumber.web.dto.CollectResponse;
import com.kraft.lotto.feature.winningnumber.infrastructure.WinningNumberRepository;
import com.kraft.lotto.support.BusinessException;
import com.kraft.lotto.support.ErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.List;
class LottoRangeCollector {

    private final LottoSingleDrawCollector singleDrawCollector;
    private final WinningNumberRepository winningNumberRepository;
    private final long backfillDelayMs;
    private final MeterRegistry meterRegistry;

    LottoRangeCollector(LottoSingleDrawCollector singleDrawCollector,
                        WinningNumberRepository winningNumberRepository,
                        long backfillDelayMs,
                        MeterRegistry meterRegistry) {
        this.singleDrawCollector = singleDrawCollector;
        this.winningNumberRepository = winningNumberRepository;
        this.backfillDelayMs = Math.max(0, backfillDelayMs);
        this.meterRegistry = meterRegistry;
    }

    CollectResponse collectRange(List<Integer> rounds, boolean refresh, boolean delayBetweenCalls) {
        int inserted = 0;
        int updated = 0;
        int skipped = 0;
        List<Integer> failedRounds = new ArrayList<>();
        boolean firstCall = true;
        for (Integer round : rounds) {
            if (!firstCall && delayBetweenCalls) {
                sleepBackfillDelay();
            }
            firstCall = false;
            CollectResponse one = singleDrawCollector.collectOne(round, refresh);
            inserted += one.collected();
            updated += one.updated();
            skipped += one.skipped();
            failedRounds.addAll(one.failedRounds());
        }
        int latestRound = winningNumberRepository.findMaxRound().orElse(0);
        if (meterRegistry != null) {
            meterRegistry.summary("kraft.collect.range.rounds").record(rounds.size());
            meterRegistry.summary("kraft.collect.range.failed").record(failedRounds.size());
        }
        return CollectResponse.of(inserted, updated, skipped, latestRound, failedRounds, false, null, false);
    }

    private void sleepBackfillDelay() {
        if (backfillDelayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(backfillDelayMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.EXTERNAL_API_FAILURE, "backfill delay interrupted", ex);
        }
    }
}
