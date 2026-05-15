package com.kraft.lotto.infra.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@DisplayName("CSP nonce filter tests")
class CspNonceFilterTest {

    @Test
    @DisplayName("Adds nonce attribute and CSP header per request")
    void addsNonceAndHeader() throws Exception {
        CspNonceFilter filter = new CspNonceFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        Object nonce = request.getAttribute(CspNonceFilter.CSP_NONCE_ATTRIBUTE);
        assertThat(nonce).isInstanceOf(String.class);
        String nonceValue = (String) nonce;
        assertThat(nonceValue).isNotBlank();
        String csp = response.getHeader("Content-Security-Policy");
        String reportTo = response.getHeader("Report-To");
        assertThat(csp).contains("script-src 'self' 'nonce-" + nonceValue + "'");
        assertThat(csp).contains("report-uri /csp/report");
        assertThat(csp).contains("report-to csp-endpoint");
        assertThat(reportTo).contains("\"group\":\"csp-endpoint\"");
        assertThat(reportTo).contains("\"url\":\"/csp/report\"");
        verify(chain).doFilter(request, response);
    }
}
