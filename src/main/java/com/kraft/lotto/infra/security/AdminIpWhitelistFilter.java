package com.kraft.lotto.infra.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kraft.lotto.support.ApiError;
import com.kraft.lotto.support.ApiResponse;
import com.kraft.lotto.support.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

public class AdminIpWhitelistFilter extends OncePerRequestFilter {

    private final List<IpAddressMatcher> allowedMatchers;
    private final ObjectMapper objectMapper;

    public AdminIpWhitelistFilter(List<String> allowedIpRanges, ObjectMapper objectMapper) {
        List<IpAddressMatcher> parsedMatchers = (allowedIpRanges == null ? List.<String>of() : allowedIpRanges).stream()
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(IpAddressMatcher::new)
                .toList();
        this.allowedMatchers = parsedMatchers.isEmpty()
                ? List.of(new IpAddressMatcher("127.0.0.1/32"), new IpAddressMatcher("::1/128"))
                : parsedMatchers;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (isAllowed(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/json;charset=UTF-8");
        ApiResponse<Void> body = ApiResponse.failure(ApiError.of(ErrorCode.FORBIDDEN_ADMIN_IP));
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private boolean isAllowed(HttpServletRequest request) {
        for (IpAddressMatcher matcher : allowedMatchers) {
            if (matcher.matches(request)) {
                return true;
            }
        }
        return false;
    }
}
