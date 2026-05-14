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
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

@DisplayName("로또 수집 서비스 테스트")
class LottoCollectionServiceTest {

    private final LottoApiClient lottoApiClient = mock(LottoApiClient.class);
    private final WinningNumberRepository winningNumberRepository = mock(WinningNumberRepository.class);
    private final WinningNumberPersister persister = mock(WinningNumberPersister.class);
    private final LottoFetchLogRepository fetchLogRepository = mock(LottoFetchLogRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final LottoCollectionService service = new LottoCollectionService(
            lottoApiClient, winningNumberRepository, persister, fetchLogRepository, eventPublisher, Clock.systemUTC(), 0);

    @Test
    @DisplayName("이미 존재하는 회차는 수집을 건너뛴다")
    void collectDrawSkipsExistingRound() {
        when(winningNumberRepository.existsByRound(1102)).thenReturn(true);
        when(winningNumberRepository.findMaxRound()).thenReturn(Optional.of(1102));

        CollectResponse result = service.collectDraw(1102);

        assertThat(result.skipped()).isEqualTo(1);
        verify(lottoApiClient, never()).fetch(1102);
        verify(fetchLogRepository).save(any(LottoFetchLogEntity.class));
    }

    @Test
    @DisplayName("새로고침 시 기존 회차 정보를 업데이트한다")
    void refreshDrawUpsertsExistingRound() {
        WinningNumber winningNumber = sample(1102);
        when(lottoApiClient.fetch(1102)).thenReturn(Optional.of(winningNumber));
        when(persister.upsert(winningNumber)).thenReturn(UpsertOutcome.UPDATED);
        when(winningNumberRepository.findMaxRound()).thenReturn(Optional.of(1102));

        CollectResponse result = service.refreshDraw(1102);

        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(0);
        verify(persister).upsert(winningNumber);
        verify(fetchLogRepository).save(any(LottoFetchLogEntity.class));
    }

    @Test
    @DisplayName("다음 회차 수집 시 바로 다음 회차만 수집한다")
    void collectNextDrawCollectsOnlyNextRound() {
        WinningNumber winningNumber = sample(1103);
        when(winningNumberRepository.findMaxRound()).thenReturn(Optional.of(1102), Optional.of(1103));
        when(winningNumberRepository.existsByRound(1103)).thenReturn(false);
        when(lottoApiClient.fetch(1103)).thenReturn(Optional.of(winningNumber));
        when(persister.upsert(winningNumber)).thenReturn(UpsertOutcome.INSERTED);

        CollectResponse result = service.collectNextDraw();

        assertThat(result.collected()).isEqualTo(1);
        verify(lottoApiClient).fetch(1103);
    }

    @Test
    @DisplayName("성공 로그에는 원본 응답을 저장하지 않는다")
    void successLogDoesNotPersistRawResponse() {
        WinningNumber winningNumber = sample(1103);
        when(winningNumberRepository.findMaxRound()).thenReturn(Optional.of(1102), Optional.of(1103));
        when(winningNumberRepository.existsByRound(1103)).thenReturn(false);
        when(lottoApiClient.fetch(1103)).thenReturn(Optional.of(winningNumber));
        when(persister.upsert(winningNumber)).thenReturn(UpsertOutcome.INSERTED);

        service.collectNextDraw();

        ArgumentCaptor<LottoFetchLogEntity> captor = ArgumentCaptor.forClass(LottoFetchLogEntity.class);
        verify(fetchLogRepository).save(captor.capture());
        assertThat(captor.getValue().getRawResponse()).isNull();
    }

    @Test
    @DisplayName("누락된 회차들만 골라서 수집한다")
    void collectMissingDrawsCollectsOnlyMissingRounds() {
        WinningNumber winningNumber = sample(2);
        when(winningNumberRepository.findMaxRound()).thenReturn(Optional.of(3), Optional.of(3));
        when(winningNumberRepository.findRoundsBetween(1, 3)).thenReturn(Set.of(1, 3));
        when(winningNumberRepository.existsByRound(2)).thenReturn(false);
        when(lottoApiClient.fetch(2)).thenReturn(Optional.of(winningNumber));
        when(persister.upsert(winningNumber)).thenReturn(UpsertOutcome.INSERTED);

        CollectResponse result = service.collectMissingDraws();

        assertThat(result.collected()).isEqualTo(1);
        verify(lottoApiClient).fetch(2);
    }

    @Test
    @DisplayName("잘못된 회차 범위의 백필 요청은 거부한다")
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
