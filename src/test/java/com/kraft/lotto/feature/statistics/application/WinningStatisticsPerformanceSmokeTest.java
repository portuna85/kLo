package com.kraft.lotto.feature.statistics.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.kraft.lotto.feature.statistics.infrastructure.WinningNumberFrequencySummaryEntity;
import com.kraft.lotto.feature.statistics.infrastructure.WinningNumberFrequencySummaryRepository;
import com.kraft.lotto.feature.winningnumber.infrastructure.WinningNumberRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@Tag("perf")
@DisplayName("WinningStatistics performance smoke")
class WinningStatisticsPerformanceSmokeTest {

    @Mock
    WinningNumberRepository winningNumberRepository;

    @Mock
    WinningNumberFrequencySummaryRepository summaryRepository;

    @Test
    @DisplayName("frequency summary path stays within smoke threshold")
    void frequencySummaryPathWithinThreshold() {
        when(winningNumberRepository.findMaxRound()).thenReturn(Optional.of(1200));
        List<WinningNumberFrequencySummaryEntity> summaryRows = new ArrayList<>();
        for (int i = 1; i <= 45; i++) {
            summaryRows.add(new WinningNumberFrequencySummaryEntity(i, i * 10L, 1200));
        }
        when(summaryRepository.findAllByOrderByBallAsc()).thenReturn(summaryRows);

        WinningStatisticsService service = new WinningStatisticsService(winningNumberRepository, summaryRepository);
        int iterations = 400;
        long startedAt = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            service.frequency();
        }
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;

        assertThat(elapsedMs).isLessThan(1500L);
    }
}
