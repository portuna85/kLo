package com.kraft.lotto.feature.winningnumber.application;

import com.kraft.lotto.feature.winningnumber.infrastructure.LottoFetchLogRepository;
import com.kraft.lotto.feature.winningnumber.infrastructure.WinningNumberRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
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
    LottoCollectionCommandService lottoCollectionCommandService(WinningNumberRepository winningNumberRepository,
                                                                LottoSingleDrawCollector singleDrawCollector,
                                                                LottoRangeCollector rangeCollector) {
        return new LottoCollectionCommandService(
                winningNumberRepository,
                singleDrawCollector,
                rangeCollector
        );
    }
}
