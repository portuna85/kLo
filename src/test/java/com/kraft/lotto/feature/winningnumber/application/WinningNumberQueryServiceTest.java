package com.kraft.lotto.feature.winningnumber.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.kraft.lotto.feature.statistics.application.WinningStatisticsService;
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
import org.junit.jupiter.api.Assertions;
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
@DisplayName("당첨 번호 조회 서비스 테스트")
class WinningNumberQueryServiceTest {

    @Mock
    WinningNumberRepository repository;

    WinningNumberQueryService service;

    @BeforeEach
    void setUp() {
        service = new WinningNumberQueryService(repository, new WinningStatisticsService(repository));
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
    @DisplayName("최신 당첨 번호를 조회한다")
    void getLatestReturnsLatest() {
        when(repository.findTopByOrderByRoundDesc()).thenReturn(Optional.of(entity(1100)));

        var dto = service.getLatest();

        assertThat(dto.round()).isEqualTo(1100);
        assertThat(dto.numbers()).hasSize(6);
    }

    @Test
    @DisplayName("당첨 번호가 없으면 최신 조회가 실패한다")
    void getLatestThrowsNotFoundWhenAbsent() {
        when(repository.findTopByOrderByRoundDesc()).thenReturn(Optional.empty());

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(service::getLatest)
                .extracting(BusinessException::getErrorCode)
                .isEqualTo(ErrorCode.WINNING_NUMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("해당 회차의 당첨 번호가 없으면 예외가 발생한다")
    void getByRoundThrowsNotFoundWhenAbsent() {
        when(repository.findById(2999)).thenReturn(Optional.empty());

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.getByRound(2999))
                .extracting(BusinessException::getErrorCode)
                .isEqualTo(ErrorCode.WINNING_NUMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("회차가 양수가 아니면 예외가 발생한다")
    void getByRoundThrowsNotFoundWhenRoundIsNonPositive() {
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.getByRound(0))
                .extracting(BusinessException::getErrorCode)
                .isEqualTo(ErrorCode.LOTTO_INVALID_TARGET_ROUND);
    }

    @Test
    @DisplayName("목록 조회 시 페이징과 기본값을 적용한다")
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
    @DisplayName("번호별 출현 빈도를 집계한다")
    void frequencyAggregatesOnlyMainNumbersAndReturnsAll45() {
        // ?????而?嶺뚮ㅄ維筌??곌랜梨띈떋??[1,7,13,22,34,45] / ?곌랜????8 ???곌랜?????노츎 嶺뚯쉶理????戮곕뇶
        Object[] row = {1, 7, 13, 22, 34, 45};
        when(repository.findAllNumbersForFrequency()).thenReturn(List.of(row, row));

        var result = service.frequency();

        assertThat(result).hasSize(45);
        assertThat(result.get(0).number()).isEqualTo(1);
        assertThat(result.get(44).number()).isEqualTo(45);
        // ?곌랜梨띈떋?筌뤾퍔夷??繹먮냱???1??7??13??22??34??45?뺢퀡?? 2?????
        assertThat(result.get(0).count()).isEqualTo(2);
        assertThat(result.get(6).count()).isEqualTo(2);
        assertThat(result.get(44).count()).isEqualTo(2);
        // 8?뺢퀡?? ?곌랜????嶺뚯쉶理????戮곕뇶)?띠럾? ?熬곣뫀鍮?projection????怨몃さ亦껋깢???0??
        assertThat(result.get(7).count()).isZero();
        // ?????? ??? 2?뺢퀡?? 0??
        assertThat(result.get(1).count()).isZero();
    }

    @Test
    @DisplayName("조합 이력 캐시 키는 순서에 상관없이 동일하다")
    void combinationHistoryCacheKeyIsOrderInsensitive() {
        String key1 = WinningStatisticsService.combinationHistoryCacheKey(List.of(1, 7, 13, 22, 34, 45));
        String key2 = WinningStatisticsService.combinationHistoryCacheKey(List.of(45, 22, 13, 7, 34, 1));
        Assertions.assertEquals(key1, key2);
    }
}

