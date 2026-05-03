package com.kraft.lotto.infra.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 스펙 16.7 Config Binding 테스트.
 *
 * <p>{@code kraft.*} prefix 의 {@code @ConfigurationProperties} 들이 정상 바인딩되는지 검증한다.
 * test 프로필 기준값:
 * <ul>
 *     <li>kraft.admin.username=testadmin / password=testpw</li>
 *     <li>kraft.api.client=mock / url=http://localhost</li>
 *     <li>kraft.recommend.max-attempts=1000</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("kraft.* ConfigurationProperties 바인딩")
class KraftPropertiesBindingTest {

    @Autowired
    KraftAdminProperties admin;

    @Autowired
    KraftApiProperties api;

    @Autowired
    KraftRecommendProperties recommend;

    @Test
    @DisplayName("admin properties 가 정상 바인딩된다")
    void bindsAdminProperties() {
        assertThat(admin.username()).isEqualTo("testadmin");
        assertThat(admin.password()).isEqualTo("testpw");
    }

    @Test
    @DisplayName("api properties 가 정상 바인딩된다")
    void bindsApiProperties() {
        assertThat(api.client()).isEqualTo("mock");
        assertThat(api.url()).isEqualTo("http://localhost");
        assertThat(api.connectTimeoutMs()).isEqualTo(2000);
        assertThat(api.readTimeoutMs()).isEqualTo(3000);
        assertThat(api.maxRetries()).isEqualTo(2);
        assertThat(api.retryBackoffMs()).isEqualTo(50);
    }

    @Test
    @DisplayName("recommend properties 가 정상 바인딩된다")
    void bindsRecommendProperties() {
        assertThat(recommend.maxAttempts()).isEqualTo(1000);
    }
}
