package com.kraft.lotto.infra.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kraft.lotto.infra.config.KraftRecommendRateLimitProperties;
import com.kraft.lotto.support.ApiError;
import com.kraft.lotto.support.ApiResponse;
import com.kraft.lotto.support.ErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

public class RecommendRateLimitFilter extends OncePerRequestFilter {

    // 추적 가능한 최대 IP 수 — 초과 시 신규 IP는 즉시 차단
    static final int MAX_TRACKED_IPS = 50_000;
    // stale 엔트리 정리 주기 (ms) — CAS 방식으로 단일 스레드만 수행
    private static final long CLEANUP_INTERVAL_MS = 60_000L;

    private final KraftRecommendRateLimitProperties properties;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final Map<String, Deque<Long>> requestHistory = new ConcurrentHashMap<>();
    private final AtomicLong lastCleanupTime = new AtomicLong(0);

    public RecommendRateLimitFilter(KraftRecommendRateLimitProperties properties,
                                    ObjectMapper objectMapper,
                                    MeterRegistry meterRegistry) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod()) || !"/api/recommend".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String key = resolveClientIp(request);
        long now = Instant.now().toEpochMilli();
        long windowStart = now - (properties.windowSeconds() * 1_000L);

        evictStaleIfNeeded(now, windowStart);

        // 신규 IP이고 추적 용량 초과 시 차단 (메모리 보호)
        if (!requestHistory.containsKey(key) && requestHistory.size() >= MAX_TRACKED_IPS) {
            meterRegistry.counter("kraft.api.recommend.rate_limit.blocked").increment();
            writeRateLimitResponse(response);
            return;
        }

        Deque<Long> timestamps = requestHistory.computeIfAbsent(key, __ -> new ArrayDeque<>());
        boolean allowed;
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
                timestamps.pollFirst();
            }
            allowed = timestamps.size() < properties.maxRequests();
            if (allowed) {
                timestamps.addLast(now);
            }
        }

        if (!allowed) {
            meterRegistry.counter("kraft.api.recommend.rate_limit.blocked").increment();
            writeRateLimitResponse(response);
            return;
        }

        meterRegistry.counter("kraft.api.recommend.rate_limit.allowed").increment();
        filterChain.doFilter(request, response);
    }

    /**
     * 윈도우 밖 타임스탬프를 정리하고 빈 데크를 맵에서 제거한다.
     * CAS로 단일 스레드만 정리를 수행하여 경합을 최소화한다.
     */
    private void evictStaleIfNeeded(long now, long windowStart) {
        long last = lastCleanupTime.get();
        if (now - last > CLEANUP_INTERVAL_MS && lastCleanupTime.compareAndSet(last, now)) {
            requestHistory.entrySet().removeIf(entry -> {
                synchronized (entry.getValue()) {
                    while (!entry.getValue().isEmpty() && entry.getValue().peekFirst() < windowStart) {
                        entry.getValue().pollFirst();
                    }
                    return entry.getValue().isEmpty();
                }
            });
        }
    }

    private void writeRateLimitResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiResponse<Void> body = ApiResponse.failure(ApiError.of(ErrorCode.TOO_MANY_REQUESTS));
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private static String resolveClientIp(HttpServletRequest request) {
        String fwd = request.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) {
            return fwd.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
