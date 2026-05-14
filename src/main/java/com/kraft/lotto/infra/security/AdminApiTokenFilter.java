package com.kraft.lotto.infra.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kraft.lotto.infra.config.KraftAdminProperties;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 운영성 작업을 일으키는 관리자 API를 공유 토큰으로 보호한다.
 *
 * <p>현재 보호 대상은 DB 쓰기와 외부 API 호출을 유발하는
 * {@code POST /api/winning-numbers/refresh} 와 {@code POST /admin/**} 이다.</p>
 */
public class AdminApiTokenFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(AdminApiTokenFilter.class);

    private static final String PROTECTED_METHOD = "POST";
    private final KraftAdminProperties properties;
    private final KraftRecommendRateLimitProperties rateLimitProperties;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public AdminApiTokenFilter(KraftAdminProperties properties,
                               KraftRecommendRateLimitProperties rateLimitProperties,
                               ObjectMapper objectMapper,
                               MeterRegistry meterRegistry) {
        this.properties = properties;
        this.rateLimitProperties = rateLimitProperties;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = pathWithinApplication(request);
        if (path.startsWith(ApiPaths.ADMIN_PREFIX)) {
            return false;
        }
        if (!PROTECTED_METHOD.equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        return !(ApiPaths.COLLECT_REFRESH_LEGACY.equals(path) || ApiPaths.COLLECT_REFRESH_V1.equals(path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!properties.hasApiToken()) {
            meterRegistry.counter("kraft.api.admin_token.blocked", "reason", "not_configured").increment();
            auditLog(request, "not_configured", "missing_config", null);
            writeUnauthorizedResponse(response);
            return;
        }

        String provided = request.getHeader(properties.resolvedTokenHeader());
        Optional<String> tokenAlias = matchedAlias(
                provided,
                properties.resolvedApiTokens(),
                properties.resolvedApiTokenHashes()
        );
        if (tokenAlias.isEmpty()) {
            meterRegistry.counter("kraft.api.admin_token.blocked", "reason", "invalid_token").increment();
            auditLog(request, "blocked", provided == null ? "missing_token" : "invalid_token", null);
            writeUnauthorizedResponse(response);
            return;
        }

        meterRegistry.counter("kraft.api.admin_token.allowed").increment();
        auditLog(request, "allowed", "ok", tokenAlias.get());
        var authentication = new UsernamePasswordAuthenticationToken(
                "admin-api-token",
                null,
                Collections.emptyList()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void writeUnauthorizedResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiResponse<Void> body = ApiResponse.failure(ApiError.of(ErrorCode.UNAUTHORIZED_ADMIN_API));
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private void auditLog(HttpServletRequest request, String result, String reason, String tokenAlias) {
        String path = pathWithinApplication(request);
        String method = request.getMethod();
        String ip = ClientIpResolver.resolve(request, rateLimitProperties);
        String from = request.getParameter("from");
        String to = request.getParameter("to");
        String drwNo = request.getParameter("drwNo");
        log.info("admin_api_audit ts={} method={} path={} ip={} tokenAlias={} result={} reason={} from={} to={} drwNo={}",
                Instant.now(), method, path, ip, tokenAlias == null ? "-" : tokenAlias, result, reason,
                from == null ? "-" : from, to == null ? "-" : to, drwNo == null ? "-" : drwNo);
    }

    private static Optional<String> matchedAlias(String provided, List<String> expectedTokens) {
        return matchedAlias(provided, expectedTokens, List.of());
    }

    private static Optional<String> matchedAlias(String provided,
                                                 List<String> expectedTokens,
                                                 List<KraftAdminProperties.AdminTokenHash> expectedTokenHashes) {
        if (provided == null) {
            return Optional.empty();
        }
        byte[] providedBytes = provided.getBytes(StandardCharsets.UTF_8);

        Optional<String> hashMatched = matchByHash(providedBytes, expectedTokenHashes);
        if (hashMatched.isPresent()) {
            return hashMatched;
        }

        if (expectedTokens == null || expectedTokens.isEmpty()) {
            return Optional.empty();
        }

        for (String expected : expectedTokens) {
            if (expected == null) {
                continue;
            }
            byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
            if (constantTimeEquals(providedBytes, expectedBytes)) {
                return Optional.of(aliasOf(expected));
            }
        }
        return Optional.empty();
    }

    private static Optional<String> matchByHash(byte[] providedBytes,
                                                List<KraftAdminProperties.AdminTokenHash> expectedTokenHashes) {
        if (expectedTokenHashes == null || expectedTokenHashes.isEmpty()) {
            return Optional.empty();
        }
        byte[] providedHash = sha256(providedBytes);
        for (KraftAdminProperties.AdminTokenHash candidate : expectedTokenHashes) {
            byte[] expectedHash = hexToBytes(candidate.hashHex());
            if (constantTimeEquals(providedHash, expectedHash)) {
                return Optional.of(candidate.id());
            }
        }
        return Optional.empty();
    }

    static boolean constantTimeEquals(byte[] left, byte[] right) {
        return MessageDigest.isEqual(left, right);
    }

    private static String aliasOf(String token) {
        byte[] digest = sha256(token.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder("tok-");
        for (int i = 0; i < 4; i++) {
            sb.append(String.format("%02x", digest[i]));
        }
        return sb.toString();
    }

    private static byte[] sha256(byte[] value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(value);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    private static String pathWithinApplication(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        if (servletPath != null && !servletPath.isBlank()) {
            return servletPath;
        }
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && requestUri.startsWith(contextPath)) {
            return requestUri.substring(contextPath.length());
        }
        return requestUri;
    }
}
