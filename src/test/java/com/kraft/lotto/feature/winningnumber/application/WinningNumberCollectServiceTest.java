package com.kraft.lotto.feature.winningnumber.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kraft.lotto.feature.winningnumber.domain.LottoCombination;
import com.kraft.lotto.feature.winningnumber.domain.WinningNumber;
import com.kraft.lotto.feature.winningnumber.event.WinningNumbersCollectedEvent;
import com.kraft.lotto.feature.winningnumber.infrastructure.WinningNumberEntity;
import com.kraft.lotto.feature.winningnumber.infrastructure.WinningNumberRepository;
import com.kraft.lotto.feature.winningnumber.web.dto.CollectResponse;
import com.kraft.lotto.support.BusinessException;
import com.kraft.lotto.support.ErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class WinningNumberCollectServiceTest {

    @Mock
    LottoApiClient lottoApiClient;

    @Mock
    WinningNumberRepository repository;

    @Mock
    ApplicationEventPublisher eventPublisher;

    WinningNumberCollectService service;
    Clock clock = Clock.fixed(LocalDate.of(2026, 5, 1).atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    Set<Integer> existing;

    @BeforeEach
    void setUp() {
        service = new WinningNumberCollectService(lottoApiClient, repository, eventPublisher, clock);
        existing = new HashSet<>();
    }

    private WinningNumber sample(int round) {
        return new WinningNumber(
                round,
                LocalDate.of(2024, 1, 1).plusWeeks(round),
                new LottoCombination(List.of(1, 7, 13, 22, 34, 45)),
                8,
                1_000_000_000L, 1, 50_000_000_000L
        );
    }

    @Test
    void targetRound_없으면_API가_empty_반환할때까지_수집() {
        when(repository.findMaxRound()).thenReturn(Optional.of(1100), Optional.of(1102));
        when(repository.existsByRound(anyInt())).thenAnswer(inv -> existing.contains(inv.getArgument(0)));
        when(repository.save(any())).thenAnswer(inv -> {
            existing.add(((WinningNumberEntity) inv.getArgument(0)).getRound());
            return inv.getArgument(0);
        });
        when(lottoApiClient.fetch(1101)).thenReturn(Optional.of(sample(1101)));
        when(lottoApiClient.fetch(1102)).thenReturn(Optional.of(sample(1102)));
        when(lottoApiClient.fetch(1103)).thenReturn(Optional.empty());

        CollectResponse result = service.collect(null);

        assertThat(result.collected()).isEqualTo(2);
        assertThat(result.skipped()).isZero();
        assertThat(result.failed()).isZero();
        verify(repository, times(2)).save(any());
        ArgumentCaptor<WinningNumbersCollectedEvent> ev = ArgumentCaptor.forClass(WinningNumbersCollectedEvent.class);
        verify(eventPublisher).publishEvent(ev.capture());
        assertThat(ev.getValue().collected()).isEqualTo(2);
    }

    @Test
    void targetRound_있으면_그회차까지만_수집() {
        when(repository.findMaxRound()).thenReturn(Optional.of(1100), Optional.of(1101));
        when(repository.existsByRound(anyInt())).thenReturn(false);
        when(lottoApiClient.fetch(1101)).thenReturn(Optional.of(sample(1101)));

        CollectResponse result = service.collect(1101);

        assertThat(result.collected()).isEqualTo(1);
        verify(lottoApiClient, times(1)).fetch(anyInt());
    }

    @Test
    void 이미_저장된_회차는_skipped로_카운트() {
        when(repository.findMaxRound()).thenReturn(Optional.of(0), Optional.of(2));
        // round 1: 이미 존재, round 2: 신규, round 3: empty
        Map<Integer, Boolean> exists = new HashMap<>();
        exists.put(1, true);
        exists.put(2, false);
        when(repository.existsByRound(anyInt())).thenAnswer(inv -> exists.getOrDefault(inv.getArgument(0), false));
        when(lottoApiClient.fetch(1)).thenReturn(Optional.of(sample(1)));
        when(lottoApiClient.fetch(2)).thenReturn(Optional.of(sample(2)));
        when(lottoApiClient.fetch(3)).thenReturn(Optional.empty());

        CollectResponse result = service.collect(null);

        assertThat(result.collected()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
        verify(repository, times(1)).save(any());
    }

    @Test
    void 저장_실패는_failed로_집계되고_다음_회차로_진행() {
        when(repository.findMaxRound()).thenReturn(Optional.of(0), Optional.of(2));
        when(repository.existsByRound(anyInt())).thenReturn(false);
        when(lottoApiClient.fetch(1)).thenReturn(Optional.of(sample(1)));
        when(lottoApiClient.fetch(2)).thenReturn(Optional.of(sample(2)));
        when(lottoApiClient.fetch(3)).thenReturn(Optional.empty());
        when(repository.save(any()))
                .thenThrow(new RuntimeException("db error"))
                .thenAnswer(inv -> inv.getArgument(0));

        CollectResponse result = service.collect(null);

        assertThat(result.collected()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        verify(eventPublisher, atLeastOnce()).publishEvent(any(WinningNumbersCollectedEvent.class));
    }

    @Test
    void 외부_API_예외는_BusinessException_EXTERNAL_API_FAILURE로_변환() {
        when(repository.findMaxRound()).thenReturn(Optional.of(0));
        when(lottoApiClient.fetch(1)).thenThrow(new LottoApiClientException("boom"));

        assertThatThrownBy(() -> service.collect(null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EXTERNAL_API_FAILURE);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void collected_0이면_이벤트_미발행() {
        when(repository.findMaxRound()).thenReturn(Optional.of(0));
        when(lottoApiClient.fetch(1)).thenReturn(Optional.empty());

        CollectResponse result = service.collect(null);

        assertThat(result.collected()).isZero();
        verify(eventPublisher, never()).publishEvent(any());
    }
}
