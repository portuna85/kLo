package com.kraft.lotto.feature.winningnumber.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class WinningNumberQueryServiceTest {

    @Mock
    WinningNumberRepository repository;

    private WinningNumberEntity entity(int round) {
        WinningNumber wn = new WinningNumber(
                round,
                LocalDate.of(2024, 1, 1).plusWeeks(round),
                new LottoCombination(List.of(1, 7, 13, 22, 34, 45)),
                8, 1_000_000_000L, 1, 50_000_000_000L);
        return WinningNumberMapper.toEntity(wn, LocalDateTime.now());
    }

    @Test
    void getLatest_정상() {
        WinningNumberQueryService service = new WinningNumberQueryService(repository);
        when(repository.findTopByOrderByRoundDesc()).thenReturn(Optional.of(entity(1100)));

        var dto = service.getLatest();

        assertThat(dto.round()).isEqualTo(1100);
        assertThat(dto.numbers()).hasSize(6);
    }

    @Test
    void getLatest_없으면_BusinessException_NOT_FOUND() {
        WinningNumberQueryService service = new WinningNumberQueryService(repository);
        when(repository.findTopByOrderByRoundDesc()).thenReturn(Optional.empty());

        assertThatThrownBy(service::getLatest)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.WINNING_NUMBER_NOT_FOUND);
    }

    @Test
    void getByRound_없으면_NOT_FOUND() {
        WinningNumberQueryService service = new WinningNumberQueryService(repository);
        when(repository.findById(9999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByRound(9999))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.WINNING_NUMBER_NOT_FOUND);
    }

    @Test
    void getByRound_0이하는_NOT_FOUND() {
        WinningNumberQueryService service = new WinningNumberQueryService(repository);

        assertThatThrownBy(() -> service.getByRound(0))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void list_size_상한_및_기본값_적용() {
        WinningNumberQueryService service = new WinningNumberQueryService(repository);
        Page<WinningNumberEntity> page = new PageImpl<>(List.of(entity(2), entity(1)), PageRequest.of(0, 100), 2);
        when(repository.findAllByOrderByRoundDesc(any())).thenReturn(page);

        var result = service.list(-3, 5_000);

        assertThat(result.content()).hasSize(2);
        assertThat(result.size()).isEqualTo(100);
        assertThat(result.page()).isZero();
        assertThat(result.totalElements()).isEqualTo(2);
    }

    @Test
    void frequency_본번호만_집계하고_1부터_45까지_모두_반환() {
        WinningNumberQueryService service = new WinningNumberQueryService(repository);
        // 두 회차 모두 본번호 [1,7,13,22,34,45] / 보너스 8 → 보너스는 집계 제외
        when(repository.findAllOrderByRoundAsc()).thenReturn(List.of(entity(1), entity(2)));

        var result = service.frequency();

        assertThat(result).hasSize(45);
        assertThat(result.get(0).number()).isEqualTo(1);
        assertThat(result.get(44).number()).isEqualTo(45);
        // 본번호로 등장한 1번/7번/13번/22번/34번/45번은 2회씩
        assertThat(result.get(0).count()).isEqualTo(2);
        assertThat(result.get(6).count()).isEqualTo(2);
        assertThat(result.get(44).count()).isEqualTo(2);
        // 보너스인 8번은 0회 (보너스는 집계 제외)
        assertThat(result.get(7).count()).isZero();
        // 사용되지 않은 2번은 0회
        assertThat(result.get(1).count()).isZero();
    }
}
