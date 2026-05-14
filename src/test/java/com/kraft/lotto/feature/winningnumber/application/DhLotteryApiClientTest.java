package com.kraft.lotto.feature.winningnumber.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kraft.lotto.feature.winningnumber.domain.WinningNumber;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("DhLotteryApiClientTest")
class DhLotteryApiClientTest {

    private final DhLotteryApiClient client =
            new DhLotteryApiClient(null, new ObjectMapper(), "http://localhost");

    @Nested
    class Parse {

        @Test
        void parseConvertsValidResponseToDomain() {
            String body = successBody(1102);

            Optional<WinningNumber> result = client.parse(1102, body);

            assertThat(result).isPresent();
            WinningNumber wn = result.get();
            assertThat(wn.round()).isEqualTo(1102);
            assertThat(wn.drawDate()).isEqualTo(LocalDate.of(2024, 1, 6));
        }

        @Test
        void parseThrowsWhenRequiredFieldIsMissing() {
            String body = """
                    {"returnValue": "success", "drwNoDate": "2024-01-06"}
                    """;
            assertThatThrownBy(() -> client.parse(1102, body)).isInstanceOf(LottoApiClientException.class);
        }

        @Test
        void parseThrowsOnHtmlResponse() {
            assertThatThrownBy(() -> client.parse(1102, "<html>error</html>"))
                    .isInstanceOf(LottoApiClientException.class);
        }

        @Test
        void parseThrowsOnBlankResponse() {
            assertThatThrownBy(() -> client.parse(1102, " "))
                    .isInstanceOf(LottoApiClientException.class);
        }

        @Test
        void parseReturnsEmptyOnReturnValueFail() {
            String body = """
                    {"returnValue":"fail","drwNo":1102}
                    """;
            assertThat(client.parse(1102, body)).isEmpty();
        }

        @Test
        void parseThrowsOnRoundMismatch() {
            String body = successBody(1103);
            assertThatThrownBy(() -> client.parse(1102, body))
                    .isInstanceOf(LottoApiClientException.class);
        }
    }

    @Nested
    class FetchRetry {

        @Test
        void fetchRetriesOnNetworkFailure() {
            DhLotteryApiClient spyClient = spy(new DhLotteryApiClient(null, new ObjectMapper(), "http://localhost", 2, 0, null));
            doThrow(new LottoApiClientException("network"))
                    .doReturn(new DhLotteryApiClient.ApiRawResponse(200, "application/json", successBody(1102)))
                    .when(spyClient).doFetch(1102);

            Optional<WinningNumber> result = spyClient.fetch(1102);

            assertThat(result).isPresent();
            verify(spyClient, times(2)).doFetch(1102);
        }

        @Test
        void fetchThrowsWhenRetryExhausted() {
            DhLotteryApiClient spyClient = spy(new DhLotteryApiClient(null, new ObjectMapper(), "http://localhost", 2, 0, null));
            doThrow(new LottoApiClientException("network")).when(spyClient).doFetch(1102);

            assertThatThrownBy(() -> spyClient.fetch(1102))
                    .isInstanceOf(LottoApiClientException.class)
                    .hasMessageContaining("attempts=3");

            verify(spyClient, times(3)).doFetch(1102);
        }
    }

    private static String successBody(int round) {
        return """
                {
                  "totSellamnt": 79760843000,
                  "returnValue": "success",
                  "drwNoDate": "2024-01-06",
                  "firstWinamnt": 2596477500,
                  "drwtNo6": 33,
                  "drwtNo4": 24,
                  "drwtNo5": 28,
                  "bnusNo": 38,
                  "firstPrzwnerCo": 11,
                  "drwtNo2": 13,
                  "drwtNo3": 23,
                  "drwtNo1": 6,
                  "drwNo": %d
                }
                """.formatted(round);
    }
}
