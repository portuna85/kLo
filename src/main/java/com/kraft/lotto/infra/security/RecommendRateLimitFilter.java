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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

public class RecommendRateLimitFilter extends OncePerRequestFilter {

    private final KraftRecommendRateLimitProperties properties;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final Map<String, Deque<Long>> requestHistory = new ConcurrentHashMap<>();

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
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            ApiResponse<Void> body = ApiResponse.failure(ApiError.of(ErrorCode.TOO_MANY_REQUESTS));
            response.getWriter().write(objectMapper.writeValueAsString(body));
            return;
        }

        meterRegistry.counter("kraft.api.recommend.rate_limit.allowed").increment();
        filterChain.doFilter(request, response);
    }

    private static String resolveClientIp(HttpServletRequest request) {
        String fwd = request.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) {
            return fwd.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
