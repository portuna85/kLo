package com.kraft.lotto.infra.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletException;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@DisplayName("LegacyApiDeprecationHeaderFilter")
class LegacyApiDeprecationHeaderFilterTest {

    private final LegacyApiDeprecationHeaderFilter filter = new LegacyApiDeprecationHeaderFilter(
            "@1785542399",
            "Fri, 31 Jul 2026 23:59:59 GMT",
            true,
            Clock.fixed(Instant.parse("2026-05-01T00:00:00Z"), ZoneOffset.UTC)
    );

    @Test
    @DisplayName("legacy /api path adds deprecation headers")
    void addsHeadersForLegacyApiPath() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/recommend");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader("Deprecation")).isEqualTo("@1785542399");
        assertThat(response.getHeader("Sunset")).isEqualTo("Fri, 31 Jul 2026 23:59:59 GMT");
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

    @Test
    @DisplayName("emit-after-sunset=false and past sunset does not add headers")
    void doesNotAddHeadersAfterSunsetWhenDisabled() throws ServletException, IOException {
        LegacyApiDeprecationHeaderFilter sunsetDisabled = new LegacyApiDeprecationHeaderFilter(
                "@1785542399",
                "Fri, 31 Jul 2026 23:59:59 GMT",
                false,
                Clock.fixed(Instant.parse("2026-08-01T00:00:00Z"), ZoneOffset.UTC)
        );
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/recommend");
        MockHttpServletResponse response = new MockHttpServletResponse();

        sunsetDisabled.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader("Deprecation")).isNull();
        assertThat(response.getHeader("Sunset")).isNull();
        assertThat(response.getHeader("Link")).isNull();
    }
}
