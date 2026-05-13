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

    @DisplayName("tests for RecommendRateLimitFilterTest")
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
    @DisplayName("allows requests within limit")
    void allowsRequestsWithinLimit() throws Exception {
        for (int i = 0; i < MAX_REQUESTS; i++) {
            assertThat(executeRequest(postRecommend("10.0.0.1"))).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("blocks requests over limit")
    void blocksRequestsOverLimit() throws Exception {
        for (int i = 0; i < MAX_REQUESTS; i++) {
            executeRequest(postRecommend("10.0.0.2"));
        }

        assertThat(executeRequest(postRecommend("10.0.0.2"))).isEqualTo(429);
    }

    @Test
    @DisplayName("blocks refresh requests over limit")
    void blocksRefreshRequestsOverLimit() throws Exception {
        for (int i = 0; i < MAX_REQUESTS; i++) {
            executeRequest(postRefresh("10.0.0.7"));
        }

        assertThat(executeRequest(postRefresh("10.0.0.7"))).isEqualTo(429);
        assertThat(executeRequest(postRecommend("10.0.0.7"))).isEqualTo(200);
    }

    @Test
    @DisplayName("ignores non limited endpoint")
    void ignoresNonLimitedEndpoint() throws Exception {
        for (int i = 0; i < MAX_REQUESTS + 1; i++) {
            assertThat(executeRequest(post("/api/winning-numbers/latest", "10.0.0.8"))).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("different ips have independent buckets")
    void differentIpsHaveIndependentBuckets() throws Exception {
        for (int i = 0; i < MAX_REQUESTS; i++) {
            executeRequest(postRecommend("10.0.0.3"));
        }

        assertThat(executeRequest(postRecommend("10.0.0.4"))).isEqualTo(200);
    }

    @Test
    @DisplayName("ignores x forwarded for and uses remote addr")
    void ignoresXForwardedForAndUsesRemoteAddr() throws Exception {
        MockHttpServletRequest req = postRecommend("127.0.0.1");
        req.addHeader("X-Forwarded-For", "203.0.113.1, 10.0.0.5");

        for (int i = 0; i < MAX_REQUESTS; i++) {
            executeRequest(req);
        }
        assertThat(executeRequest(req)).isEqualTo(429);
    }

    @Test
    @DisplayName("same remote addr shares bucket regardless of forwarded header")
    void sameRemoteAddrSharesBucketRegardlessOfForwardedHeader() throws Exception {
        MockHttpServletRequest req = postRecommend("203.0.113.9");
        req.addHeader("X-Forwarded-For", "198.51.100.1");

        for (int i = 0; i < MAX_REQUESTS; i++) {
            assertThat(executeRequest(req)).isEqualTo(200);
        }
        assertThat(executeRequest(req)).isEqualTo(429);

        MockHttpServletRequest sameIpWithDifferentForwarded = postRecommend("203.0.113.9");
        sameIpWithDifferentForwarded.addHeader("X-Forwarded-For", "198.51.100.99");
        assertThat(executeRequest(sameIpWithDifferentForwarded)).isEqualTo(429);
    }

    @Test
    @DisplayName("blocked response body contains error code")
    void blockedResponseBodyContainsErrorCode() throws Exception {
        for (int i = 0; i < MAX_REQUESTS; i++) {
            executeRequest(postRecommend("10.0.0.6"));
        }

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(postRecommend("10.0.0.6"), response, mock(FilterChain.class));

        assertThat(response.getContentAsString()).contains("TOO_MANY_REQUESTS");
    }


    @Test
    @DisplayName("blocked response contains retry after header")
    void blockedResponseContainsRetryAfterHeader() throws Exception {
        for (int i = 0; i < MAX_REQUESTS; i++) {
            executeRequest(postRecommend("10.0.0.11"));
        }

        MockHttpServletResponse response = executeAndReturnResponse(postRecommend("10.0.0.11"));

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo(String.valueOf(WINDOW_SECONDS));
    }

    @Test
    @DisplayName("applies different endpoint limits")
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
    @DisplayName("blocks new ip when capacity exceeded")
    @SuppressWarnings("unchecked")
    void blocksNewIpWhenCapacityExceeded() throws Exception {
        // requestHistory ?熬곣뫀援??嶺뚯쉳???嶺??????紐꾩럸 ?貫?????⑤객臾??嶺뚮씭??キ??
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
    @DisplayName("concurrent requests respect limit")
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
