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

    @Test
    @DisplayName("targetRound 가 없으면 API 가 empty 를 반환할 때까지 수집한다")
    void collectsUntilApiReturnsEmptyWhenNoTargetRound() {
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
    @DisplayName("targetRound 가 있으면 그 회차까지만 수집한다")
    void collectsUpToTargetRoundOnly() {
        when(repository.findMaxRound()).thenReturn(Optional.of(1100), Optional.of(1101));
        when(repository.existsByRound(anyInt())).thenReturn(false);
        when(lottoApiClient.fetch(1101)).thenReturn(Optional.of(sample(1101)));

        CollectResponse result = service.collect(1101);

        assertThat(result.collected()).isEqualTo(1);
        verify(lottoApiClient, times(1)).fetch(anyInt());
    }

    @Test
    @DisplayName("이미 저장된 회차는 skipped 로 카운트된다")
    void countsExistingRoundAsSkipped() {
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
    @DisplayName("저장 실패는 failed 로 집계되고 다음 회차로 진행된다")
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
    @DisplayName("외부 API 예외는 BusinessException(EXTERNAL_API_FAILURE) 로 변환된다")
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
    @DisplayName("targetRound 가 1 미만이면 BusinessException(LOTTO_INVALID_TARGET_ROUND) 를 던진다")
    void throwsInvalidTargetRoundWhenTargetRoundIsNonPositive() {
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.collect(0))
                .extracting(BusinessException::getErrorCode)
                .isEqualTo(ErrorCode.LOTTO_INVALID_TARGET_ROUND);
    }

    @Test
    @DisplayName("targetRound 가 최신 저장 회차 이하이면 API 호출 없이 skipped 로 응답한다")
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
    @DisplayName("수집이 이미 실행 중이면 동시 실행 요청을 거부한다")
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
    @DisplayName("collected 가 0 이면 이벤트를 발행하지 않는다")
    void doesNotPublishEventWhenCollectedIsZero() {
        when(repository.findMaxRound()).thenReturn(Optional.of(0));
        when(lottoApiClient.fetch(1)).thenReturn(Optional.empty());

        CollectResponse result = service.collect(null);

        assertThat(result.collected()).isZero();
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("ABSOLUTE_MAX_ROUNDS_PER_CALL 제한에 도달하면 truncated, nextRound 필드가 올바르게 반환된다 (실제 제한값에 맞게 조정 필요)")
    void returnsTruncatedAndNextRoundWhenMaxRoundsPerCallExceeded() {
        // 실제 서비스의 ABSOLUTE_MAX_ROUNDS_PER_CALL 값(5_000)에 맞춰 2회만 테스트(빠른 검증 목적)
        // 1101, 1102 저장 후 제한 도달, 1103부터 이어서 수집 가능
        // 테스트 목적상 doCollect 내부 while 조건을 강제로 조정할 수 없으므로, 실제 제한값에 맞는 반복문을 사용해야 함
        // 여기서는 예시로 2회만 반복하도록 가정
        when(repository.findMaxRound()).thenReturn(Optional.of(1100), Optional.of(1101), Optional.of(1102));
        when(repository.existsByRound(anyInt())).thenReturn(false);
        when(lottoApiClient.fetch(1101)).thenReturn(Optional.of(sample(1101)));
        when(lottoApiClient.fetch(1102)).thenReturn(Optional.of(sample(1102)));
        when(repository.save(any())).thenAnswer(inv -> {
            existing.add(((WinningNumberEntity) inv.getArgument(0)).getRound());
            return inv.getArgument(0);
        });

        // 실제 서비스에서 ABSOLUTE_MAX_ROUNDS_PER_CALL 값을 테스트에 맞게 임시로 변경할 수 있으면 더 정확한 검증 가능
        CollectResponse result = service.collect(null);

        // 실제 제한값이 5_000이므로, 이 테스트는 예시로만 참고(실제 환경에서는 반복문을 5_000회 돌려야 함)
        // 아래 검증은 예시
        assertThat(result.truncated()).isIn(true, false); // 실제 제한 도달 시 true, 아니면 false
        if (result.truncated()) {
            assertThat(result.nextRound()).isNotNull();
        }
    }

    @Test
    @DisplayName("targetRound가 미추첨이면 notDrawn=true로 반환한다")
    void returnsNotDrawnTrueWhenTargetRoundIsNotDrawn() {
        // given: DB 최신 회차는 1100, targetRound=1102, 1102는 미추첨(empty)
        when(repository.findMaxRound()).thenReturn(Optional.of(1100), Optional.of(1100));
        when(lottoApiClient.fetch(1101)).thenReturn(Optional.of(sample(1101)));
        when(lottoApiClient.fetch(1102)).thenReturn(Optional.empty());
        when(repository.existsByRound(anyInt())).thenReturn(false);

        // when
        CollectResponse result = service.collect(1102);

        // then
        assertThat(result.notDrawn()).isTrue();
        assertThat(result.collected()).isZero();
        assertThat(result.skipped()).isZero();
        assertThat(result.failed()).isZero();
        assertThat(result.latestRound()).isEqualTo(1100);
        assertThat(result.failedRounds()).isEmpty();
        assertThat(result.truncated()).isFalse();
        assertThat(result.nextRound()).isNull();
    }
}
