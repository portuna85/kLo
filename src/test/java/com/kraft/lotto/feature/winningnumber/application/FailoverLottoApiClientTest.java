package com.kraft.lotto.feature.winningnumber.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kraft.lotto.feature.winningnumber.domain.LottoCombination;
import com.kraft.lotto.feature.winningnumber.domain.WinningNumber;
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
@DisplayName("FailoverLottoApiClient")
class FailoverLottoApiClientTest {

    @Mock
    LottoApiClient primary;

    @Mock
    LottoApiClient fallback;

    FailoverLottoApiClient client;

    @BeforeEach
    void setUp() {
        client = new FailoverLottoApiClient(primary, fallback);
    }

    @Test
    @DisplayName("delegates to primary when primary is healthy")
    void delegatesToPrimaryWhenHealthy() {
        when(primary.fetch(1)).thenReturn(Optional.of(sample(1)));

        assertThat(client.fetch(1)).isPresent();
        verify(primary).fetch(1);
        verifyNoFallback();
    }

    @Test
    @DisplayName("switches to fallback when primary fails")
    void switchesToFallbackOnPrimaryFailure() {
        when(primary.fetch(1)).thenThrow(new LottoApiClientException("down"));
        when(fallback.fetch(1)).thenReturn(Optional.of(sample(1)));

        assertThat(client.fetch(1)).isPresent();
        verify(fallback).fetch(1);
    }

    @Test
    @DisplayName("stays on fallback while cooldown is active")
    void staysInFallbackAfterActivation() {
        when(primary.fetch(1)).thenThrow(new LottoApiClientException("down"));
        when(fallback.fetch(anyInt())).thenReturn(Optional.empty());

        client.fetch(1);
        client.fetch(2);

        verify(primary, times(1)).fetch(anyInt());
        verify(fallback, times(2)).fetch(anyInt());
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
