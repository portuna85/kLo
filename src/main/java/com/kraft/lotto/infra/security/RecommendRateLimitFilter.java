package com.kraft.lotto.infra.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kraft.lotto.infra.config.KraftRateLimitRedisProperties;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

public class RecommendRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RecommendRateLimitFilter.class);

    static final int MAX_TRACKED_IPS = 50_000;
    private static final long CLEANUP_INTERVAL_MS = 60_000L;
    private static final String UNKNOWN_CLIENT_IP = "unknown";

    private final KraftRecommendRateLimitProperties properties;
    private final KraftRateLimitRedisProperties redisProperties;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final StringRedisTemplate redisTemplate;
    private final Map<String, Deque<Long>> requestHistory = new ConcurrentHashMap<>();
    private final AtomicLong lastCleanupTime = new AtomicLong(0);

    public RecommendRateLimitFilter(KraftRecommendRateLimitProperties properties,
                                    ObjectMapper objectMapper,
                                    MeterRegistry meterRegistry) {
        this(properties, objectMapper, meterRegistry, null, new KraftRateLimitRedisProperties());
    }

    public RecommendRateLimitFilter(KraftRecommendRateLimitProperties properties,
                                    ObjectMapper objectMapper,
                                    MeterRegistry meterRegistry,
                                    StringRedisTemplate redisTemplate,
                                    KraftRateLimitRedisProperties redisProperties) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.redisTemplate = redisTemplate;
        this.redisProperties = redisProperties;
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

        String clientIp = resolveClientIp(request, properties);
        if (UNKNOWN_CLIENT_IP.equals(clientIp)) {
            log.warn("rate limit client IP could not be resolved: method={}, uri={}",
                    request.getMethod(), request.getRequestURI());
        }

        var limit = properties.endpoint(endpoint.id);
        long now = Instant.now().toEpochMilli();

        Decision decision = isRedisMode()
                ? decideByRedis(endpoint, clientIp, now, limit)
                : decideByInMemory(endpoint, clientIp, now, limit);

        if (!decision.allowed()) {
            meterRegistry.counter("kraft.api.rate_limit.requests", "endpoint", endpoint.id,
                    "result", "blocked", "reason", "limit_exceeded").increment();
            writeRateLimitResponse(response, decision.retryAfterSeconds());
            return;
        }

        meterRegistry.counter("kraft.api.rate_limit.requests", "endpoint", endpoint.id,
                "result", "allowed", "reason", "within_limit").increment();
        filterChain.doFilter(request, response);
    }

    private Decision decideByInMemory(Endpoint endpoint,
                                      String clientIp,
                                      long now,
                                      KraftRecommendRateLimitProperties.Endpoint limit) {
        long windowMs = limit.windowSeconds() * 1_000L;
        long windowStart = now - windowMs;
        String key = endpoint.id + ':' + clientIp;

        evictStaleIfNeeded(now, windowStart);

        if (!requestHistory.containsKey(key) && requestHistory.size() >= MAX_TRACKED_IPS) {
            meterRegistry.counter("kraft.api.rate_limit.requests", "endpoint", endpoint.id,
                    "result", "blocked", "reason", "capacity_exceeded").increment();
            log.warn("rate limit bucket capacity exceeded: endpoint={}, trackedBuckets={}",
                    endpoint.id, requestHistory.size());
            return new Decision(false, limit.windowSeconds());
        }

        Deque<Long> timestamps = requestHistory.computeIfAbsent(key, __ -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
                timestamps.pollFirst();
            }
            if (timestamps.size() < limit.maxRequests()) {
                timestamps.addLast(now);
                return new Decision(true, 1);
            }
            int retryAfter = retryAfterSeconds(timestamps.peekFirst(), windowMs, now);
            return new Decision(false, retryAfter);
        }
    }

    private Decision decideByRedis(Endpoint endpoint,
                                   String clientIp,
                                   long now,
                                   KraftRecommendRateLimitProperties.Endpoint limit) {
        long windowSeconds = Math.max(1, limit.windowSeconds());
        long bucket = now / (windowSeconds * 1000L);
        String key = redisProperties.resolvedKeyPrefix() + ":" + endpoint.id + ":" + clientIp + ":" + bucket;
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, java.time.Duration.ofSeconds(windowSeconds + 1));
            }
            if (count != null && count <= limit.maxRequests()) {
                return new Decision(true, 1);
            }
            int retryAfter = (int) Math.max(1L, windowSeconds - ((now / 1000L) % windowSeconds));
            return new Decision(false, retryAfter);
        } catch (RuntimeException ex) {
            if (redisProperties.strict()) {
                log.warn("redis rate-limit failed in strict mode, request blocked", ex);
                return new Decision(false, 5);
            }
            log.warn("redis rate-limit failed, fallback to in-memory", ex);
            return decideByInMemory(endpoint, clientIp, now, limit);
        }
    }

    private boolean isRedisMode() {
        return redisProperties.enabled() && redisTemplate != null;
    }

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
        if ("/api/recommend".equals(path) || "/api/v1/recommend".equals(path)) {
            return Endpoint.RECOMMEND;
        }
        if ("/api/winning-numbers/refresh".equals(path)
                || "/api/v1/winning-numbers/refresh".equals(path)
                || isAdminCollectPath(path)) {
            return Endpoint.COLLECT;
        }
        return null;
    }

    private static boolean isAdminCollectPath(String path) {
        if (path == null) {
            return false;
        }
        if ("/admin/lotto/draws/collect-next".equals(path)
                || "/admin/lotto/draws/collect-missing".equals(path)
                || "/admin/lotto/draws/backfill".equals(path)) {
            return true;
        }
        return path.matches("^/admin/lotto/draws/\\d+/refresh$");
    }

    private static String resolveClientIp(HttpServletRequest request, KraftRecommendRateLimitProperties properties) {
        String remoteAddr = normalizeIp(request.getRemoteAddr());
        if (properties.trustForwardedHeaders() && isTrustedProxy(remoteAddr, properties.trustedProxyIps())) {
            String forwarded = firstForwardedIp(request.getHeader("X-Forwarded-For"));
            if (forwarded != null) {
                return forwarded;
            }
            String realIp = normalizeIp(request.getHeader("X-Real-IP"));
            if (realIp != null) {
                return realIp;
            }
        }
        return remoteAddr == null ? UNKNOWN_CLIENT_IP : remoteAddr;
    }

    private static boolean isTrustedProxy(String remoteAddr, List<String> trustedProxyIps) {
        if (remoteAddr == null) {
            return false;
        }
        Set<String> defaults = Set.of("127.0.0.1", "::1");
        if (defaults.contains(remoteAddr)) {
            return true;
        }
        return trustedProxyIps.contains(remoteAddr);
    }

    private static String firstForwardedIp(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String first = value.split(",")[0].trim();
        return normalizeIp(first);
    }

    private static String normalizeIp(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private enum Endpoint {
        RECOMMEND("recommend"),
        COLLECT("collect");

        private final String id;

        Endpoint(String id) {
            this.id = id;
        }
    }

    private record Decision(boolean allowed, int retryAfterSeconds) {
    }
}
