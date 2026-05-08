package com.kraft.lotto.feature.winningnumber.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kraft.lotto.feature.winningnumber.domain.LottoCombination;
import com.kraft.lotto.feature.winningnumber.domain.WinningNumber;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

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

    private static final List<String> ALLOWED_FAILURE_REASONS = List.of(
            "blank_body", "network", "json_parse", "validation", "transform", "unexpected_return_value"
    );

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
        count("kraft.api.dhlottery.call.total");
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                URI uri = UriComponentsBuilder.fromUriString(baseUrl)
                        .queryParam("method", "getLottoNumber")
                        .queryParam("drwNo", round)
                        .build()
                        .toUri();
                String body = restClient.get()
                        .uri(uri)
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
        String returnValue = requiredText(node, "returnValue", round);
        if ("fail".equalsIgnoreCase(returnValue)) {
            log.debug("dhlottery returned fail for round={}", round);
            return Optional.empty();
        }
        if (!"success".equalsIgnoreCase(returnValue)) {
            count("kraft.api.dhlottery.call.failure", "reason", "unexpected_return_value");
            throw new LottoApiClientException(
                    "외부 API 응답 returnValue가 예상과 다릅니다 (round=" + round + ", returnValue=" + returnValue + ")");
        }
        try {
            requireFields(node, round, "drwNo", "drwNoDate", "drwtNo1", "drwtNo2", "drwtNo3",
                    "drwtNo4", "drwtNo5", "drwtNo6", "bnusNo", "firstWinamnt",
                    "firstPrzwnerCo", "totSellamnt");
            int drwNo = requiredInt(node, "drwNo", round);
            if (drwNo != round) {
                throw new LottoApiClientException(
                        "응답 회차 불일치: 요청=" + round + ", 응답=" + drwNo);
            }
            LocalDate drawDate = LocalDate.parse(node.path("drwNoDate").asText());
            List<Integer> mains = List.of(
                    requiredInt(node, "drwtNo1", round),
                    requiredInt(node, "drwtNo2", round),
                    requiredInt(node, "drwtNo3", round),
                    requiredInt(node, "drwtNo4", round),
                    requiredInt(node, "drwtNo5", round),
                    requiredInt(node, "drwtNo6", round)
            );
            int bonus = requiredInt(node, "bnusNo", round);
            long firstPrize = requiredLong(node, "firstWinamnt", round);
            int firstWinners = requiredInt(node, "firstPrzwnerCo", round);
            long totalSales = requiredLong(node, "totSellamnt", round);
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

    /**
     * 필수 int 필드: 존재, null 아님, 정수 타입, int 범위 검증.
     */
    private static int requiredInt(JsonNode node, String fieldName, int round) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            throw new LottoApiClientException("외부 API 응답 필드가 누락되었습니다 (round=" + round + ", field=" + fieldName + ")");
        }
        if (!value.isInt() && !value.isLong() && !value.isNumber()) {
            throw new LottoApiClientException("외부 API 응답 필드가 정수가 아닙니다 (round=" + round + ", field=" + fieldName + ", value=" + value + ")");
        }
        long longVal = value.asLong();
        if (longVal < Integer.MIN_VALUE || longVal > Integer.MAX_VALUE) {
            throw new LottoApiClientException("외부 API 응답 필드가 int 범위를 벗어납니다 (round=" + round + ", field=" + fieldName + ", value=" + longVal + ")");
        }
        return (int) longVal;
    }

    /**
     * 필수 long 필드: 존재, null 아님, 숫자 타입, long 범위 검증.
     */
    private static long requiredLong(JsonNode node, String fieldName, int round) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            throw new LottoApiClientException("외부 API 응답 필드가 누락되었습니다 (round=" + round + ", field=" + fieldName + ")");
        }
        if (!value.isLong() && !value.isInt() && !value.isNumber()) {
            throw new LottoApiClientException("외부 API 응답 필드가 숫자가 아닙니다 (round=" + round + ", field=" + fieldName + ", value=" + value + ")");
        }
        return value.asLong();
    }

    private static void requireFields(JsonNode node, int round, String... fieldNames) {
        for (String fieldName : fieldNames) {
            if (node.path(fieldName).isMissingNode() || node.path(fieldName).isNull()) {
                throw new LottoApiClientException(
                        "외부 API 응답 필드가 누락되었습니다 (round=" + round + ", field=" + fieldName + ")");
            }
        }
    }

    private static String requiredText(JsonNode node, String fieldName, int round) {
        if (node.path(fieldName).isMissingNode() || node.path(fieldName).isNull()) {
            throw new LottoApiClientException(
                    "외부 API 응답 필드가 누락되었습니다 (round=" + round + ", field=" + fieldName + ")");
        }
        return node.path(fieldName).asText();
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
        // metricName이 failure이고 reason 태그가 있으면 값 제한
        if ("kraft.api.dhlottery.call.failure".equals(metricName) && tags.length >= 2 && "reason".equals(tags[0])) {
            String reason = tags[1];
            if (!ALLOWED_FAILURE_REASONS.contains(reason)) {
                tags[1] = "other";
            }
        }
        meterRegistry.counter(metricName, tags).increment();
    }
}
