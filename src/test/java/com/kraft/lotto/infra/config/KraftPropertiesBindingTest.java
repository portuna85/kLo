package com.kraft.lotto.infra.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Configuration properties binding")
class KraftPropertiesBindingTest {

    @Autowired
    KraftApiProperties api;

    @Autowired
    KraftRecommendProperties recommend;

    @Test
    @DisplayName("binds api properties")
    void bindsApiProperties() {
        assertThat(api.client()).isEqualTo("mock");
        assertThat(api.url()).isEqualTo("http://localhost");
        assertThat(api.connectTimeoutMs()).isEqualTo(2000);
        assertThat(api.readTimeoutMs()).isEqualTo(3000);
        assertThat(api.maxRetries()).isEqualTo(2);
        assertThat(api.retryBackoffMs()).isEqualTo(50);
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
}
