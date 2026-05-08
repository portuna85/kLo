package com.kraft.lotto.infra.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kraft.lotto.infra.config.KraftRecommendRateLimitProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@DisplayName("RecommendRateLimitFilter")
class RecommendRateLimitFilterTest {

    private static final int MAX_REQUESTS = 3;
    private static final int WINDOW_SECONDS = 60;

    private RecommendRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RecommendRateLimitFilter(
                new KraftRecommendRateLimitProperties(MAX_REQUESTS, WINDOW_SECONDS),
                new ObjectMapper(),
                new SimpleMeterRegistry()
        );
    }

    private MockHttpServletRequest postRecommend(String remoteAddr) {
        return post("/api/recommend", remoteAddr);
    }

    private MockHttpServletRequest postRefresh(String remoteAddr) {
        return post("/api/winning-numbers/refresh", remoteAddr);
    }

    private MockHttpServletRequest post(String path, String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr(remoteAddr);
        return request;
    }

    private int executeRequest(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, mock(FilterChain.class));
        return response.getStatus();
    }

    private MockHttpServletResponse executeAndReturnResponse(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, mock(FilterChain.class));
        return response;
    }

    @Test
    @DisplayName("허용 횟수 이하 요청은 통과된다")
    void allowsRequestsWithinLimit() throws Exception {
        for (int i = 0; i < MAX_REQUESTS; i++) {
            assertThat(executeRequest(postRecommend("10.0.0.1"))).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("허용 횟수 초과 요청은 429를 반환한다")
    void blocksRequestsOverLimit() throws Exception {
        for (int i = 0; i < MAX_REQUESTS; i++) {
            executeRequest(postRecommend("10.0.0.2"));
        }

        assertThat(executeRequest(postRecommend("10.0.0.2"))).isEqualTo(429);
    }

    @Test
    @DisplayName("당첨번호 refresh 요청은 독립된 endpoint 버킷으로 rate limit 을 적용한다")
    void blocksRefreshRequestsOverLimit() throws Exception {
        for (int i = 0; i < MAX_REQUESTS; i++) {
            executeRequest(postRefresh("10.0.0.7"));
        }

        assertThat(executeRequest(postRefresh("10.0.0.7"))).isEqualTo(429);
        assertThat(executeRequest(postRecommend("10.0.0.7"))).isEqualTo(200);
    }

    @Test
    @DisplayName("rate limit 대상이 아닌 요청은 필터링하지 않는다")
    void ignoresNonLimitedEndpoint() throws Exception {
        for (int i = 0; i < MAX_REQUESTS + 1; i++) {
            assertThat(executeRequest(post("/api/winning-numbers/latest", "10.0.0.8"))).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("다른 IP는 독립된 버킷을 가진다")
    void differentIpsHaveIndependentBuckets() throws Exception {
        for (int i = 0; i < MAX_REQUESTS; i++) {
            executeRequest(postRecommend("10.0.0.3"));
        }

        assertThat(executeRequest(postRecommend("10.0.0.4"))).isEqualTo(200);
    }

    @Test
    @DisplayName("신뢰된 프록시(사설/루프백)에서만 X-Forwarded-For 헤더를 신뢰한다")
    void usesXForwardedForOnlyFromTrustedProxy() throws Exception {
        MockHttpServletRequest req = postRecommend("127.0.0.1");
        req.addHeader("X-Forwarded-For", "203.0.113.1, 10.0.0.5");

        for (int i = 0; i < MAX_REQUESTS; i++) {
            executeRequest(req);
        }
        assertThat(executeRequest(req)).isEqualTo(429);
    }

    @Test
    @DisplayName("신뢰되지 않은 원격지에서는 X-Forwarded-For를 무시하고 remoteAddr로 제한한다")
    void ignoresXForwardedForForUntrustedRemote() throws Exception {
        MockHttpServletRequest req = postRecommend("203.0.113.9");
        req.addHeader("X-Forwarded-For", "198.51.100.1");

        for (int i = 0; i < MAX_REQUESTS; i++) {
            assertThat(executeRequest(req)).isEqualTo(200);
        }
        assertThat(executeRequest(req)).isEqualTo(429);

        MockHttpServletRequest another = postRecommend("203.0.113.10");
        another.addHeader("X-Forwarded-For", "198.51.100.1");
        assertThat(executeRequest(another)).isEqualTo(200);
    }

    @Test
    @DisplayName("차단 응답 body 에 TOO_MANY_REQUESTS 코드가 포함된다")
    void blockedResponseBodyContainsErrorCode() throws Exception {
        for (int i = 0; i < MAX_REQUESTS; i++) {
            executeRequest(postRecommend("10.0.0.6"));
        }

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(postRecommend("10.0.0.6"), response, mock(FilterChain.class));

        assertThat(response.getContentAsString()).contains("TOO_MANY_REQUESTS");
    }


    @Test
    @DisplayName("차단 응답은 Retry-After 헤더를 포함한다")
    void blockedResponseContainsRetryAfterHeader() throws Exception {
        for (int i = 0; i < MAX_REQUESTS; i++) {
            executeRequest(postRecommend("10.0.0.11"));
        }

        MockHttpServletResponse response = executeAndReturnResponse(postRecommend("10.0.0.11"));

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo(String.valueOf(WINDOW_SECONDS));
    }

    @Test
    @DisplayName("endpoint 별 제한값을 다르게 적용할 수 있다")
    void appliesDifferentEndpointLimits() throws Exception {
        RecommendRateLimitFilter splitFilter = new RecommendRateLimitFilter(
                new KraftRecommendRateLimitProperties(
                        3,
                        60,
                        new KraftRecommendRateLimitProperties.Endpoint(3, 60),
                        new KraftRecommendRateLimitProperties.Endpoint(1, 120)
                ),
                new ObjectMapper(),
                new SimpleMeterRegistry()
        );

        MockHttpServletResponse firstCollect = new MockHttpServletResponse();
        splitFilter.doFilter(postRefresh("10.0.0.12"), firstCollect, mock(FilterChain.class));
        MockHttpServletResponse secondCollect = new MockHttpServletResponse();
        splitFilter.doFilter(postRefresh("10.0.0.12"), secondCollect, mock(FilterChain.class));

        assertThat(firstCollect.getStatus()).isEqualTo(200);
        assertThat(secondCollect.getStatus()).isEqualTo(429);
        assertThat(secondCollect.getHeader("Retry-After")).isEqualTo("120");
    }

    @Test
    @DisplayName("추적 IP 용량 초과 시 신규 IP는 429를 받는다")
    @SuppressWarnings("unchecked")
    void blocksNewIpWhenCapacityExceeded() throws Exception {
        // requestHistory 필드를 직접 채워 용량 초과 상태를 만든다
        java.lang.reflect.Field field = RecommendRateLimitFilter.class.getDeclaredField("requestHistory");
        field.setAccessible(true);
        Map<String, Deque<Long>> history = (Map<String, Deque<Long>>) field.get(filter);
        long now = System.currentTimeMillis();
        for (int i = 0; i < RecommendRateLimitFilter.MAX_TRACKED_IPS; i++) {
            Deque<Long> bucket = new ArrayDeque<>();
            bucket.addLast(now);
            history.put("fill." + i, bucket);
        }

        assertThat(executeRequest(postRecommend("172.16.99.99"))).isEqualTo(429);
    }

    @Test
    @DisplayName("동시 요청이 발생해도 허용 횟수를 정확히 지킨다")
    void concurrentRequestsRespectLimit() throws Exception {
        int threads = 10;
        AtomicInteger allowed = new AtomicInteger(0);
        AtomicInteger blocked = new AtomicInteger(0);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    start.await();
                    int status = executeRequest(postRecommend("10.1.1.1"));
                    if (status == 200) allowed.incrementAndGet();
                    else blocked.incrementAndGet();
                } catch (Exception e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await();
        executor.shutdown();

        assertThat(allowed.get()).isEqualTo(MAX_REQUESTS);
        assertThat(blocked.get()).isEqualTo(threads - MAX_REQUESTS);
    }
}
