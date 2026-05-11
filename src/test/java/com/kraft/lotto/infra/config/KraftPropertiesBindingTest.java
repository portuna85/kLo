package com.kraft.lotto.infra.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * ?ㅽ럺 16.7 Config Binding ?뚯뒪??
 *
 * <p>{@code kraft.*} prefix ??{@code @ConfigurationProperties} ?ㅼ씠 ?뺤긽 諛붿씤?⑸릺?붿? 寃利앺븳??
 * test ?꾨줈??湲곗?媛?
 * <ul>
 *     <li>kraft.api.client=mock / url=http://localhost</li>
 *     <li>kraft.recommend.max-attempts=1000</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
    @DisplayName("테스트")
class KraftPropertiesBindingTest {

    @Autowired
    KraftApiProperties api;

    @Autowired
    KraftRecommendProperties recommend;

    @Autowired
    KraftRecommendRateLimitProperties rateLimit;

    @Test
    @DisplayName("테스트")
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
    @DisplayName("테스트")
    void bindsRecommendProperties() {
        assertThat(recommend.maxAttempts()).isEqualTo(1000);
        assertThat(recommend.rules().birthdayThreshold()).isEqualTo(31);
        assertThat(recommend.rules().longRunThreshold()).isEqualTo(5);
        assertThat(recommend.rules().decadeThreshold()).isEqualTo(5);
    }

    @Test
    @DisplayName("테스트")
    void bindsRateLimitProperties() {
        assertThat(rateLimit.endpoint("recommend").maxRequests()).isEqualTo(30);
        assertThat(rateLimit.endpoint("recommend").windowSeconds()).isEqualTo(60);
        assertThat(rateLimit.endpoint("collect").maxRequests()).isEqualTo(30);
        assertThat(rateLimit.endpoint("collect").windowSeconds()).isEqualTo(60);
    }
}
