package com.kraft.lotto.feature.winningnumber.application;

import com.kraft.lotto.feature.winningnumber.infrastructure.LottoFetchLogRepository;
import com.kraft.lotto.feature.winningnumber.infrastructure.WinningNumberRepository;
import com.kraft.lotto.feature.winningnumber.web.dto.CollectResponse;
import java.time.Clock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class LottoCollectionService {

    private final LottoCollectionCommandService commands;

    public LottoCollectionService(LottoCollectionCommandService commands) {
        this.commands = commands;
    }

    static LottoCollectionService forTest(LottoApiClient lottoApiClient,
                                          WinningNumberRepository winningNumberRepository,
                                          WinningNumberPersister persister,
                                          LottoFetchLogRepository fetchLogRepository,
                                          ApplicationEventPublisher eventPublisher,
                                          Clock clock,
                                          long backfillDelayMs) {
        LottoSingleDrawCollector single = new LottoSingleDrawCollector(
                lottoApiClient, winningNumberRepository, persister, fetchLogRepository, clock);
        LottoCollectionCommandService commandService = new LottoCollectionCommandService(
                winningNumberRepository,
                single,
                new LottoRangeCollector(single, winningNumberRepository, backfillDelayMs, null),
                new LottoCollectionGate(eventPublisher)
        );
        return new LottoCollectionService(commandService);
    }

    public CollectResponse collectDraw(int drwNo) { return commands.collectDraw(drwNo); }
    public CollectResponse collect(Integer targetRound) { return commands.collect(targetRound); }
    public CollectResponse collectNextDraw() { return commands.collectNextDraw(); }
    public CollectResponse collectMissingDraws() { return commands.collectMissingDraws(); }
    public CollectResponse backfill(int from, int to) { return commands.backfill(from, to); }
    public CollectResponse refreshDraw(int drwNo) { return commands.refreshDraw(drwNo); }
}
