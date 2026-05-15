package com.kraft.lotto.feature.winningnumber.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kraft.lotto.feature.winningnumber.infrastructure.WinningNumberRepository;
import com.kraft.lotto.infra.config.KraftApiProperties;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.client.RestClient;

@DisplayName("LottoApiClientConfig")
class LottoApiClientConfigTest {

    @Test
    @DisplayName("mock-latest-round이 0이면 DB max round + 1을 mock latest round로 사용한다")
    void usesDbMaxRoundPlusOneWhenMockLatestRoundIsZero() {
        LottoApiClientConfig config = new LottoApiClientConfig();
        KraftApiProperties properties = new KraftApiProperties(
                "mock",
                "http://localhost",
                1000,
                1000,
                0,
                0,
                false,
                0
        );
        WinningNumberRepository repository = mock(WinningNumberRepository.class);
        when(repository.findMaxRound()).thenReturn(Optional.of(1223));

        LottoApiClient client = config.lottoApiClient(
                properties,
                RestClient.create(),
                fixedProvider(new ObjectMapper()),
                emptyProvider(),
                fixedProvider(repository)
        );

        assertThat(client.fetch(1224)).isPresent();
        assertThat(client.fetch(1225)).isEmpty();
    }

    private static <T> ObjectProvider<T> fixedProvider(T value) {
        return new ObjectProvider<>() {
            @Override
            public T getObject(Object... args) {
                return value;
            }

            @Override
            public T getIfAvailable() {
                return value;
            }

            @Override
            public T getIfUnique() {
                return value;
            }
        };
    }

    private static <T> ObjectProvider<T> emptyProvider() {
        return new ObjectProvider<>() {
            @Override
            public T getObject(Object... args) {
                return null;
            }

            @Override
            public T getIfAvailable() {
                return null;
            }

            @Override
            public T getIfUnique() {
                return null;
            }
        };
    }
}
