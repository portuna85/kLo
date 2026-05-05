package com.kraft.lotto.feature.winningnumber.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.kraft.lotto.feature.winningnumber.domain.LottoCombination;
import com.kraft.lotto.feature.winningnumber.domain.WinningNumber;
import com.kraft.lotto.feature.winningnumber.infrastructure.WinningNumberEntity;
import com.kraft.lotto.feature.winningnumber.infrastructure.WinningNumberMapper;
import com.kraft.lotto.feature.winningnumber.infrastructure.WinningNumberRepository;
import com.kraft.lotto.support.BusinessException;
import com.kraft.lotto.support.ErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
@DisplayName("WinningNumberQueryService")
class WinningNumberQueryServiceTest {

    @Mock
    WinningNumberRepository repository;

    WinningNumberQueryService service;

    @BeforeEach
    void setUp() {
        service = new WinningNumberQueryService(repository);
    }

    private static WinningNumberEntity entity(int round) {
        WinningNumber wn = new WinningNumber(
                round,
                LocalDate.of(2024, 1, 1).plusWeeks(round),
                new LottoCombination(List.of(1, 7, 13, 22, 34, 45)),
                8, 1_000_000_000L, 1, 50_000_000_000L);
        return WinningNumberMapper.toEntity(wn, LocalDateTime.now());
    }

    @Test
    @DisplayName("getLatest 는 최신 회차를 반환한다")
    void getLatestReturnsLatest() {
        when(repository.findTopByOrderByRoundDesc()).thenReturn(Optional.of(entity(1100)));

        var dto = service.getLatest();

        assertThat(dto.round()).isEqualTo(1100);
        assertThat(dto.numbers()).hasSize(6);
    }

    @Test
    @DisplayName("getLatest 결과가 없으면 BusinessException(WINNING_NUMBER_NOT_FOUND) 을 던진다")
    void getLatestThrowsNotFoundWhenAbsent() {
        when(repository.findTopByOrderByRoundDesc()).thenReturn(Optional.empty());

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(service::getLatest)
                .extracting(BusinessException::getErrorCode)
                .isEqualTo(ErrorCode.WINNING_NUMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("getByRound 결과가 없으면 NOT_FOUND 를 던진다")
    void getByRoundThrowsNotFoundWhenAbsent() {
        when(repository.findById(2999)).thenReturn(Optional.empty());

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.getByRound(2999))
                .extracting(BusinessException::getErrorCode)
                .isEqualTo(ErrorCode.WINNING_NUMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("getByRound 에 0 이하 회차를 주면 NOT_FOUND 를 던진다")
    void getByRoundThrowsNotFoundWhenRoundIsNonPositive() {
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.getByRound(0));
    }

    @Test
    @DisplayName("list 는 size 상한 및 기본값을 적용한다")
    void listAppliesSizeBoundsAndDefaults() {
        Page<WinningNumberEntity> page = new PageImpl<>(List.of(entity(2), entity(1)), PageRequest.of(0, 100), 2);
        when(repository.findAllByOrderByRoundDesc(any())).thenReturn(page);

        var result = service.list(-3, 5_000);

        assertThat(result.content()).hasSize(2);
        assertThat(result.size()).isEqualTo(100);
        assertThat(result.page()).isZero();
        assertThat(result.totalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("frequency 는 본번호만 집계하고 1~45 모두 반환한다")
    void frequencyAggregatesOnlyMainNumbersAndReturnsAll45() {
        // 두 회차 모두 본번호 [1,7,13,22,34,45] / 보너스 8 → 보너스는 집계 제외
        Object[] row = {1, 7, 13, 22, 34, 45};
        when(repository.findAllNumbersForFrequency()).thenReturn(List.of(row, row));

        var result = service.frequency();

        assertThat(result).hasSize(45);
        assertThat(result.get(0).number()).isEqualTo(1);
        assertThat(result.get(44).number()).isEqualTo(45);
        // 본번호로 등장한 1번/7번/13번/22번/34번/45번은 2회씩
        assertThat(result.get(0).count()).isEqualTo(2);
        assertThat(result.get(6).count()).isEqualTo(2);
        assertThat(result.get(44).count()).isEqualTo(2);
        // 8번은 보너스(집계 제외)가 아닌 projection에 없으므로 0회
        assertThat(result.get(7).count()).isZero();
        // 사용되지 않은 2번은 0회
        assertThat(result.get(1).count()).isZero();
    }
}

