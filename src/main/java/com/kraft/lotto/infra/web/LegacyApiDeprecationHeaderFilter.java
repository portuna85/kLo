package com.kraft.lotto.infra.web;

import com.kraft.lotto.infra.security.ApiPaths;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class LegacyApiDeprecationHeaderFilter extends OncePerRequestFilter {

    static final String DEPRECATION_HEADER = "Deprecation";
    static final String SUNSET_HEADER = "Sunset";
    static final String LINK_HEADER = "Link";

    private final String deprecationValue;
    private final String sunsetValue;
    private final boolean emitAfterSunset;
    private final Clock clock;

    @Autowired
    public LegacyApiDeprecationHeaderFilter(
            @Value("${kraft.api.legacy.deprecation.value:@1785542399}") String deprecationValue,
            @Value("${kraft.api.legacy.sunset.value:Fri, 31 Jul 2026 23:59:59 GMT}") String sunsetValue,
            @Value("${kraft.api.legacy.emit-after-sunset:true}") boolean emitAfterSunset) {
        this(deprecationValue, sunsetValue, emitAfterSunset, Clock.systemUTC());
    }

    LegacyApiDeprecationHeaderFilter(String deprecationValue, String sunsetValue, boolean emitAfterSunset, Clock clock) {
        this.deprecationValue = deprecationValue;
        this.sunsetValue = sunsetValue;
        this.emitAfterSunset = emitAfterSunset;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = requestPath(request);
        if (isLegacyApiPath(path) && shouldEmitHeaders()) {
            setIfMissing(response, DEPRECATION_HEADER, deprecationValue);
            setIfMissing(response, SUNSET_HEADER, sunsetValue);
            String successorPath = ApiPaths.API_V1_PREFIX + path.substring(ApiPaths.API_LEGACY_PREFIX.length());
            setIfMissing(response, LINK_HEADER, "<" + successorPath + ">; rel=\"successor-version\"");
        }
        filterChain.doFilter(request, response);
    }

    private boolean shouldEmitHeaders() {
        if (emitAfterSunset) {
            return true;
        }
        Instant sunsetInstant = parseSunsetInstant(sunsetValue);
        return sunsetInstant == null || clock.instant().isBefore(sunsetInstant);
    }

    private static Instant parseSunsetInstant(String value) {
        try {
            return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.US)).toInstant();
        } catch (DateTimeParseException ex) {
            return null;
        }
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
