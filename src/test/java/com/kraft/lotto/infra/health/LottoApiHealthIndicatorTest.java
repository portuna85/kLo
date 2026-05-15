package com.kraft.lotto.infra.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.kraft.lotto.feature.winningnumber.infrastructure.WinningNumberEntity;
import com.kraft.lotto.feature.winningnumber.infrastructure.WinningNumberRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

@ExtendWith(MockitoExtension.class)
@DisplayName("로또 API 헬스 인디케이터 테스트")
class LottoApiHealthIndicatorTest {

    @Mock
    WinningNumberRepository winningNumberRepository;

    LottoApiHealthIndicator indicator;

    @BeforeEach
    void setUp() {
        indicator = new LottoApiHealthIndicator(winningNumberRepository);
    }

    @Test
    @DisplayName("레포지토리 쿼리 성공 시 UP을 반환한다")
    void returnsUpWhenRepositoryQuerySucceeds() {
        WinningNumberEntity latest = org.mockito.Mockito.mock(WinningNumberEntity.class);
        when(winningNumberRepository.findMaxRound()).thenReturn(Optional.of(1100));
        when(winningNumberRepository.findTopByOrderByRoundDesc()).thenReturn(Optional.of(latest));
        when(latest.getFetchedAt()).thenReturn(LocalDateTime.of(2026, 5, 11, 12, 0));

        Health result = indicator.health();
        assertThat(result.getStatus()).isEqualTo(Status.UP);
        assertThat(result.getDetails()).containsEntry("latestStoredRound", 1100);
        assertThat(result.getDetails()).containsKey("lastCollectedAt");
    }

    @Test
    @DisplayName("레포지토리 쿼리 실패 시 DOWN을 반환한다")
    void returnsDownWhenRepositoryQueryFails() {
        when(winningNumberRepository.findMaxRound()).thenThrow(new RuntimeException("db down"));

        Health result = indicator.health();

        assertThat(result.getStatus()).isEqualTo(Status.DOWN);
        assertThat(result.getDetails()).containsEntry("error", "repository_query_failed");
    }
}
