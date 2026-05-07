package com.kraft.lotto.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kraft.admin")
public record KraftAdminProperties(
        String apiToken,
        String tokenHeader
) {
    public String resolvedTokenHeader() {
        return tokenHeader == null || tokenHeader.isBlank() ? "X-Kraft-Admin-Token" : tokenHeader.trim();
    }

    public boolean hasApiToken() {
        return apiToken != null && !apiToken.isBlank();
    }
}
