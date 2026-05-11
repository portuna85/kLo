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

    @DisplayName("테스트")
class AdminApiTokenFilterTest {

    private final AdminApiTokenFilter filter = new AdminApiTokenFilter(
            new KraftAdminProperties("secret-token", "X-Kraft-Admin-Token"),
            new ObjectMapper(),
            new SimpleMeterRegistry()
    );

    @Test
    @DisplayName("테스트")
    void ignoresNonProtectedEndpoint() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/winning-numbers/latest");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("테스트")
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
    @DisplayName("테스트")
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
    @DisplayName("테스트")
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
    @DisplayName("테스트")
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
    @DisplayName("테스트")
    void blocksAdminLottoDrawEndpointWithoutToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/lotto/draws/collect-next");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("테스트")
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
    @DisplayName("테스트")
    void blocksWhenServerTokenIsNotConfigured() throws Exception {
        AdminApiTokenFilter missingConfigFilter = new AdminApiTokenFilter(
                new KraftAdminProperties("", "X-Kraft-Admin-Token"),
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
}
