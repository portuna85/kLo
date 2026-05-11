package com.kraft.lotto.infra.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kraft.lotto.infra.config.KraftAdminProperties;
import com.kraft.lotto.infra.config.KraftRateLimitRedisProperties;
import com.kraft.lotto.infra.config.KraftRecommendRateLimitProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

@Configuration
@ConditionalOnWebApplication
public class SecurityConfig {

    @Bean
    public SecurityFilterChain appSecurityFilterChain(HttpSecurity http,
                                                      RecommendRateLimitFilter recommendRateLimitFilter,
                                                      AdminApiTokenFilter adminApiTokenFilter) throws Exception {
        http
                .csrf(csrf -> csrf.ignoringRequestMatchers(
                        "/api/**",
                        "/admin/**",
                        "/actuator/**"
                ))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .addFilterBefore(recommendRateLimitFilter, BasicAuthenticationFilter.class)
                .addFilterBefore(adminApiTokenFilter, RecommendRateLimitFilter.class)
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
                        .requestMatchers("/robots.txt", "/sitemap.xml").permitAll()
                        .requestMatchers("/api/recommend/**", "/api/v1/recommend/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/winning-numbers/**", "/api/v1/winning-numbers/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/winning-numbers/refresh", "/api/v1/winning-numbers/refresh").authenticated()
                        .requestMatchers(HttpMethod.POST, "/admin/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/admin/**").authenticated()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/docs", "/docs/", "/docs/**").permitAll()
                        .anyRequest().denyAll()
                );
        return http.build();
    }

    @Bean
    public AdminApiTokenFilter adminApiTokenFilter(KraftAdminProperties properties,
                                                   MeterRegistry meterRegistry,
                                                   ObjectMapper objectMapper) {
        return new AdminApiTokenFilter(properties, objectMapper, meterRegistry);
    }

    @Bean
    public RecommendRateLimitFilter recommendRateLimitFilter(KraftRecommendRateLimitProperties properties,
                                                             KraftRateLimitRedisProperties redisProperties,
                                                             MeterRegistry meterRegistry,
                                                             ObjectMapper objectMapper,
                                                             ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        return new RecommendRateLimitFilter(
                properties,
                objectMapper,
                meterRegistry,
                redisTemplateProvider.getIfAvailable(),
                redisProperties
        );
    }
}

