# KraftLotto (kLo)

> 로또 6/45 번호 추천과 당첨번호 조회/수집을 제공하는 Spring Boot 애플리케이션입니다.
>
> KraftLotto는 당첨 확률을 보장하지 않습니다. 대신 생일 번호 편향, 완전 등차수열, 긴 연속번호,
> 특정 십의 자리 쏠림, 과거 1등 조합과의 완전 중복처럼 사람이 보기에도 피하고 싶은 조합을
> 규칙 기반으로 걸러 더 납득 가능한 후보를 생성합니다.

[![Java](https://img.shields.io/badge/Java-25-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.5-6DB33F?style=flat-square&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Gradle](https://img.shields.io/badge/Gradle-9-02303A?style=flat-square&logo=gradle&logoColor=white)](https://gradle.org/)
[![MariaDB](https://img.shields.io/badge/MariaDB-11.8-003545?style=flat-square&logo=mariadb&logoColor=white)](https://mariadb.org/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=flat-square&logo=docker&logoColor=white)](https://docs.docker.com/compose/)

---

## 목차

1. [현재 상태](#현재-상태)
2. [기술 스택](#기술-스택)
3. [아키텍처](#아키텍처)
4. [파일 맵](#파일-맵)
5. [추천 규칙](#추천-규칙)
6. [빠른 시작](#빠른-시작)
7. [환경 변수](#환경-변수)
8. [프로파일 정책](#프로파일-정책)
9. [API 레퍼런스](#api-레퍼런스)
10. [보안과 운영](#보안과-운영)
11. [데이터 모델](#데이터-모델)
12. [테스트와 문서 생성](#테스트와-문서-생성)
13. [CI/CD](#cicd)
14. [에러 코드](#에러-코드)

---

## 현재 상태

| 항목 | 내용 |
|:---|:---|
| 사용자 접근 | 화면, 추천, 당첨번호 조회, 당첨번호 수집 API를 모두 공개 접근으로 제공 |
| 관리자 기능 | 관리자 전용 API, Basic 인증, 관리자 IP 화이트리스트, `KRAFT_ADMIN_*` 설정 제거 |
| 추천 API 보호 | `POST /api/recommend`에만 IP 기준 sliding-window rate limit 적용 |
| 당첨번호 저장 | MariaDB 저장, Flyway 스키마 관리, JPA `ddl-auto=validate` |
| 당첨번호 수집 | 공개 API `POST /api/winning-numbers/refresh`가 동행복권 API 또는 mock client에서 순차 수집 |
| 화면 | Thymeleaf SSR 진입점과 Vanilla JS 기반 API 호출 |
| 배포 | GitHub Actions CD가 self-hosted runner에서 Docker Compose 재기동 |
| 상태 확인 | Docker healthcheck와 CD readiness check 모두 Actuator readiness 기준 |

관리자만 접근 가능한 영역을 없애고, 누구나 애플리케이션을 사용할 수 있는 공개 서비스 형태로 단순화했습니다.
운영에서 남은 주요 방어선은 추천 API의 요청 수 제한, 보안 응답 헤더, reverse proxy 신뢰 경계, DB/secret 분리입니다.

---

## 기술 스택

| 영역 | 기술 |
|:---|:---|
| Language | Java 25 |
| Framework | Spring Boot 4.0.5, Web, Validation, Actuator, Security, Thymeleaf |
| Persistence | Spring Data JPA, Flyway, MariaDB 11.8 |
| API Client | Spring `RestClient`, Jackson |
| Build | Gradle Kotlin DSL, Asciidoctor |
| Frontend | Thymeleaf, Bootstrap 5.3.3, Bootstrap Icons, Vanilla JS |
| Test | JUnit 5, Mockito, Spring MVC Test, Spring Security Test, REST Docs, Testcontainers, ArchUnit |
| Runtime | Docker Compose, Eclipse Temurin JDK/JRE 25, non-root runtime image |
| Operations | GitHub Actions CI/CD, Docker healthcheck, Nginx reverse proxy example |

---

## 아키텍처

코드는 feature-first 구조입니다. `recommend`와 `winningnumber`를 나누고, 각 feature 안에서
web, application, domain, infrastructure 책임을 분리합니다. 도메인 객체는 Spring, JPA, Web 세부 기술에
직접 의존하지 않도록 ArchUnit으로 검증합니다.

```text
com.kraft.lotto
|
+- feature
|  +- recommend
|  |  +- domain          ExclusionRule, 추천 제외 규칙, PastWinningCache
|  |  +- application     RecommendService, LottoRecommender, rule config, cache loader
|  |  +- web             RecommendController, request/response DTO
|  |
|  +- winningnumber
|     +- domain          LottoCombination, WinningNumber
|     +- application     Collect/Query service, LottoApiClient, dhlottery/mock adapters
|     +- infrastructure  JPA entity, repository, mapper
|     +- event           WinningNumbersCollectedEvent
|     +- web             공개 조회/수집 controller, DTO, round validation
|
+- infra
|  +- config             .env loader, datasource URL fixer, required config validator, properties, Jackson
|  +- security           public security headers, recommend rate limit filter
|
+- support               ApiResponse, ApiError, ErrorCode, BusinessException, GlobalExceptionHandler
```

### 의존성 규칙

```text
web -> application -> domain
          |
          +-> infrastructure

domain -> Spring/JPA/Web/Hibernate 의존 금지
domain -> support.BusinessException 직접 의존 금지
web    -> feature.*.infrastructure 직접 의존 금지
@Entity는 feature.*.infrastructure 패키지에만 위치
```

---

## 파일 맵

### 루트와 빌드

| 경로 | 역할 |
|:---|:---|
| `build.gradle.kts` | Spring Boot 4.0.5, Java 25 toolchain, JPA/Flyway/Security/Thymeleaf/Test 의존성, REST Docs/Asciidoctor/bootJar 연결 |
| `settings.gradle.kts` | Gradle root project 이름 `kraft-lotto` |
| `gradlew`, `gradlew.bat`, `gradle/wrapper/*` | Gradle wrapper |
| `.env.example` | 로컬/Compose 실행용 환경 변수 템플릿 |
| `.gitignore` | build, IDE, logs, `.env` 제외 |
| `.gitattributes` | shell, YAML, Dockerfile 등 EOL 규칙 |
| `klo_roadmap.md` | 개선 로드맵과 인프라 메모 |
| `docs/assets/readme-hero.svg` | README 또는 문서용 시각 자산 |

### 배포와 운영

| 경로 | 역할 |
|:---|:---|
| `.github/workflows/ci.yml` | `develop` push와 `main`/`develop` PR에서 `./gradlew --no-daemon clean build` 실행, 테스트 리포트 업로드 |
| `.github/workflows/cd.yml` | `main` push 또는 수동 실행 시 production `.env` 생성, Docker Compose 재기동, readiness 대기, smoke test |
| `Dockerfile` | JDK 25 build stage, JRE 25 runtime stage, non-root `kraft` 사용자, `/app/healthcheck.sh` |
| `docker-compose.yml` | MariaDB 11.8 + app 서비스, 명시 네트워크/볼륨, readiness healthcheck |
| `docker/healthcheck.sh` | `KRAFT_HEALTHCHECK_URL` 호출 후 HTTP 200과 `"status":"UP"` 확인 |
| `docs/deploy/nginx/kraft-lotto-site.conf` | Nginx site 예시, HTTPS redirect, reverse proxy, Actuator 내부망 제한 예시 |
| `docs/deploy/nginx/kraft-lotto-proxy.conf` | 공통 proxy header와 timeout snippet |

### 애플리케이션 설정

| 경로 | 역할 |
|:---|:---|
| `src/main/resources/application.yml` | 공통 Spring, datasource, JPA, Flyway, Actuator probe, `kraft.*` 설정 |
| `src/main/resources/application-local.yml` | 로컬 개발용 SQL 로그, template/static cache 비활성화, health 상세 노출 |
| `src/main/resources/application-prod.yml` | 운영 profile override, health/info 중심 노출 |
| `src/main/resources/logback-spring.xml` | 콘솔 로그, 전체/error/warn/debug 레벨별 rolling file appender |
| `src/main/resources/META-INF/spring.factories` | EnvironmentPostProcessor 등록 |
| `src/main/resources/db/migration/V1__init_winning_numbers.sql` | `winning_numbers` 테이블, CHECK 제약, draw date index |

### Spring 진입점과 인프라

| 경로 | 역할 |
|:---|:---|
| `src/main/java/com/kraft/lotto/KraftLottoApplication.java` | Spring Boot 진입점, `@ConfigurationPropertiesScan` |
| `src/main/java/com/kraft/lotto/web/IndexController.java` | `/`, `/index` Thymeleaf view 진입점 |
| `infra/config/DotenvEnvironmentPostProcessor.java` | `.env`를 낮은 우선순위 PropertySource로 로드 |
| `infra/config/DatasourceUrlAutoFixer.java` | 호스트 OS 실행 시 compose 서비스명 DB host를 로컬 host로 자동 치환 |
| `infra/config/RequiredConfigValidator.java` | datasource 필수값과 JDBC host 해석 가능 여부를 부팅 초기에 검증 |
| `infra/config/KraftApiProperties.java` | 외부 API client, URL, timeout, retry 설정 바인딩 |
| `infra/config/KraftRecommendProperties.java` | 추천 생성 최대 시도 횟수 설정 바인딩 |
| `infra/config/KraftRecommendRateLimitProperties.java` | 추천 API rate limit window와 허용 요청 수 설정 바인딩 |
| `infra/config/JacksonConfig.java` | JSON 직렬화 설정 |
| `infra/security/SecurityConfig.java` | 공개 접근 정책, CSRF 예외, security headers, recommend rate limit filter 등록 |
| `infra/security/RecommendRateLimitFilter.java` | `POST /api/recommend`에만 적용되는 IP별 sliding-window 제한 |

### 추천 feature

| 경로 | 역할 |
|:---|:---|
| `feature/recommend/web/RecommendController.java` | `POST /api/recommend`, `GET /api/recommend/rules` |
| `feature/recommend/web/dto/*` | 추천 요청/응답, 조합 DTO, 규칙 DTO |
| `feature/recommend/application/RecommendService.java` | 추천 요청 orchestration, rule 설명 제공 |
| `feature/recommend/application/LottoRecommender.java` | 무작위 조합 생성, 중복 제거, 규칙 적용, timeout 처리 |
| `feature/recommend/application/RecommendRuleConfig.java` | 제외 규칙 순서 구성 |
| `feature/recommend/application/PastWinningCacheLoader.java` | DB의 과거 당첨 조합을 `PastWinningCache`로 적재/갱신 |
| `feature/recommend/application/RecommendGenerationTimeoutException.java` | 추천 생성 시도 초과 예외 |
| `feature/recommend/domain/*Rule.java` | 생일 편향, 등차수열, 긴 연속번호, 십의 자리 쏠림, 과거 당첨 조합 제외 규칙 |
| `feature/recommend/domain/PastWinningCache.java` | 과거 1등 조합 set cache |
| `feature/recommend/domain/ExclusionRule.java` | 추천 제외 규칙 인터페이스 |

### 당첨번호 feature

| 경로 | 역할 |
|:---|:---|
| `feature/winningnumber/web/WinningNumberController.java` | 최신/회차별/목록/번호 빈도 공개 조회 API |
| `feature/winningnumber/web/WinningNumberCollectController.java` | 공개 수집 API `POST /api/winning-numbers/refresh` |
| `feature/winningnumber/web/dto/*` | 당첨번호, 페이지, 빈도, 수집 요청/응답 DTO |
| `feature/winningnumber/web/validation/*` | 회차 path variable 검증 |
| `feature/winningnumber/application/WinningNumberCollectService.java` | 최신 저장 회차 다음부터 외부 API 응답이 없을 때까지 순차 수집 |
| `feature/winningnumber/application/WinningNumberQueryService.java` | 최신/회차별/목록/번호별 빈도 조회 |
| `feature/winningnumber/application/DhLotteryApiClient.java` | 동행복권 API adapter |
| `feature/winningnumber/application/MockLottoApiClient.java` | 로컬/test용 mock adapter |
| `feature/winningnumber/application/LottoApiClientConfig.java` | client 선택 구성 |
| `feature/winningnumber/application/LottoApiClientException.java` | 외부 API 장애 추상화 |
| `feature/winningnumber/domain/LottoCombination.java` | 로또 본번호 6개 불변 조합과 검증 |
| `feature/winningnumber/domain/WinningNumber.java` | 회차, 추첨일, 본번호, 보너스, 금액/당첨자 도메인 |
| `feature/winningnumber/event/WinningNumbersCollectedEvent.java` | 수집 성공 후 과거 당첨 cache 갱신 이벤트 |
| `feature/winningnumber/infrastructure/*` | JPA entity, repository, domain mapper |

### 공통 지원과 화면

| 경로 | 역할 |
|:---|:---|
| `support/ApiResponse.java` | `{ success, data, error }` 공통 응답 envelope |
| `support/ApiError.java` | 에러 응답 payload |
| `support/BusinessException.java` | 도메인/애플리케이션 계층의 업무 예외 |
| `support/ErrorCode.java` | HTTP status와 기본 메시지를 포함한 에러 코드 |
| `support/GlobalExceptionHandler.java` | validation, business, rate limit, unexpected exception 변환 |
| `src/main/resources/templates/index.html` | 추천, 최신 회차, 회차 검색, 빈도, 목록 화면 layout |
| `src/main/resources/templates/fragments/header.html` | head/navbar, theme toggle, 당첨번호 갱신 modal |
| `src/main/resources/templates/fragments/footer.html` | footer, Bootstrap/app script, toast container |
| `src/main/resources/static/js/app.js` | API wrapper, rendering, pagination, refresh modal, theme, toast |
| `src/main/resources/static/css/app.css` | Bootstrap 보완 디자인, 카드, 로또볼, 빈도 grid, toast |
| `src/main/resources/static/images/favicon.svg` | 앱 favicon |

### 테스트와 문서

| 경로 | 역할 |
|:---|:---|
| `src/test/java/com/kraft/lotto/KraftLottoApplicationTests.java` | 부트스트랩 context smoke test |
| `src/test/java/com/kraft/lotto/architecture/ArchitectureTest.java` | 계층/의존성 규칙 검증 |
| `src/test/java/com/kraft/lotto/infra/*` | 설정 바인딩, 보안 공개 접근, 추천 rate limit 검증 |
| `src/test/java/com/kraft/lotto/feature/recommend/*` | 추천 규칙, recommender, service, controller 검증 |
| `src/test/java/com/kraft/lotto/feature/winningnumber/*` | 도메인, query/collect service, API client, controller, repository IT 검증 |
| `src/test/resources/application-test.yml` | H2 기반 slice/context test 설정 |
| `src/test/resources/application-it.yml` | Testcontainers MariaDB 기반 integration test 설정 |
| `src/docs/asciidoc/index.adoc` | REST Docs snippet을 조립하는 API 문서 원본 |

---

## 추천 규칙

`LottoRecommender`는 1~45 사이 고유 번호 6개를 만들고 아래 규칙을 순서대로 적용합니다.
후보가 하나라도 제외 조건에 걸리면 버리고 새 후보를 생성합니다.

| 순서 | 규칙 | 제외 조건 | 예시 |
|:---:|:---|:---|:---|
| 1 | `BirthdayBiasRule` | 6개 번호가 모두 31 이하 | `1, 7, 13, 22, 29, 31` |
| 2 | `ArithmeticSequenceRule` | 6개 번호가 완전 등차수열 | `3, 6, 9, 12, 15, 18` |
| 3 | `LongRunRule` | 5개 이상 연속 번호 포함 | `10, 11, 12, 13, 14, 40` |
| 4 | `SingleDecadeRule` | 같은 십의 자리 bucket에 5개 이상 집중 | `1, 3, 5, 7, 9, 40` |
| 5 | `PastWinningRule` | 과거 1등 본번호 조합과 완전 동일 | DB 저장 회차 전체 |

`PastWinningCache`는 기동 시 repository에서 적재됩니다. 당첨번호 수집으로 새 회차가 저장되면
`WinningNumbersCollectedEvent`가 발행되고, cache loader가 이벤트를 받아 과거 당첨 조합 cache를 갱신합니다.

---

## 빠른 시작

### Docker Compose

```powershell
Copy-Item .env.example .env
# .env에서 KRAFT_DB_PASSWORD, KRAFT_DB_ROOT_PASSWORD를 운영/개발 환경에 맞게 교체하세요.

docker compose up -d --build
docker compose ps
```

| 서비스 | URL |
|:---|:---|
| Web | `http://localhost:8080` |
| Health | `http://localhost:8080/actuator/health` |
| Liveness | `http://localhost:8080/actuator/health/liveness` |
| Readiness | `http://localhost:8080/actuator/health/readiness` |
| API Docs | `http://localhost:8080/docs/index.html` |
| MariaDB | `localhost:3306` |

### 로컬 bootRun

```powershell
$env:SPRING_PROFILES_ACTIVE = "local"
$env:KRAFT_DB_URL = "jdbc:mariadb://localhost:3306/kraft_lotto"
$env:KRAFT_DB_USER = "kraft"
$env:KRAFT_DB_PASSWORD = "change-me"
$env:KRAFT_API_CLIENT = "mock"

.\gradlew.bat bootRun
```

`.env.example`의 DB URL은 compose service name인 `mariadb`를 가리킵니다. 호스트 OS에서 직접 실행하면
`DatasourceUrlAutoFixer`가 `mariadb`를 `KRAFT_DB_LOCAL_HOST` 값으로 자동 치환할 수 있습니다.

### 테스트와 빌드

```powershell
.\gradlew.bat test
.\gradlew.bat build
.\gradlew.bat bootJar
```

---

## 환경 변수

| 변수 | 기본값 예시 | 설명 |
|:---|:---|:---|
| `SPRING_PROFILES_ACTIVE` | `local` | 실행 profile |
| `KRAFT_APP_PORT` | `8080` | Docker Compose app host port |
| `KRAFT_DB_PORT` | `3306` | Docker Compose DB host port |
| `KRAFT_DB_NAME` | `kraft_lotto` | MariaDB database name |
| `KRAFT_DB_URL` | `jdbc:mariadb://mariadb:3306/kraft_lotto?...` | JDBC URL |
| `KRAFT_DB_USER` | `kraft` | DB user |
| `KRAFT_DB_PASSWORD` | 필수 교체 | DB password |
| `KRAFT_DB_ROOT_PASSWORD` | 필수 교체 | MariaDB root password |
| `KRAFT_DB_LOCAL_HOST` | `localhost` | 호스트 OS bootRun 시 compose DB host 치환 대상 |
| `KRAFT_DB_HOST_REWRITE` | `true` | `false`면 datasource host 자동 치환 비활성화 |
| `KRAFT_IN_CONTAINER` | `false` / compose는 `true` | 컨테이너 내부 실행 여부 힌트 |
| `KRAFT_API_CLIENT` | `mock` | `mock`, `dhlottery`, `real` |
| `KRAFT_API_URL` | `https://www.dhlottery.co.kr/common.do` | 동행복권 API base URL |
| `KRAFT_API_CONNECT_TIMEOUT_MS` | `2000` | 외부 API 연결 timeout |
| `KRAFT_API_READ_TIMEOUT_MS` | `3000` | 외부 API read timeout |
| `KRAFT_API_MAX_RETRIES` | `2` | 외부 API 재시도 횟수 |
| `KRAFT_API_RETRY_BACKOFF_MS` | `200` | 재시도 backoff |
| `KRAFT_RECOMMEND_MAX_ATTEMPTS` | `100000` | 추천 생성 최대 후보 시도 횟수 |
| `KRAFT_RECOMMEND_RATE_LIMIT_MAX_REQUESTS` | `30` | IP별 recommend 요청 허용 수 |
| `KRAFT_RECOMMEND_RATE_LIMIT_WINDOW_SECONDS` | `60` | recommend rate limit window |
| `KRAFT_LOG_PATH` | `./logs` | logback file output path |
| `KRAFT_HEALTHCHECK_URL` | `http://localhost:8080/actuator/health/readiness` | container healthcheck URL |
| `KRAFT_HEALTHCHECK_TIMEOUT_SECONDS` | `3` | healthcheck curl timeout |

`.env`는 Git 추적 대상이 아닙니다. 실제 비밀값은 `.env`, GitHub Actions secret, 또는 호스팅 환경 변수로 주입합니다.

---

## 프로파일 정책

| 프로파일 | DataSource | 외부 API | Actuator/리소스 | 비고 |
|:---|:---|:---|:---|:---|
| `local` | MariaDB, env 기반 | `mock` 기본 | health 상세, static/template cache off | 개발 편의 |
| `prod` | MariaDB, env 필수 | `dhlottery` 권장 | health/info 중심 | CD에서 `.env` 생성 |
| `test` | H2 MySQL mode | `mock` | 테스트 전용 | 단위/slice/context test |
| `it` | Testcontainers MariaDB | `mock` | 테스트 전용 | Docker가 없으면 persistence IT 비활성화 |

공통 설정의 default profile은 `local`입니다. 운영에서는 `SPRING_PROFILES_ACTIVE=prod`를 명시합니다.

---

## API 레퍼런스

모든 API는 공통 envelope 형태로 응답합니다.

```json
{
  "success": true,
  "data": {},
  "error": null
}
```

### 추천 API

```bash
curl -X POST http://localhost:8080/api/recommend \
  -H "Content-Type: application/json" \
  -d '{"count":5}'

curl http://localhost:8080/api/recommend/rules
```

| Method | Path | 설명 |
|:---|:---|:---|
| `POST` | `/api/recommend` | 추천 조합 생성. `count`는 1~10, 생략 시 5 |
| `GET` | `/api/recommend/rules` | 적용 중인 제외 규칙 목록 조회 |

`POST /api/recommend`는 IP별 rate limit 대상입니다.

### 당첨번호 조회 API

```bash
curl http://localhost:8080/api/winning-numbers/latest
curl http://localhost:8080/api/winning-numbers/1100
curl "http://localhost:8080/api/winning-numbers?page=0&size=20"
curl http://localhost:8080/api/winning-numbers/stats/frequency
```

| Method | Path | 설명 |
|:---|:---|:---|
| `GET` | `/api/winning-numbers/latest` | DB에 저장된 최신 회차 조회 |
| `GET` | `/api/winning-numbers/{round}` | 특정 회차 조회 |
| `GET` | `/api/winning-numbers?page=&size=` | 회차 목록 조회. `page`는 0-base, `size`는 1~100 |
| `GET` | `/api/winning-numbers/stats/frequency` | 본번호 기준 번호별 출현 빈도 조회 |

### 당첨번호 수집 API

```bash
curl -X POST http://localhost:8080/api/winning-numbers/refresh \
  -H "Content-Type: application/json" \
  -d '{"targetRound":"1103"}'
```

| Method | Path | 설명 |
|:---|:---|:---|
| `POST` | `/api/winning-numbers/refresh` | 최신 저장 회차 다음부터 `targetRound` 또는 외부 API 미추첨 응답 전까지 수집 |

`targetRound`를 생략하면 DB의 최신 저장 회차 다음부터 외부 API가 아직 공개하지 않은 회차를 만날 때까지 시도합니다.
수집 성공으로 새 회차가 저장되면 과거 당첨 조합 cache가 갱신됩니다.

---

## 보안과 운영

- 화면과 API는 공개 접근입니다. Basic/Form login, logout, 관리자 전용 security chain은 사용하지 않습니다.
- CSRF는 `/api/**`, `/actuator/**`에 대해 비활성화되어 API와 healthcheck 호출을 단순화합니다.
- Security headers는 CSP, Referrer Policy, Permissions Policy, Frame Options를 적용합니다.
- 추천 생성 API는 `RecommendRateLimitFilter`로 IP별 요청 수를 제한합니다.
- reverse proxy 앞단에서는 신뢰 가능한 proxy에서만 `X-Forwarded-For`를 설정하도록 Nginx를 구성해야 합니다.
- 애플리케이션은 `server.forward-headers-strategy=NATIVE`를 사용합니다.
- 운영 secret은 DB 사용자 비밀번호/root 비밀번호와 외부 API 관련 값을 포함합니다.
- 공개 수집 API는 접근성을 우선한 현재 정책입니다. 트래픽이 늘면 수집 API에도 별도 rate limit, job queue, scheduler 권한 정책을 추가하는 것이 좋습니다.
- Nginx 예시는 `/actuator/`를 내부망으로 제한합니다. 애플리케이션 자체는 health endpoint를 공개하므로 운영 노출 범위는 proxy에서 한 번 더 제한합니다.

---

## 데이터 모델

Flyway `V1__init_winning_numbers.sql`은 `winning_numbers` 테이블을 생성합니다.

```sql
CREATE TABLE winning_numbers (
    round INT NOT NULL,
    draw_date DATE NOT NULL,
    n1 INT NOT NULL,
    n2 INT NOT NULL,
    n3 INT NOT NULL,
    n4 INT NOT NULL,
    n5 INT NOT NULL,
    n6 INT NOT NULL,
    bonus_number INT NOT NULL,
    first_prize BIGINT NOT NULL,
    first_winners INT NOT NULL,
    total_sales BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT pk_winning_numbers PRIMARY KEY (round)
);
```

마이그레이션에는 다음 방어가 포함됩니다.

| 제약 | 내용 |
|:---|:---|
| 회차 | `round > 0` |
| 번호 범위 | 본번호와 보너스 번호 모두 1~45 |
| 본번호 정렬 | `n1 < n2 < n3 < n4 < n5 < n6` |
| 보너스 중복 방지 | `bonus_number NOT IN (n1..n6)` |
| 금액/인원 | `first_prize`, `first_winners`, `total_sales` 음수 금지 |
| 조회 성능 | `draw_date` index |

도메인 객체 `LottoCombination`, `WinningNumber`도 같은 불변식을 애플리케이션 레이어에서 한 번 더 검증합니다.

---

## 테스트와 문서 생성

```powershell
.\gradlew.bat test
.\gradlew.bat build
```

| 범위 | 주요 테스트 |
|:---|:---|
| 부트스트랩 | `KraftLottoApplicationTests` |
| 도메인 | `LottoCombinationTest`, `WinningNumberTest`, `ExclusionRulesTest` |
| 추천 | `LottoRecommenderTest`, `RecommendServiceTest`, `RecommendControllerTest` |
| 당첨번호 | query/collect service, API client, controller, repository IT |
| 보안 | 공개 접근 정책, health 접근, recommend rate limit |
| 설정 | `KraftPropertiesBindingTest` |
| 아키텍처 | `ArchitectureTest` |

REST Docs는 WebMvc 테스트에서 `build/generated-snippets`를 만들고, `asciidoctor`가
`build/docs/asciidoc/index.html`을 생성합니다. `bootJar`는 이 산출물을 `static/docs`로 포함합니다.

Testcontainers 기반 `WinningNumberRepositoryIT`는 MariaDB 11.8 컨테이너와 Flyway/JPA validate를 검증합니다.
Docker가 없는 환경에서는 `@Testcontainers(disabledWithoutDocker = true)` 정책에 따라 해당 IT가 비활성화됩니다.

---

## CI/CD

### CI

`.github/workflows/ci.yml`

| 이벤트 | 작업 |
|:---|:---|
| `pull_request` to `main`, `develop` | JDK 25 setup, Gradle cache, `./gradlew --no-daemon clean build`, 테스트 리포트 업로드 |
| `push` to `develop` | 동일 |

### CD

`.github/workflows/cd.yml`

| 이벤트 | 작업 |
|:---|:---|
| `push` to `main` | self-hosted runner에서 production `.env` 생성, Docker Compose build/restart, readiness 대기, smoke test |
| `workflow_dispatch` | 수동 배포 |

배포 성공 기준은 `http://localhost:8080/actuator/health/readiness`의 HTTP 200과 `"status":"UP"`입니다.
실패 시 container health detail, compose status, 최근 app logs가 GitHub Actions 로그에 남습니다.

---

## 에러 코드

| 코드 | HTTP | 의미 |
|:---|:---:|:---|
| `LOTTO_INVALID_COUNT` | 400 | 추천 개수 1~10 범위 초과 |
| `LOTTO_INVALID_NUMBER` | 400 | 유효하지 않은 로또 번호 |
| `LOTTO_INVALID_TARGET_ROUND` | 400 | 회차 또는 수집 대상 회차 오류 |
| `LOTTO_INVALID_PAGE_REQUEST` | 400 | 페이지 파라미터 오류 |
| `REQUEST_VALIDATION_ERROR` | 400 | 요청 값 검증 실패 |
| `LOTTO_GENERATION_TIMEOUT` | 503 | 추천 조합 생성 시도 한도 초과 |
| `WINNING_NUMBER_NOT_FOUND` | 404 | 해당 회차 당첨번호 없음 |
| `EXTERNAL_API_FAILURE` | 502 | 외부 API 호출/파싱 실패 |
| `COLLECT_FAILED` | 500 | 당첨번호 수집 실패 |
| `TOO_MANY_REQUESTS` | 429 | 추천 API 요청 한도 초과 |
| `INTERNAL_SERVER_ERROR` | 500 | 처리하지 못한 서버 오류 |

---

> Built with care by [portuna85](https://github.com/portuna85)
