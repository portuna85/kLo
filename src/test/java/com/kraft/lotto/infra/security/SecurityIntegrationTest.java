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
import org.springframework.http.HttpHeaders;
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
 *     <li>{@code /api/admin/**} 는 미인증 시 401 + {@code UNAUTHORIZED_ADMIN}</li>
 *     <li>잘못된 자격증명도 401</li>
 *     <li>ADMIN 권한 인증 시 정상 응답</li>
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
    @DisplayName("admin 엔드포인트는 미인증 시 401 UNAUTHORIZED_ADMIN 을 반환한다")
    void adminEndpointReturns401WhenUnauthenticated() throws Exception {
        mockMvc().perform(post("/api/admin/winning-numbers/refresh")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED_ADMIN"));
    }

    @Test
    @DisplayName("admin 엔드포인트는 잘못된 자격증명에도 401을 반환한다")
    void adminEndpointReturns401WhenCredentialsInvalid() throws Exception {
        // testadmin / wrongpw (test 프로필 비밀번호는 testpw)
        String basic = basicAuth("testadmin", "wrongpw");

        mockMvc().perform(post("/api/admin/winning-numbers/refresh")
                        .header(HttpHeaders.AUTHORIZATION, basic)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED_ADMIN"));
    }

    @Test
    @DisplayName("admin 엔드포인트는 ADMIN 자격증명으로 접근 가능하다")
    void adminEndpointIsAccessibleWithAdminCredentials() throws Exception {
        Mockito.when(collectService.collect(Mockito.any()))
                .thenReturn(new CollectResponse(0, 0, 0, 0));

        // application-test.yml: kraft.admin.username=testadmin / password=testpw
        String basic = basicAuth("testadmin", "testpw");

        mockMvc().perform(post("/api/admin/winning-numbers/refresh")
                        .header(HttpHeaders.AUTHORIZATION, basic)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("admin 엔드포인트는 비허용 IP에서 403 FORBIDDEN_ADMIN_IP 를 반환한다")
    void adminEndpointReturns403WhenIpNotAllowed() throws Exception {
        String basic = basicAuth("testadmin", "testpw");

        mockMvc().perform(post("/api/admin/winning-numbers/refresh")
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.10");
                            return request;
                        })
                        .header(HttpHeaders.AUTHORIZATION, basic)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN_ADMIN_IP"));
    }

    private static String basicAuth(String username, String password) {
        return "Basic " + java.util.Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes());
    }
}

