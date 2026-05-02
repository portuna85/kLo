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
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.security.web.FilterChainProxy;

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
    void public_recommend_rules_엔드포인트는_인증없이_접근가능() throws Exception {
        mockMvc().perform(get("/api/recommend/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void public_winning_numbers_latest는_인증없이_접근가능() throws Exception {
        Mockito.when(queryService.getLatest()).thenReturn(new WinningNumberDto(
                1100, LocalDate.of(2024, 1, 1),
                List.of(1, 7, 13, 22, 34, 45),
                8, 0L, 0, 0L));

        mockMvc().perform(get("/api/winning-numbers/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void actuator_health_은_인증없이_접근가능() throws Exception {
        mockMvc().perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void admin_엔드포인트는_미인증시_401_UNAUTHORIZED_ADMIN() throws Exception {
        mockMvc().perform(post("/api/admin/winning-numbers/refresh")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED_ADMIN"));
    }

    @Test
    void admin_엔드포인트는_잘못된_자격증명도_401() throws Exception {
        // testadmin / wrongpw (test 프로필 비밀번호는 testpw)
        String basic = "Basic " + java.util.Base64.getEncoder()
                .encodeToString("testadmin:wrongpw".getBytes());

        mockMvc().perform(post("/api/admin/winning-numbers/refresh")
                        .header(HttpHeaders.AUTHORIZATION, basic)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED_ADMIN"));
    }

    @Test
    void admin_엔드포인트는_ADMIN_자격증명으로_접근가능() throws Exception {
        Mockito.when(collectService.collect(Mockito.any()))
                .thenReturn(new CollectResponse(0, 0, 0, 0));

        // application-test.yml: kraft.admin.username=testadmin / password=testpw
        String basic = "Basic " + java.util.Base64.getEncoder()
                .encodeToString("testadmin:testpw".getBytes());

        mockMvc().perform(post("/api/admin/winning-numbers/refresh")
                        .header(HttpHeaders.AUTHORIZATION, basic)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
