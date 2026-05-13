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
 * ë¶€??ì§í›„ ?„ìˆ˜ ?¤ì •ê°’ì˜ ì¡´ì¬/? íš¨?±ì„ ê²€ì¦í•œ?? ë¯¸í•´ê²?placeholder({@code ${...}}) ê°€
 * ?°ì´?°ì†Œ??ì´ˆê¸°?”ê¹Œì§€ ?˜ëŸ¬ê°€ ?Œê¸° ?´ë ¤???ˆì™¸ ë©”ì‹œì§€ë¡?ë³€?˜ëŠ” ë¬¸ì œë¥??¬ì „??ì°¨ë‹¨?œë‹¤.
 *
 * <p>ê²€ì¦??€???¤ì? ê·??˜ë???{@link #REQUIRED} ë§¤í•‘??ì°¸ì¡°?˜ë¼.
 * ê²€ì¦??¤íŒ¨ ??{@link IllegalStateException} ?¼ë¡œ ì¦‰ì‹œ ì»¨í…?¤íŠ¸ ì´ˆê¸°?”ë? ì¤‘ë‹¨?œë‹¤.
 */
public class RequiredConfigValidator implements EnvironmentPostProcessor, Ordered {

    /** Dotenv ë³´ë‹¤ ?? ?¼ë°˜ PropertySource ?????ì¬??ì§í›„???™ì‘. */
    public static final int ORDER = DotenvEnvironmentPostProcessor.ORDER + 100;

    /** key ???¬ëŒ ì¹œí™” ?¤ëª…. */
    private static final Map<String, String> REQUIRED = new LinkedHashMap<>();
    static {
        REQUIRED.put("spring.datasource.url",      "DB JDBC URL (env: KRAFT_DB_URL)");
        REQUIRED.put("spring.datasource.username", "DB ?¬ìš©??(env: KRAFT_DB_USER)");
        REQUIRED.put("spring.datasource.password", "DB ë¹„ë?ë²ˆí˜¸ (env: KRAFT_DB_PASSWORD)");
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
                // ë¯¸í•´ê²?placeholder ??ê²½ìš° Spring ???˜ì????ˆì™¸ë¥??¡ì•„ ì¹œì ˆ??ë©”ì‹œì§€ë¡?ì¹˜í™˜.
                problems.add(format(key, e.getValue(), "ë¯¸í•´ê²?placeholder: " + placeholderFail.getMessage()));
                continue;
            }
            if (raw == null || raw.isBlank()) {
                problems.add(format(key, e.getValue(), "ë¹„ì–´ ?ˆìŒ"));
            } else if (raw.contains("${")) {
                problems.add(format(key, e.getValue(), "ì¹˜í™˜?˜ì? ?Šì? placeholder: " + raw));
            }
        }
        // JDBC URL ?¸ìŠ¤???¬ì „ DNS ì§„ë‹¨ ??ì»¨í…Œ?´ë„ˆ ?¸ë? ?¤í–‰?ì„œ ?”í•œ 'mariadb' ë¯¸í•´ê²??±ì„ ë¹ ë¥´ê²??ˆë‚´.
        String jdbcUrl = safeGet(env, "spring.datasource.url");
        if (jdbcUrl != null && !jdbcUrl.isBlank() && !jdbcUrl.contains("${")) {
            String host = extractJdbcHost(jdbcUrl);
            if (host != null && !isHostResolvable(host)) {
                problems.add(
                        "  - [spring.datasource.url] DB ?¸ìŠ¤??DNS ?´ì„ ?¤íŒ¨: '" + host + "'\n"
                      + "      ???„ì»¤ ì»´í¬ì¦??ˆì—?œë§Œ ?¬ìš©?˜ëŠ” ?œë¹„?¤ëª…?¸ì? ?•ì¸?˜ì„¸??\n"
                      + "      ???¸ìŠ¤??OS ?¤í–‰ ?? KRAFT_DB_LOCAL_HOST=localhost ?ëŠ” ì§ì ‘ KRAFT_DB_URL ?˜ì •.\n"
                      + "      ???ë™ ì¹˜í™˜ ë¹„í™œ?±í™” ?íƒœ?¸ì?(KRAFT_DB_HOST_REWRITE=false) ?•ì¸.");
            }
        }
        addProdAdminTokenProblem(env, problems);
        if (!problems.isEmpty()) {
            String msg = """
                    
                    ?”â•?â•?â•?â•?â•?â•?â•?â•?â•?â•?â•?â•?â•?â•?â•?â•?â•?â•?â•?â•?â•?â•?â•?â•?â•?â•?â•?â•?â•?â•?â•?â•—
                    ?? KraftLotto ë¶€??ì¤‘ë‹¨ ???„ìˆ˜ ?¤ì •ê°’ì´ ?„ë½?˜ì—ˆ?µë‹ˆ??        ??
                    ?šâ•?â•?â•?â•?â•?â•?â•?â•?â•?â•?â•?â•?â•?â•?â•?â•?â•?â•?â•?â•?â•?â•?â•?â•?â•?â•?â•?â•?â•?â•?â•?â•
                    %s
                    
                    ?´ê²° ë°©ë²•:
                      ???„ë¡œ?íŠ¸ ë£¨íŠ¸??.env ë¥?ì±„ìš°ê±°ë‚˜ (.env.example ì°¸ê³ )
                      ???œìŠ¤???˜ê²½ ë³€?˜ë¡œ ì§ì ‘ ì§€?•í•œ ???¤ì‹œ ?¤í–‰?˜ì„¸??
                      ??ì»¨í…Œ?´ë„ˆ ?¤í–‰ ?? docker compose up -d
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

    /** {@code jdbc:<vendor>://<host>[:port]/...} ??host ë¶€ë¶„ì„ ì¶”ì¶œ. ë§¤ì¹­ ?¤íŒ¨ ??null. */
    static String extractJdbcHost(String jdbcUrl) {
        Matcher m = JDBC_URL_PATTERN.matcher(jdbcUrl);
        return m.find() ? m.group(1) : null;
    }

    private static final Pattern JDBC_URL_PATTERN =
            Pattern.compile("^jdbc:[a-zA-Z0-9]+://([A-Za-z0-9._-]+)");

    private static boolean isHostResolvable(String host) {
        // localhost / 127.x / IPv6 ë£¨í”„ë°±ì? ??ƒ ?´ì„ ê°€?¥ìœ¼ë¡?ê°„ì£¼.
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
