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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

public class RecommendRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RecommendRateLimitFilter.class);

    // 추적 가능한 최대 endpoint/IP 버킷 수 — 초과 시 신규 버킷은 즉시 차단
    static final int MAX_TRACKED_IPS = 50_000;
    // stale 엔트리 정리 주기 (ms) — CAS 방식으로 단일 스레드만 수행
    private static final long CLEANUP_INTERVAL_MS = 60_000L;
    private static final String UNKNOWN_CLIENT_IP = "unknown";

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
        return endpointFor(request) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Endpoint endpoint = endpointFor(request);
        if (endpoint == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = resolveClientIp(request);
        if (UNKNOWN_CLIENT_IP.equals(clientIp)) {
            log.warn("rate limit client IP could not be resolved: method={}, uri={}",
                    request.getMethod(), request.getRequestURI());
        }

        var limit = properties.endpoint(endpoint.id);
        long now = Instant.now().toEpochMilli();
        long windowMs = limit.windowSeconds() * 1_000L;
        long windowStart = now - windowMs;
        String key = endpoint.id + ':' + clientIp;

        evictStaleIfNeeded(now, windowStart);

        // 신규 endpoint/IP 버킷이고 추적 용량 초과 시 차단 (메모리 보호)
        if (!requestHistory.containsKey(key) && requestHistory.size() >= MAX_TRACKED_IPS) {
            meterRegistry.counter("kraft.api.rate_limit.requests", "endpoint", endpoint.id,
                    "result", "blocked", "reason", "capacity_exceeded").increment();
            log.warn("rate limit bucket capacity exceeded: endpoint={}, trackedBuckets={}",
                    endpoint.id, requestHistory.size());
            writeRateLimitResponse(response, limit.windowSeconds());
            return;
        }

        Deque<Long> timestamps = requestHistory.computeIfAbsent(key, __ -> new ArrayDeque<>());
        boolean allowed;
        int retryAfterSeconds = limit.windowSeconds();
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
                timestamps.pollFirst();
            }
            allowed = timestamps.size() < limit.maxRequests();
            if (allowed) {
                timestamps.addLast(now);
            } else {
                retryAfterSeconds = retryAfterSeconds(timestamps.peekFirst(), windowMs, now);
            }
        }

        if (!allowed) {
            meterRegistry.counter("kraft.api.rate_limit.requests", "endpoint", endpoint.id,
                    "result", "blocked", "reason", "limit_exceeded").increment();
            writeRateLimitResponse(response, retryAfterSeconds);
            return;
        }

        meterRegistry.counter("kraft.api.rate_limit.requests", "endpoint", endpoint.id,
                "result", "allowed", "reason", "within_limit").increment();
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

    private void writeRateLimitResponse(HttpServletResponse response, int retryAfterSeconds) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, retryAfterSeconds)));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiResponse<Void> body = ApiResponse.failure(ApiError.of(ErrorCode.TOO_MANY_REQUESTS));
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private static int retryAfterSeconds(Long oldestTimestamp, long windowMs, long now) {
        if (oldestTimestamp == null) {
            return 1;
        }
        long retryAfterMs = Math.max(1L, oldestTimestamp + windowMs - now);
        return (int) Math.max(1L, (retryAfterMs + 999L) / 1_000L);
    }

    private static Endpoint endpointFor(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return null;
        }
        String path = request.getServletPath();
        if (path == null || path.isEmpty()) {
            path = request.getRequestURI();
        }
        if ("/api/recommend".equals(path)) {
            return Endpoint.RECOMMEND;
        }
        if ("/api/winning-numbers/refresh".equals(path)) {
            return Endpoint.COLLECT;
        }
        return null;
    }

    private static String resolveClientIp(HttpServletRequest request) {
        String remoteAddr = normalizeIp(request.getRemoteAddr());
        if (remoteAddr == null) {
            return UNKNOWN_CLIENT_IP;
        }
        if (!isTrustedProxy(remoteAddr)) {
            return remoteAddr;
        }

        String fwd = request.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) {
            String forwardedClientIp = normalizeIp(fwd.split(",")[0]);
            if (forwardedClientIp != null) {
                return forwardedClientIp;
            }
        }
        return remoteAddr;
    }

    private static String normalizeIp(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static boolean isTrustedProxy(String remoteAddr) {
        try {
            java.net.InetAddress addr = java.net.InetAddress.getByName(remoteAddr);
            return addr.isLoopbackAddress() || addr.isSiteLocalAddress() || addr.isLinkLocalAddress();
        } catch (java.net.UnknownHostException ex) {
            return false;
        }
    }

    private enum Endpoint {
        RECOMMEND("recommend"),
        COLLECT("collect");

        private final String id;

        Endpoint(String id) {
            this.id = id;
        }
    }
}
