package com.kraft.lotto.feature.winningnumber.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kraft.lotto.feature.winningnumber.infrastructure.LottoFetchLogRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@DisplayName("LottoFetchLogRetentionScheduler")
class LottoFetchLogRetentionSchedulerTest {

    @Mock
    LottoFetchLogRepository fetchLogRepository;

    @Test
    @DisplayName("expired logs are deleted in batches until empty")
    void purgesInBatches() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-05-15T00:00:00Z"), ZoneOffset.UTC);
        LottoFetchLogRetentionScheduler scheduler =
                new LottoFetchLogRetentionScheduler(fetchLogRepository, fixedClock, 90, 2);
        when(fetchLogRepository.findIdsByFetchedAtBefore(any(), any(Pageable.class)))
                .thenReturn(List.of(1L, 2L))
                .thenReturn(List.of(3L))
                .thenReturn(List.of());

        scheduler.purgeExpiredLogs();

        verify(fetchLogRepository).deleteAllByIdInBatch(List.of(1L, 2L));
        verify(fetchLogRepository).deleteAllByIdInBatch(List.of(3L));
        verify(fetchLogRepository, never()).deleteByFetchedAtBefore(any());
    }

    @Test
    @DisplayName("no-op when there are no expired logs")
    void noOpWhenNothingToDelete() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-05-15T00:00:00Z"), ZoneOffset.UTC);
        LottoFetchLogRetentionScheduler scheduler =
                new LottoFetchLogRetentionScheduler(fetchLogRepository, fixedClock, 90, 1000);
        when(fetchLogRepository.findIdsByFetchedAtBefore(any(), any(Pageable.class)))
                .thenReturn(List.of());

        scheduler.purgeExpiredLogs();

        verify(fetchLogRepository, never()).deleteAllByIdInBatch(any());
        verify(fetchLogRepository, never()).deleteByFetchedAtBefore(any());
    }
}
