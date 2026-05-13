# syntax=docker/dockerfile:1.7

# ============================================================
#  KraftLotto ??Multi-stage Dockerfile
#  - JDK 25 build ??JRE 25 runtime (non-root)
#  - Gradle dependency caching via BuildKit cache mounts
# ============================================================

# ---- Build stage ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# 1) Gradle wrapper / 鍮뚮뱶 ?ㅽ겕由쏀듃 癒쇱? 蹂듭궗?섏뿬 ?섏〈??罹먯떆瑜??쒖슜
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
# Windows(CRLF) ?섍꼍?먯꽌 ?앹꽦??gradlew ?곕콉??LF 濡??뺢퇋??RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

# 2) ?섏〈?깅쭔 誘몃━ ?댁꽍???덉씠??罹먯떆 ?곸쨷瑜?洹밸???(?ㅽ뙣?대룄 吏꾪뻾)
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon dependencies -q || true

# 3) ?뚯뒪 蹂듭궗 ??bootJar
COPY src ./src
RUN --mount=type=cache,target=/root/.gradle \
    mkdir -p build/generated-snippets \
 && ./gradlew --no-daemon clean bootJarWithDocs -x test

# ---- Runtime stage ----
FROM eclipse-temurin:25-jre
WORKDIR /app

# healthcheck ??curl 留?異붽?
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

# 而⑦뀒?대꼫 ?섍꼍 湲곕낯 JVM ?쒕떇 (cgroup 硫붾え由??몄떇 + Asia/Seoul)
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError -Duser.timezone=Asia/Seoul" \
    KRAFT_LOG_PATH="/app/logs" \
    KRAFT_HEALTHCHECK_URL="http://localhost:8080/actuator/health/readiness" \
    KRAFT_HEALTHCHECK_TIMEOUT_SECONDS="3"

VOLUME ["/app/logs"]

HEALTHCHECK --interval=15s --timeout=5s --start-period=45s --retries=10 \
  CMD /app/healthcheck.sh

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
