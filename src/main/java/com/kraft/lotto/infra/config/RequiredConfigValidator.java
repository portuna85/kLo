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
 * Validates required runtime configuration before the app starts.
 */
public class RequiredConfigValidator implements EnvironmentPostProcessor, Ordered {

    public static final int ORDER = DotenvEnvironmentPostProcessor.ORDER + 100;

    private static final Map<String, String> REQUIRED = new LinkedHashMap<>();

    static {
        REQUIRED.put("spring.datasource.url", "DB JDBC URL (env: KRAFT_DB_URL)");
        REQUIRED.put("spring.datasource.username", "DB username (env: KRAFT_DB_USER)");
        REQUIRED.put("spring.datasource.password", "DB password (env: KRAFT_DB_PASSWORD)");
    }

    private static final List<String> REQUIRED_DEPLOY_ENV_VARS = List.of(
            "KRAFT_DB_NAME",
            "KRAFT_DB_USER",
            "KRAFT_DB_PASSWORD",
            "KRAFT_DB_ROOT_PASSWORD",
            "KRAFT_ADMIN_API_TOKENS"
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
                problems.add(format(key, entry.getValue(), "placeholder resolution failed: " + placeholderFail.getMessage()));
                continue;
            }

            if (raw == null || raw.isBlank()) {
                problems.add(format(key, entry.getValue(), "value is blank"));
            } else if (raw.contains("${")) {
                problems.add(format(key, entry.getValue(), "unresolved placeholder: " + raw));
            }
        }

        String jdbcUrl = safeGet(env, "spring.datasource.url");
        if (jdbcUrl != null && !jdbcUrl.isBlank() && !jdbcUrl.contains("${")) {
            String host = extractJdbcHost(jdbcUrl);
            if (host != null && !isHostResolvable(host)) {
                problems.add(
                        "  - [spring.datasource.url] DB host is not resolvable by DNS: '" + host + "'\n"
                                + "      - For local runtime, set KRAFT_DB_LOCAL_HOST=localhost or adjust KRAFT_DB_URL\n"
                                + "      - To skip host rewrite, set KRAFT_DB_HOST_REWRITE=false"
                );
            }
        }

        addProdAdminTokenProblem(env, problems);
        addProdOperationalConfigProblems(env, problems);
        addProfilePolicyProblems(env, problems);

        if (!problems.isEmpty()) {
            String msg = """

                    ============================================================
                    KraftLotto startup validation failed.
                    Please review environment variables and active profile.
                    ============================================================
                    %s

                    Quick checks:
                      - Make sure .env exists and is correctly populated from .env.example.
                      - Verify required values are available for the active profile.
                      - If running with Docker, ensure expected profile and env vars are set.
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
        String tokens = safeGet(env, "kraft.admin.api-tokens");
        boolean hasToken = token != null && !token.isBlank();
        boolean hasTokens = tokens != null && !tokens.isBlank();
        if (!hasToken && !hasTokens) {
            problems.add(format(
                    "kraft.admin.api-tokens",
                    "Admin API token list (env: KRAFT_ADMIN_API_TOKENS, legacy: KRAFT_ADMIN_API_TOKEN)",
                    "blank in prod profile"
            ));
        }
    }

    static void addProdOperationalConfigProblems(ConfigurableEnvironment env, List<String> problems) {
        if (!env.matchesProfiles("prod")) {
            return;
        }
        requireNonBlank(env, problems, "kraft.api.url", "External API URL (env: KRAFT_API_URL)");
        requireNonBlank(env, problems, "kraft.recommend.max-attempts",
                "Recommend max attempts (env: KRAFT_RECOMMEND_MAX_ATTEMPTS)");

        String apiClient = safeGet(env, "kraft.api.client");
        if (apiClient == null || apiClient.isBlank() || !"real".equalsIgnoreCase(apiClient.trim())) {
            problems.add(format(
                    "kraft.api.client",
                    "Lotto API client mode (env: KRAFT_API_CLIENT)",
                    "prod profile requires value 'real'"
            ));
        }
    }

    private static void requireNonBlank(ConfigurableEnvironment env,
                                        List<String> problems,
                                        String key,
                                        String desc) {
        String value = safeGet(env, key);
        if (value == null || value.isBlank()) {
            problems.add(format(key, desc, "required in prod profile but blank"));
        }
    }

    static void addProfilePolicyProblems(ConfigurableEnvironment env, List<String> problems) {
        boolean inContainer = Boolean.parseBoolean(env.getProperty("KRAFT_IN_CONTAINER", "false"));
        String[] activeProfiles = env.getActiveProfiles();
        String active = activeProfiles.length == 0 ? "<none>" : String.join(",", activeProfiles);

        if (inContainer) {
            if (!env.matchesProfiles("prod")) {
                problems.add(format(
                        "spring.profiles.active",
                        "active profile",
                        "KRAFT_IN_CONTAINER=true requires prod profile (current: " + active + ")"
                ));
            }
            return;
        }

        if (!env.matchesProfiles("local")) {
            problems.add(format(
                    "spring.profiles.active",
                    "active profile",
                    "local runtime requires local profile (current: " + active + ")"
            ));
        }
    }

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
