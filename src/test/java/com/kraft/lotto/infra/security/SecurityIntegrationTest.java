package com.kraft.lotto.infra.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kraft.lotto.feature.recommend.application.RecommendService;
import com.kraft.lotto.feature.recommend.web.RecommendController;
import com.kraft.lotto.feature.winningnumber.application.LottoCollectionService;
import com.kraft.lotto.feature.winningnumber.application.WinningNumberQueryService;
import com.kraft.lotto.feature.winningnumber.web.WinningNumberCollectController;
import com.kraft.lotto.feature.winningnumber.web.WinningNumberController;
import com.kraft.lotto.feature.winningnumber.web.dto.CollectResponse;
import com.kraft.lotto.feature.winningnumber.web.dto.WinningNumberDto;
import com.kraft.lotto.infra.config.KraftAdminProperties;
import com.kraft.lotto.infra.config.KraftRateLimitRedisProperties;
import com.kraft.lotto.infra.config.KraftRecommendRateLimitProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {
        WinningNumberController.class,
        WinningNumberCollectController.class,
        RecommendController.class
})
@Import({SecurityConfig.class, SecurityIntegrationTest.TestJsonConfig.class})
@ImportAutoConfiguration({
        SecurityAutoConfiguration.class,
        SecurityFilterAutoConfiguration.class,
        ServletWebSecurityAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class
})
@ActiveProfiles("test")
    @DisplayName("tests for SecurityIntegrationTest")
class SecurityIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    WinningNumberQueryService queryService;

    @MockitoBean
    LottoCollectionService collectService;

    @MockitoBean
    RecommendService recommendService;

    @MockitoBean
    KraftAdminProperties adminProperties;

    @MockitoBean
    KraftRecommendRateLimitProperties rateLimitProperties;

    @MockitoBean
    KraftRateLimitRedisProperties redisRateLimitProperties;

    @Test
    @DisplayName("public recommend rules is accessible without auth")
    void publicRecommendRulesIsAccessibleWithoutAuth() throws Exception {
        givenSecurityProperties();
        Mockito.when(recommendService.rules()).thenReturn(List.of());

        mockMvc.perform(get("/api/recommend/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("public winning numbers latest is accessible without auth")
    void publicWinningNumbersLatestIsAccessibleWithoutAuth() throws Exception {
        givenSecurityProperties();
        Mockito.when(queryService.getLatest()).thenReturn(new WinningNumberDto(
                1100, LocalDate.of(2024, 1, 1),
                List.of(1, 7, 13, 22, 34, 45),
                8, 0L, 0, 0L));

        mockMvc.perform(get("/api/winning-numbers/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("winning number refresh requires admin token")
    void winningNumberRefreshRequiresAdminToken() throws Exception {
        givenSecurityProperties();
        mockMvc.perform(post("/api/winning-numbers/refresh")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED_ADMIN_API"));
    }

    @Test
    @DisplayName("winning number refresh is accessible with admin token")
    void winningNumberRefreshIsAccessibleWithAdminToken() throws Exception {
        givenSecurityProperties();
        Mockito.when(collectService.collect(Mockito.nullable(Integer.class)))
                .thenReturn(new CollectResponse(0, 0, 0, 0));

        mockMvc.perform(post("/api/winning-numbers/refresh")
                        .header("X-Kraft-Admin-Token", "test-admin-token")
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.10");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("winning number refresh is rate limited by ip")
    void winningNumberRefreshIsRateLimitedByIp() throws Exception {
        givenSecurityProperties();
        Mockito.when(collectService.collect(Mockito.nullable(Integer.class)))
                .thenReturn(new CollectResponse(0, 0, 0, 0));

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/winning-numbers/refresh")
                            .header("X-Kraft-Admin-Token", "test-admin-token")
                            .with(request -> {
                                request.setRemoteAddr("203.0.113.30");
                                return request;
                            })
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/winning-numbers/refresh")
                        .header("X-Kraft-Admin-Token", "test-admin-token")
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.30");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("TOO_MANY_REQUESTS"));
    }

    @Test
    @DisplayName("unknown endpoint is denied")
    void unknownEndpointIsDenied() throws Exception {
        givenSecurityProperties();
        mockMvc.perform(get("/admin/unknown"))
                .andExpect(status().isUnauthorized());
    }

    private void givenSecurityProperties() {
        Mockito.when(adminProperties.apiToken()).thenReturn("test-admin-token");
        Mockito.when(adminProperties.resolvedApiTokens()).thenReturn(List.of("test-admin-token"));
        Mockito.when(adminProperties.hasApiToken()).thenReturn(true);
        Mockito.when(adminProperties.resolvedTokenHeader()).thenReturn("X-Kraft-Admin-Token");
        Mockito.when(rateLimitProperties.endpoint("recommend"))
                .thenReturn(new KraftRecommendRateLimitProperties.Endpoint(30, 60));
        Mockito.when(rateLimitProperties.endpoint("collect"))
                .thenReturn(new KraftRecommendRateLimitProperties.Endpoint(10, 60));
        Mockito.when(redisRateLimitProperties.enabled()).thenReturn(false);
        Mockito.when(redisRateLimitProperties.resolvedKeyPrefix()).thenReturn("kraft:rate-limit");
    }

    static class TestJsonConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("winningNumberFrequency", "combinationPrizeHistory");
        }
    }
}
