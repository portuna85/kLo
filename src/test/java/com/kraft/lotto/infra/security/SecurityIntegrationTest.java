package com.kraft.lotto.infra.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kraft.lotto.feature.winningnumber.application.WinningNumberCollectService;
import com.kraft.lotto.feature.winningnumber.application.WinningNumberQueryService;
import com.kraft.lotto.feature.winningnumber.web.dto.CollectResponse;
import com.kraft.lotto.feature.winningnumber.web.dto.WinningNumberDto;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 스펙 16.6 Security 테스트.
 *
 * <p>실제 Spring Security 필터 체인이 적용된 상태에서 다음을 검증한다:
 * <ul>
 *     <li>public endpoint는 인증 없이 접근 가능</li>
 *     <li>당첨번호 수집 트리거도 인증 없이 접근 가능</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Security 통합 테스트")
class SecurityIntegrationTest {

    @Autowired
    WebApplicationContext context;

    @Autowired
    FilterChainProxy springSecurityFilterChain;

    @MockitoBean
    WinningNumberQueryService queryService;

    @MockitoBean
    WinningNumberCollectService collectService;

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    @Test
    @DisplayName("public recommend/rules 엔드포인트는 인증 없이 접근 가능하다")
    void publicRecommendRulesIsAccessibleWithoutAuth() throws Exception {
        mockMvc().perform(get("/api/recommend/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("public winning-numbers/latest 는 인증 없이 접근 가능하다")
    void publicWinningNumbersLatestIsAccessibleWithoutAuth() throws Exception {
        Mockito.when(queryService.getLatest()).thenReturn(new WinningNumberDto(
                1100, LocalDate.of(2024, 1, 1),
                List.of(1, 7, 13, 22, 34, 45),
                8, 0L, 0, 0L));

        mockMvc().perform(get("/api/winning-numbers/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("actuator/health 는 인증 없이 접근 가능하다")
    void actuatorHealthIsAccessibleWithoutAuth() throws Exception {
        mockMvc().perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("actuator health group endpoints are accessible without auth")
    void actuatorHealthGroupsAreAccessibleWithoutAuth() throws Exception {
        mockMvc().perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("당첨번호 수집 트리거는 인증 없이 접근 가능하다")
    void winningNumberRefreshIsAccessibleWithoutAuth() throws Exception {
        Mockito.when(collectService.collect(Mockito.nullable(Integer.class)))
                .thenReturn(new CollectResponse(0, 0, 0, 0));

        mockMvc().perform(post("/api/winning-numbers/refresh")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("당첨번호 수집 트리거는 외부 IP에서도 별도 IP 제한 없이 접근 가능하다")
    void winningNumberRefreshDoesNotRequireIpWhitelist() throws Exception {
        Mockito.when(collectService.collect(Mockito.nullable(Integer.class)))
                .thenReturn(new CollectResponse(0, 0, 0, 0));

        mockMvc().perform(post("/api/winning-numbers/refresh")
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.10");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}

