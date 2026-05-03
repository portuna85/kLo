<div align="center">

# KraftLotto

**로또 6/45 편향 회피형 추천 서비스**

당첨 확률을 높이는 도구가 아닙니다.  
생일 편중 · 등차수열 · 연속번호 · 단일 십의자리 · 과거 1등 조합을 **회피**하기 위한 Spring Boot 백엔드입니다.

<p>
  <img src="https://img.shields.io/badge/Java-25_LTS-007396?style=flat-square&logo=openjdk&logoColor=white"/>
  <img src="https://img.shields.io/badge/Spring_Boot-4.0.5-6DB33F?style=flat-square&logo=springboot&logoColor=white"/>
  <img src="https://img.shields.io/badge/MariaDB-11.8_LTS-003545?style=flat-square&logo=mariadb&logoColor=white"/>
  <img src="https://img.shields.io/badge/Flyway-validate-CC0200?style=flat-square&logo=flyway&logoColor=white"/>
  <img src="https://img.shields.io/badge/Gradle-Kotlin_DSL-02303A?style=flat-square&logo=gradle&logoColor=white"/>
  <img src="https://img.shields.io/badge/Docker-Compose-2496ED?style=flat-square&logo=docker&logoColor=white"/>
  <img src="https://img.shields.io/badge/ArchUnit-enforced-8A2BE2?style=flat-square"/>
</p>

</div>

---

> [!IMPORTANT]
> KraftLotto 는 **당첨 확률을 높이지 않습니다.**  
> 통계적으로 분명하게 편향된 조합을 걸러내는 **백엔드 학습용 MVP** 입니다.

---

## 목차

