package com.kraft.lotto.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kraft.api")
public record KraftApiProperties(
        String client,
        String url,
        int connectTimeoutMs,
        int readTimeoutMs,
        int maxRetries,
        int retryBackoffMs,
        boolean fallbackToMockOnFailure,
        int mockLatestRound
) {
}
