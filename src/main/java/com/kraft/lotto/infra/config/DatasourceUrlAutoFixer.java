package com.kraft.lotto.infra.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.boot.logging.DeferredLog;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * 컨테이너 외부(호스트 OS) 에서 직접 부팅했을 때 JDBC URL 의 도커 네트워크 호스트명을
 * 자동으로 로컬 호스트로 치환한다.
 *
 * <p>해결하는 시나리오:
 * <pre>
 *   $ ./gradlew bootRun        # 호스트 OS 에서 실행
 *   .env: KRAFT_DB_URL=jdbc:mariadb://mariadb:3306/...
 *   → java.net.UnknownHostException: mariadb
 * </pre>
 *
 * <p>동작:
 * <ol>
 *   <li>현재 프로세스가 컨테이너 내부인지 휴리스틱으로 판정 ({@code /.dockerenv} 존재,
 *       {@code /proc/1/cgroup} 의 'docker'/'containerd' 마커, 또는 {@code KRAFT_IN_CONTAINER=true}).</li>
 *   <li>컨테이너 외부 + JDBC URL 이 {@code //mariadb}/{@code //db}/{@code //postgres} 등
 *       compose 서비스명을 가리키면 {@code //localhost} (또는
 *       {@code KRAFT_DB_LOCAL_HOST} 값) 로 치환.</li>
 *   <li>치환 결과는 가장 높은 우선순위 PropertySource 로 등록되어 원본 .env 값을 덮는다.</li>
 * </ol>
 *
 * <p>이 처리기는 의도적으로 보수적이다. 명시 옵트아웃: {@code KRAFT_DB_HOST_REWRITE=false}.
 */
public class DatasourceUrlAutoFixer
        implements EnvironmentPostProcessor, ApplicationListener<ApplicationPreparedEvent>, Ordered {

    /** Dotenv 적용 이후, 검증기({@link RequiredConfigValidator}) 보다 먼저. */
    public static final int ORDER = DotenvEnvironmentPostProcessor.ORDER + 50;
    private static final String SOURCE_NAME = "kraftDatasourceAutoFix";
    private static final String[] CONTAINER_SERVICE_HOSTS = {"mariadb", "mysql", "postgres", "db"};

    private final DeferredLog log = new DeferredLog();

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
        String optOut = env.getProperty("KRAFT_DB_HOST_REWRITE", "true");
        if ("false".equalsIgnoreCase(optOut)) {
            return;
        }
        if (isInsideContainer(env)) {
            return;
        }
        String url = env.getProperty("KRAFT_DB_URL");
        if (url == null || url.isBlank()) {
            return;
        }
        String localHost = env.getProperty("KRAFT_DB_LOCAL_HOST", "localhost");
        String rewritten = rewriteHost(url, localHost);
        if (rewritten == null || rewritten.equals(url)) {
            return;
        }
        Map<String, Object> overrides = new LinkedHashMap<>();
        overrides.put("KRAFT_DB_URL", rewritten);
        // application.yml 의 ${KRAFT_DB_URL} placeholder 로 흘러가도록 SystemEnvironment 와 동일 키로 덮는다.
        env.getPropertySources().addFirst(new MapPropertySource(SOURCE_NAME, overrides));
        log.info("호스트 OS 실행 감지 — JDBC URL 호스트를 자동 치환했습니다.");
        log.info("  before: " + url);
        log.info("  after : " + rewritten);
        log.info("  비활성화하려면 KRAFT_DB_HOST_REWRITE=false 또는 KRAFT_DB_LOCAL_HOST=<host> 로 지정하세요.");
    }

    @Override
    public void onApplicationEvent(ApplicationPreparedEvent event) {
        log.replayTo(DatasourceUrlAutoFixer.class);
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    /** {@code jdbc:mariadb://<host>[:port]/...} 형태에서 host 부분만 치환. 매칭 실패 시 null. */
    static String rewriteHost(String jdbcUrl, String newHost) {
        for (String svc : CONTAINER_SERVICE_HOSTS) {
            String marker = "//" + svc;
            int idx = jdbcUrl.indexOf(marker);
            if (idx < 0) continue;
            int hostStart = idx + 2; // skip "//"
            int hostEnd = hostStart + svc.length();
            // 다음 문자가 ':' (port) 또는 '/' (path) 이어야 정확한 host 토큰
            if (hostEnd >= jdbcUrl.length()) continue;
            char next = jdbcUrl.charAt(hostEnd);
            if (next != ':' && next != '/' && next != '?') continue;
            return jdbcUrl.substring(0, hostStart) + newHost + jdbcUrl.substring(hostEnd);
        }
        return null;
    }

    private static boolean isInsideContainer(ConfigurableEnvironment env) {
        String forced = env.getProperty("KRAFT_IN_CONTAINER");
        if (forced != null) return "true".equalsIgnoreCase(forced.trim());
        if (Files.exists(Paths.get("/.dockerenv"))) return true;
        Path cgroup = Paths.get("/proc/1/cgroup");
        if (Files.isReadable(cgroup)) {
            try {
                String content = Files.readString(cgroup, StandardCharsets.UTF_8);
                if (content.contains("docker") || content.contains("containerd") || content.contains("kubepods")) {
                    return true;
                }
            } catch (IOException ignore) { /* noop */ }
        }
        return false;
    }
}

