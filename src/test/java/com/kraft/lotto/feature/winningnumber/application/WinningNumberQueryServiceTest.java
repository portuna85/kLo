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
    void getLatestReturnsLatest() {
        when(repository.findTopByOrderByRoundDesc()).thenReturn(Optional.of(entity(1100)));

        var dto = service.getLatest();

        assertThat(dto.round()).isEqualTo(1100);
        assertThat(dto.numbers()).hasSize(6);
    }

    @Test
    void getLatestThrowsNotFoundWhenAbsent() {
        when(repository.findTopByOrderByRoundDesc()).thenReturn(Optional.empty());

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(service::getLatest)
                .extracting(BusinessException::getErrorCode)
                .isEqualTo(ErrorCode.WINNING_NUMBER_NOT_FOUND);
    }

    @Test
    void getByRoundThrowsNotFoundWhenAbsent() {
        when(repository.findById(2999)).thenReturn(Optional.empty());

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.getByRound(2999))
                .extracting(BusinessException::getErrorCode)
                .isEqualTo(ErrorCode.WINNING_NUMBER_NOT_FOUND);
    }

    @Test
    void getByRoundThrowsNotFoundWhenRoundIsNonPositive() {
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.getByRound(0))
                .extracting(BusinessException::getErrorCode)
                .isEqualTo(ErrorCode.LOTTO_INVALID_TARGET_ROUND);
    }

    @Test
    void listAppliesSizeBoundsAndDefaults() {
        Page<WinningNumberEntity> page = new PageImpl<>(List.of(entity(2), entity(1)), PageRequest.of(0, 100), 2);
        when(repository.findAllByOrderByRoundDesc(any())).thenReturn(page);

        var result = service.list(-3, 5_000);

        assertThat(result.content()).hasSize(2);
        assertThat(result.size()).isEqualTo(100);
        assertThat(result.page()).isZero();
        assertThat(result.totalElements()).isEqualTo(2);
    }
}