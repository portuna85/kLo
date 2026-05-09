package com.kraft.lotto.feature.winningnumber.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kraft.lotto.feature.winningnumber.domain.WinningNumber;
import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@DisplayName("DhLotteryApiClient")
class DhLotteryApiClientTest {

    @Test
    @DisplayName("parse 는 숫자 필드가 문자열이면 예외를 던진다")
    void parseThrowsWhenNumberFieldIsString() {
        String body = """
                {
                  "returnValue": "success",
                  "drwNoDate": "2024-01-06",
                  "drwtNo1": "not-a-number", "drwtNo2": 13, "drwtNo3": 23,
                  "drwtNo4": 24, "drwtNo5": 28, "drwtNo6": 33,
                  "bnusNo": 38, "firstWinamnt": 0, "firstPrzwnerCo": 0, "totSellamnt": 0,
                  "drwNo": 1102
                }
                """;
        assertThatThrownBy(() -> client.parse(1102, body))
                .isInstanceOf(LottoApiClientException.class)
                .hasMessageContaining("정수가 아닙니다");
    }

    @Test
    @DisplayName("parse 는 숫자 필드가 null 이면 예외를 던진다")
    void parseThrowsWhenNumberFieldIsNull() {
        String body = """
                {
                  "returnValue": "success",
                  "drwNoDate": "2024-01-06",
                  "drwtNo1": null, "drwtNo2": 13, "drwtNo3": 23,
                  "drwtNo4": 24, "drwtNo5": 28, "drwtNo6": 33,
                  "bnusNo": 38, "firstWinamnt": 0, "firstPrzwnerCo": 0, "totSellamnt": 0,
                  "drwNo": 1102
                }
                """;
        assertThatThrownBy(() -> client.parse(1102, body))
                .isInstanceOf(LottoApiClientException.class)
                .hasMessageContaining("누락");
    }

    @Test
    @DisplayName("parse 는 숫자 필드가 boolean 이면 예외를 던진다")
    void parseThrowsWhenNumberFieldIsBoolean() {
        String body = """
                {
                  "returnValue": "success",
                  "drwNoDate": "2024-01-06",
                  "drwtNo1": true, "drwtNo2": 13, "drwtNo3": 23,
                  "drwtNo4": 24, "drwtNo5": 28, "drwtNo6": 33,
                  "bnusNo": 38, "firstWinamnt": 0, "firstPrzwnerCo": 0, "totSellamnt": 0,
                  "drwNo": 1102
                }
                """;
        assertThatThrownBy(() -> client.parse(1102, body))
                .isInstanceOf(LottoApiClientException.class)
                .hasMessageContaining("정수가 아닙니다");
    }

    @Test
    @DisplayName("parse 는 숫자 필드가 소수이면 예외를 던진다")
    void parseThrowsWhenNumberFieldIsDecimal() {
        String body = """
                {
                  "returnValue": "success",
                  "drwNoDate": "2024-01-06",
                  "drwtNo1": 6.5, "drwtNo2": 13, "drwtNo3": 23,
                  "drwtNo4": 24, "drwtNo5": 28, "drwtNo6": 33,
                  "bnusNo": 38, "firstWinamnt": 0, "firstPrzwnerCo": 0, "totSellamnt": 0,
                  "drwNo": 1102
                }
                """;
        assertThatThrownBy(() -> client.parse(1102, body))
                .isInstanceOf(LottoApiClientException.class)
                .hasMessageContaining("정수가 아닙니다");
    }

    @Test
    @DisplayName("parse 는 long 범위를 벗어난 금액 필드이면 예외를 던진다")
    void parseThrowsWhenLongFieldOverflows() {
        String body = """
                {
                  "returnValue": "success",
                  "drwNoDate": "2024-01-06",
                  "drwtNo1": 6, "drwtNo2": 13, "drwtNo3": 23,
                  "drwtNo4": 24, "drwtNo5": 28, "drwtNo6": 33,
                  "bnusNo": 38, "firstWinamnt": 9223372036854775808, "firstPrzwnerCo": 0, "totSellamnt": 0,
                  "drwNo": 1102
                }
                """;
        assertThatThrownBy(() -> client.parse(1102, body))
                .isInstanceOf(LottoApiClientException.class)
                .hasMessageContaining("long 범위를 벗어납니다");
    }

    private final DhLotteryApiClient client =
            new DhLotteryApiClient(null, new ObjectMapper(), "http://localhost");

