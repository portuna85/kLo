package com.kraft.lotto.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kraft.admin")
public record KraftAdminProperties(
        String apiToken,
        String apiTokens,
        String tokenHeader
) {
    private static final String DEFAULT_TOKEN_HEADER = "X-Kraft-Admin-Token";

    public String resolvedTokenHeader() {
        return normalize(tokenHeader, DEFAULT_TOKEN_HEADER);
    }

    public boolean hasApiToken() {
        return (apiToken != null && !apiToken.isBlank()) || !resolvedApiTokens().isEmpty();
    }

    public java.util.List<String> resolvedApiTokens() {
        java.util.LinkedHashSet<String> tokens = new java.util.LinkedHashSet<>();
        for (String token : splitTokens(apiTokens)) {
            tokens.add(token);
        }
        String singleToken = normalize(apiToken, null);
        if (singleToken != null) {
            tokens.add(singleToken);
        }
        return java.util.List.copyOf(tokens);
    }

    private static java.util.List<String> splitTokens(String rawTokens) {
        if (rawTokens == null || rawTokens.isBlank()) {
            return java.util.List.of();
        }
        java.util.List<String> result = new java.util.ArrayList<>();
        for (String token : rawTokens.split(",")) {
            String normalized = normalize(token, null);
            if (normalized != null) {
                result.add(normalized);
            }
        }
        return result;
    }

    private static String normalize(String value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? defaultValue : trimmed;
    }
}
