# syntax=docker/dockerfile:1.7

# ============================================================
#  KraftLotto — Multi-stage Dockerfile
#  - JDK 25 build → JRE 25 runtime (non-root)
#  - Gradle dependency caching via BuildKit cache mounts
# ============================================================

# ---- Build stage ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# 1) Gradle wrapper / 빌드 스크립트 먼저 복사하여 의존성 캐시를 활용
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
# Windows(CRLF) 환경에서 생성된 gradlew 셰뱅을 LF 로 정규화
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

# 2) 의존성만 미리 해석해 레이어 캐시 적중률 극대화 (실패해도 진행)
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon dependencies -q || true

# 3) 소스 복사 후 bootJar
COPY src ./src
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon clean -x test \
 && mkdir -p build/generated-snippets \
 && ./gradlew --no-daemon bootJar -x test

# ---- Runtime stage ----
FROM eclipse-temurin:25-jre
WORKDIR /app

# healthcheck 용 curl 만 추가
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd --system kraft \
 && useradd --system --gid kraft --home /app kraft

COPY --from=build /workspace/build/libs/*.jar /app/app.jar
COPY docker/healthcheck.sh /app/healthcheck.sh
RUN mkdir -p /app/logs \
 && chmod +x /app/healthcheck.sh \
 && chown -R kraft:kraft /app
USER kraft

EXPOSE 8080

# 컨테이너 환경 기본 JVM 튜닝 (cgroup 메모리 인식 + Asia/Seoul)
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError -Duser.timezone=Asia/Seoul" \
    KRAFT_LOG_PATH="/app/logs" \
    KRAFT_HEALTHCHECK_URL="http://localhost:8080/actuator/health/liveness" \
    KRAFT_HEALTHCHECK_TIMEOUT_SECONDS="2"

VOLUME ["/app/logs"]

HEALTHCHECK --interval=15s --timeout=3s --start-period=30s --retries=5 \
  CMD /app/healthcheck.sh

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
