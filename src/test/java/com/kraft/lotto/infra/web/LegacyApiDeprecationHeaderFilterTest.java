package com.kraft.lotto.infra.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@DisplayName("LegacyApiDeprecationHeaderFilter")
class LegacyApiDeprecationHeaderFilterTest {

    private final LegacyApiDeprecationHeaderFilter filter = new LegacyApiDeprecationHeaderFilter();

    @Test
    @DisplayName("legacy /api path adds deprecation headers")
    void addsHeadersForLegacyApiPath() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/recommend");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader("Deprecation")).isEqualTo("true");
        assertThat(response.getHeader("Sunset")).isEqualTo("Thu, 31 Jul 2026 23:59:59 GMT");
        assertThat(response.getHeader("Link")).isEqualTo("</api/v1/recommend>; rel=\"successor-version\"");
    }

    @Test
    @DisplayName("/api/v1 path does not add deprecation headers")
    void doesNotAddHeadersForV1Path() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/winning-numbers/latest");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader("Deprecation")).isNull();
        assertThat(response.getHeader("Sunset")).isNull();
        assertThat(response.getHeader("Link")).isNull();
    }
}
