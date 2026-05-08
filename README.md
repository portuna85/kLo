<div align="center">

# Kraft Lotto

**로또 6/45 당첨번호 조회 · 수집 · 편향 회피 추천 서비스**

*A Spring Boot web application for the 6/45 lottery — query, ingest, and bias-aware recommendation.*

[![Java](https://img.shields.io/badge/Java-25-c0322b?style=flat-square&labelColor=15110b)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.5-6DB33F?style=flat-square&labelColor=15110b)](https://spring.io/projects/spring-boot)
[![Gradle](https://img.shields.io/badge/Gradle-9.4.1%20KTS-02303A?style=flat-square&labelColor=15110b)](https://gradle.org/)
[![MariaDB](https://img.shields.io/badge/MariaDB-11.8-003545?style=flat-square&labelColor=15110b)](https://mariadb.org/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=flat-square&labelColor=15110b)](https://www.docker.com/)
[![License](https://img.shields.io/badge/License-TBD-a78248?style=flat-square&labelColor=15110b)](#라이선스)

</div>

---

Kraft Lotto는 로또 6/45 당첨번호를 조회·수집하고, 편향 패턴을 피한 무작위 번호 조합을 추천하는 Spring Boot 기반 웹 애플리케이션입니다. 웹 화면과 REST API를 함께 제공하며, MariaDB 저장소, Flyway 마이그레이션, Spring Security, Actuator, Docker Compose 운영 구성을 포함합니다.

> [!NOTE]
> 번호 추천 기능은 통계적·편향 회피 목적의 **무작위 조합 생성 기능**입니다. 당첨 확률 향상을 보장하지 않습니다.

## 목차

- [주요 기능](#주요-기능)
- [기술 스택](#기술-스택)
- [프로젝트 구조](#프로젝트-구조)
- [실행 준비](#실행-준비)
- [로컬 실행](#로컬-실행)
- [빌드](#빌드)
- [테스트](#테스트)
- [API 요약](#api-요약)
- [API 사용 예시](#api-사용-예시)
- [보안 정책](#보안-정책)
- [당첨번호 수집 정책](#당첨번호-수집-정책)
- [추천 정책](#추천-정책)
- [운영 메모](#운영-메모)
- [REST Docs](#rest-docs)
- [주의사항](#주의사항)
- [라이선스](#라이선스)

---

## 주요 기능

### 01 · 당첨번호 조회

- 최신 회차 당첨번호 조회
- 특정 회차 당첨번호 조회
- 당첨번호 페이지 목록 조회
- 번호별 출현 빈도 조회

### 02 · 당첨번호 수집

- 동행복권 API 기반 당첨번호 수집
- Mock API client 기반 개발/테스트 실행
- 저장된 최신 회차 이후부터 순차 수집
- 수집 대상 회차 지정 지원
- 중복 수집 및 일부 실패 회차 처리 정책 포함

### 03 · 번호 추천

- 1~10개 추천 조합 생성 (기본 추천 개수: 5개)
- 과거 당첨 조합, 생일 편향, 단일 번호대 집중, 장기 연속수, 등차수열 등 제외 규칙 적용
- 운영 환경에서 `SecureRandom` 기반 번호 생성

### 04 · 운영 / 보안

- Spring Security 기반 stateless 보안 설정
- 명시 허용된 endpoint 외 요청 차단
- 관리자성 API 토큰 필터
- 추천/수집 API rate limit
- Actuator health, readiness, liveness probe
- Dockerfile, Docker Compose, 컨테이너 healthcheck 제공
- 로그 볼륨 분리

---

## 기술 스택

| 구분            | 기술                                                                                                                       |
| :-------------- | :------------------------------------------------------------------------------------------------------------------------- |
| **Language**    | Java 25                                                                                                                    |
| **Framework**   | Spring Boot 4.0.5                                                                                                          |
| **Web**         | Spring MVC · Thymeleaf · Bootstrap 5 · WebJars                                                                             |
| **Persistence** | Spring Data JPA · MariaDB                                                                                                  |
| **Migration**   | Flyway                                                                                                                     |
| **Security**    | Spring Security                                                                                                            |
| **Monitoring**  | Spring Boot Actuator · Micrometer                                                                                          |
| **Test**        | JUnit 5 · Spring Boot Test · Spring Security Test · REST Docs · Testcontainers · ArchUnit · H2                             |
| **Build**       | Gradle Kotlin DSL                                                                                                          |
| **Runtime**     | Docker · Docker Compose                                                                                                    |

---

## 프로젝트 구조

```text
.
├── src
│   ├── main
│   │   ├── java/com/kraft/lotto
│   │   │   ├── feature/recommend        # 번호 추천 도메인/API
│   │   │   ├── feature/winningnumber    # 당첨번호 조회·수집 도메인/API
│   │   │   ├── infra                    # 설정, 보안, 인프라 구성
│   │   │   ├── support                  # 공통 응답/예외 처리
│   │   │   └── web                      # 웹 화면 컨트롤러
│   │   └── resources
│   │       ├── db/migration             # Flyway migration
│   │       ├── static                   # 정적 리소스
│   │       ├── templates                # Thymeleaf 템플릿
│   │       └── application.yml
│   └── test                             # 단위/통합/아키텍처 테스트
├── docker                               # 컨테이너 헬스체크 스크립트
├── Dockerfile
├── docker-compose.yml
├── build.gradle.kts
├── settings.gradle.kts
└── .env.example
```

---

## 실행 준비

### 1. 환경 변수 파일 생성

```bash
cp .env.example .env
```

`.env`에는 실제 실행 환경에 맞는 값을 설정합니다.

> [!WARNING]
> 비밀값은 절대로 저장소에 커밋하지 마십시오. `.env` 파일은 반드시 `.gitignore`에 포함되어야 합니다.

<details>
<summary><b>주요 환경 변수 (펼치기)</b></summary>

<br/>

| 변수                                         | 설명                          | 예시                                          |
| :------------------------------------------- | :---------------------------- | :-------------------------------------------- |
| `SPRING_PROFILES_ACTIVE`                     | Spring profile                | `local`                                       |
| `KRAFT_APP_PORT`                             | 애플리케이션 외부 포트        | `8080`                                        |
| `KRAFT_DB_PORT`                              | MariaDB 외부 포트             | `3306`                                        |
| `KRAFT_DB_NAME`                              | DB 이름                       | `kraft_lotto`                                 |
| `KRAFT_DB_URL`                               | JDBC URL                      | `jdbc:mariadb://mariadb:3306/kraft_lotto...`  |
| `KRAFT_DB_USER`                              | DB 사용자                     | `kraft`                                       |
| `KRAFT_DB_PASSWORD`                          | DB 비밀번호                   | `change-me`                                   |
| `KRAFT_DB_ROOT_PASSWORD`                     | MariaDB root 비밀번호         | `change-me-root`                              |
| `KRAFT_API_CLIENT`                           | 외부 API client               | `mock` 또는 `real`                            |
| `KRAFT_ADMIN_API_TOKEN`                      | 관리자 API 토큰               | `change-me-admin-token`                       |
| `KRAFT_ADMIN_TOKEN_HEADER`                   | 관리자 토큰 헤더명            | `X-Kraft-Admin-Token`                         |
| `KRAFT_RECOMMEND_RATE_LIMIT_MAX_REQUESTS`    | 추천 API rate limit 요청 수   | `30`                                          |
| `KRAFT_RECOMMEND_RATE_LIMIT_WINDOW_SECONDS`  | 추천 API rate limit 윈도우(초)| `60`                                          |

</details>

---

## 로컬 실행

### Docker Compose로 실행

가장 단순한 실행 방식입니다. 애플리케이션과 MariaDB를 함께 실행합니다.

```bash
docker compose up --build
```

실행 후 다음 주소로 접속합니다.

```text
http://localhost:8080
```

컨테이너를 중지합니다.

```bash
docker compose down
```

DB 볼륨까지 삭제하려면 다음 명령을 사용합니다.

```bash
docker compose down -v
```

### Gradle로 직접 실행

> [!IMPORTANT]
> 로컬에 **Java 25**와 **MariaDB**가 준비되어 있어야 합니다.

```bash
./gradlew bootRun
```

Windows PowerShell에서는 다음 명령을 사용할 수 있습니다.

```powershell
.\gradlew.bat bootRun
```

---

## 빌드

```bash
./gradlew clean bootJar
```

생성되는 실행 JAR 파일 경로는 다음과 같습니다.

```text
build/libs/app.jar
```

REST Docs 결과물을 포함한 JAR가 필요하면 다음 task를 사용합니다.

```bash
./gradlew bootJarWithDocs
```

결과 파일 경로는 다음과 같습니다.

```text
build/libs/app-with-docs.jar
```

---

## 테스트

전체 테스트 실행:

```bash
./gradlew test
```

테스트 구성에는 다음 항목이 포함됩니다.

- Spring Boot Test
- Spring Security Test
- Spring REST Docs
- H2 테스트 DB
- Testcontainers MariaDB
- ArchUnit 아키텍처 테스트

---

## API 요약

> [!TIP]
> 공통 응답 형식은 `ApiResponse<T>`를 사용합니다.

| Method   | Path                                    | 설명                          |
| :------- | :-------------------------------------- | :---------------------------- |
| `GET`    | `/`                                     | 웹 화면                       |
| `POST`   | `/api/recommend`                        | 추천 번호 조합 생성           |
| `GET`    | `/api/recommend/rules`                  | 추천 제외 규칙 조회           |
| `GET`    | `/api/winning-numbers/latest`           | 최신 회차 당첨번호 조회       |
| `GET`    | `/api/winning-numbers/{round}`          | 특정 회차 당첨번호 조회       |
| `GET`    | `/api/winning-numbers`                  | 당첨번호 페이지 목록 조회     |
| `GET`    | `/api/winning-numbers/stats/frequency`  | 번호별 출현 빈도 조회         |
| `POST`   | `/api/winning-numbers/refresh`          | 당첨번호 수집 트리거 *(admin)* |
| `GET`    | `/actuator/health`                      | 기본 헬스체크                 |
| `GET`    | `/actuator/health/liveness`             | Liveness probe                |
| `GET`    | `/actuator/health/readiness`            | Readiness probe               |
| `GET`    | `/docs` · `/docs/**`                    | REST Docs 산출물              |

---

## API 사용 예시

### 추천 번호 생성

요청 본문을 생략하면 기본값으로 5개 조합을 생성합니다.

```bash
curl -X POST http://localhost:8080/api/recommend \
  -H 'Content-Type: application/json'
```

추천 개수를 지정할 수 있습니다.

```bash
curl -X POST http://localhost:8080/api/recommend \
  -H 'Content-Type: application/json' \
  -d '{"count": 3}'
```

> [!NOTE]
> `count`는 **1~10 범위**만 허용됩니다.

### 추천 제외 규칙 조회

```bash
curl http://localhost:8080/api/recommend/rules
```

### 당첨번호 조회

```bash
# 최신 회차
curl http://localhost:8080/api/winning-numbers/latest

# 특정 회차
curl http://localhost:8080/api/winning-numbers/1000

# 페이지 목록 (size는 1~100 범위만 허용)
curl 'http://localhost:8080/api/winning-numbers?page=0&size=20'

# 번호별 출현 빈도
curl http://localhost:8080/api/winning-numbers/stats/frequency
```

### 당첨번호 수집 *(admin)*

> [!CAUTION]
> `POST /api/winning-numbers/refresh`는 DB 쓰기와 외부 API 호출을 유발하는 **관리자성 API**입니다. `KRAFT_ADMIN_API_TOKEN`이 설정되어 있어야 하며, 요청 시 지정된 헤더로 토큰을 전달해야 합니다.

```bash
curl -X POST http://localhost:8080/api/winning-numbers/refresh \
  -H 'Content-Type: application/json' \
  -H 'X-Kraft-Admin-Token: change-me-admin-token'
```

특정 회차까지만 수집하려면 `targetRound`를 전달합니다.

```bash
curl -X POST http://localhost:8080/api/winning-numbers/refresh \
  -H 'Content-Type: application/json' \
  -H 'X-Kraft-Admin-Token: change-me-admin-token' \
  -d '{"targetRound": "1200"}'
```

---

## 보안 정책

- CSRF는 `/api/**`, `/actuator/**`에 대해 제외됩니다.
- HTTP Basic, form login, logout은 비활성화됩니다.
- 세션 정책은 stateless입니다.
- `/`, 정적 리소스, 추천 API, 당첨번호 GET API, health, docs만 명시적으로 허용됩니다.
- 그 외 요청은 `denyAll` 정책으로 차단됩니다.
- `POST /api/winning-numbers/refresh`는 관리자 토큰 필터로 보호됩니다.
- 추천/수집 API에는 IP 기반 rate limit이 적용됩니다.
- CSP, Referrer Policy, Permissions Policy, Frame Options 보안 헤더가 설정됩니다.

---

## 당첨번호 수집 정책

- 저장된 최신 회차 이후부터 수집합니다.
- `targetRound`가 지정되면 해당 회차까지만 수집합니다.
- `targetRound`가 없으면 외부 API가 미추첨 응답을 줄 때까지 순차 수집합니다.
- 이미 저장된 최신 회차 이하 요청은 외부 API 호출 없이 건너뜁니다.
- 회차별 저장 실패는 전체 수집을 즉시 중단하지 않고 실패 회차 목록에 포함합니다.
- 외부 API 호출 자체가 실패하면 부분 진행 이벤트를 발행한 뒤 비즈니스 예외로 종료합니다.

---

## 추천 정책

추천기는 무작위 조합을 생성한 뒤 **제외 규칙을 통과한 조합만** 반환합니다.

현재 확인되는 주요 제외 규칙은 다음과 같습니다.

- 과거 당첨 조합 제외
- 생일 편향 조합 제외
- 하나의 번호대에 과도하게 몰린 조합 제외
- 긴 연속수 조합 제외
- 등차수열 패턴 조합 제외

> [!NOTE]
> 이 정책은 *“사람들이 자주 고르는 형태”* 또는 *“눈에 띄는 패턴”* 을 줄이기 위한 필터링이며, 수학적으로 당첨 확률을 높이는 기능이 아닙니다.

---

## 운영 메모

### Profile

프로파일을 명시하지 않으면 `local`로 동작합니다.

```yaml
spring:
  profiles:
    default: local
```

### Healthcheck

Dockerfile과 Compose 모두 healthcheck를 사용합니다.

```text
/actuator/health/readiness
```

### 로그

컨테이너 실행 시 로그는 `/app/logs`에 기록되고, Compose에서는 호스트의 `./logs`로 마운트됩니다.

---

## REST Docs

REST Docs 산출물을 포함해 빌드하려면 다음 명령을 사용합니다.

```bash
./gradlew bootJarWithDocs
```

빌드된 문서는 애플리케이션 정적 리소스로 포함됩니다.

```text
/docs
/docs/**
```

---

## 주의사항

> [!WARNING]
> **운영 전 반드시 확인하십시오.**
>
> - 이 프로젝트는 로또 번호 조회·수집·추천 기능을 제공하지만, **당첨을 보장하지 않습니다**.
> - 추천 결과는 **오락/참고용**으로만 사용해야 합니다.
> - 운영 환경에서는 반드시 `.env`의 비밀번호, 관리자 토큰, 포트 설정을 변경해야 합니다.
> - 실제 동행복권 API를 사용하려면 `KRAFT_API_CLIENT`와 API 관련 환경 변수를 운영 정책에 맞게 설정해야 합니다.

---

## 라이선스

라이선스가 정해지면 이 섹션에 명시합니다.

<div align="center">

<sub>**Kraft Works** · Lotto 6/45 · Built with Spring Boot 4 on Java 25</sub>

</div>
