package com.kraft.lotto.feature.winningnumber.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("WinningNumberCollectService")
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

    private void givenDbHasRounds(int... rounds) {
        existing.clear();
        for (int round : rounds) {
            existing.add(round);
        }
        when(repository.existsByRound(anyInt())).thenAnswer(inv -> existing.contains(inv.getArgument(0)));
        when(repository.save(any())).thenAnswer(inv -> {
            WinningNumberEntity entity = inv.getArgument(0);
            existing.add(entity.getRound());
            return entity;
        });
    }

    @Test
    @DisplayName("targetRound ?? ?????API ?? empty ???????????? ??????")
    void collectsUntilApiReturnsEmptyWhenNoTargetRound() {
        when(repository.findMaxRound()).thenReturn(Optional.of(1100), Optional.of(1102));
        givenDbHasRounds(1100);
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
    @DisplayName("targetRound ?? ?????????????????????")
    void collectsUpToTargetRoundOnly() {
        when(repository.findMaxRound()).thenReturn(Optional.of(1100), Optional.of(1101));
        when(repository.existsByRound(anyInt())).thenReturn(false);
        when(lottoApiClient.fetch(1101)).thenReturn(Optional.of(sample(1101)));

        CollectResponse result = service.collect(1101);

        assertThat(result.collected()).isEqualTo(1);
        verify(lottoApiClient, times(1)).fetch(anyInt());
    }

    @Test
    @DisplayName("counts already collected round as skipped")
    void countsExistingRoundAsSkipped() {
        when(repository.findMaxRound()).thenReturn(Optional.of(0), Optional.of(2));
        // round 1: ??? ???, round 2: ???, round 3: empty
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
    @DisplayName("?????????failed ???????? ??? ???????????")
    void countsSaveFailureAsFailedAndContinues() {
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
        assertThat(result.failedRounds()).containsExactly(1);
        verify(eventPublisher, atLeastOnce()).publishEvent(any(WinningNumbersCollectedEvent.class));
    }

    @Test
    @DisplayName("wraps API exception as EXTERNAL_API_FAILURE")
    void wrapsExternalApiExceptionAsBusinessException() {
        when(repository.findMaxRound()).thenReturn(Optional.of(0));
        when(lottoApiClient.fetch(1)).thenThrow(new LottoApiClientException("boom"));

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.collect(null))
                .extracting(BusinessException::getErrorCode)
                .isEqualTo(ErrorCode.EXTERNAL_API_FAILURE);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("throws LOTTO_INVALID_TARGET_ROUND when targetRound <= 0")
    void throwsInvalidTargetRoundWhenTargetRoundIsNonPositive() {
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.collect(0))
                .extracting(BusinessException::getErrorCode)
                .isEqualTo(ErrorCode.LOTTO_INVALID_TARGET_ROUND);
    }

    @Test
    @DisplayName("returns skipped when targetRound is already collected")
    void returnsSkippedWhenTargetRoundIsAlreadyCollected() {
        when(repository.findMaxRound()).thenReturn(Optional.of(1100));

        CollectResponse result = service.collect(1099);

        assertThat(result.collected()).isZero();
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        assertThat(result.latestRound()).isEqualTo(1100);
        assertThat(result.failedRounds()).isEmpty();
        verify(lottoApiClient, never()).fetch(anyInt());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("???????? ??? ???????? ??? ???????????")
    void rejectsConcurrentCollectExecution() throws Exception {
        CountDownLatch fetchEntered = new CountDownLatch(1);
        CountDownLatch releaseFetch = new CountDownLatch(1);
        when(repository.findMaxRound()).thenReturn(Optional.of(0), Optional.of(0));
        when(lottoApiClient.fetch(1)).thenAnswer(inv -> {
            fetchEntered.countDown();
            releaseFetch.await(2, TimeUnit.SECONDS);
            return Optional.empty();
        });

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            var first = executor.submit(() -> service.collect(null));
            assertThat(fetchEntered.await(1, TimeUnit.SECONDS)).isTrue();

            AtomicReference<ErrorCode> errorCode = new AtomicReference<>();
            assertThatExceptionOfType(BusinessException.class)
                    .isThrownBy(() -> service.collect(null))
                    .satisfies(ex -> errorCode.set(ex.getErrorCode()))
                    .extracting(BusinessException::getErrorCode)
                    .isEqualTo(ErrorCode.TOO_MANY_REQUESTS);

            releaseFetch.countDown();
            assertThat(first.get(1, TimeUnit.SECONDS).collected()).isZero();
            assertThat(errorCode.get()).isEqualTo(ErrorCode.TOO_MANY_REQUESTS);
        } finally {
            releaseFetch.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("does not publish event when collected is zero")
    void doesNotPublishEventWhenCollectedIsZero() {
        when(repository.findMaxRound()).thenReturn(Optional.of(0));
        when(lottoApiClient.fetch(1)).thenReturn(Optional.empty());

        CollectResponse result = service.collect(null);

        assertThat(result.collected()).isZero();
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("returns truncated and nextRound when max rounds per call is exceeded")
    void returnsTruncatedAndNextRoundWhenMaxRoundsPerCallExceeded() {
        service = new WinningNumberCollectService(lottoApiClient, repository, eventPublisher, clock, 2);
        when(repository.findMaxRound()).thenReturn(Optional.of(1100), Optional.of(1102));
        givenDbHasRounds();
        when(lottoApiClient.fetch(1101)).thenReturn(Optional.of(sample(1101)));
        when(lottoApiClient.fetch(1102)).thenReturn(Optional.of(sample(1102)));

        CollectResponse result = service.collect(null);

        assertThat(result.truncated()).isTrue();
        assertThat(result.nextRound()).isEqualTo(1103);
        assertThat(result.collected()).isEqualTo(2);
    }

    @Test
    @DisplayName("returns notDrawn=true when targetRound is not drawn")
    void returnsNotDrawnTrueWhenTargetRoundIsNotDrawn() {
        when(repository.findMaxRound()).thenReturn(Optional.of(1100), Optional.of(1101));
        givenDbHasRounds(1100);
        when(lottoApiClient.fetch(1101)).thenReturn(Optional.of(sample(1101)));
        when(lottoApiClient.fetch(1102)).thenReturn(Optional.empty());

        CollectResponse result = service.collect(1102);

        assertThat(result.notDrawn()).isTrue();
        assertThat(result.collected()).isEqualTo(1);
        assertThat(result.skipped()).isZero();
        assertThat(result.failed()).isZero();
        assertThat(result.latestRound()).isEqualTo(1101);
        assertThat(result.failedRounds()).isEmpty();
        assertThat(result.truncated()).isFalse();
        assertThat(result.nextRound()).isNull();
        verify(eventPublisher).publishEvent(any(WinningNumbersCollectedEvent.class));
    }
}

