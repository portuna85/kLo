package com.kraft.lotto.infra.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kraft.lotto.infra.config.KraftAdminProperties;
import com.kraft.lotto.infra.config.KraftRecommendRateLimitProperties;
import com.kraft.lotto.support.ApiError;
import com.kraft.lotto.support.ApiResponse;
import com.kraft.lotto.support.ErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

@Configuration
@ConditionalOnWebApplication
public class SecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain adminSecurityFilterChain(HttpSecurity http,
                                                        AdminAuthenticationEntryPoint entryPoint,
                                                        AdminIpWhitelistFilter adminIpWhitelistFilter) throws Exception {
        http
                .securityMatcher("/api/admin/**", "/actuator/metrics/**")
                .csrf(AbstractHttpConfigurer::disable)
                .addFilterBefore(adminIpWhitelistFilter, BasicAuthenticationFilter.class)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().hasRole("ADMIN"))
                .httpBasic(basic -> basic.authenticationEntryPoint(entryPoint))
                .exceptionHandling(eh -> eh.authenticationEntryPoint(entryPoint));
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain appSecurityFilterChain(HttpSecurity http,
                                                      AdminAuthenticationEntryPoint entryPoint,
                                                      RecommendRateLimitFilter recommendRateLimitFilter) throws Exception {
        http
                .csrf(csrf -> csrf.ignoringRequestMatchers(
                        "/api/**",
                        "/actuator/**"
                ))
                .addFilterBefore(recommendRateLimitFilter, BasicAuthenticationFilter.class)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; " +
                                "img-src 'self' data:; " +
                                "style-src 'self' 'unsafe-inline'; " +
                                "font-src 'self' data:; " +
                                "script-src 'self'; " +
                                "connect-src 'self'; " +
                                "frame-ancestors 'none'; " +
                                "base-uri 'self'; " +
                                "form-action 'self'"))
                        .referrerPolicy(rp -> rp.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .permissionsPolicyHeader(pp -> pp.policy(
                                "geolocation=(), microphone=(), camera=(), payment=(), usb=()"))
                        .frameOptions(Customizer.withDefaults())
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/index", "/error", "/favicon.ico").permitAll()
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/webjars/**").permitAll()
                        .requestMatchers("/api/recommend/**").permitAll()
                        .requestMatchers("/api/winning-numbers/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/docs", "/docs/", "/docs/**").permitAll()
                        .anyRequest().authenticated()
                )
                .httpBasic(basic -> basic.authenticationEntryPoint(entryPoint))
                .exceptionHandling(eh -> eh.authenticationEntryPoint(entryPoint));
        return http.build();
    }

    @Bean
    public RecommendRateLimitFilter recommendRateLimitFilter(KraftRecommendRateLimitProperties properties,
                                                             MeterRegistry meterRegistry,
                                                             ObjectMapper objectMapper) {
        return new RecommendRateLimitFilter(properties, objectMapper, meterRegistry);
    }

    @Bean
    public AdminIpWhitelistFilter adminIpWhitelistFilter(KraftAdminProperties properties, ObjectMapper objectMapper) {
        return new AdminIpWhitelistFilter(properties.allowedIpRanges(), objectMapper);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(KraftAdminProperties props, PasswordEncoder encoder) {
        var admin = User.withUsername(props.username())
                .password(encoder.encode(props.password()))
                .roles("ADMIN")
                .build();
        return new InMemoryUserDetailsManager(admin);
    }

    @Bean
    public AdminAuthenticationEntryPoint adminAuthenticationEntryPoint(ObjectMapper objectMapper) {
        return new AdminAuthenticationEntryPoint(objectMapper);
    }

    public static class AdminAuthenticationEntryPoint
            implements org.springframework.security.web.AuthenticationEntryPoint {

        private final ObjectMapper objectMapper;

        public AdminAuthenticationEntryPoint(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public void commence(jakarta.servlet.http.HttpServletRequest request,
                             jakarta.servlet.http.HttpServletResponse response,
                             org.springframework.security.core.AuthenticationException authException)
                throws java.io.IOException {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json;charset=UTF-8");
            ApiResponse<Void> body = ApiResponse.failure(ApiError.of(ErrorCode.UNAUTHORIZED_ADMIN));
            response.getWriter().write(objectMapper.writeValueAsString(body));
        }
    }
}