1. [기술 스택](#1-기술-스택)
2. [아키텍처](#2-아키텍처)
3. [추천 규칙](#3-추천-규칙)
4. [빠른 시작](#4-빠른-시작)
5. [환경 변수](#5-환경-변수)
6. [프로파일 정책](#6-프로파일-정책)
7. [API 레퍼런스](#7-api-레퍼런스)
8. [에러 코드](#8-에러-코드)
9. [데이터 모델](#9-데이터-모델)
10. [테스트 전략](#10-테스트-전략)
11. [운영 메모](#11-운영-메모)

---

## 1. 기술 스택

| 레이어 | 기술 |
|--------|------|
| 언어 / 런타임 | Java **25 LTS** · Eclipse Temurin |
| 프레임워크 | Spring Boot **4.0.5** — Web · Validation · Actuator · Security · Thymeleaf |
| 퍼시스턴스 | Spring Data JPA (`ddl-auto=validate`) · **Flyway** · MariaDB **11.8 LTS** |
| 외부 통신 | `RestClient` + Jackson — `DhLotteryApiClient` / `MockLottoApiClient` |
| 문서 | Spring REST Docs + Asciidoctor (`/docs/index.html`) |
| 빌드 | Gradle Kotlin DSL · 멀티스테이지 Dockerfile (JRE 25, 비루트) |
| 테스트 | JUnit 5 · Mockito · Spring Security Test · **Testcontainers** · **ArchUnit** |
| 프론트엔드 | Bootstrap 5.3 · Bootstrap Icons · Vanilla JS (IIFE, Logger, AbortController) |

---

## 2. 아키텍처

**Feature-First** 로 도메인을 수직 분할하고, 각 feature 내부는 **Hexagonal-lite** 레이어를 따릅니다.

```
com.kraft.lotto
│
├─ feature/
│  ├─ recommend/                          🎯 추천 도메인
│  │   ├─ domain/      ExclusionRule × 5종, PastWinningCache
│  │   ├─ application/ RecommendService, LottoRecommender, CacheLoader
│  │   └─ web/         RecommendController (+dto)
│  │
│  └─ winningnumber/                      🔎 당첨번호 도메인
│      ├─ domain/      LottoCombination, WinningNumber (POJO 불변식)
│      ├─ application/ Collect/Query Service, LottoApiClient(port)
│      ├─ infrastructure/ JPA Entity, Repository, Mapper
│      ├─ event/       WinningNumbersCollectedEvent
│      └─ web/         WinningNumberController, AdminWinningNumberController (+dto)
│
├─ support/            ApiResponse, ApiError, ErrorCode, GlobalExceptionHandler
└─ infra/
   ├─ config/          @ConfigurationProperties (admin/api/recommend/rate-limit)
   └─ security/        SecurityConfig, RecommendRateLimitFilter
```

### 의존성 규칙 (ArchUnit 빌드 타임 강제)

```
web ──► application ──► domain
              │
              └──► infrastructure (JPA)

✘  domain  →  Spring / JPA / Web
✘  web     →  feature.*.infrastructure
✓  @Entity ⊂  feature.*.infrastructure
```

---

## 3. 추천 규칙

`LottoRecommender` 가 후보 조합을 생성하면 아래 규칙이 **순서대로** 평가됩니다.  
하나라도 매칭되면 조합을 폐기하고 재시도합니다. (가벼운 패턴 → 비용 큰 캐시 순)

| # | 규칙 | 제외 조건 | 예시 |
|---|------|-----------|------|
| 1 | `BirthdayBiasRule` | 6개 모두 ≤ 31 | `1·7·13·22·29·31` |
| 2 | `ArithmeticSequenceRule` | 동일 공차의 완전 등차수열 | `3·6·9·12·15·18` |
| 3 | `LongRunRule` | 5개 이상 연속 | `10·11·12·13·14·40` |
| 4 | `SingleDecadeRule` | 한 십의자리 버킷에 5개 이상 | `1·3·5·7·9·40` |
| 5 | `PastWinningRule` | 과거 1등 조합과 완전 동일 | DB 전체 회차 |

> [!NOTE]
> 캐시는 기동 시 1회 적재되고, `WinningNumbersCollectedEvent` 로 수집 완료 후 자동 재적재됩니다.  
> 시도 횟수가 `KRAFT_RECOMMEND_MAX_ATTEMPTS` 를 초과하면 `LOTTO_GENERATION_TIMEOUT` (HTTP 503) 을 반환합니다.

---

## 4. 빠른 시작

### 🐳 Docker Compose (권장)

```bash
cp .env.example .env
# KRAFT_ADMIN_PASSWORD / KRAFT_DB_PASSWORD 등 비밀값을 반드시 교체하세요.

docker compose up --build
```

| 서비스 | 주소 |
|--------|------|
| 애플리케이션 | http://localhost:8080 |
| MariaDB | `localhost:3306` (DB: `kraft_lotto`, 사용자: `kraft`) |

### 💻 로컬 개발 (PowerShell)

```powershell
$env:KRAFT_DB_URL      = "jdbc:mariadb://localhost:3306/kraft_lotto"
$env:KRAFT_DB_USER     = "kraft"
$env:KRAFT_DB_PASSWORD = "kraft"
$env:SPRING_PROFILES_ACTIVE = "local"
./gradlew bootRun
```

### 🧪 테스트 / 빌드

```bash
./gradlew test      # 단위 + WebMvc + Security + Persistence(IT) + ArchUnit
./gradlew build     # 전체 검증
./gradlew bootJar   # 실행 가능 JAR
```

> [!TIP]
> Persistence IT(`WinningNumberRepositoryIT`)는 Testcontainers MariaDB를 사용합니다.  
> Docker 없이도 `@Testcontainers(disabledWithoutDocker = true)` 로 자동 비활성화되므로 단위 테스트는 항상 실행됩니다.

---

## 5. 환경 변수

| 변수 | 기본값 | 설명 |
|------|--------|------|
| `SPRING_PROFILES_ACTIVE` | — | `local` · `prod` · `test` · `it` |
| `KRAFT_DB_URL` | `jdbc:mariadb://localhost:3306/kraft_lotto` | JDBC URL |
| `KRAFT_DB_USER` | `kraft` | DB 사용자 |
| `KRAFT_DB_PASSWORD` | `kraft` | DB 비밀번호 |
| `KRAFT_ADMIN_USERNAME` | **(필수)** | 관리자 Basic Auth ID |
| `KRAFT_ADMIN_PASSWORD` | **(필수)** | 관리자 Basic Auth PW |
| `KRAFT_API_CLIENT` | `mock` | `mock` \| `dhlottery` |
| `KRAFT_API_URL` | `https://www.dhlottery.co.kr/common.do` | 외부 API base URL |
| `KRAFT_API_CONNECT_TIMEOUT_MS` | `2000` | 연결 타임아웃 (ms) |
| `KRAFT_API_READ_TIMEOUT_MS` | `3000` | 읽기 타임아웃 (ms) |
| `KRAFT_API_MAX_RETRIES` | `2` | 네트워크 오류 재시도 횟수 |
| `KRAFT_API_RETRY_BACKOFF_MS` | `200` | 재시도 대기 시간 (ms) |
| `KRAFT_RECOMMEND_MAX_ATTEMPTS` | `100000` | 추천 생성 최대 시도 횟수 |
| `KRAFT_RECOMMEND_RATE_LIMIT_MAX_REQUESTS` | `30` | IP당 허용 요청 수 |
| `KRAFT_RECOMMEND_RATE_LIMIT_WINDOW_SECONDS` | `60` | 슬라이딩 윈도우 크기 (초) |

> [!WARNING]
> 운영(`prod`)에서는 `KRAFT_ADMIN_USERNAME` / `KRAFT_ADMIN_PASSWORD` 의 기본값을 두지 않습니다.  
> 환경변수 또는 secret store 로 반드시 주입해야 합니다.

---

## 6. 프로파일 정책

| 프로파일 | DataSource | 외부 API | 비고 |
|----------|-----------|---------|------|
| `local` | MariaDB (env) | `mock` | 개발 기본값 |
| `prod` | MariaDB (env, 비밀값 필수) | `${KRAFT_API_CLIENT:mock}` | `dhlottery` 명시 권장 |
| `test` | H2 (MySQL 모드) | `mock` | 단위/슬라이스 테스트 |
| `it` | Testcontainers MariaDB | `mock` | Persistence IT |

---

## 7. API 레퍼런스

모든 응답은 단일 envelope 형식입니다.

```jsonc
// 성공
{ "success": true,  "data": { ... }, "error": null }

// 실패
{ "success": false, "data": null,   "error": { "code": "...", "message": "..." } }
```

### 공개 — 추천

```bash
# 추천 조합 생성 (count: 1–10, 기본 5)
curl -X POST http://localhost:8080/api/recommend \
     -H "Content-Type: application/json" \
     -d '{"count": 5}'

# 적용 중인 제외 규칙 목록
curl http://localhost:8080/api/recommend/rules
```

추천 API는 IP당 `KRAFT_RECOMMEND_RATE_LIMIT_MAX_REQUESTS`(기본 30)회/분 제한이 적용됩니다.

### 공개 — 당첨번호 조회

```bash
curl http://localhost:8080/api/winning-numbers/latest
curl http://localhost:8080/api/winning-numbers/1100
curl 'http://localhost:8080/api/winning-numbers?page=0&size=20'  # size 상한 100
curl http://localhost:8080/api/winning-numbers/stats/frequency   # 1~45 출현 빈도
```

### 관리자 — 수집 트리거 (Basic Auth + `ROLE_ADMIN`)

```bash
curl -u "$KRAFT_ADMIN_USERNAME:$KRAFT_ADMIN_PASSWORD" \
     -X POST http://localhost:8080/api/admin/winning-numbers/refresh \
     -H "Content-Type: application/json" \
     -d '{"targetRound": 1103}'   # 생략 시 미추첨 회차까지 자동 수집
```

```json
{
  "success": true,
  "data": { "collected": 3, "skipped": 1, "failed": 0, "latestRound": 1103 },
  "error": null
}
```

### 보안 매트릭스

| Endpoint | 접근 권한 |
|----------|-----------|
| `/api/recommend/**` | 공개 |
| `/api/winning-numbers/**` | 공개 |
| `/actuator/health` | 공개 |
| `/docs/**` | 공개 |
| `/api/admin/**` | Basic Auth + `ROLE_ADMIN` |
| `/actuator/metrics/**` | Basic Auth + `ROLE_ADMIN` |

---

## 8. 에러 코드

| 코드 | HTTP | 의미 |
|------|:----:|------|
| `LOTTO_INVALID_COUNT` | 400 | 추천 개수 1–10 범위 초과 |
| `LOTTO_INVALID_NUMBER` | 400 | 유효하지 않은 로또 번호 |
| `LOTTO_INVALID_TARGET_ROUND` | 400 | targetRound 가 1 미만 |
| `LOTTO_INVALID_PAGE_REQUEST` | 400 | 페이지 파라미터 오류 |
| `REQUEST_VALIDATION_ERROR` | 400 | 요청 값 유효성 오류 |
| `LOTTO_GENERATION_TIMEOUT` | 503 | 추천 생성 시도 한도 초과 |
| `WINNING_NUMBER_NOT_FOUND` | 404 | 해당 회차 당첨번호 없음 |
| `EXTERNAL_API_FAILURE` | 502 | 외부 API 호출 실패 |
| `TOO_MANY_REQUESTS` | 429 | 추천 API 요청 한도 초과 |
| `UNAUTHORIZED_ADMIN` | 401 | 관리자 인증 실패 |
| `INTERNAL_SERVER_ERROR` | 500 | 처리되지 않은 예외 |

---

## 9. 데이터 모델

마이그레이션은 **Flyway** (`db/migration/V1__init_winning_numbers.sql`)가 담당하며, JPA는 `ddl-auto=validate` 로 검증만 수행합니다.

```sql
CREATE TABLE winning_numbers (
    round         INT      NOT NULL PRIMARY KEY,  -- CHECK > 0
    draw_date     DATE     NOT NULL,
    n1..n6        INT      NOT NULL,              -- CHECK 1≤n≤45, n1<n2<…<n6
    bonus_number  INT      NOT NULL,              -- 본번호와 중복 금지
    first_prize   BIGINT   NOT NULL,              -- ≥ 0
    first_winners INT      NOT NULL,              -- ≥ 0
    total_sales   BIGINT   NOT NULL,              -- ≥ 0
    created_at    DATETIME NOT NULL
);
-- + 14개 CHECK 제약, draw_date 인덱스
```

도메인 `LottoCombination` · `WinningNumber` 가 동일한 불변식을 POJO 단계에서 한 번 더 강제합니다.  
_다중 방어: Domain → JPA Validation → Database CHECK_

---

## 10. 테스트 전략

| 레이어 | 도구 | 포커스 |
|--------|------|--------|
| 도메인 단위 | JUnit 5 | `LottoCombination`, `WinningNumber`, 5종 규칙 |
| 애플리케이션 | JUnit 5 + Mockito | `RecommendService`, Collect/Query Service, `DhLotteryApiClient` |
| 인프라 필터 | JUnit 5 + Mockito | `RecommendRateLimitFilter` (허용/차단/동시성/용량 초과) |
| WebMvc 슬라이스 | `@WebMvcTest` + `MockitoBean` | 컨트롤러 + envelope + 상태 코드 매핑 |
| Security 통합 | `@SpringBootTest` + 실제 필터 체인 | 401/200 흐름, ADMIN 권한 |
| Persistence IT | Testcontainers MariaDB + Flyway | Repository + CHECK 제약 + 페이지네이션 |
| 아키텍처 | ArchUnit | `domain ↛ Spring/JPA`, `web ↛ infrastructure` |

**컨벤션:** 메서드명 영어 camelCase · `@DisplayName` 한글 한 줄 명세

---

## 11. 운영 메모

- **비밀값** (관리자 PW, DB PW)은 코드/이미지에 포함하지 않고 env 또는 secret store 로 주입합니다.
- 운영에서는 `KRAFT_API_CLIENT=dhlottery` 를 명시하세요. 장애 시 `EXTERNAL_API_FAILURE` 로 envelope 처리됩니다.
- `POST /api/admin/winning-numbers/refresh` 완료 후 `WinningNumbersCollectedEvent` 가 발행되어 `PastWinningCache` 가 자동 갱신됩니다.
- DB 스키마 변경은 Flyway 마이그레이션(`V{n}__*.sql`)으로만 진행합니다.
- `/actuator/metrics/**` 는 ADMIN 인증이 필요합니다.
- `LOTTO_GENERATION_TIMEOUT` 이 반복되면 규칙 강도 또는 `KRAFT_RECOMMEND_MAX_ATTEMPTS` 를 재검토하세요.
- 추천 API 레이트 리밋(`RecommendRateLimitFilter`)은 슬라이딩 윈도우 방식이며, IP 추적 용량(50,000개)을 초과하면 신규 IP는 즉시 429를 받습니다.

---

<div align="center">

**KraftLotto** — _당첨이 아니라 회피로._

`Java 25` · `Spring Boot 4` · `MariaDB 11.8`

</div>
