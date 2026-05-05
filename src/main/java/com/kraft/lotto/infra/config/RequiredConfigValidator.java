package com.kraft.lotto.infra.config;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * 부팅 직후 필수 설정값의 존재/유효성을 검증한다. 미해결 placeholder({@code ${...}}) 가
 * 데이터소스 초기화까지 흘러가 알기 어려운 예외 메시지로 변하는 문제를 사전에 차단한다.
 *
 * <p>검증 대상 키와 그 의미는 {@link #REQUIRED} 매핑을 참조하라.
 * 검증 실패 시 {@link IllegalStateException} 으로 즉시 컨텍스트 초기화를 중단한다.
 */
public class RequiredConfigValidator implements EnvironmentPostProcessor, Ordered {

    /** Dotenv 보다 뒤, 일반 PropertySource 들 다 적재된 직후에 동작. */
    public static final int ORDER = DotenvEnvironmentPostProcessor.ORDER + 100;

    /** key → 사람 친화 설명. */
    private static final Map<String, String> REQUIRED = new LinkedHashMap<>();
    static {
        REQUIRED.put("spring.datasource.url",      "DB JDBC URL (env: KRAFT_DB_URL)");
        REQUIRED.put("spring.datasource.username", "DB 사용자 (env: KRAFT_DB_USER)");
        REQUIRED.put("spring.datasource.password", "DB 비밀번호 (env: KRAFT_DB_PASSWORD)");
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
        if (isRunningUnderTest()) {
            return;
        }
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, String> e : REQUIRED.entrySet()) {
            String key = e.getKey();
            String raw;
            try {
                raw = env.getProperty(key);
            } catch (RuntimeException placeholderFail) {
                // 미해결 placeholder 의 경우 Spring 이 던지는 예외를 잡아 친절한 메시지로 치환.
                problems.add(format(key, e.getValue(), "미해결 placeholder: " + placeholderFail.getMessage()));
                continue;
            }
            if (raw == null || raw.isBlank()) {
                problems.add(format(key, e.getValue(), "비어 있음"));
            } else if (raw.contains("${")) {
                problems.add(format(key, e.getValue(), "치환되지 않은 placeholder: " + raw));
            }
        }
        // JDBC URL 호스트 사전 DNS 진단 — 컨테이너 외부 실행에서 흔한 'mariadb' 미해결 등을 빠르게 안내.
        String jdbcUrl = safeGet(env, "spring.datasource.url");
        if (jdbcUrl != null && !jdbcUrl.isBlank() && !jdbcUrl.contains("${")) {
            String host = extractJdbcHost(jdbcUrl);
            if (host != null && !isHostResolvable(host)) {
                problems.add(
                        "  - [spring.datasource.url] DB 호스트 DNS 해석 실패: '" + host + "'\n"
                      + "      • 도커 컴포즈 안에서만 사용되는 서비스명인지 확인하세요.\n"
                      + "      • 호스트 OS 실행 시: KRAFT_DB_LOCAL_HOST=localhost 또는 직접 KRAFT_DB_URL 수정.\n"
                      + "      • 자동 치환 비활성화 상태인지(KRAFT_DB_HOST_REWRITE=false) 확인.");
            }
        }
        if (!problems.isEmpty()) {
            String msg = """
                    
                    ╔══════════════════════════════════════════════════════════════╗
                    ║  KraftLotto 부팅 중단 — 필수 설정값이 누락되었습니다.        ║
                    ╚══════════════════════════════════════════════════════════════╝
                    %s
                    
                    해결 방법:
                      • 프로젝트 루트의 .env 를 채우거나 (.env.example 참고)
                      • 시스템 환경 변수로 직접 지정한 뒤 다시 실행하세요.
                      • 컨테이너 실행 시: docker compose up -d
                    """.formatted(String.join(System.lineSeparator(), problems));
            throw new IllegalStateException(msg);
        }
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    private static String format(String key, String desc, String reason) {
        return "  - [" + key + "] " + desc + " — " + reason;
    }

    private static String safeGet(ConfigurableEnvironment env, String key) {
        try { return env.getProperty(key); } catch (RuntimeException ex) { return null; }
    }

    /** {@code jdbc:<vendor>://<host>[:port]/...} 의 host 부분을 추출. 매칭 실패 시 null. */
    static String extractJdbcHost(String jdbcUrl) {
        Matcher m = JDBC_URL_PATTERN.matcher(jdbcUrl);
        return m.find() ? m.group(1) : null;
    }

    private static final Pattern JDBC_URL_PATTERN =
            Pattern.compile("^jdbc:[a-zA-Z0-9]+://([A-Za-z0-9._-]+)");

    private static boolean isHostResolvable(String host) {
        // localhost / 127.x / IPv6 루프백은 항상 해석 가능으로 간주.
        if ("localhost".equalsIgnoreCase(host) || host.startsWith("127.") || "::1".equals(host)) return true;
        try {
            InetAddress.getByName(host);
            return true;
        } catch (UnknownHostException ex) {
            return false;
        }
    }

    private static boolean isRunningUnderTest() {
        return System.getProperty("org.gradle.test.worker") != null
                || "true".equalsIgnoreCase(System.getProperty("kraft.skip.required-config-validator"));
    }
}
