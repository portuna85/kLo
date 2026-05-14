package com.kraft.lotto.feature.winningnumber.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kraft.lotto.feature.winningnumber.domain.WinningNumber;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

public class DhLotteryApiClient implements LottoApiClient {

    private static final Logger log = LoggerFactory.getLogger(DhLotteryApiClient.class);

    private final RestClient restClient;
    private final DhLotteryResponseParser responseParser;
    private final String baseUrl;
    private final int maxRetries;
    private final int retryBackoffMs;
    private final MeterRegistry meterRegistry;

    private static final List<String> ALLOWED_FAILURE_REASONS = List.of(
            "blank_body", "network", "json_parse", "validation", "transform", "unexpected_return_value"
    );

    public DhLotteryApiClient(RestClient restClient, ObjectMapper objectMapper, String baseUrl) {
        this(restClient, objectMapper, baseUrl, 0, 0, null, Clock.systemDefaultZone());
    }

    public DhLotteryApiClient(RestClient restClient,
                              ObjectMapper objectMapper,
                              String baseUrl,
                              int maxRetries,
                              int retryBackoffMs,
                              MeterRegistry meterRegistry) {
        this(restClient, objectMapper, baseUrl, maxRetries, retryBackoffMs, meterRegistry, Clock.systemDefaultZone());
    }

    DhLotteryApiClient(RestClient restClient,
                       ObjectMapper objectMapper,
                       String baseUrl,
                       int maxRetries,
                       int retryBackoffMs,
                       MeterRegistry meterRegistry,
                       Clock clock) {
        this.restClient = restClient;
        this.responseParser = new DhLotteryResponseParser(objectMapper, clock);
        this.baseUrl = baseUrl;
        this.maxRetries = Math.max(0, maxRetries);
        this.retryBackoffMs = Math.max(0, retryBackoffMs);
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Optional<WinningNumber> fetch(int round) {
        long started = System.nanoTime();
        int attempts = maxRetries + 1;
        count("kraft.api.dhlottery.call.total");
        int attempt = 0;
        try {
            while (true) {
                attempt++;
                try {
                    ApiRawResponse response = doFetch(round);
                    if (response.statusCode() >= 400) {
                        count("kraft.api.dhlottery.call.failure", "reason", "network");
                        throw new LottoApiClientException("external API HTTP error (round=" + round + ", status=" + response.statusCode()
                                + ", preview=" + preview(response.body()) + ")", response.statusCode(), response.body());
                    }
                    String body = response.body();
                    if (body == null || body.isBlank()) {
                        count("kraft.api.dhlottery.call.failure", "reason", "blank_body");
                        throw new LottoApiClientException("response body is blank (round=" + round + ")",
                                response.statusCode(), response.body());
                    }
                    validateJsonResponse(round, response);
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
                                "external API call failed (round=" + round + ", attempts=" + attempts + ")", ex);
                    }
                    count("kraft.api.dhlottery.call.retry");
                    log.warn("dhlottery call failed, retrying: round={}, attempt={}/{}, reason={}",
                            round, attempt, attempts, ex.getMessage());
                    log.debug("dhlottery retry detail: round={}, attempt={}/{}", round, attempt, attempts, ex);
                    sleepBackoff();
                } catch (LottoApiClientException ex) {
                    if (attempt >= attempts) {
                        throw new LottoApiClientException(
                                "external API call failed (round=" + round + ", attempts=" + attempts + ")", ex);
                    }
                    count("kraft.api.dhlottery.call.retry");
                    log.warn("dhlottery call failed, retrying: round={}, attempt={}/{}, reason={}",
                            round, attempt, attempts, ex.getMessage());
                    log.debug("dhlottery retry detail: round={}, attempt={}/{}", round, attempt, attempts, ex);
                    sleepBackoff();
                }
            }
        } finally {
            if (meterRegistry != null) {
                meterRegistry.timer("kraft.api.dhlottery.latency")
                        .record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
            }
        }
    }

    ApiRawResponse doFetch(int round) {
        URI uri = UriComponentsBuilder.fromUriString(baseUrl)
                .queryParam("method", "getLottoNumber")
                .queryParam("drwNo", round)
                .build()
                .toUri();
        return restClient.get()
                .uri(uri)
                .exchange((request, rawResponse) -> {
                    int statusCode = rawResponse.getStatusCode().value();
                    String body = StreamUtils.copyToString(rawResponse.getBody(), StandardCharsets.UTF_8);
                    MediaType contentType = rawResponse.getHeaders().getContentType();
                    return new ApiRawResponse(statusCode, contentType == null ? null : contentType.toString(), body);
                });
    }

    Optional<WinningNumber> parse(int round, String body) {
        return responseParser.parse(round, body);
    }

    private static void validateJsonResponse(int round, ApiRawResponse response) {
        String body = response.body() == null ? "" : response.body().trim();
        String contentType = response.contentType() == null ? "" : response.contentType().toLowerCase();
        boolean jsonContentType = contentType.isBlank() || contentType.contains("json") || contentType.contains("javascript");
        boolean jsonBody = body.startsWith("{") || body.startsWith("[");
        if (!jsonContentType || !jsonBody) {
            throw new LottoApiClientException("response is not JSON (round=" + round
                    + ", contentType=" + response.contentType() + ", preview=" + preview(body) + ")", response.statusCode(), response.body());
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
            throw new LottoApiClientException("retry sleep interrupted", ie);
        }
    }

    private void count(String metricName, String... tags) {
        if (meterRegistry == null) {
            return;
        }
        if ("kraft.api.dhlottery.call.failure".equals(metricName) && tags.length >= 2 && "reason".equals(tags[0])) {
            String reason = tags[1];
            if (!ALLOWED_FAILURE_REASONS.contains(reason)) {
                tags[1] = "other";
            }
        }
        meterRegistry.counter(metricName, tags).increment();
    }

    private static String preview(String body) {
        int limit = Math.min(80, body.length());
        return body.substring(0, limit).replaceAll("\\s+", " ");
    }

    record ApiRawResponse(int statusCode, String contentType, String body) {
    }
}
