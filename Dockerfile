# syntax=docker/dockerfile:1.7

# ---- Build stage ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Gradle wrapper / 빌드 스크립트 먼저 복사하여 의존성 캐시를 활용합니다.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle

# 소스 복사 후 빌드
COPY src ./src
RUN chmod +x gradlew \
 && ./gradlew --no-daemon clean bootJar -x test

# ---- Runtime stage ----
FROM eclipse-temurin:25-jre
WORKDIR /app

# 비루트 사용자로 실행
RUN groupadd --system kraft \
 && useradd --system --gid kraft --home /app kraft

COPY --from=build /workspace/build/libs/*.jar /app/app.jar
RUN chown -R kraft:kraft /app
USER kraft

EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
