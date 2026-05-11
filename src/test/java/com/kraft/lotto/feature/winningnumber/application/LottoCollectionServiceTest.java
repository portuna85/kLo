package com.kraft.lotto.feature.winningnumber.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kraft.lotto.feature.winningnumber.domain.LottoCombination;
import com.kraft.lotto.feature.winningnumber.domain.WinningNumber;
import com.kraft.lotto.feature.winningnumber.infrastructure.LottoFetchLogEntity;
import com.kraft.lotto.feature.winningnumber.infrastructure.LottoFetchLogRepository;
import com.kraft.lotto.feature.winningnumber.infrastructure.WinningNumberRepository;
import com.kraft.lotto.feature.winningnumber.web.dto.CollectResponse;
import com.kraft.lotto.support.BusinessException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LottoCollectionService")
class LottoCollectionServiceTest {

    private final LottoApiClient lottoApiClient = mock(LottoApiClient.class);
    private final WinningNumberRepository winningNumberRepository = mock(WinningNumberRepository.class);
    private final WinningNumberPersister persister = mock(WinningNumberPersister.class);
    private final LottoFetchLogRepository fetchLogRepository = mock(LottoFetchLogRepository.class);
    private final LottoCollectionService service = new LottoCollectionService(
            lottoApiClient, winningNumberRepository, persister, fetchLogRepository, Clock.systemUTC(), 0);

    @Test
    @DisplayName("collectDraw 는 이미 저장된 회차를 기본적으로 스킵한다")
    void collectDrawSkipsExistingRound() {
        when(winningNumberRepository.existsByRound(1102)).thenReturn(true);
        when(winningNumberRepository.findMaxRound()).thenReturn(Optional.of(1102));

        CollectResponse result = service.collectDraw(1102);

        assertThat(result.skipped()).isEqualTo(1);
        verify(lottoApiClient, never()).fetch(1102);
        verify(fetchLogRepository).save(any(LottoFetchLogEntity.class));
    }

    @Test
    @DisplayName("refreshDraw 는 기존 회차도 API에서 다시 받아 upsert 한다")
    void refreshDrawUpsertsExistingRound() {
        WinningNumber winningNumber = sample(1102);
        when(lottoApiClient.fetch(1102)).thenReturn(Optional.of(winningNumber));
        when(persister.upsert(winningNumber)).thenReturn(false);
        when(winningNumberRepository.findMaxRound()).thenReturn(Optional.of(1102));

        CollectResponse result = service.refreshDraw(1102);

        assertThat(result.skipped()).isEqualTo(1);
        verify(persister).upsert(winningNumber);
        verify(fetchLogRepository).save(any(LottoFetchLogEntity.class));
    }

    @Test
    @DisplayName("collectNextDraw 는 DB 최대 회차 + 1 만 수집한다")
    void collectNextDrawCollectsOnlyNextRound() {
        WinningNumber winningNumber = sample(1103);
        when(winningNumberRepository.findMaxRound()).thenReturn(Optional.of(1102), Optional.of(1103));
        when(winningNumberRepository.existsByRound(1103)).thenReturn(false);
        when(lottoApiClient.fetch(1103)).thenReturn(Optional.of(winningNumber));
        when(persister.upsert(winningNumber)).thenReturn(true);

        CollectResponse result = service.collectNextDraw();

        assertThat(result.collected()).isEqualTo(1);
        verify(lottoApiClient).fetch(1103);
    }

    @Test
    @DisplayName("collectMissingDraws 는 누락된 회차만 수집한다")
    void collectMissingDrawsCollectsOnlyMissingRounds() {
        WinningNumber winningNumber = sample(2);
        when(winningNumberRepository.findMaxRound()).thenReturn(Optional.of(3), Optional.of(3));
        when(winningNumberRepository.findRoundsBetween(1, 3)).thenReturn(Set.of(1, 3));
        when(winningNumberRepository.existsByRound(2)).thenReturn(false);
        when(lottoApiClient.fetch(2)).thenReturn(Optional.of(winningNumber));
        when(persister.upsert(winningNumber)).thenReturn(true);

        CollectResponse result = service.collectMissingDraws();

        assertThat(result.collected()).isEqualTo(1);
        verify(lottoApiClient).fetch(2);
    }

    @Test
    @DisplayName("backfill 은 from 이 to 보다 크면 예외를 던진다")
    void backfillRejectsInvalidRange() {
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.backfill(10, 1));
    }

    private WinningNumber sample(int round) {
        return new WinningNumber(
                round,
                LocalDate.of(2024, 1, 6),
                new LottoCombination(List.of(6, 13, 23, 24, 28, 33)),
                38,
                2_596_477_500L,
                11,
                79_760_843_000L,
                28_561_252_500L,
                "{\"returnValue\":\"success\"}",
                null
        );
    }
}
