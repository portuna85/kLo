package com.kraft.lotto.infra.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLog;
import org.springframework.context.ApplicationListener;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * 프로젝트 루트의 {@code .env} 파일을 Spring {@link ConfigurableEnvironment} 에 주입한다.
 * <p>
 * 적용 우선순위:
 * <ol>
 *   <li>이미 시스템/OS 환경 변수에 동일 키가 있으면 그 값을 우선한다 (덮어쓰지 않음).</li>
 *   <li>{@code .env} 키 = 가장 낮은 우선순위 PropertySource 로 등록.</li>
 * </ol>
 * 지원 문법:
 * <ul>
 *   <li>{@code KEY=VALUE} (공백 트리밍)</li>
 *   <li>{@code # ...} 주석 / 빈 줄 무시</li>
 *   <li>선택적 {@code export } 접두사 허용</li>
 *   <li>큰따옴표/작은따옴표로 감싼 값의 따옴표 제거</li>
 * </ul>
 * 본 처리기는 의도적으로 외부 의존성 없이 작성되었으며, 운영 컨테이너에서는 docker-compose 의 env_file 이
 * 우선 적용되므로 별다른 부작용이 없다.
 */
public class DotenvEnvironmentPostProcessor
        implements EnvironmentPostProcessor, ApplicationListener<ApplicationPreparedEvent>, Ordered {

    /** 가장 먼저 적용해 후속 PropertySource 들이 이 값을 참조 가능하도록 한다. */
    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 10;
    private static final String SOURCE_NAME = "kraftDotenv";

    private final DeferredLog log = new DeferredLog();

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
        Path file = locateDotenvFile();
        if (file == null) {
            log.debug(".env 파일을 찾지 못했습니다 (정상: 컨테이너/CI 환경).");
            return;
        }
        Map<String, Object> parsed = parse(file);
        if (parsed.isEmpty()) {
            log.debug(".env 파일이 비어 있습니다: " + file);
            return;
        }
        // 이미 OS/시스템 환경에 정의된 키는 덮지 않는다.
        Map<String, Object> filtered = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : parsed.entrySet()) {
            if (env.getProperty(e.getKey()) == null) {
                filtered.put(e.getKey(), e.getValue());
            }
        }
        if (filtered.isEmpty()) {
            log.info(".env 모든 키가 이미 환경에 존재하여 건너뜀: " + file);
            return;
        }
        env.getPropertySources().addLast(new MapPropertySource(SOURCE_NAME, filtered));
        log.info(".env 로드 완료: " + file + " (keys=" + filtered.size() + ")");
    }

    @Override
    public void onApplicationEvent(ApplicationPreparedEvent event) {
        log.replayTo(DotenvEnvironmentPostProcessor.class);
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    /** 현재 작업 디렉터리 → 상위로 한 단계까지 .env 를 탐색한다. */
    private static Path locateDotenvFile() {
        Path cwd = Paths.get("").toAbsolutePath();
        Path here = cwd.resolve(".env");
        if (Files.isRegularFile(here)) return here;
        Path parent = cwd.getParent();
        if (parent != null) {
            Path up = parent.resolve(".env");
            if (Files.isRegularFile(up)) return up;
        }
        return null;
    }

    private Map<String, Object> parse(Path file) {
        Map<String, Object> out = new LinkedHashMap<>();
        try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = r.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                if (trimmed.startsWith("export ")) trimmed = trimmed.substring("export ".length()).trim();
                int eq = trimmed.indexOf('=');
                if (eq <= 0) continue;
                String key = trimmed.substring(0, eq).trim();
                String value = trimmed.substring(eq + 1).trim();
                value = stripQuotes(value);
                if (!key.isEmpty()) {
                    out.put(key, value);
                }
            }
        } catch (IOException ex) {
            // .env 읽기 실패는 치명적이지 않으므로 경고만 남긴다.
            // (DeferredLog 는 부트 완료 후 실제 출력)
            log.warn(".env 읽기 실패: " + file + " — " + ex.getMessage());
        }
        return out;
    }

    private static String stripQuotes(String v) {
        if (v.length() >= 2) {
            char a = v.charAt(0);
            char b = v.charAt(v.length() - 1);
            if ((a == '"' && b == '"') || (a == '\'' && b == '\'')) {
                return v.substring(1, v.length() - 1);
            }
        }
        return v;
    }
}

