package com.kraft.lotto.feature.winningnumber.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kraft.lotto.feature.winningnumber.domain.LottoCombination;
import com.kraft.lotto.feature.winningnumber.domain.WinningNumber;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 동행복권(dhlottery.co.kr) JSON API 어댑터.
 * 호출 형태: {@code GET ${url}?method=getLottoNumber&drwNo={round}}.
 *
 * 응답에서 {@code returnValue == "fail"}이면 미추첨 회차로 간주하여 {@link Optional#empty()}를 반환한다.
 * 그 외 네트워크/파싱 오류는 {@link LottoApiClientException}로 변환한다.
 */
public class DhLotteryApiClient implements LottoApiClient {

    private static final Logger log = LoggerFactory.getLogger(DhLotteryApiClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final int maxRetries;
    private final int retryBackoffMs;
    private final MeterRegistry meterRegistry;

    public DhLotteryApiClient(RestClient restClient, ObjectMapper objectMapper, String baseUrl) {
        this(restClient, objectMapper, baseUrl, 0, 0, null);
    }

    public DhLotteryApiClient(RestClient restClient,
                              ObjectMapper objectMapper,
                              String baseUrl,
                              int maxRetries,
                              int retryBackoffMs,
                              MeterRegistry meterRegistry) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.maxRetries = Math.max(0, maxRetries);
        this.retryBackoffMs = Math.max(0, retryBackoffMs);
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Optional<WinningNumber> fetch(int round) {
        int attempts = maxRetries + 1;
        count("kraft.api.dhlottery.call.total", "round", String.valueOf(round));
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                String body = restClient.get()
                        .uri(baseUrl + "?method=getLottoNumber&drwNo=" + round)
                        .retrieve()
                        .body(String.class);
                if (body == null || body.isBlank()) {
                    count("kraft.api.dhlottery.call.failure", "reason", "blank_body");
                    throw new LottoApiClientException("외부 API 응답이 비어 있습니다 (round=" + round + ")");
                }
                Optional<WinningNumber> parsed = parse(round, body);
                if (parsed.isEmpty()) {
                    count("kraft.api.dhlottery.call.empty", "reason", "not_drawn");
                } else {
                    count("kraft.api.dhlottery.call.success");
                }
                return parsed;
            } catch (RestClientException ex) {
                count("kraft.api.dhlottery.call.failure", "reason", "network");
                if (attempt >= attempts) {
                    throw new LottoApiClientException(
                            "외부 API 호출 실패 (round=" + round + ", attempts=" + attempts + ")", ex);
                }
                count("kraft.api.dhlottery.call.retry");
                log.warn("dhlottery 호출 실패, 재시도합니다: round={}, attempt={}/{}", round, attempt, attempts, ex);
                sleepBackoff();
            }
        }
    }

    Optional<WinningNumber> parse(int round, String body) {
        JsonNode node;
        try {
            node = objectMapper.readTree(body);
        } catch (Exception ex) {
            count("kraft.api.dhlottery.call.failure", "reason", "json_parse");
            throw new LottoApiClientException("외부 API 응답 파싱 실패 (round=" + round + ")", ex);
        }
        String returnValue = node.path("returnValue").asText("");
        if ("fail".equalsIgnoreCase(returnValue)) {
            log.debug("dhlottery returned fail for round={}", round);
            return Optional.empty();
        }
        try {
            int drwNo = node.path("drwNo").asInt();
            if (drwNo != round) {
                throw new LottoApiClientException(
                        "응답 회차 불일치: 요청=" + round + ", 응답=" + drwNo);
            }
            LocalDate drawDate = LocalDate.parse(node.path("drwNoDate").asText());
            List<Integer> mains = List.of(
                    node.path("drwtNo1").asInt(),
                    node.path("drwtNo2").asInt(),
                    node.path("drwtNo3").asInt(),
                    node.path("drwtNo4").asInt(),
                    node.path("drwtNo5").asInt(),
                    node.path("drwtNo6").asInt()
            );
            int bonus = node.path("bnusNo").asInt();
            long firstPrize = node.path("firstWinamnt").asLong();
            int firstWinners = node.path("firstPrzwnerCo").asInt();
            long totalSales = node.path("totSellamnt").asLong();
            return Optional.of(new WinningNumber(
                    drwNo,
                    drawDate,
                    new LottoCombination(mains),
                    bonus,
                    firstPrize,
                    firstWinners,
                    totalSales
            ));
        } catch (LottoApiClientException ex) {
            count("kraft.api.dhlottery.call.failure", "reason", "validation");
            throw ex;
        } catch (DateTimeParseException | IllegalArgumentException | NullPointerException ex) {
            count("kraft.api.dhlottery.call.failure", "reason", "transform");
            throw new LottoApiClientException(
                    "외부 API 응답 변환 실패 (round=" + round + "): " + ex.getMessage(), ex);
        }
    }

    private void sleepBackoff() {
        if (retryBackoffMs <= 0) {
            return;
        }
        try {
            Thread.sleep(retryBackoffMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new LottoApiClientException("재시도 대기 중 인터럽트가 발생했습니다.", ie);
        }
    }

    private void count(String metricName, String... tags) {
        if (meterRegistry == null) {
            return;
        }
        meterRegistry.counter(metricName, tags).increment();
    }
}
