package com.kraft.lotto.infra.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.kraft.lotto.feature.winningnumber.application.LottoApiClient;
import com.kraft.lotto.feature.winningnumber.application.LottoApiClientException;
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
@DisplayName("LottoApiHealthIndicator")
class LottoApiHealthIndicatorTest {

    @Mock
    LottoApiClient lottoApiClient;

    LottoApiHealthIndicator indicator;

    @BeforeEach
    void setUp() {
        indicator = new LottoApiHealthIndicator(lottoApiClient);
    }

    @Test
    @DisplayName("returns UP when API is reachable")
    void returnsUpWhenApiIsReachable() {
        when(lottoApiClient.fetch(1)).thenReturn(Optional.empty());

        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    @DisplayName("returns DOWN when API is unreachable")
    void returnsDownWhenApiIsUnreachable() {
        when(lottoApiClient.fetch(1)).thenThrow(new LottoApiClientException("timeout"));

        Health result = indicator.health();

        assertThat(result.getStatus()).isEqualTo(Status.DOWN);
        assertThat(result.getDetails()).containsKey("error");
    }
}
