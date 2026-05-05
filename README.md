# KraftLotto (kLo)

> **로또 6/45 편향 회피형 추천 서비스**
>
> 당첨 확률을 높인다고 주장하지 않습니다. 생일 편중, 등차수열, 장기 연속번호,
> 단일 십의자리 편중, 과거 1등 조합처럼 통계적으로 피하고 싶은 조합을 줄이는
> Spring Boot 백엔드 학습용 MVP입니다.

[![Java](https://img.shields.io/badge/Java-25-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.5-6DB33F?style=flat-square&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Gradle](https://img.shields.io/badge/Gradle-9-02303A?style=flat-square&logo=gradle&logoColor=white)](https://gradle.org/)
[![MariaDB](https://img.shields.io/badge/MariaDB-11.8-003545?style=flat-square&logo=mariadb&logoColor=white)](https://mariadb.org/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=flat-square&logo=docker&logoColor=white)](https://docs.docker.com/compose/)

---

## 목차

1. [기술 스택](#기술-스택)
2. [아키텍처](#아키텍처)
3. [추천 규칙](#추천-규칙)
4. [빠른 시작](#빠른-시작)
5. [환경 변수](#환경-변수)
6. [프로파일 정책](#프로파일-정책)
7. [API 레퍼런스](#api-레퍼런스)
8. [보안 운영 메모](#보안-운영-메모)
9. [에러 코드](#에러-코드)
10. [데이터 모델](#데이터-모델)
11. [테스트](#테스트)

---

## 기술 스택

| 영역 | 기술 |
|:---|:---|
| Language | Java 25 |
| Framework | Spring Boot 4.0.5, Web, Validation, Actuator, Security, Thymeleaf |
| Persistence | Spring Data JPA, Flyway, MariaDB 11.8 |
| API Client | RestClient, Jackson |
| Build | Gradle Kotlin DSL, Asciidoctor |
| Frontend | Thymeleaf, Bootstrap 5.3, Bootstrap Icons, Vanilla JS |
| Test | JUnit 5, Mockito, Spring Security Test, REST Docs, Testcontainers, ArchUnit |
| Runtime | Docker Compose, non-root multi-stage Docker image |

---

## 아키텍처

Feature-first 구조로 도메인을 수직 분할하고, 각 feature 내부는 가벼운 hexagonal layer를 따릅니다.

```text
com.kraft.lotto
|
├─ feature
|  ├─ recommend
|  |  ├─ domain          추천 제외 규칙, PastWinningCache
|  |  ├─ application     RecommendService, LottoRecommender, CacheLoader
|  |  └─ web             RecommendController, DTO
|  |
|  └─ winningnumber
|     ├─ domain          LottoCombination, WinningNumber
|     ├─ application     Collect/Query Service, LottoApiClient
|     ├─ infrastructure  JPA Entity, Repository, Mapper
|     ├─ event           WinningNumbersCollectedEvent
|     └─ web             공개/관리자 Controller, DTO, validation
|
├─ infra
|  ├─ config             환경 변수, datasource, Jackson 설정
|  └─ security           SecurityConfig, rate limit, admin IP whitelist
|
└─ support               ApiResponse, ApiError, ErrorCode, GlobalExceptionHandler
```

### 의존성 규칙

ArchUnit 테스트로 다음 규칙을 빌드 타임에 확인합니다.

```text
web -> application -> domain
          |
          └-> infrastructure

domain -> Spring/JPA/Web 의존 금지
web    -> feature.*.infrastructure 직접 의존 금지
@Entity는 feature.*.infrastructure 아래에만 위치
```

---

## 추천 규칙

`LottoRecommender`가 후보 조합을 만들면 아래 규칙을 순서대로 평가합니다.
하나라도 매칭되면 해당 조합을 폐기하고 다시 생성합니다.

| # | 규칙 | 제외 조건 | 예시 |
|:---:|:---|:---|:---|
| 1 | `BirthdayBiasRule` | 6개 번호가 모두 31 이하 | `1, 7, 13, 22, 29, 31` |
| 2 | `ArithmeticSequenceRule` | 완전한 등차수열 | `3, 6, 9, 12, 15, 18` |
| 3 | `LongRunRule` | 5개 이상 연속 번호 | `10, 11, 12, 13, 14, 40` |
| 4 | `SingleDecadeRule` | 한 십의자리 구간에 5개 이상 집중 | `1, 3, 5, 7, 9, 40` |
| 5 | `PastWinningRule` | 과거 1등 조합과 완전히 동일 | DB 저장 회차 전체 |

`PastWinningCache`는 기동 시 적재되고, `WinningNumbersCollectedEvent`가 발행되면 자동으로 갱신됩니다.
생성 시도 횟수가 `KRAFT_RECOMMEND_MAX_ATTEMPTS`를 넘으면 `LOTTO_GENERATION_TIMEOUT`을 반환합니다.

---

## 빠른 시작

### Docker Compose

```powershell
Copy-Item .env.example .env
# .env에서 KRAFT_DB_PASSWORD, KRAFT_DB_ROOT_PASSWORD, KRAFT_ADMIN_PASSWORD를 반드시 교체하세요.

docker compose up -d --build
```

| 서비스 | URL |
|:---|:---|
| Web | `http://localhost:8080` |
| Health | `http://localhost:8080/actuator/health` |
| API Docs | `http://localhost:8080/docs/index.html` |
| MariaDB | `localhost:3306` |

### 로컬 개발

```powershell
$env:SPRING_PROFILES_ACTIVE = "local"
$env:KRAFT_DB_URL = "jdbc:mariadb://localhost:3306/kraft_lotto"
$env:KRAFT_DB_USER = "kraft"
$env:KRAFT_DB_PASSWORD = "change-me"
$env:KRAFT_ADMIN_USERNAME = "admin"
$env:KRAFT_ADMIN_PASSWORD = "change-me-admin"
$env:KRAFT_ADMIN_ALLOWED_IP_RANGES = "127.0.0.1/32,::1/128"

.\gradlew.bat bootRun
```

### 테스트와 빌드

```powershell
.\gradlew.bat test
.\gradlew.bat build
.\gradlew.bat bootJar
```

---

## 환경 변수

| 변수 | 기본값/예시 | 설명 |
|:---|:---|:---|
| `SPRING_PROFILES_ACTIVE` | `local` | 실행 프로파일 |
| `KRAFT_APP_PORT` | `8080` | Docker Compose 앱 포트 |
| `KRAFT_DB_PORT` | `3306` | Docker Compose DB 포트 |
| `KRAFT_DB_NAME` | `kraft_lotto` | DB 이름 |
| `KRAFT_DB_URL` | `jdbc:mariadb://...` | JDBC URL |
| `KRAFT_DB_USER` | `kraft` | DB 사용자 |
| `KRAFT_DB_PASSWORD` | 필수 교체 | DB 비밀번호 |
| `KRAFT_DB_ROOT_PASSWORD` | 필수 교체 | MariaDB root 비밀번호 |
| `KRAFT_ADMIN_USERNAME` | 필수 | 관리자 Basic Auth ID |
| `KRAFT_ADMIN_PASSWORD` | 필수 교체 | 관리자 Basic Auth 비밀번호 |
| `KRAFT_ADMIN_ALLOWED_IP_RANGES` | `127.0.0.1/32,::1/128` | 관리자 API 허용 IP/CIDR 목록 |
| `KRAFT_API_CLIENT` | `mock` | `mock`, `dhlottery`, `real` |
| `KRAFT_API_URL` | 동행복권 API URL | 외부 API base URL |
| `KRAFT_API_CONNECT_TIMEOUT_MS` | `2000` | 연결 타임아웃 |
| `KRAFT_API_READ_TIMEOUT_MS` | `3000` | 읽기 타임아웃 |
| `KRAFT_API_MAX_RETRIES` | `2` | 외부 API 재시도 횟수 |
| `KRAFT_API_RETRY_BACKOFF_MS` | `200` | 재시도 대기 시간 |
| `KRAFT_RECOMMEND_MAX_ATTEMPTS` | `100000` | 추천 생성 최대 시도 횟수 |
| `KRAFT_RECOMMEND_RATE_LIMIT_MAX_REQUESTS` | `30` | IP당 요청 허용 수 |
| `KRAFT_RECOMMEND_RATE_LIMIT_WINDOW_SECONDS` | `60` | 레이트 리밋 윈도우 |
| `KRAFT_LOG_PATH` | `./logs` | 로그 파일 출력 경로 |

`.env`는 Git 추적 대상이 아닙니다. 실제 비밀값은 `.env`, 배포 secret, 또는 런타임 환경 변수로 주입합니다.

---

## 프로파일 정책

| 프로파일 | DataSource | 외부 API | 비고 |
|:---|:---|:---|:---|
| `local` | MariaDB, env 기반 | `mock` 기본 | 개발 편의 설정, 정적 리소스 캐시 비활성화 |
| `prod` | MariaDB, env 필수 | 명시값 권장 | 운영용, 비밀값 기본값 없음 |
| `test` | H2 MySQL mode | `mock` | 단위/슬라이스 테스트 |
| `it` | Testcontainers MariaDB | `mock` | Persistence 통합 테스트 |

---

## API 레퍼런스

모든 API 응답은 envelope 형식입니다.

```json
{
  "success": true,
  "data": {},
  "error": null
}
```

### 추천

```bash
curl -X POST http://localhost:8080/api/recommend \
  -H "Content-Type: application/json" \
  -d '{"count":5}'

curl http://localhost:8080/api/recommend/rules
```

추천 API는 IP 기준 슬라이딩 윈도우 레이트 리밋을 적용합니다.

### 당첨번호 조회

```bash
curl http://localhost:8080/api/winning-numbers/latest
curl http://localhost:8080/api/winning-numbers/1100
curl "http://localhost:8080/api/winning-numbers?page=0&size=20"
curl http://localhost:8080/api/winning-numbers/stats/frequency
```

### 관리자 수집 트리거

관리자 API는 Basic Auth, `ROLE_ADMIN`, IP 화이트리스트를 모두 통과해야 합니다.

```bash
curl -u "$KRAFT_ADMIN_USERNAME:$KRAFT_ADMIN_PASSWORD" \
  -X POST http://localhost:8080/api/admin/winning-numbers/refresh \
  -H "Content-Type: application/json" \
  -d '{"targetRound":"1103"}'
```

`targetRound`는 문자열로 받을 수 있지만, 내부 검증에서 양의 회차 형식만 허용합니다. 생략하면 저장된 최신 회차 다음부터 미추첨 응답 전까지 순차 수집합니다.

---

## 보안 운영 메모

- 운영에서는 `KRAFT_ADMIN_USERNAME`, `KRAFT_ADMIN_PASSWORD`, DB 비밀번호를 반드시 외부 secret으로 주입합니다.
- 관리자 API와 `/actuator/metrics/**`는 별도 security chain에서 Basic Auth와 IP 화이트리스트를 적용합니다.
- `KRAFT_ADMIN_ALLOWED_IP_RANGES`에는 CIDR 또는 단일 IP를 콤마로 구분해 넣습니다.
- reverse proxy 뒤에서 운영할 때는 신뢰 가능한 proxy만 실제 client IP를 전달하도록 구성하고, 애플리케이션은 `server.forward-headers-strategy=NATIVE`를 사용합니다.
- 공개 API 중 `/api/recommend/**`에는 `RecommendRateLimitFilter`가 적용됩니다.
- Security headers는 CSP, Referrer Policy, Permissions Policy, Frame Options를 포함합니다.

---

## 에러 코드

| 코드 | HTTP | 의미 |
|:---|:---:|:---|
| `LOTTO_INVALID_COUNT` | 400 | 추천 개수 1~10 범위 초과 |
| `LOTTO_INVALID_NUMBER` | 400 | 유효하지 않은 로또 번호 |
| `LOTTO_INVALID_TARGET_ROUND` | 400 | 수집 대상 회차 오류 |
| `LOTTO_INVALID_PAGE_REQUEST` | 400 | 페이지 파라미터 오류 |
| `REQUEST_VALIDATION_ERROR` | 400 | 요청 값 검증 실패 |
| `LOTTO_GENERATION_TIMEOUT` | 503 | 추천 생성 시도 한도 초과 |
| `WINNING_NUMBER_NOT_FOUND` | 404 | 해당 회차 당첨번호 없음 |
| `EXTERNAL_API_FAILURE` | 502 | 외부 API 호출 실패 |
| `COLLECT_FAILED` | 500 | 당첨번호 수집 실패 |
| `UNAUTHORIZED_ADMIN` | 401 | 관리자 인증 실패 |
| `FORBIDDEN_ADMIN_IP` | 403 | 허용되지 않은 관리자 접근 IP |
| `TOO_MANY_REQUESTS` | 429 | 추천 API 요청 한도 초과 |
| `INTERNAL_SERVER_ERROR` | 500 | 처리되지 않은 서버 오류 |

---

## 데이터 모델

마이그레이션은 Flyway가 담당하고, JPA는 `ddl-auto=validate`로 스키마를 검증합니다.

```sql
CREATE TABLE winning_numbers (
    round INT NOT NULL PRIMARY KEY,
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
    created_at DATETIME NOT NULL
);
```

DB CHECK 제약과 도메인 객체 불변식이 함께 번호 범위, 정렬, 보너스 번호 중복, 금액/인원 음수 여부를 방어합니다.

---

## 테스트

```powershell
.\gradlew.bat test
```

주요 검증 범위는 도메인 규칙, 추천 서비스, 외부 API client, Web MVC, 관리자 보안, 레이트 리밋, JPA/Flyway 통합, ArchUnit 의존성 규칙입니다.

Persistence IT는 Testcontainers MariaDB를 사용합니다. Docker가 없는 환경에서는 해당 테스트가 자동 비활성화되도록 구성되어 있어 단위 테스트는 계속 실행됩니다.

---

> Built with care by [portuna85](https://github.com/portuna85)
