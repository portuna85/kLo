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
 * 遺??吏곹썑 ?꾩닔 ?ㅼ젙媛믪쓽 議댁옱/?좏슚?깆쓣 寃利앺븳?? 誘명빐寃?placeholder({@code ${...}}) 媛
 * ?곗씠?곗냼??珥덇린?붽퉴吏 ?섎윭媛 ?뚭린 ?대젮???덉쇅 硫붿떆吏濡?蹂?섎뒗 臾몄젣瑜??ъ쟾??李⑤떒?쒕떎.
 *
 * <p>寃利?????ㅼ? 洹??섎???{@link #REQUIRED} 留ㅽ븨??李몄“?섎씪.
 * 寃利??ㅽ뙣 ??{@link IllegalStateException} ?쇰줈 利됱떆 而⑦뀓?ㅽ듃 珥덇린?붾? 以묐떒?쒕떎.
 */
public class RequiredConfigValidator implements EnvironmentPostProcessor, Ordered {

    /** Dotenv 蹂대떎 ?? ?쇰컲 PropertySource ?????곸옱??吏곹썑???숈옉. */
    public static final int ORDER = DotenvEnvironmentPostProcessor.ORDER + 100;

    /** key ???щ엺 移쒗솕 ?ㅻ챸. */
    private static final Map<String, String> REQUIRED = new LinkedHashMap<>();
    static {
        REQUIRED.put("spring.datasource.url",      "DB JDBC URL (env: KRAFT_DB_URL)");
        REQUIRED.put("spring.datasource.username", "DB ?ъ슜??(env: KRAFT_DB_USER)");
        REQUIRED.put("spring.datasource.password", "DB 鍮꾨?踰덊샇 (env: KRAFT_DB_PASSWORD)");
    }

    private static final List<String> REQUIRED_DEPLOY_ENV_VARS = List.of(
            "KRAFT_DB_NAME",
            "KRAFT_DB_USER",
            "KRAFT_DB_PASSWORD",
            "KRAFT_DB_ROOT_PASSWORD",
            "KRAFT_ADMIN_API_TOKEN"
    );

    public static List<String> requiredDeployEnvVars() {
        return REQUIRED_DEPLOY_ENV_VARS;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
        if (isRunningUnderTest(env)) {
            return;
        }
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, String> e : REQUIRED.entrySet()) {
            String key = e.getKey();
            String raw;
            try {
                raw = env.getProperty(key);
            } catch (RuntimeException placeholderFail) {
                // 誘명빐寃?placeholder ??寃쎌슦 Spring ???섏????덉쇅瑜??≪븘 移쒖젅??硫붿떆吏濡?移섑솚.
                problems.add(format(key, e.getValue(), "誘명빐寃?placeholder: " + placeholderFail.getMessage()));
                continue;
            }
            if (raw == null || raw.isBlank()) {
                problems.add(format(key, e.getValue(), "鍮꾩뼱 ?덉쓬"));
            } else if (raw.contains("${")) {
                problems.add(format(key, e.getValue(), "移섑솚?섏? ?딆? placeholder: " + raw));
            }
        }
        // JDBC URL ?몄뒪???ъ쟾 DNS 吏꾨떒 ??而⑦뀒?대꼫 ?몃? ?ㅽ뻾?먯꽌 ?뷀븳 'mariadb' 誘명빐寃??깆쓣 鍮좊Ⅴ寃??덈궡.
        String jdbcUrl = safeGet(env, "spring.datasource.url");
        if (jdbcUrl != null && !jdbcUrl.isBlank() && !jdbcUrl.contains("${")) {
            String host = extractJdbcHost(jdbcUrl);
            if (host != null && !isHostResolvable(host)) {
                problems.add(
                        "  - [spring.datasource.url] DB ?몄뒪??DNS ?댁꽍 ?ㅽ뙣: '" + host + "'\n"
                      + "      ???꾩빱 而댄룷利??덉뿉?쒕쭔 ?ъ슜?섎뒗 ?쒕퉬?ㅻ챸?몄? ?뺤씤?섏꽭??\n"
                      + "      ???몄뒪??OS ?ㅽ뻾 ?? KRAFT_DB_LOCAL_HOST=localhost ?먮뒗 吏곸젒 KRAFT_DB_URL ?섏젙.\n"
                      + "      ???먮룞 移섑솚 鍮꾪솢?깊솕 ?곹깭?몄?(KRAFT_DB_HOST_REWRITE=false) ?뺤씤.");
            }
        }
        addProdAdminTokenProblem(env, problems);
        addProdOperationalConfigProblems(env, problems);
        if (!problems.isEmpty()) {
            String msg = """
                    
                    ?붴븧?먥븧?먥븧?먥븧?먥븧?먥븧?먥븧?먥븧?먥븧?먥븧?먥븧?먥븧?먥븧?먥븧?먥븧?먥븧?먥븧?먥븧?먥븧?먥븧?먥븧?먥븧?먥븧?먥븧?먥븧?먥븧?먥븧?먥븧?먥븧?먥븧?먥븧?먥븮
                    ?? KraftLotto 遺??以묐떒 ???꾩닔 ?ㅼ젙媛믪씠 ?꾨씫?섏뿀?듬땲??        ??
                    ?싢븧?먥븧?먥븧?먥븧?먥븧?먥븧?먥븧?먥븧?먥븧?먥븧?먥븧?먥븧?먥븧?먥븧?먥븧?먥븧?먥븧?먥븧?먥븧?먥븧?먥븧?먥븧?먥븧?먥븧?먥븧?먥븧?먥븧?먥븧?먥븧?먥븧?먥븧?먥븴
                    %s
                    
                    ?닿껐 諛⑸쾿:
                      ???꾨줈?앺듃 猷⑦듃??.env 瑜?梨꾩슦嫄곕굹 (.env.example 李멸퀬)
                      ???쒖뒪???섍꼍 蹂?섎줈 吏곸젒 吏?뺥븳 ???ㅼ떆 ?ㅽ뻾?섏꽭??
                      ??而⑦뀒?대꼫 ?ㅽ뻾 ?? docker compose up -d
                    """.formatted(String.join(System.lineSeparator(), problems));
            throw new IllegalStateException(msg);
        }
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    private static String format(String key, String desc, String reason) {
        return "  - [" + key + "] " + desc + " ??" + reason;
    }

    private static String safeGet(ConfigurableEnvironment env, String key) {
        try { return env.getProperty(key); } catch (RuntimeException ex) { return null; }
    }

    static void addProdAdminTokenProblem(ConfigurableEnvironment env, List<String> problems) {
        if (!env.matchesProfiles("prod")) {
            return;
        }
        String token = safeGet(env, "kraft.admin.api-token");
        if (token == null || token.isBlank()) {
            problems.add(format("kraft.admin.api-token",
                    "Admin API token (env: KRAFT_ADMIN_API_TOKEN)",
                    "blank in prod profile"));
        }
    }

    static void addProdOperationalConfigProblems(ConfigurableEnvironment env, List<String> problems) {
        if (!env.matchesProfiles("prod")) {
            return;
        }
        requireNonBlank(env, problems, "kraft.api.url", "Lotto API URL (env: KRAFT_API_URL)");
        requireNonBlank(env, problems, "kraft.recommend.max-attempts",
                "Recommend max attempts (env: KRAFT_RECOMMEND_MAX_ATTEMPTS)");
    }

    private static void requireNonBlank(ConfigurableEnvironment env,
                                        List<String> problems,
                                        String key,
                                        String desc) {
        String value = safeGet(env, key);
        if (value == null || value.isBlank()) {
            problems.add(format(key, desc, "blank in prod profile"));
        }
    }

    /** {@code jdbc:<vendor>://<host>[:port]/...} ??host 遺遺꾩쓣 異붿텧. 留ㅼ묶 ?ㅽ뙣 ??null. */
    static String extractJdbcHost(String jdbcUrl) {
        Matcher m = JDBC_URL_PATTERN.matcher(jdbcUrl);
        return m.find() ? m.group(1) : null;
    }

    private static final Pattern JDBC_URL_PATTERN =
            Pattern.compile("^jdbc:[a-zA-Z0-9]+://([A-Za-z0-9._-]+)");

    private static boolean isHostResolvable(String host) {
        // localhost / 127.x / IPv6 猷⑦봽諛깆? ??긽 ?댁꽍 媛?μ쑝濡?媛꾩＜.
        if ("localhost".equalsIgnoreCase(host) || host.startsWith("127.") || "::1".equals(host)) return true;
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
