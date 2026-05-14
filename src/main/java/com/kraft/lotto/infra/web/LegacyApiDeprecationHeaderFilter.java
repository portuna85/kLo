package com.kraft.lotto.infra.web;

import com.kraft.lotto.infra.security.ApiPaths;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class LegacyApiDeprecationHeaderFilter extends OncePerRequestFilter {

    static final String DEPRECATION_HEADER = "Deprecation";
    static final String SUNSET_HEADER = "Sunset";
    static final String LINK_HEADER = "Link";
    static final String DEPRECATION_VALUE = "true";
    static final String SUNSET_VALUE = "Thu, 31 Jul 2026 23:59:59 GMT";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = requestPath(request);
        if (isLegacyApiPath(path)) {
            setIfMissing(response, DEPRECATION_HEADER, DEPRECATION_VALUE);
            setIfMissing(response, SUNSET_HEADER, SUNSET_VALUE);
            String successorPath = ApiPaths.API_V1_PREFIX + path.substring(ApiPaths.API_LEGACY_PREFIX.length());
            setIfMissing(response, LINK_HEADER, "<" + successorPath + ">; rel=\"successor-version\"");
        }
        filterChain.doFilter(request, response);
    }

    private static String requestPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath == null || contextPath.isBlank() || !uri.startsWith(contextPath)) {
            return uri;
        }
        return uri.substring(contextPath.length());
    }

    private static boolean isLegacyApiPath(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        if (!path.equals(ApiPaths.API_LEGACY_PREFIX) && !path.startsWith(ApiPaths.API_LEGACY_PREFIX + "/")) {
            return false;
        }
        return !path.startsWith(ApiPaths.API_V1_PREFIX + "/") && !path.equals(ApiPaths.API_V1_PREFIX);
    }

    private static void setIfMissing(HttpServletResponse response, String headerName, String value) {
        if (response.getHeader(headerName) == null) {
            response.setHeader(headerName, value);
        }
    }
}
