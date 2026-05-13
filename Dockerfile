# syntax=docker/dockerfile:1.7

# ============================================================
#  KraftLotto Multi-stage Dockerfile
#  - JDK 25 build / JRE 25 runtime (non-root)
#  - Gradle dependency caching via BuildKit cache mounts
# ============================================================

# ---- Build stage ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# 1) Gradle wrapper / 빌드 스크립트 복사하여 의존성 캐시를 활용
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
# Windows(CRLF) 환경에서 생성된 gradlew 개행을 LF로 변환
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

# 2) 의존성만 미리 다운로드 → 레이어 캐시 최적화 (실패해도 계속 실행)
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon dependencies -q || true

# 3) 소스 복사 후 bootJarWithDocs 빌드
#    - clean 먼저 실행 후 generated-snippets 디렉토리를 재생성
#      (clean이 build/ 전체를 삭제하기 때문에 순서가 중요)
#    - -x test : Docker 빌드에서는 테스트를 CI에서 이미 통과한 것으로 간주
#    - asciidoctor는 build.gradle.kts의 doFirst에서 디렉토리를 보장
COPY src ./src
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon clean -x test && \
    mkdir -p build/generated-snippets && \
    ./gradlew --no-daemon bootJarWithDocs -x test

# ---- Runtime stage ----
FROM eclipse-temurin:25-jre
WORKDIR /app

# healthcheck용 curl 설치
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd --system kraft \
 && useradd --system --gid kraft --home /app kraft

COPY --from=build /workspace/build/libs/app-with-docs.jar /app/app.jar
COPY docker/healthcheck.sh /app/healthcheck.sh
RUN mkdir -p /app/logs \
 && chmod +x /app/healthcheck.sh \
 && chown -R kraft:kraft /app
USER kraft

EXPOSE 8080

# 컨테이너 환경 기본 JVM 옵션 (cgroup 메모리 감지 + Asia/Seoul)
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError -Duser.timezone=Asia/Seoul" \
    KRAFT_LOG_PATH="/app/logs" \
    KRAFT_HEALTHCHECK_URL="http://localhost:8080/actuator/health/readiness" \
    KRAFT_HEALTHCHECK_TIMEOUT_SECONDS="3"

VOLUME ["/app/logs"]

HEALTHCHECK --interval=15s --timeout=5s --start-period=45s --retries=10 \
  CMD /app/healthcheck.sh

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]