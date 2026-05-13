package com.kraft.lotto.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kraft.recommend.rate-limit.redis")
public record KraftRateLimitRedisProperties(
        boolean enabled,
        String keyPrefix,
        boolean strict
) {
    public KraftRateLimitRedisProperties() {
        this(false, "kraft:rate-limit", false);
    }

    public String resolvedKeyPrefix() {
        return (keyPrefix == null || keyPrefix.isBlank()) ? "kraft:rate-limit" : keyPrefix.trim();
    }
}
