package com.kraft.lotto.feature.winningnumber.application;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@DisplayName("LottoCollectionService")
class LottoCollectionServiceTest {

    private final LottoApiClient lottoApiClient = mock(LottoApiClient.class);
    private final WinningNumberRepository winningNumberRepository = mock(WinningNumberRepository.class);
    private final WinningNumberPersister persister = mock(WinningNumberPersister.class);
    private final LottoFetchLogRepository fetchLogRepository = mock(LottoFetchLogRepository.class);
    private final LottoSingleDrawCollector singleDrawCollector = new LottoSingleDrawCollector(
            lottoApiClient, winningNumberRepository, persister, fetchLogRepository, Clock.systemUTC());

    private final LottoCollectionCommandService commandService = new LottoCollectionCommandService(
            winningNumberRepository,
            singleDrawCollector,
            new LottoRangeCollector(singleDrawCollector, winningNumberRepository, 0, null)
    );
    private final LottoCollectionService service = new LottoCollectionService(commandService);

    @Test
    void collectNextIfNeededCollectsOnlyNextRound() {
        WinningNumber winningNumber = sample(1103);
        when(winningNumberRepository.findMaxRound()).thenReturn(Optional.of(1102), Optional.of(1103));
        when(winningNumberRepository.existsByRound(1103)).thenReturn(false);
        when(lottoApiClient.fetch(1103)).thenReturn(Optional.of(winningNumber));
        when(persister.upsert(winningNumber)).thenReturn(UpsertOutcome.INSERTED);

        CollectResponse result = service.collectNextIfNeeded();

        assertThat(result.collected()).isEqualTo(1);
        verify(lottoApiClient).fetch(1103);
    }

    @Test
    void collectNextIfNeededSkipsAlreadyExistingRound() {
        when(winningNumberRepository.findMaxRound()).thenReturn(Optional.of(1102));
        when(winningNumberRepository.existsByRound(1103)).thenReturn(true);

        CollectResponse result = service.collectNextIfNeeded();

        assertThat(result.skipped()).isEqualTo(1);
        verify(lottoApiClient, never()).fetch(1103);
        verify(fetchLogRepository).save(any(LottoFetchLogEntity.class));
    }

    @Test
    void collectMissingOnceCollectsOnlyMissingRounds() {
        WinningNumber winningNumber = sample(2);
        when(winningNumberRepository.findMaxRound()).thenReturn(Optional.of(3), Optional.of(3));
        when(winningNumberRepository.findRoundsBetween(1, 3)).thenReturn(Set.of(1, 3));
        when(winningNumberRepository.existsByRound(2)).thenReturn(false);
        when(lottoApiClient.fetch(2)).thenReturn(Optional.of(winningNumber));
        when(persister.upsert(winningNumber)).thenReturn(UpsertOutcome.INSERTED);

        CollectResponse result = service.collectMissingOnce();

        assertThat(result.collected()).isEqualTo(1);
        verify(lottoApiClient).fetch(2);
    }

    @Test
    void successLogDoesNotPersistRawResponse() {
        WinningNumber winningNumber = sample(1103);
        when(winningNumberRepository.findMaxRound()).thenReturn(Optional.of(1102), Optional.of(1103));
        when(winningNumberRepository.existsByRound(1103)).thenReturn(false);
        when(lottoApiClient.fetch(1103)).thenReturn(Optional.of(winningNumber));
        when(persister.upsert(winningNumber)).thenReturn(UpsertOutcome.INSERTED);

        service.collectNextIfNeeded();

        ArgumentCaptor<LottoFetchLogEntity> captor = ArgumentCaptor.forClass(LottoFetchLogEntity.class);
        verify(fetchLogRepository).save(captor.capture());
        assertThat(captor.getValue().getRawResponse()).isNull();
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