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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RecommendRateLimitFilterTest {

    private static final int MAX_REQUESTS = 3;
    private static final int WINDOW_SECONDS = 60;

    private RecommendRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RecommendRateLimitFilter(
                new KraftRecommendRateLimitProperties(
                        MAX_REQUESTS,
                        WINDOW_SECONDS,
                        KraftRecommendRateLimitProperties.CapacityExceededPolicy.BLOCK,
                        false,
                        java.util.List.of(),
                        null,
                        null
                ),
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
    void allowsRequestsWithinLimit() throws Exception {
        for (int i = 0; i < MAX_REQUESTS; i++) {
            assertThat(executeRequest(postRecommend("10.0.0.1"))).isEqualTo(200);
        }
    }

    @Test
    void blocksRequestsOverLimit() throws Exception {
        for (int i = 0; i < MAX_REQUESTS; i++) {
            executeRequest(postRecommend("10.0.0.2"));
        }
        assertThat(executeRequest(postRecommend("10.0.0.2"))).isEqualTo(429);
    }

    @Test
    void blocksRefreshRequestsOverLimit() throws Exception {
        for (int i = 0; i < MAX_REQUESTS; i++) {
            executeRequest(postRefresh("10.0.0.7"));
        }
        assertThat(executeRequest(postRefresh("10.0.0.7"))).isEqualTo(429);
        assertThat(executeRequest(postRecommend("10.0.0.7"))).isEqualTo(200);
    }

    @Test
    void ignoresNonLimitedEndpoint() throws Exception {
        for (int i = 0; i < MAX_REQUESTS + 1; i++) {
            assertThat(executeRequest(post("/api/winning-numbers/latest", "10.0.0.8"))).isEqualTo(200);
        }
    }

    @Test
    void differentIpsHaveIndependentBuckets() throws Exception {
        for (int i = 0; i < MAX_REQUESTS; i++) {
            executeRequest(postRecommend("10.0.0.3"));
        }
        assertThat(executeRequest(postRecommend("10.0.0.4"))).isEqualTo(200);
    }

    @Test
    void defaultModeIgnoresForwardedHeaders() throws Exception {
        MockHttpServletRequest req = postRecommend("203.0.113.9");
        req.addHeader("X-Forwarded-For", "198.51.100.1");

        for (int i = 0; i < MAX_REQUESTS; i++) {
            assertThat(executeRequest(req)).isEqualTo(200);
        }
        assertThat(executeRequest(req)).isEqualTo(429);

        MockHttpServletRequest sameRemoteDifferentForwarded = postRecommend("203.0.113.9");
        sameRemoteDifferentForwarded.addHeader("X-Forwarded-For", "198.51.100.99");
        assertThat(executeRequest(sameRemoteDifferentForwarded)).isEqualTo(429);
    }

    @Test
    void trustedProxyModeUsesForwardedHeaders() throws Exception {
        RecommendRateLimitFilter forwardedFilter = new RecommendRateLimitFilter(
                new KraftRecommendRateLimitProperties(
                        MAX_REQUESTS,
                        WINDOW_SECONDS,
                        KraftRecommendRateLimitProperties.CapacityExceededPolicy.BLOCK,
                        true,
                        java.util.List.of("127.0.0.1"),
                        null,
                        null
                ),
                new ObjectMapper(),
                new SimpleMeterRegistry()
        );

        MockHttpServletRequest requestA = postRecommend("127.0.0.1");
        requestA.addHeader("X-Forwarded-For", "203.0.113.1, 10.0.0.5");

        for (int i = 0; i < MAX_REQUESTS; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            forwardedFilter.doFilter(requestA, response, mock(FilterChain.class));
            assertThat(response.getStatus()).isEqualTo(200);
        }
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        forwardedFilter.doFilter(requestA, blocked, mock(FilterChain.class));
        assertThat(blocked.getStatus()).isEqualTo(429);

        MockHttpServletRequest requestB = postRecommend("127.0.0.1");
        requestB.addHeader("X-Forwarded-For", "203.0.113.2, 10.0.0.5");
        MockHttpServletResponse allowed = new MockHttpServletResponse();
        forwardedFilter.doFilter(requestB, allowed, mock(FilterChain.class));
        assertThat(allowed.getStatus()).isEqualTo(200);
    }

    @Test
    void blockedResponseBodyContainsErrorCode() throws Exception {
        for (int i = 0; i < MAX_REQUESTS; i++) {
            executeRequest(postRecommend("10.0.0.6"));
        }

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(postRecommend("10.0.0.6"), response, mock(FilterChain.class));

        assertThat(response.getContentAsString()).contains("TOO_MANY_REQUESTS");
    }

    @Test
    void blockedResponseContainsRetryAfterHeader() throws Exception {
        for (int i = 0; i < MAX_REQUESTS; i++) {
            executeRequest(postRecommend("10.0.0.11"));
        }

        MockHttpServletResponse response = executeAndReturnResponse(postRecommend("10.0.0.11"));

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo(String.valueOf(WINDOW_SECONDS));
    }

    @Test
    void appliesDifferentEndpointLimits() throws Exception {
        RecommendRateLimitFilter splitFilter = new RecommendRateLimitFilter(
                new KraftRecommendRateLimitProperties(
                        3,
                        60,
                        KraftRecommendRateLimitProperties.CapacityExceededPolicy.BLOCK,
                        false,
                        java.util.List.of(),
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
    void blocksNewIpWhenCapacityExceeded() throws Exception {
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
                    if (status == 200) {
                        allowed.incrementAndGet();
                    } else {
                        blocked.incrementAndGet();
                    }
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

    @Test
    void concurrentNewIpsDoNotExceedTrackedBucketCapacity() throws Exception {
        java.lang.reflect.Field field = RecommendRateLimitFilter.class.getDeclaredField("requestHistory");
        field.setAccessible(true);
        Map<String, Deque<Long>> history = (Map<String, Deque<Long>>) field.get(filter);
        long now = System.currentTimeMillis();
        for (int i = 0; i < RecommendRateLimitFilter.MAX_TRACKED_IPS - 1; i++) {
            Deque<Long> bucket = new ArrayDeque<>();
            bucket.addLast(now);
            history.put("seed." + i, bucket);
        }

        AtomicInteger blocked = new AtomicInteger(0);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        String[] ips = {"172.16.10.1", "172.16.10.2"};
        for (String ip : ips) {
            executor.submit(() -> {
                try {
                    start.await();
                    int status = executeRequest(postRecommend(ip));
                    if (status == 429) {
                        blocked.incrementAndGet();
                    }
                } catch (Exception ignored) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        assertThat(history.size()).isEqualTo(RecommendRateLimitFilter.MAX_TRACKED_IPS);
        assertThat(blocked.get()).isEqualTo(1);
    }
}
