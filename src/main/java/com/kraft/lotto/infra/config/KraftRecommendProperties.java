package com.kraft.lotto.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kraft.recommend")
public record KraftRecommendProperties(int maxAttempts,
                                       int initialPickMaxAttempts,
                                       int fixupMaxAttempts,
                                       Rules rules) {

    public KraftRecommendProperties {
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("kraft.recommend.max-attempts must be positive");
        }
        if (initialPickMaxAttempts <= 0) {
            throw new IllegalArgumentException("kraft.recommend.initial-pick-max-attempts must be positive");
        }
        if (fixupMaxAttempts <= 0) {
            throw new IllegalArgumentException("kraft.recommend.fixup-max-attempts must be positive");
        }
        if (rules == null) {
            rules = new Rules(31, 5, 5);
        }
    }

    public record Rules(int birthdayThreshold, int longRunThreshold, int decadeThreshold) {
        public Rules {
            if (birthdayThreshold < 1 || birthdayThreshold > 44) {
                throw new IllegalArgumentException(
                        "kraft.recommend.rules.birthday-threshold must be between 1 and 44");
            }
            if (longRunThreshold < 2 || longRunThreshold > 6) {
                throw new IllegalArgumentException(
                        "kraft.recommend.rules.long-run-threshold must be between 2 and 6");
            }
            if (decadeThreshold < 2 || decadeThreshold > 6) {
                throw new IllegalArgumentException(
                        "kraft.recommend.rules.decade-threshold must be between 2 and 6");
            }
        }
    }
}
