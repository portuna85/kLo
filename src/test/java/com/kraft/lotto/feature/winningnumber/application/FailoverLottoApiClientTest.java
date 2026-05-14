package com.kraft.lotto.feature.winningnumber.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kraft.lotto.feature.winningnumber.domain.LottoCombination;
import com.kraft.lotto.feature.winningnumber.domain.WinningNumber;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("장애 복구 로또 API 클라이언트 테스트")
class FailoverLottoApiClientTest {

    @Mock
    LottoApiClient primary;

    @Mock
    LottoApiClient fallback;

    FailoverLottoApiClient client;

    @BeforeEach
    void setUp() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .minimumNumberOfCalls(1)
                .slidingWindowSize(1)
                .failureRateThreshold(1.0f)
                .waitDurationInOpenState(Duration.ofMinutes(5))
                .recordException(ex -> ex instanceof LottoApiClientException)
                .build();
        client = new FailoverLottoApiClient(primary, fallback, CircuitBreaker.of("test", config));
    }

    @Test
    @DisplayName("정상 상태일 때는 기본 클라이언트에 위임한다")
    void delegatesToPrimaryWhenHealthy() {
        when(primary.fetch(1)).thenReturn(Optional.of(sample(1)));

        assertThat(client.fetch(1)).isPresent();
        verify(primary).fetch(1);
        verifyNoFallback();
    }

    @Test
    @DisplayName("기본 클라이언트 실패 시 대체 클라이언트로 전환한다")
    void switchesToFallbackOnPrimaryFailure() {
        when(primary.fetch(1)).thenThrow(new LottoApiClientException("down"));
        when(fallback.fetch(1)).thenReturn(Optional.of(sample(1)));

        assertThat(client.fetch(1)).isPresent();
        verify(fallback).fetch(1);
    }

    @Test
    @DisplayName("서킷 브레이커가 열려 있는 동안 대체 클라이언트를 사용한다")
    void usesFallbackWhileCircuitOpen() {
        when(primary.fetch(1)).thenThrow(new LottoApiClientException("down"));
        when(fallback.fetch(anyInt())).thenReturn(Optional.empty());

        client.fetch(1);
        client.fetch(2);

        verify(primary, times(1)).fetch(anyInt());
        verify(fallback, times(2)).fetch(anyInt());
    }

    @Test
    @DisplayName("CLOSED -> OPEN -> HALF_OPEN -> CLOSED 전이를 따른다")
    void followsCircuitBreakerStateTransitions() throws Exception {
        CircuitBreakerConfig transitionConfig = CircuitBreakerConfig.custom()
                .minimumNumberOfCalls(2)
                .slidingWindowSize(4)
                .failureRateThreshold(50.0f)
                .waitDurationInOpenState(Duration.ofMillis(100))
                .permittedNumberOfCallsInHalfOpenState(1)
                .recordException(ex -> ex instanceof LottoApiClientException)
                .build();
        client = new FailoverLottoApiClient(primary, fallback, CircuitBreaker.of("transition", transitionConfig));

        when(primary.fetch(1)).thenThrow(new LottoApiClientException("down-1"));
        when(primary.fetch(2)).thenThrow(new LottoApiClientException("down-2"));
        when(primary.fetch(4)).thenReturn(Optional.of(sample(4)));
        when(primary.fetch(5)).thenReturn(Optional.of(sample(5)));
        when(fallback.fetch(anyInt())).thenReturn(Optional.empty());

        client.fetch(1);
        client.fetch(2);
        client.fetch(3);

        verify(primary, times(2)).fetch(anyInt());
        verify(fallback, times(3)).fetch(anyInt());

        Thread.sleep(130);
        assertThat(client.fetch(4)).isPresent();
        assertThat(client.fetch(5)).isPresent();

        verify(primary, times(4)).fetch(anyInt());
    }

    private void verifyNoFallback() {
        verify(fallback, never()).fetch(anyInt());
    }

    private WinningNumber sample(int round) {
        return new WinningNumber(
                round,
                LocalDate.of(2024, 1, 1),
                new LottoCombination(List.of(1, 7, 13, 22, 34, 45)),
                8,
                1_000_000L,
                1,
                10_000_000L
        );
    }
}
