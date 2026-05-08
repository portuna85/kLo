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

@DisplayName("AdminApiTokenFilter")
class AdminApiTokenFilterTest {

    private final AdminApiTokenFilter filter = new AdminApiTokenFilter(
            new KraftAdminProperties("secret-token", "X-Kraft-Admin-Token"),
            new ObjectMapper(),
            new SimpleMeterRegistry()
    );

    @Test
    @DisplayName("보호 대상이 아니면 필터링하지 않는다")
    void ignoresNonProtectedEndpoint() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/winning-numbers/latest");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("관리자 토큰이 없으면 401을 반환한다")
    void blocksMissingToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/winning-numbers/refresh");
        request.setServletPath("/api/winning-numbers/refresh");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("UNAUTHORIZED_ADMIN_API");
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("관리자 토큰이 틀리면 401을 반환한다")
    void blocksInvalidToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/winning-numbers/refresh");
        request.setServletPath("/api/winning-numbers/refresh");
        request.addHeader("X-Kraft-Admin-Token", "wrong-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("UNAUTHORIZED_ADMIN_API");
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("관리자 토큰이 맞으면 요청을 통과시킨다")
    void allowsValidToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/winning-numbers/refresh");
        request.setServletPath("/api/winning-numbers/refresh");
        request.addHeader("X-Kraft-Admin-Token", "secret-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("서버에 관리자 토큰이 설정되지 않으면 보호 대상 요청을 차단한다")
    void blocksWhenServerTokenIsNotConfigured() throws Exception {
        AdminApiTokenFilter missingConfigFilter = new AdminApiTokenFilter(
                new KraftAdminProperties("", "X-Kraft-Admin-Token"),
                new ObjectMapper(),
                new SimpleMeterRegistry()
        );
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/winning-numbers/refresh");
        request.setServletPath("/api/winning-numbers/refresh");
        request.addHeader("X-Kraft-Admin-Token", "secret-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        missingConfigFilter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(chain);
    }
}
