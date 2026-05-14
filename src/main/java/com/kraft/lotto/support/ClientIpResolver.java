package com.kraft.lotto.support;

import com.kraft.lotto.infra.config.KraftRecommendRateLimitProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Set;

public final class ClientIpResolver {

    public static final String UNKNOWN_CLIENT_IP = "unknown";

    private ClientIpResolver() {
    }

    public static String resolve(HttpServletRequest request, KraftRecommendRateLimitProperties properties) {
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
}
