package com.kraft.lotto.infra.config;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * 애플리케이션 시작 시 필수 설정값을 검증한다.
 *
 * <p>미해결 placeholder가 런타임까지 전파되어 난해한 오류를 만드는 상황을
 * 시작 단계에서 차단하기 위한 검증기다.</p>
 */
public class RequiredConfigValidator implements EnvironmentPostProcessor, Ordered {

    /** DotenvEnvironmentPostProcessor 이후에 실행한다. */
    public static final int ORDER = DotenvEnvironmentPostProcessor.ORDER + 100;

    /** 필수 설정 키와 설명. */
    private static final Map<String, String> REQUIRED = new LinkedHashMap<>();

    static {
        REQUIRED.put("spring.datasource.url", "DB JDBC URL (env: KRAFT_DB_URL)");
        REQUIRED.put("spring.datasource.username", "DB 계정 (env: KRAFT_DB_USER)");
        REQUIRED.put("spring.datasource.password", "DB 비밀번호 (env: KRAFT_DB_PASSWORD)");
    }

    private static final List<String> REQUIRED_DEPLOY_ENV_VARS = List.of(
            "KRAFT_DB_NAME",
            "KRAFT_DB_USER",
            "KRAFT_DB_PASSWORD",
            "KRAFT_DB_ROOT_PASSWORD",
            "KRAFT_ADMIN_API_TOKEN"
    );

    private static final Pattern JDBC_URL_PATTERN =
            Pattern.compile("^jdbc:[a-zA-Z0-9]+://([A-Za-z0-9._-]+)");

    public static List<String> requiredDeployEnvVars() {
        return REQUIRED_DEPLOY_ENV_VARS;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
        if (isRunningUnderTest(env)) {
            return;
        }

        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, String> entry : REQUIRED.entrySet()) {
            String key = entry.getKey();
            String raw;
            try {
                raw = env.getProperty(key);
            } catch (RuntimeException placeholderFail) {
                problems.add(format(key, entry.getValue(), "placeholder 해석 실패: " + placeholderFail.getMessage()));
                continue;
            }

            if (raw == null || raw.isBlank()) {
                problems.add(format(key, entry.getValue(), "값이 비어 있음"));
            } else if (raw.contains("${")) {
                problems.add(format(key, entry.getValue(), "미해결 placeholder 포함: " + raw));
            }
        }

        String jdbcUrl = safeGet(env, "spring.datasource.url");
        if (jdbcUrl != null && !jdbcUrl.isBlank() && !jdbcUrl.contains("${")) {
            String host = extractJdbcHost(jdbcUrl);
            if (host != null && !isHostResolvable(host)) {
                problems.add(
                        "  - [spring.datasource.url] DB 호스트 DNS 조회 실패: '" + host + "'\n"
                                + "      - 로컬 실행이면 KRAFT_DB_LOCAL_HOST=localhost 설정 또는 KRAFT_DB_URL 재지정\n"
                                + "      - host rewrite를 끄려면 KRAFT_DB_HOST_REWRITE=false 설정"
                );
            }
        }

        addProdAdminTokenProblem(env, problems);
        addProdOperationalConfigProblems(env, problems);
        addProfilePolicyProblems(env, problems);

        if (!problems.isEmpty()) {
            String msg = """

                    ============================================================
                    KraftLotto 시작 중단: 필수 설정이 누락되었거나 유효하지 않습니다.
                    ============================================================
                    %s

                    해결 방법:
                      - 프로젝트 루트의 .env 파일을 채웁니다. (.env.example 참고)
                      - 현재 셸 환경 변수로 값을 주입한 뒤 재실행합니다.
                      - 컨테이너 실행 시 docker compose up -d 를 사용합니다.
                    """.formatted(String.join(System.lineSeparator(), problems));
            throw new IllegalStateException(msg);
        }
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    private static String format(String key, String desc, String reason) {
        return "  - [" + key + "] " + desc + " => " + reason;
    }

    private static String safeGet(ConfigurableEnvironment env, String key) {
        try {
            return env.getProperty(key);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    static void addProdAdminTokenProblem(ConfigurableEnvironment env, List<String> problems) {
        if (!env.matchesProfiles("prod")) {
            return;
        }
        String token = safeGet(env, "kraft.admin.api-token");
        if (token == null || token.isBlank()) {
            problems.add(format(
                    "kraft.admin.api-token",
                    "관리자 API 토큰 (env: KRAFT_ADMIN_API_TOKEN)",
                    "prod 프로필에서 값이 비어 있음"
            ));
        }
    }

    static void addProdOperationalConfigProblems(ConfigurableEnvironment env, List<String> problems) {
        if (!env.matchesProfiles("prod")) {
            return;
        }
        requireNonBlank(env, problems, "kraft.api.url", "로또 API URL (env: KRAFT_API_URL)");
        requireNonBlank(env, problems, "kraft.recommend.max-attempts",
                "추천 최대 시도 횟수 (env: KRAFT_RECOMMEND_MAX_ATTEMPTS)");
    }

    private static void requireNonBlank(ConfigurableEnvironment env,
                                        List<String> problems,
                                        String key,
                                        String desc) {
        String value = safeGet(env, key);
        if (value == null || value.isBlank()) {
            problems.add(format(key, desc, "prod 프로필에서 값이 비어 있음"));
        }
    }

    /**
     * 실행 환경별 프로필 정책을 강제한다.
     * - KRAFT_IN_CONTAINER=true  : 반드시 prod
     * - 그 외(local 실행)         : 반드시 local
     */
    static void addProfilePolicyProblems(ConfigurableEnvironment env, List<String> problems) {
        boolean inContainer = Boolean.parseBoolean(env.getProperty("KRAFT_IN_CONTAINER", "false"));
        String[] activeProfiles = env.getActiveProfiles();
        String active = activeProfiles.length == 0 ? "<none>" : String.join(",", activeProfiles);

        if (inContainer) {
            if (!env.matchesProfiles("prod")) {
                problems.add(format(
                        "spring.profiles.active",
                        "활성 프로필",
                        "KRAFT_IN_CONTAINER=true 인 경우 prod 여야 함 (현재: " + active + ")"
                ));
            }
            return;
        }

        if (!env.matchesProfiles("local")) {
            problems.add(format(
                    "spring.profiles.active",
                    "활성 프로필",
                    "로컬 실행에서는 local 이어야 함 (현재: " + active + ")"
            ));
        }
    }

    /** {@code jdbc:<vendor>://<host>[:port]/...} 형태에서 host를 추출한다. 실패 시 null. */
    static String extractJdbcHost(String jdbcUrl) {
        Matcher m = JDBC_URL_PATTERN.matcher(jdbcUrl);
        return m.find() ? m.group(1) : null;
    }

    private static boolean isHostResolvable(String host) {
        if ("localhost".equalsIgnoreCase(host) || host.startsWith("127.") || "::1".equals(host)) {
            return true;
        }
        try {
            InetAddress.getByName(host);
            return true;
        } catch (UnknownHostException ex) {
            return false;
        }
    }

    private static boolean isRunningUnderTest(ConfigurableEnvironment env) {
        return System.getProperty("org.gradle.test.worker") != null
                || "true".equalsIgnoreCase(System.getProperty("kraft.skip.required-config-validator"))
                || Boolean.parseBoolean(env.getProperty("kraft.skip.required-config-validator", "false"));
    }
}
