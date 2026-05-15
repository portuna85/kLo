package com.kraft.lotto.infra.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kraft.lotto.infra.config.KraftAdminProperties;
import com.kraft.lotto.infra.config.KraftRateLimitRedisProperties;
import com.kraft.lotto.infra.config.KraftRecommendRateLimitProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.CrossOriginOpenerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.CrossOriginResourcePolicyHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

@Configuration
@ConditionalOnWebApplication
public class SecurityConfig {

    @Bean
    public SecurityFilterChain appSecurityFilterChain(HttpSecurity http,
                                                      RecommendRateLimitFilter recommendRateLimitFilter,
                                                      AdminApiTokenFilter adminApiTokenFilter,
                                                      CspNonceFilter cspNonceFilter,
                                                      @Value("${kraft.security.monitoring.public-prometheus:false}")
                                                      boolean publicPrometheus) throws Exception {
        http
                .csrf(csrf -> csrf.ignoringRequestMatchers(
                        "/api/**",
                        "/admin/**",
                        "/actuator/**",
                        "/csp/**"
                ))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .addFilterBefore(recommendRateLimitFilter, BasicAuthenticationFilter.class)
                .addFilterBefore(adminApiTokenFilter, RecommendRateLimitFilter.class)
                .addFilterAfter(cspNonceFilter, AdminApiTokenFilter.class)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h
                        .crossOriginOpenerPolicy(coop -> coop.policy(
                                CrossOriginOpenerPolicyHeaderWriter.CrossOriginOpenerPolicy.SAME_ORIGIN))
                        .crossOriginResourcePolicy(corp -> corp.policy(
                                CrossOriginResourcePolicyHeaderWriter.CrossOriginResourcePolicy.SAME_ORIGIN))
                        .referrerPolicy(rp -> rp.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .permissionsPolicyHeader(pp -> pp.policy(
                                "geolocation=(), microphone=(), camera=(), payment=(), usb=()"))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .maxAgeInSeconds(31536000)
                                .includeSubDomains(true)
                                .preload(true))
                        .contentTypeOptions(Customizer.withDefaults())
                        .frameOptions(Customizer.withDefaults())
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/index", "/error", "/favicon.ico").permitAll()
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/webjars/**").permitAll()
                        .requestMatchers("/robots.txt", "/sitemap.xml").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(ApiPaths.RECOMMEND_LEGACY + "/**", ApiPaths.RECOMMEND_V1 + "/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/winning-numbers/**", "/api/v1/winning-numbers/**").permitAll()
                        .requestMatchers(HttpMethod.POST, ApiPaths.COLLECT_REFRESH_LEGACY, ApiPaths.COLLECT_REFRESH_V1).authenticated()
                        .requestMatchers(HttpMethod.POST, ApiPaths.ADMIN_PREFIX + "**").authenticated()
                        .requestMatchers(HttpMethod.GET, ApiPaths.ADMIN_PREFIX + "**").authenticated()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/csp/report").permitAll()
                        .requestMatchers("/actuator/prometheus").access((authentication, context) ->
                                new org.springframework.security.authorization.AuthorizationDecision(publicPrometheus))
                        .requestMatchers("/docs", "/docs/", "/docs/**").permitAll()
                        .anyRequest().denyAll()
                );
        return http.build();
    }

    @Bean
    public AdminApiTokenFilter adminApiTokenFilter(KraftAdminProperties properties,
                                                   KraftRecommendRateLimitProperties rateLimitProperties,
                                                   MeterRegistry meterRegistry,
                                                   ObjectMapper objectMapper) {
        return new AdminApiTokenFilter(properties, rateLimitProperties, objectMapper, meterRegistry);
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
