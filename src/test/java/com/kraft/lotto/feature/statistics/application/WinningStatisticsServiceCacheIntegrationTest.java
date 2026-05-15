package com.kraft.lotto.feature.statistics.application;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kraft.lotto.feature.winningnumber.infrastructure.WinningNumberRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(classes = WinningStatisticsServiceCacheIntegrationTest.CacheTestConfig.class)
@DisplayName("WinningStatisticsService cache integration")
class WinningStatisticsServiceCacheIntegrationTest {

    @Configuration
    @EnableCaching
    static class CacheTestConfig {
        @Bean
        WinningStatisticsService winningStatisticsService(WinningNumberRepository repository) {
            return new WinningStatisticsService(repository);
        }

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("winningNumberFrequency", "combinationPrizeHistory");
        }
    }

    @Autowired
    WinningStatisticsService service;

    @MockitoBean
    WinningNumberRepository repository;

    @Test
    @DisplayName("frequencySummary uses cache through proxy and avoids duplicate frequency aggregation query")
    void frequencySummaryUsesCachedFrequencyViaProxy() {
        when(repository.findAllNumbersForFrequency()).thenReturn(List.<Object[]>of(
                new Object[]{1, 2, 3, 4, 5, 6}
        ));
        when(repository.findPrizeHitsByNumbers(anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt()))
                .thenReturn(List.of());
        when(repository.findMaxRound()).thenReturn(Optional.of(1));

        service.frequencySummary();
        service.frequencySummary();

        verify(repository, times(1)).findAllNumbersForFrequency();
    }
}