    @Test
    @DisplayName("parse 는 정상 응답을 도메인으로 변환한다")
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
        assertThat(wn.combination().numbers()).containsExactly(6, 13, 23, 24, 28, 33);
        assertThat(wn.bonusNumber()).isEqualTo(38);
        assertThat(wn.firstPrize()).isEqualTo(2_596_477_500L);
        assertThat(wn.firstWinners()).isEqualTo(11);
        assertThat(wn.totalSales()).isEqualTo(79_760_843_000L);
    }

    @Test
    @DisplayName("parse 는 returnValue 가 fail 이면 empty 를 반환한다")
    void parseReturnsEmptyWhenReturnValueIsFail() {
        String body = "{\"returnValue\":\"fail\"}";

        Optional<WinningNumber> result = client.parse(99999, body);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("parse 는 필수 필드가 누락되면 예외를 던진다")
    void parseThrowsWhenRequiredFieldIsMissing() {
        String body = """
                {
                  "returnValue": "success",
                  "drwNoDate": "2024-01-06",
                  "drwtNo1": 6, "drwtNo2": 13, "drwtNo3": 23,
                  "drwtNo4": 24, "drwtNo5": 28, "drwtNo6": 33,
                  "bnusNo": 38, "firstWinamnt": 0, "firstPrzwnerCo": 0, "totSellamnt": 0
                }
                """;

        assertThatThrownBy(() -> client.parse(1102, body))
                .isInstanceOf(LottoApiClientException.class)
                .hasMessageContaining("필드가 누락");
    }

    @Test
    @DisplayName("parse 는 예상하지 못한 returnValue 에 대해 예외를 던진다")
    void parseThrowsWhenReturnValueIsUnexpected() {
        String body = "{\"returnValue\":\"pending\"}";

        assertThatThrownBy(() -> client.parse(1102, body))
                .isInstanceOf(LottoApiClientException.class)
                .hasMessageContaining("returnValue");
    }

    @Test
    @DisplayName("parse 는 응답 회차가 불일치하면 예외를 던진다")
    void parseThrowsWhenRoundMismatches() {
        String body = """
                {
                  "returnValue": "success",
                  "drwNoDate": "2024-01-06",
                  "drwtNo1": 6, "drwtNo2": 13, "drwtNo3": 23,
                  "drwtNo4": 24, "drwtNo5": 28, "drwtNo6": 33,
                  "bnusNo": 38, "firstWinamnt": 0, "firstPrzwnerCo": 0, "totSellamnt": 0,
                  "drwNo": 1102
                }
                """;

        assertThatThrownBy(() -> client.parse(1101, body))
                .isInstanceOf(LottoApiClientException.class)
                .hasMessageContaining("회차 불일치");
    }

    @Test
    @DisplayName("parse 는 잘못된 JSON 에 대해 예외를 던진다")
    void parseThrowsOnInvalidJson() {
        assertThatThrownBy(() -> client.parse(1, "not json"))
                .isInstanceOf(LottoApiClientException.class);
    }

    @Test
    @DisplayName("parse 는 도메인 검증 실패를 LottoApiClientException 으로 래핑한다")
    void parseWrapsDomainValidationFailure() {
        // 본번호와 보너스 번호가 중복인 케이스 → WinningNumber 생성 시 IllegalArgumentException
        String body = """
                {
                  "returnValue": "success",
                  "drwNoDate": "2024-01-06",
                  "drwtNo1": 6, "drwtNo2": 13, "drwtNo3": 23,
                  "drwtNo4": 24, "drwtNo5": 28, "drwtNo6": 33,
                  "bnusNo": 6, "firstWinamnt": 0, "firstPrzwnerCo": 0, "totSellamnt": 0,
                  "drwNo": 1102
                }
                """;

        assertThatThrownBy(() -> client.parse(1102, body))
                .isInstanceOf(LottoApiClientException.class);
    }

    @Test
    @DisplayName("fetch 는 네트워크 오류 시 설정된 재시도 횟수 내에서 재시도 후 성공할 수 있다")
    void fetchRetriesOnNetworkFailure() {
        RestClient restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
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
        when(restClient.get().uri(any(URI.class)).retrieve().body(String.class))
                .thenThrow(new RestClientException("temporary network failure"))
                .thenReturn(successBody);

        DhLotteryApiClient retryingClient = new DhLotteryApiClient(
                restClient, new ObjectMapper(), "http://localhost", 2, 0, null);

        Optional<WinningNumber> result = retryingClient.fetch(1102);

        assertThat(result).isPresent();
        assertThat(result.get().round()).isEqualTo(1102);
    }

    @Test
    @DisplayName("fetch 는 재시도를 모두 소진하면 LottoApiClientException 을 던진다")
    void fetchThrowsWhenRetryExhausted() {
        RestClient restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
        AtomicInteger calls = new AtomicInteger();
        when(restClient.get().uri(any(URI.class)).retrieve().body(String.class))
                .thenAnswer(inv -> {
                    calls.incrementAndGet();
                    throw new RestClientException("network down");
                });
        DhLotteryApiClient retryingClient = new DhLotteryApiClient(
                restClient, new ObjectMapper(), "http://localhost", 2, 0, null);

        assertThatThrownBy(() -> retryingClient.fetch(1102))
                .isInstanceOf(LottoApiClientException.class)
                .hasMessageContaining("attempts=3");

        assertThat(calls.get()).isEqualTo(3);
    }

}
