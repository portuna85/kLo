package com.kraft.lotto.infra.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class CspNonceFilter extends OncePerRequestFilter {

    public static final String CSP_NONCE_ATTRIBUTE = "cspNonce";
    private static final String REPORT_TO_VALUE =
            "{\"group\":\"csp-endpoint\",\"max_age\":10886400,\"endpoints\":[{\"url\":\"/csp/report\"}],\"include_subdomains\":false}";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String nonce = generateNonce();
        request.setAttribute(CSP_NONCE_ATTRIBUTE, nonce);
        response.setHeader("Report-To", REPORT_TO_VALUE);
        response.setHeader("Content-Security-Policy", buildPolicy(nonce));
        filterChain.doFilter(request, response);
    }

    private static String generateNonce() {
        byte[] bytes = new byte[16];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static String buildPolicy(String nonce) {
        return "default-src 'self'; "
                + "img-src 'self' data:; "
                + "style-src 'self' 'unsafe-inline'; "
                + "font-src 'self' data:; "
                + "script-src 'self' 'nonce-" + nonce + "'; "
                + "connect-src 'self'; "
                + "frame-ancestors 'none'; "
                + "base-uri 'self'; "
                + "form-action 'self'; "
                + "report-uri /csp/report; "
                + "report-to csp-endpoint";
    }
}
