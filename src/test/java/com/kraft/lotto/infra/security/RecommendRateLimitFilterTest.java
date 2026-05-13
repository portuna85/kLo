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

@DisplayName("추천 요율 제한 필터 테스트")
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
    @DisplayName("제한 범위 내의 요청은 허용한다")
    void allowsRequestsWithinLimit() throws Exception {
        for (int i = 0; i < MAX_REQUESTS; i++) {
            assertThat(executeRequest(postRecommend("10.0.0.1"))).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("제한을 초과하는 요청은 차단한다")
    void blocksRequestsOverLimit() throws Exception {
        for (int i = 0; i < MAX_REQUESTS; i++) {
            executeRequest(postRecommend("10.0.0.2"));
        }

        assertThat(executeRequest(postRecommend("10.0.0.2"))).isEqualTo(429);
    }

    @Test
    @DisplayName("새로고침 요청이 제한을 초과하면 차단한다")
    void blocksRefreshRequestsOverLimit() throws Exception {
        for (int i = 0; i < MAX_REQUESTS; i++) {
            executeRequest(postRefresh("10.0.0.7"));
        }

        assertThat(executeRequest(postRefresh("10.0.0.7"))).isEqualTo(429);
        assertThat(executeRequest(postRecommend("10.0.0.7"))).isEqualTo(200);
    }

    @Test
    @DisplayName("제한 대상이 아닌 엔드포인트는 무시한다")
    void ignoresNonLimitedEndpoint() throws Exception {
        for (int i = 0; i < MAX_REQUESTS + 1; i++) {
            assertThat(executeRequest(post("/api/winning-numbers/latest", "10.0.0.8"))).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("서로 다른 IP는 독립적인 버킷을 가진다")
    void differentIpsHaveIndependentBuckets() throws Exception {
        for (int i = 0; i < MAX_REQUESTS; i++) {
            executeRequest(postRecommend("10.0.0.3"));
        }

        assertThat(executeRequest(postRecommend("10.0.0.4"))).isEqualTo(200);
    }

    @Test
    @DisplayName("X-Forwarded-For 헤더를 무시하고 Remote Address를 사용한다")
    void ignoresXForwardedForAndUsesRemoteAddr() throws Exception {
        MockHttpServletRequest req = postRecommend("127.0.0.1");
        req.addHeader("X-Forwarded-For", "203.0.113.1, 10.0.0.5");

        for (int i = 0; i < MAX_REQUESTS; i++) {
            executeRequest(req);
        }
        assertThat(executeRequest(req)).isEqualTo(429);
    }

    @Test
    @DisplayName("동일한 Remote Address는 Forwarded 헤더와 상관없이 버킷을 공유한다")
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
    @DisplayName("차단된 응답 본문에 에러 코드가 포함된다")
    void blockedResponseBodyContainsErrorCode() throws Exception {
        for (int i = 0; i < MAX_REQUESTS; i++) {
            executeRequest(postRecommend("10.0.0.6"));
        }

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(postRecommend("10.0.0.6"), response, mock(FilterChain.class));

        assertThat(response.getContentAsString()).contains("TOO_MANY_REQUESTS");
    }


    @Test
    @DisplayName("차단된 응답에 Retry-After 헤더가 포함된다")
    void blockedResponseContainsRetryAfterHeader() throws Exception {
        for (int i = 0; i < MAX_REQUESTS; i++) {
            executeRequest(postRecommend("10.0.0.11"));
        }

        MockHttpServletResponse response = executeAndReturnResponse(postRecommend("10.0.0.11"));

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo(String.valueOf(WINDOW_SECONDS));
    }

    @Test
    @DisplayName("엔드포인트별로 서로 다른 제한을 적용한다")
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
    @DisplayName("IP 추적 용량을 초과하면 새로운 IP의 요청을 차단한다")
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
    @DisplayName("동시 요청 시에도 제한을 준수한다")
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
