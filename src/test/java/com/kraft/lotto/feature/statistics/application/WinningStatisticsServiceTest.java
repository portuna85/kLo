package com.kraft.lotto.feature.statistics.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kraft.lotto.feature.statistics.infrastructure.WinningNumberFrequencySummaryEntity;
import com.kraft.lotto.feature.statistics.infrastructure.WinningNumberFrequencySummaryRepository;
import com.kraft.lotto.feature.winningnumber.infrastructure.WinningNumberRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("WinningStatisticsService")
class WinningStatisticsServiceTest {

    @Mock
    WinningNumberRepository winningNumberRepository;

    @Mock
    WinningNumberFrequencySummaryRepository summaryRepository;

    @Test
    @DisplayName("uses summary table when summary is up to date")
    void usesSummaryWhenUpToDate() {
        when(winningNumberRepository.findMaxRound()).thenReturn(Optional.of(1200));
        List<WinningNumberFrequencySummaryEntity> rows = new ArrayList<>();
        for (int i = 1; i <= 45; i++) {
            rows.add(new WinningNumberFrequencySummaryEntity(i, i * 2L, 1200));
        }
        when(summaryRepository.findAllByOrderByBallAsc()).thenReturn(rows);

        WinningStatisticsService service = new WinningStatisticsService(winningNumberRepository, summaryRepository);
        var result = service.frequency();

        assertThat(result).hasSize(45);
        assertThat(result.get(0).number()).isEqualTo(1);
        assertThat(result.get(0).count()).isEqualTo(2L);
        verify(winningNumberRepository, never()).findAllNumbersForFrequency();
    }

    @Test
    @DisplayName("recomputes and saves summary when summary is stale")
    void recomputesWhenSummaryIsStale() {
        when(winningNumberRepository.findMaxRound()).thenReturn(Optional.of(1201));
        when(summaryRepository.findAllByOrderByBallAsc()).thenReturn(List.of(
                new WinningNumberFrequencySummaryEntity(1, 10L, 1200)
        ));
        when(winningNumberRepository.findAllNumbersForFrequency()).thenReturn(List.of(
                new Object[]{1, 2, 3, 4, 5, 6},
                new Object[]{1, 2, 3, 4, 5, 6}
        ));

        WinningStatisticsService service = new WinningStatisticsService(winningNumberRepository, summaryRepository);
        var result = service.frequency();

        assertThat(result).hasSize(45);
        assertThat(result.get(0).count()).isEqualTo(2L);
        verify(summaryRepository).saveAll(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    @DisplayName("records frequency latency and summary refresh metrics on recompute")
    void recordsMetricsOnRecompute() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        when(winningNumberRepository.findMaxRound()).thenReturn(Optional.of(1201));
        when(summaryRepository.findAllByOrderByBallAsc()).thenReturn(List.of(
                new WinningNumberFrequencySummaryEntity(1, 10L, 1200)
        ));
        when(winningNumberRepository.findAllNumbersForFrequency()).thenReturn(List.<Object[]>of(
                new Object[]{1, 2, 3, 4, 5, 6}
        ));

        WinningStatisticsService service =
                new WinningStatisticsService(winningNumberRepository, summaryRepository, meterRegistry);
        service.frequency();

        double refreshCount = meterRegistry.get("kraft.statistics.frequency.summary.refresh")
                .counter().count();
        assertThat(refreshCount).isEqualTo(1.0);
        long latencyCount = meterRegistry.get("kraft.statistics.frequency.latency")
                .tag("source", "recompute")
                .timer()
                .count();
        assertThat(latencyCount).isEqualTo(1L);
    }
}
