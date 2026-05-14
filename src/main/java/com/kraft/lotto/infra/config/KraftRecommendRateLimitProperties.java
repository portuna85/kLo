package com.kraft.lotto.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kraft.recommend.rate-limit")
public record KraftRecommendRateLimitProperties(
        int maxRequests,
        int windowSeconds,
        boolean trustForwardedHeaders,
        java.util.List<String> trustedProxyIps,
        Endpoint recommend,
        Endpoint collect
) {

    public KraftRecommendRateLimitProperties(int maxRequests, int windowSeconds) {
        this(maxRequests, windowSeconds, false, java.util.List.of(), null, null);
    }

    public KraftRecommendRateLimitProperties {
        validatePositive("kraft.recommend.rate-limit.max-requests", maxRequests);
        validatePositive("kraft.recommend.rate-limit.window-seconds", windowSeconds);
        trustedProxyIps = trustedProxyIps == null ? java.util.List.of()
                : trustedProxyIps.stream().filter(it -> it != null && !it.isBlank()).map(String::trim).toList();
        recommend = Endpoint.withDefaults(recommend, maxRequests, windowSeconds);
        collect = Endpoint.withDefaults(collect, maxRequests, windowSeconds);
    }

    public KraftRecommendRateLimitProperties() {
        this(30, 60, false, java.util.List.of(), null, null);
    }

    public Endpoint endpoint(String endpointName) {
        if ("collect".equals(endpointName)) {
            return collect;
        }
        return recommend;
    }

    private static void validatePositive(String propertyName, int value) {
        if (value <= 0) {
            throw new IllegalArgumentException(propertyName + " must be positive");
        }
    }

    public record Endpoint(int maxRequests, int windowSeconds) {

        public Endpoint {
            validatePositive("rate-limit endpoint max-requests", maxRequests);
            validatePositive("rate-limit endpoint window-seconds", windowSeconds);
        }

        static Endpoint withDefaults(Endpoint endpoint, int defaultMaxRequests, int defaultWindowSeconds) {
            return endpoint == null ? new Endpoint(defaultMaxRequests, defaultWindowSeconds) : endpoint;
        }
    }
}
