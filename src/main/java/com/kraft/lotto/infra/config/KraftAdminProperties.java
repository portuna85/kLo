package com.kraft.lotto.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kraft.admin")
public record KraftAdminProperties(
        String apiToken,
        String apiTokens,
        String tokenHeader
) {
    public String resolvedTokenHeader() {
        return tokenHeader == null || tokenHeader.isBlank() ? "X-Kraft-Admin-Token" : tokenHeader.trim();
    }

    public boolean hasApiToken() {
        return (apiToken != null && !apiToken.isBlank()) || !resolvedApiTokens().isEmpty();
    }

    public java.util.List<String> resolvedApiTokens() {
        java.util.LinkedHashSet<String> tokens = new java.util.LinkedHashSet<>();
        if (apiTokens != null && !apiTokens.isBlank()) {
            for (String token : apiTokens.split(",")) {
                String trimmed = token.trim();
                if (!trimmed.isEmpty()) {
                    tokens.add(trimmed);
                }
            }
        }
        if (apiToken != null && !apiToken.isBlank()) {
            tokens.add(apiToken.trim());
        }
        return java.util.List.copyOf(tokens);
    }
}
