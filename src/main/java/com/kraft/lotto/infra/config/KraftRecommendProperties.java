package com.kraft.lotto.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kraft.recommend")
public record KraftRecommendProperties(int maxAttempts, Rules rules) {

    public KraftRecommendProperties {
        if (rules == null) {
            rules = new Rules(31, 5, 5);
        }
    }

    public record Rules(int birthdayThreshold, int longRunThreshold, int decadeThreshold) {
    }
}
