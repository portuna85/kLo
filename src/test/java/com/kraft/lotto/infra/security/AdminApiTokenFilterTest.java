package com.kraft.lotto.infra.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kraft.lotto.infra.config.KraftAdminProperties;
import com.kraft.lotto.infra.config.KraftRecommendRateLimitProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@DisplayName("Admin API token filter tests")
class AdminApiTokenFilterTest {

    private final AdminApiTokenFilter filter = new AdminApiTokenFilter(
            new KraftAdminProperties("secret-token", "", "X-Kraft-Admin-Token"),
            new KraftRecommendRateLimitProperties(),
            new ObjectMapper(),
            new SimpleMeterRegistry()
    );

    @Test
    @DisplayName("Ignores non-protected endpoints")
    void ignoresNonProtectedEndpoint() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/winning-numbers/latest");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("Blocks request when token is missing")
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
    @DisplayName("Blocks protected endpoint even with context path")
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
    @DisplayName("Blocks invalid token")
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
    @DisplayName("Allows valid token")
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
    @DisplayName("Blocks admin lotto draws endpoint without token")
    void blocksAdminLottoDrawEndpointWithoutToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/lotto/draws/collect-next");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("Allows admin lotto draws endpoint with valid token")
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
    @DisplayName("Blocks when server token is not configured")
    void blocksWhenServerTokenIsNotConfigured() throws Exception {
        AdminApiTokenFilter missingConfigFilter = new AdminApiTokenFilter(
                new KraftAdminProperties("", "", "X-Kraft-Admin-Token"),
                new KraftRecommendRateLimitProperties(),
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
    @DisplayName("Allows any one of rotated csv tokens")
    void allowsOneOfRotatedCsvTokens() throws Exception {
        AdminApiTokenFilter rotatedFilter = new AdminApiTokenFilter(
                new KraftAdminProperties("", "token-a, token-b", "X-Kraft-Admin-Token"),
                new KraftRecommendRateLimitProperties(),
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
