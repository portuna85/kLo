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
 * ???ャ뀖??域????⑤젰????筌믨퀣援?????ш끽維?????源놁젳??좊즴????濡ろ떟?癲ル슣鍮섌뜮?믩눀??
 *
 * <p>雅?퍔瑗띰㎖硫대쑏?믠뫁臾?placeholder??좊읈? ?????ш낄猷?湲븐땡?堉온 ??ш낄援???筌뚯슦苑??????뤅??????곸씔??癲ル슢?????????????
 * ??筌믨퀣援???影?됀?????癲ル슓堉곁땟??????꾨탿 ??ш낄援η뵳??濡ろ떟?癲ル슣鍮섌뜮蹂〓뎨??</p>
 */
public class RequiredConfigValidator implements EnvironmentPostProcessor, Ordered {

    /** DotenvEnvironmentPostProcessor ??熬곣뫖???????덈틖??筌먲퐢?? */
    public static final int ORDER = DotenvEnvironmentPostProcessor.ORDER + 100;

    /** ??ш끽維?????源놁젳 ??? ????용럡. */
    private static final Map<String, String> REQUIRED = new LinkedHashMap<>();

    static {
        REQUIRED.put("spring.datasource.url", "DB JDBC URL (env: KRAFT_DB_URL)");
        REQUIRED.put("spring.datasource.username", "DB ??節뚮쳮??(env: KRAFT_DB_USER)");
        REQUIRED.put("spring.datasource.password", "DB ?????類????(env: KRAFT_DB_PASSWORD)");
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
                problems.add(format(key, entry.getValue(), "placeholder ???⑤똾留?????됰꽡: " + placeholderFail.getMessage()));
                continue;
            }

            if (raw == null || raw.isBlank()) {
                problems.add(format(key, entry.getValue(), "??좊즴???????룹젂????源낆쓱"));
            } else if (raw.contains("${")) {
                problems.add(format(key, entry.getValue(), "雅?퍔瑗띰㎖硫대쑏?믠뫁臾?placeholder ???? " + raw));
            }
        }

        String jdbcUrl = safeGet(env, "spring.datasource.url");
        if (jdbcUrl != null && !jdbcUrl.isBlank() && !jdbcUrl.contains("${")) {
            String host = extractJdbcHost(jdbcUrl);
            if (host != null && !isHostResolvable(host)) {
                problems.add(
                        "  - [spring.datasource.url] DB ?嶺뚮ㅎ?ц짆??DNS ?釉뚰???????됰꽡: '" + host + "'\n"
                                + "      - ?棺??짆?쏆춾?????덈틖?????KRAFT_DB_LOCAL_HOST=localhost ???源놁젳 ???獒?KRAFT_DB_URL ?????n"
                                + "      - host rewrite????ш끽維??ъ땡?KRAFT_DB_HOST_REWRITE=false ???源놁젳"
                );
            }
        }

        addProdAdminTokenProblem(env, problems);
        addProdOperationalConfigProblems(env, problems);
        addProfilePolicyProblems(env, problems);

        if (!problems.isEmpty()) {
            String msg = """

                    ============================================================
                    KraftLotto ??筌믨퀣援?濚욌꼬?댄꺇?? ??ш끽維?????源놁젳????ш끽維곲??筌?癲꾧퀗???믩쨨????レ챺???? ?????????덊렡.
                    ============================================================
                    %s

                    ????됰쐳 ?袁⑸젻泳?쉬??
                      - ??ш끽維곩ㅇ???됰씭肄???룸Ŧ爾???.env ??????癲???????덊렡. (.env.example 癲ル슔?蹂앸듋??
                      - ??ш끽維????????듬젿 ?怨뚮뼚???嚥?큔 ??좊즴?????낆뒩??????????????????덊렡.
                      - ???爾??????????덈틖 ??docker compose up -d ???????筌뤾퍓???
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
        requireNonBlank(env, problems, "kraft.api.url", "?棺??짆??API URL (env: KRAFT_API_URL)");
        requireNonBlank(env, problems, "kraft.recommend.max-attempts",
                "??⑤베毓??癲ル슔?됭짆? ??筌먲퐣??????낅묄 (env: KRAFT_RECOMMEND_MAX_ATTEMPTS)");
    }

    private static void requireNonBlank(ConfigurableEnvironment env,
                                        List<String> problems,
                                        String key,
                                        String desc) {
        String value = safeGet(env, key);
        if (value == null || value.isBlank()) {
            problems.add(format(key, desc, "prod ??ш끽維곩ㅇ??ш끽維?????좊즴???????룹젂????源낆쓱"));
        }
    }

    /**
     * ????덈틖 ????듬젿????ш끽維곩ㅇ???嶺뚮Ĳ??????좊즴甕???筌먲퐢??
     * - KRAFT_IN_CONTAINER=true  : ?袁⑸즵?쀫쓧???prod
     * - ????local ????덈틖)         : ?袁⑸즵?쀫쓧???local
     */
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

    /** {@code jdbc:<vendor>://<host>[:port]/...} ?嶺뚮쮳釉띚?????host????⑤베毓???筌먲퐢?? ????됰꽡 ??null. */
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
