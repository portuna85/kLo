package com.kraft.lotto.infra.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kraft.lotto.infra.config.KraftAdminProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@DisplayName("관리자 API 토큰 필터 테스트")
class AdminApiTokenFilterTest {

    private final AdminApiTokenFilter filter = new AdminApiTokenFilter(
            new KraftAdminProperties("secret-token", "", "X-Kraft-Admin-Token"),
            new ObjectMapper(),
            new SimpleMeterRegistry()
    );

    @Test
    @DisplayName("보호되지 않은 엔드포인트는 무시한다")
    void ignoresNonProtectedEndpoint() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/winning-numbers/latest");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("토큰이 누락된 요청은 차단한다")
    void blocksMissingToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/winning-numbers/refresh");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("UNAUTHORIZED_ADMIN_API");
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("컨텍스트 경로가 포함된 보호된 엔드포인트를 차단한다")
    void blocksProtectedEndpointWithContextPath() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/klo/api/winning-numbers/refresh");
        request.setContextPath("/klo");
        request.setServletPath("/api/winning-numbers/refresh");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("유효하지 않은 토큰은 차단한다")
    void blocksInvalidToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/winning-numbers/refresh");
        request.addHeader("X-Kraft-Admin-Token", "wrong-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("UNAUTHORIZED_ADMIN_API");
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("유효한 토큰은 허용한다")
    void allowsValidToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/winning-numbers/refresh");
        request.addHeader("X-Kraft-Admin-Token", "secret-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("토큰 없이 관리자 로또 당첨 엔드포인트 접근을 차단한다")
    void blocksAdminLottoDrawEndpointWithoutToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/lotto/draws/collect-next");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("유효한 토큰으로 관리자 로또 당첨 엔드포인트 접근을 허용한다")
    void allowsAdminLottoDrawEndpointWithValidToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/lotto/draws/collect-next");
        request.addHeader("X-Kraft-Admin-Token", "secret-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("서버 토큰이 설정되지 않은 경우 차단한다")
    void blocksWhenServerTokenIsNotConfigured() throws Exception {
        AdminApiTokenFilter missingConfigFilter = new AdminApiTokenFilter(
                new KraftAdminProperties("", "", "X-Kraft-Admin-Token"),
                new ObjectMapper(),
                new SimpleMeterRegistry()
        );
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/winning-numbers/refresh");
        request.addHeader("X-Kraft-Admin-Token", "secret-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        missingConfigFilter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("순환되는 여러 토큰 중 하나라도 일치하면 허용한다")
    void allowsOneOfRotatedCsvTokens() throws Exception {
        AdminApiTokenFilter rotatedFilter = new AdminApiTokenFilter(
                new KraftAdminProperties("", "token-a, token-b", "X-Kraft-Admin-Token"),
                new ObjectMapper(),
                new SimpleMeterRegistry()
        );
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/lotto/draws/collect-next");
        request.addHeader("X-Kraft-Admin-Token", "token-b");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        rotatedFilter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(request, response);
    }
}
