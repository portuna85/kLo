package com.kraft.lotto.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kraft.recommend.rate-limit")
public record KraftRecommendRateLimitProperties(int maxRequests, int windowSeconds) {

    public KraftRecommendRateLimitProperties {
        if (maxRequests <= 0) {
            throw new IllegalArgumentException("kraft.recommend.rate-limit.max-requests must be positive");
        }
        if (windowSeconds <= 0) {
            throw new IllegalArgumentException("kraft.recommend.rate-limit.window-seconds must be positive");
        }
    }
}
