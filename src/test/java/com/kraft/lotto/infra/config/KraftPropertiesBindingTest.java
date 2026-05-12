package com.kraft.lotto.infra.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * ??쎈읃 16.7 Config Binding ???뮞??
 *
 * <p>{@code kraft.*} prefix ??{@code @ConfigurationProperties} ??쇱뵠 ?類ㅺ맒 獄쏅뗄???몃┷?遺? 野꺜筌앹빜釉??
 * test ?袁⑥쨮??疫꿸퀣?揶?
 * <ul>
 *     <li>kraft.api.client=mock / url=http://localhost</li>
 *     <li>kraft.recommend.max-attempts=1000</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
    @DisplayName("tests for KraftPropertiesBindingTest")
class KraftPropertiesBindingTest {

    @Autowired
    KraftApiProperties api;

    @Autowired
    KraftRecommendProperties recommend;

    @Autowired
    KraftRecommendRateLimitProperties rateLimit;

    @Test
    @DisplayName("binds api properties")
    void bindsApiProperties() {
        assertThat(api.client()).isEqualTo("mock");
        assertThat(api.url()).isEqualTo("http://localhost");
        assertThat(api.connectTimeoutMs()).isEqualTo(2000);
        assertThat(api.readTimeoutMs()).isEqualTo(3000);
        assertThat(api.maxRetries()).isEqualTo(2);
        assertThat(api.retryBackoffMs()).isEqualTo(50);
        assertThat(api.fallbackToMockOnFailure()).isFalse();
        assertThat(api.mockLatestRound()).isEqualTo(1200);
    }

    @Test
    @DisplayName("binds recommend properties")
    void bindsRecommendProperties() {
        assertThat(recommend.maxAttempts()).isEqualTo(1000);
        assertThat(recommend.rules().birthdayThreshold()).isEqualTo(31);
        assertThat(recommend.rules().longRunThreshold()).isEqualTo(5);
        assertThat(recommend.rules().decadeThreshold()).isEqualTo(5);
    }

    @Test
    @DisplayName("binds rate limit properties")
    void bindsRateLimitProperties() {
        assertThat(rateLimit.endpoint("recommend").maxRequests()).isEqualTo(30);
        assertThat(rateLimit.endpoint("recommend").windowSeconds()).isEqualTo(60);
        assertThat(rateLimit.endpoint("collect").maxRequests()).isEqualTo(30);
        assertThat(rateLimit.endpoint("collect").windowSeconds()).isEqualTo(60);
    }
}
