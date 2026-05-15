package com.kraft.lotto.feature.winningnumber.application;

import com.kraft.lotto.feature.winningnumber.infrastructure.LottoFetchLogRepository;
import com.kraft.lotto.feature.winningnumber.infrastructure.WinningNumberRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Clock;
import net.javacrumbs.shedlock.core.LockProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class LottoCollectionConfiguration {

    @Bean
    LottoSingleDrawCollector lottoSingleDrawCollector(LottoApiClient lottoApiClient,
                                                      WinningNumberRepository winningNumberRepository,
                                                      WinningNumberPersister persister,
                                                      LottoFetchLogRepository fetchLogRepository) {
        return new LottoSingleDrawCollector(
                lottoApiClient,
                winningNumberRepository,
                persister,
                fetchLogRepository,
                Clock.systemDefaultZone()
        );
    }

    @Bean
    LottoRangeCollector lottoRangeCollector(LottoSingleDrawCollector singleDrawCollector,
                                            WinningNumberRepository winningNumberRepository,
                                            ObjectProvider<MeterRegistry> meterRegistryProvider,
                                            @Value("${kraft.lotto.api.backfill-delay-ms:${kraft.api.retry-backoff-ms:700}}")
                                            long backfillDelayMs) {
        return new LottoRangeCollector(
                singleDrawCollector,
                winningNumberRepository,
                backfillDelayMs,
                meterRegistryProvider.getIfAvailable()
        );
    }

    @Bean
    LottoCollectionGate lottoCollectionGate(ApplicationEventPublisher eventPublisher,
                                            LockProvider lockProvider,
                                            @Value("${kraft.collect.gate.lock-at-most-for:PT10M}") Duration lockAtMostFor,
                                            @Value("${kraft.collect.gate.lock-at-least-for:PT1S}") Duration lockAtLeastFor) {
        return new LottoCollectionGate(eventPublisher, lockProvider, lockAtMostFor, lockAtLeastFor);
    }

    @Bean
    LottoCollectionCommandService lottoCollectionCommandService(WinningNumberRepository winningNumberRepository,
                                                                LottoSingleDrawCollector singleDrawCollector,
                                                                LottoRangeCollector rangeCollector,
                                                                LottoCollectionGate gate) {
        return new LottoCollectionCommandService(
                winningNumberRepository,
                singleDrawCollector,
                rangeCollector,
                gate
        );
    }
}
