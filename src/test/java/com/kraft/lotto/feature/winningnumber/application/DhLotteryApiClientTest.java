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

@DisplayName("동행복권 API 클라이언트 테스트")
class DhLotteryApiClientTest {

    private final DhLotteryApiClient client =
            new DhLotteryApiClient(null, new ObjectMapper(), "http://localhost");

    @Nested
    @DisplayName("응답 파싱 테스트")
    class Parse {

        @Test
        @DisplayName("유효한 응답을 도메인 객체로 변환한다")
        void parseConvertsValidResponseToDomain() {
            String body = """
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
                      "drwNo": 1102
                    }
                    """;

            Optional<WinningNumber> result = client.parse(1102, body);

            assertThat(result).isPresent();
            WinningNumber wn = result.get();
            assertThat(wn.round()).isEqualTo(1102);
            assertThat(wn.drawDate()).isEqualTo(LocalDate.of(2024, 1, 6));
        }

        @Test
        @DisplayName("필수 필드가 누락되면 예외가 발생한다")
        void parseThrowsWhenRequiredFieldIsMissing() {
            String body = """
                    {"returnValue": "success", "drwNoDate": "2024-01-06"}
                    """;
            assertThatThrownBy(() -> client.parse(1102, body)).isInstanceOf(LottoApiClientException.class);
        }

        @Test
        @DisplayName("HTML 응답이 오면 예외가 발생한다")
        void parseThrowsOnHtmlResponse() {
            assertThatThrownBy(() -> client.parse(1102, "<html>error</html>"))
                    .isInstanceOf(LottoApiClientException.class);
        }

        @Test
        @DisplayName("빈 응답이 오면 예외가 발생한다")
        void parseThrowsOnBlankResponse() {
            assertThatThrownBy(() -> client.parse(1102, " "))
                    .isInstanceOf(LottoApiClientException.class);
        }

        @Test
        @DisplayName("returnValue가 fail이면 예외가 발생한다")
        void parseThrowsOnReturnValueFail() {
            String body = """
                    {"returnValue":"fail","drwNo":1102}
                    """;
            assertThatThrownBy(() -> client.parse(1102, body))
                    .isInstanceOf(LottoApiClientException.class);
        }

        @Test
        @DisplayName("회차가 일치하지 않으면 예외가 발생한다")
        void parseThrowsOnRoundMismatch() {
            String body = """
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
                      "drwNo": 1103
                    }
                    """;
            assertThatThrownBy(() -> client.parse(1102, body))
                    .isInstanceOf(LottoApiClientException.class);
        }

        @Test
        @DisplayName("정수 범위를 초과하는 값이 오면 예외가 발생한다")
        void parseThrowsOnIntOverflow() {
            String body = """
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
                      "drwtNo1": 2147483648,
                      "drwNo": 1102
                    }
                    """;
            assertThatThrownBy(() -> client.parse(1102, body))
                    .isInstanceOf(LottoApiClientException.class);
        }
    }

    @Nested
    @DisplayName("재시도 로직 테스트")
    class FetchRetry {

        @Test
        @DisplayName("네트워크 오류 발생 시 재시도한다")
        void fetchRetriesOnNetworkFailure() {
            DhLotteryApiClient spyClient = spy(new DhLotteryApiClient(null, new ObjectMapper(), "http://localhost", 2, 0, null));
            String successBody = """
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
                      "drwNo": 1102
                    }
                    """;
            doThrow(new LottoApiClientException("network")).doReturn(new DhLotteryApiClient.ApiRawResponse(200, "application/json", successBody))
                    .when(spyClient).doFetch(1102);

            Optional<WinningNumber> result = spyClient.fetch(1102);

            assertThat(result).isPresent();
            verify(spyClient, times(2)).doFetch(1102);
        }

        @Test
        @DisplayName("재시도 횟수를 초과하면 예외가 발생한다")
        void fetchThrowsWhenRetryExhausted() {
            DhLotteryApiClient spyClient = spy(new DhLotteryApiClient(null, new ObjectMapper(), "http://localhost", 2, 0, null));
            doThrow(new LottoApiClientException("network")).when(spyClient).doFetch(1102);

            assertThatThrownBy(() -> spyClient.fetch(1102))
                    .isInstanceOf(LottoApiClientException.class)
                    .hasMessageContaining("attempts=3");

            verify(spyClient, times(3)).doFetch(1102);
        }
    }
}
