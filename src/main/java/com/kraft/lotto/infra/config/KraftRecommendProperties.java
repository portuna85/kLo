package com.kraft.lotto.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kraft.recommend")
public record KraftRecommendProperties(int maxAttempts) {
}
