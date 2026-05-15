package com.kraft.lotto.infra.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kraft.lotto.infra.config.KraftRateLimitRedisProperties;
import com.kraft.lotto.infra.config.KraftRecommendRateLimitProperties;
import com.kraft.lotto.support.ApiError;
import com.kraft.lotto.support.ApiResponse;
import com.kraft.lotto.support.ClientIpResolver;
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
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

public class RecommendRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RecommendRateLimitFilter.class);

    static final int MAX_TRACKED_IPS = 50_000;
    private static final long CLEANUP_INTERVAL_MS = 60_000L;
    private static final RedisScript<Long> INCR_EXPIRE_SCRIPT = buildIncrExpireScript();
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

        String clientIp = ClientIpResolver.resolve(request, properties);
        if (ClientIpResolver.UNKNOWN_CLIENT_IP.equals(clientIp)) {
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

        Deque<Long> timestamps = getOrCreateBucket(endpoint, key, limit.windowSeconds());
        if (timestamps == null) {
            return new Decision(false, limit.windowSeconds());
        }
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

    private Deque<Long> getOrCreateBucket(Endpoint endpoint, String key, int windowSeconds) {
        Deque<Long> existing = requestHistory.get(key);
        if (existing != null) {
            return existing;
        }
        synchronized (requestHistory) {
            existing = requestHistory.get(key);
            if (existing != null) {
                return existing;
            }
            if (requestHistory.size() >= MAX_TRACKED_IPS) {
                if (properties.capacityExceededPolicy() == KraftRecommendRateLimitProperties.CapacityExceededPolicy.BLOCK) {
                    meterRegistry.counter("kraft.api.rate_limit.requests", "endpoint", endpoint.id,
                            "result", "blocked", "reason", "capacity_exceeded").increment();
                    log.warn("rate limit bucket capacity exceeded and blocked: endpoint={}, trackedBuckets={}",
                            endpoint.id, requestHistory.size());
                    return null;
                }
                String evictedKey = requestHistory.keySet().iterator().next();
                requestHistory.remove(evictedKey);
                meterRegistry.counter("kraft.api.rate_limit.requests", "endpoint", endpoint.id,
                        "result", "allowed", "reason", "capacity_evicted_oldest").increment();
                log.warn("rate limit bucket capacity exceeded, evicted oldest bucket: endpoint={}, evictedKey={}",
                        endpoint.id, evictedKey);
            }
            Deque<Long> created = new ArrayDeque<>();
            requestHistory.put(key, created);
            return created;
        }
    }

    private Decision decideByRedis(Endpoint endpoint,
                                   String clientIp,
                                   long now,
                                   KraftRecommendRateLimitProperties.Endpoint limit) {
        long windowSeconds = Math.max(1, limit.windowSeconds());
        long bucket = now / (windowSeconds * 1000L);
        String key = redisProperties.resolvedKeyPrefix() + ":" + endpoint.id + ":" + clientIp + ":" + bucket;
        String ttlSeconds = String.valueOf(windowSeconds + 1);
        try {
            Long count = redisTemplate.execute(INCR_EXPIRE_SCRIPT, java.util.List.of(key), ttlSeconds);
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

    private static RedisScript<Long> buildIncrExpireScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setResultType(Long.class);
        script.setScriptText(
                "local v = redis.call('INCR', KEYS[1]) "
                        + "if v == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end "
                        + "return v");
        return script;
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
        if (ApiPaths.RECOMMEND_LEGACY.equals(path) || ApiPaths.RECOMMEND_V1.equals(path)) {
            return Endpoint.RECOMMEND;
        }
        if (ApiPaths.COLLECT_REFRESH_LEGACY.equals(path)
                || ApiPaths.COLLECT_REFRESH_V1.equals(path)
                || isAdminCollectPath(path)) {
            return Endpoint.COLLECT;
        }
        return null;
    }

    private static boolean isAdminCollectPath(String path) {
        if (path == null) {
            return false;
        }
        if (ApiPaths.ADMIN_COLLECT_NEXT.equals(path)
                || ApiPaths.ADMIN_COLLECT_MISSING.equals(path)
                || ApiPaths.ADMIN_BACKFILL.equals(path)) {
            return true;
        }
        return path.matches("^/admin/lotto/draws/\\d+/refresh$");
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
