package com.kraft.lotto.feature.winningnumber.application;

import com.kraft.lotto.feature.winningnumber.infrastructure.LottoFetchLogRepository;
import com.kraft.lotto.feature.winningnumber.infrastructure.WinningNumberRepository;
import com.kraft.lotto.feature.winningnumber.web.dto.CollectResponse;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class LottoCollectionService {

    private final LottoCollectionCommandService commands;

    @Autowired
    public LottoCollectionService(LottoApiClient lottoApiClient,
                                  WinningNumberRepository winningNumberRepository,
                                  WinningNumberPersister persister,
                                  LottoFetchLogRepository fetchLogRepository,
                                  ApplicationEventPublisher eventPublisher,
                                  ObjectProvider<MeterRegistry> meterRegistryProvider,
                                  @Value("${kraft.lotto.api.backfill-delay-ms:${kraft.api.retry-backoff-ms:700}}") long backfillDelayMs) {
        LottoSingleDrawCollector single = new LottoSingleDrawCollector(
                lottoApiClient, winningNumberRepository, persister, fetchLogRepository, Clock.systemDefaultZone());
        this.commands = new LottoCollectionCommandService(
                winningNumberRepository,
                single,
                new LottoRangeCollector(
                        single,
                        winningNumberRepository,
                        backfillDelayMs,
                        meterRegistryProvider.getIfAvailable()
                ),
                new LottoCollectionGate(eventPublisher)
        );
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

    private LottoCollectionService(LottoCollectionCommandService commands) {
        this.commands = commands;
    }

    public CollectResponse collectDraw(int drwNo) { return commands.collectDraw(drwNo); }
    public CollectResponse collect(Integer targetRound) { return commands.collect(targetRound); }
    public CollectResponse collectNextDraw() { return commands.collectNextDraw(); }
    public CollectResponse collectMissingDraws() { return commands.collectMissingDraws(); }
    public CollectResponse backfill(int from, int to) { return commands.backfill(from, to); }
    public CollectResponse refreshDraw(int drwNo) { return commands.refreshDraw(drwNo); }
}
