package com.kraft.lotto.feature.winningnumber.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kraft.lotto.feature.winningnumber.domain.LottoCombination;
import com.kraft.lotto.feature.winningnumber.domain.WinningNumber;
import com.kraft.lotto.feature.winningnumber.infrastructure.WinningNumberEntity;
import com.kraft.lotto.feature.winningnumber.infrastructure.WinningNumberRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

@DisplayName("당첨번호 저장기 테스트")
class WinningNumberPersisterTest {

    private final WinningNumberRepository repository = mock(WinningNumberRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-05-14T00:00:00Z"), ZoneOffset.UTC);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final WinningNumberPersister persister = new WinningNumberPersister(repository, clock, meterRegistry);

    @Test
    @DisplayName("saveIfAbsent는 회차가 없으면 저장하고 true를 반환한다")
    void saveIfAbsentStoresWhenMissing() {
        WinningNumber winningNumber = sample(1200);
        when(repository.existsByRound(1200)).thenReturn(false);

        boolean result = persister.saveIfAbsent(1200, winningNumber);

        assertThat(result).isTrue();
        verify(repository).save(any(WinningNumberEntity.class));
    }

    @Test
    @DisplayName("saveIfAbsent는 이미 회차가 있으면 저장하지 않고 false를 반환한다")
    void saveIfAbsentSkipsWhenExists() {
        when(repository.existsByRound(1200)).thenReturn(true);

        boolean result = persister.saveIfAbsent(1200, sample(1200));

        assertThat(result).isFalse();
        verify(repository, never()).save(any(WinningNumberEntity.class));
    }

    @Test
    @DisplayName("saveIfAbsent는 저장 충돌 시 false를 반환한다")
    void saveIfAbsentReturnsFalseOnConflict() {
        when(repository.existsByRound(1200)).thenReturn(false);
        doThrow(new DataIntegrityViolationException("dup")).when(repository).save(any(WinningNumberEntity.class));

        boolean result = persister.saveIfAbsent(1200, sample(1200));

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("upsert는 동일 데이터면 UNCHANGED를 반환한다")
    void upsertReturnsUnchangedForSameData() {
        WinningNumber winningNumber = sample(1200);
        when(repository.findById(1200)).thenReturn(Optional.of(entityFrom(winningNumber)));

        UpsertOutcome outcome = persister.upsert(winningNumber);

        assertThat(outcome).isEqualTo(UpsertOutcome.UNCHANGED);
        verify(repository, never()).save(any(WinningNumberEntity.class));
    }

    @Test
    @DisplayName("upsert는 기존 데이터가 다르면 UPDATED를 반환하고 엔티티를 갱신한다")
    void upsertReturnsUpdatedWhenDataChanged() {
        WinningNumber existing = sample(1200);
        WinningNumber incoming = new WinningNumber(
                1200,
                LocalDate.of(2026, 5, 10),
                new LottoCombination(List.of(1, 2, 3, 4, 5, 6)),
                7,
                3_000_000_000L,
                9,
                80_000_000_000L,
                30_000_000_000L,
                "{\"returnValue\":\"success\"}",
                null
        );
        WinningNumberEntity existingEntity = entityFrom(existing);
        when(repository.findById(1200)).thenReturn(Optional.of(existingEntity));

        UpsertOutcome outcome = persister.upsert(incoming);

        assertThat(outcome).isEqualTo(UpsertOutcome.UPDATED);
        assertThat(existingEntity.getN1()).isEqualTo(1);
        assertThat(existingEntity.getBonusNumber()).isEqualTo(7);
    }

    @Test
    @DisplayName("upsert는 신규 데이터면 INSERTED를 반환한다")
    void upsertReturnsInsertedWhenMissing() {
        WinningNumber winningNumber = sample(1201);
        when(repository.findById(1201)).thenReturn(Optional.empty());

        UpsertOutcome outcome = persister.upsert(winningNumber);

        assertThat(outcome).isEqualTo(UpsertOutcome.INSERTED);
        ArgumentCaptor<WinningNumberEntity> captor = ArgumentCaptor.forClass(WinningNumberEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getRound()).isEqualTo(1201);
    }

    @Test
    @DisplayName("upsert는 신규 저장 충돌 시 UNCHANGED를 반환한다")
    void upsertReturnsUnchangedOnInsertConflict() {
        WinningNumber winningNumber = sample(1202);
        when(repository.findById(1202)).thenReturn(Optional.empty());
        doThrow(new DataIntegrityViolationException("dup")).when(repository).save(any(WinningNumberEntity.class));

        UpsertOutcome outcome = persister.upsert(winningNumber);

        assertThat(outcome).isEqualTo(UpsertOutcome.UNCHANGED);
    }

    private WinningNumber sample(int round) {
        return new WinningNumber(
                round,
                LocalDate.of(2026, 5, 3),
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

    private WinningNumberEntity entityFrom(WinningNumber winningNumber) {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), clock.getZone());
        return new WinningNumberEntity(
                winningNumber.round(),
                winningNumber.drawDate(),
                winningNumber.combination().numbers().get(0),
                winningNumber.combination().numbers().get(1),
                winningNumber.combination().numbers().get(2),
                winningNumber.combination().numbers().get(3),
                winningNumber.combination().numbers().get(4),
                winningNumber.combination().numbers().get(5),
                winningNumber.bonusNumber(),
                winningNumber.firstPrize(),
                winningNumber.firstWinners(),
                winningNumber.totalSales(),
                winningNumber.firstAccumAmount(),
                winningNumber.rawJson(),
                now,
                now,
                now
        );
    }
}
