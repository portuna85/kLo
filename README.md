<div align="center">

<img src="https://img.shields.io/badge/🎰-KraftLotto-1f6feb?style=for-the-badge&labelColor=0d1117" alt="KraftLotto" height="42"/>

# KraftLotto

### _로또 6/45 — 편향 회피형 추천 백엔드_

당첨 확률을 **높이는** 도구가 아닙니다.<br/>
통계적으로 명백히 편향된 조합 — 생일 편중 · 등차수열 · 연속번호 · 단일 십의자리 · 과거 1등 — 을 **회피**하기 위한 Spring Boot MVP 입니다.

<br/>

<p>
  <img src="https://img.shields.io/badge/Java-25%20LTS-007396?style=flat-square&logo=openjdk&logoColor=white"/>
  <img src="https://img.shields.io/badge/Spring%20Boot-4.0.5-6DB33F?style=flat-square&logo=springboot&logoColor=white"/>
  <img src="https://img.shields.io/badge/MariaDB-11.8%20LTS-003545?style=flat-square&logo=mariadb&logoColor=white"/>
  <img src="https://img.shields.io/badge/Flyway-validate-CC0200?style=flat-square&logo=flyway&logoColor=white"/>
  <img src="https://img.shields.io/badge/Gradle-Kotlin%20DSL-02303A?style=flat-square&logo=gradle&logoColor=white"/>
  <img src="https://img.shields.io/badge/Docker-Compose-2496ED?style=flat-square&logo=docker&logoColor=white"/>
  <img src="https://img.shields.io/badge/Tests-100%2B%20JUnit%205-25A162?style=flat-square&logo=junit5&logoColor=white"/>
  <img src="https://img.shields.io/badge/ArchUnit-enforced-8A2BE2?style=flat-square"/>
</p>

<sub>_“당첨이 아니라 회피로.”_ · Feature-First × Hexagonal-lite · ApiResponse Envelope · Testcontainers</sub>

</div>

---

> [!IMPORTANT]
> KraftLotto 는 **확률을 높이지 않습니다.**
> 통계적으로 분명하게 편향된 조합을 걸러내는 **백엔드 학습용 MVP** 입니다.

<br/>

## ✨ 한눈에 보기

| 영역 | 한 줄 요약 |
| :--- | :--- |
| 🎯 **추천 엔진** | 5가지 편향 회피 규칙으로 후보 필터링 (`LottoRecommender`) |
| 🔎 **회차 조회** | 최신 / 회차별 / 페이지 + `1~45` 출현 빈도 통계 |
| 🔐 **관리자 API** | Basic Auth + `ROLE_ADMIN` 으로 수동 수집 트리거 |
| 📡 **외부 연동** | 동행복권 (`dhlottery`) 또는 결정론적 `mock` — 토큰 교체로 스왑 |
| 🧱 **아키텍처** | Feature-First × Hexagonal-lite, **ArchUnit 으로 빌드 타임 강제** |
| 📦 **응답 포맷** | `ApiResponse<T>` 단일 envelope (`{ success, data, error }`) |
| 🛡️ **다중 방어** | 도메인 POJO 불변식 + JPA Validation + DB `CHECK` 제약 |

<br/>

## 🧭 목차

| | | |
| :-- | :-- | :-- |
| 1 · [기술 스택](#1-기술-스택)         | 5 · [환경 변수](#5-환경-변수)        | 9 · [데이터 모델](#9-데이터-모델--마이그레이션) |
| 2 · [아키텍처](#2-아키텍처--패키지-구조) | 6 · [프로파일 정책](#6-프로파일-정책)  | 10 · [테스트 전략](#10-테스트-전략) |
| 3 · [추천 규칙](#3-추천-규칙-카탈로그)   | 7 · [API 레퍼런스](#7-api-레퍼런스)  | 11 · [운영 메모](#11-운영-메모) |
| 4 · [빠른 시작](#4-빠른-시작)         | 8 · [에러 코드](#8-에러-코드-표)     | 12 · [디렉터리 트리](#12-디렉터리-트리) |

<br/>

## 1. 기술 스택

<table>
<tr><th align="left" width="180">레이어</th><th align="left">사용 기술</th></tr>
<tr><td>🟦 언어 / 런타임</td><td>Java <strong>25 LTS</strong> · Eclipse Temurin</td></tr>
<tr><td>🟩 프레임워크</td><td>Spring Boot <strong>4.0.5</strong> · Web · Validation · Actuator · Security</td></tr>
<tr><td>🟧 퍼시스턴스</td><td>Spring Data JPA <code>ddl-auto=validate</code> · <strong>Flyway</strong> · MariaDB <strong>11.8 LTS</strong></td></tr>
<tr><td>🟪 외부 통신</td><td><code>RestClient</code> + Jackson — <code>DhLotteryApiClient</code> / <code>MockLottoApiClient</code></td></tr>
<tr><td>🟨 문서</td><td>Spring REST Docs + Asciidoctor 정적 문서 (<code>/docs/index.html</code>)</td></tr>
<tr><td>⬛ 빌드</td><td>Gradle Kotlin DSL · Wrapper 9.x · 멀티스테이지 Dockerfile (JRE 25, 비루트)</td></tr>
<tr><td>🟥 테스트</td><td>JUnit 5 · Mockito · Spring Security Test · <strong>Testcontainers</strong> (MariaDB) · <strong>ArchUnit</strong></td></tr>
</table>

<br/>

## 2. 아키텍처 & 패키지 구조

**Feature-First** 로 도메인을 수직 분할하고, 각 feature 내부는 **Hexagonal-lite** 레이어를 따릅니다.

```text
com.kraft.lotto
│
├─ feature/
│  │
│  ├─ recommend/                          🎯 추천 도메인
│  │   ├─ domain/         ─ ExclusionRule + 5종 구현 + PastWinningCache
│  │   ├─ application/    ─ RecommendService · LottoRecommender · CacheLoader
│  │   └─ web/  (+dto/)   ─ RecommendController
│  │
│  └─ winningnumber/                      🔎 당첨번호 도메인
│      ├─ domain/         ─ LottoCombination · WinningNumber  (POJO 불변식)
│      ├─ application/    ─ Collect/Query Service · LottoApiClient(port)
│      │                    └ MockLottoApiClient · DhLotteryApiClient
│      ├─ infrastructure/ ─ JPA Entity · Repository · Mapper
│      ├─ event/          ─ WinningNumbersCollectedEvent
│      └─ web/  (+dto/)   ─ WinningNumberController · AdminWinningNumberController
│
├─ support/                               📦 ApiResponse · ApiError · ErrorCode
│                                            BusinessException · GlobalExceptionHandler
│
└─ infra/
   ├─ config/                             ⚙️  @ConfigurationProperties (admin/api/recommend)
   └─ security/                           🛡️  SecurityConfig · AdminAuthenticationEntryPoint
```

### 🛡️ 강제되는 의존성 규칙 — `ArchitectureTest`

```
┌─────────────────────────  의존성 방향  ─────────────────────────┐
│                                                                 │
│       web  ────►  application  ────►  domain                    │
│                          │                                      │
│                          └──────►  infrastructure  (JPA)        │
│                                                                 │
│   ✘  domain   →   Spring / JPA / Web / Hibernate                │
│   ✘  domain   →   support.BusinessException                     │
│   ✘  web      →   feature.*.infrastructure                      │
│   ✓  @Entity  ⊂   feature.*.infrastructure                      │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

<sub>위 4가지 룰은 빌드 시 ArchUnit 이 자동 검증합니다.</sub>

<br/>

## 3. 추천 규칙 카탈로그

`LottoRecommender` 가 후보 조합을 만들면, 아래 규칙이 **선언된 순서대로** 평가되어 **하나라도 매칭되면 폐기**됩니다.
가벼운 패턴 → 비용 큰 캐시 검색 순으로 정렬되어 있습니다.

<table>
<tr>
  <th>#</th><th align="left">규칙</th><th align="left">제외 조건</th><th align="left">예시</th>
</tr>
<tr>
  <td align="center">1️⃣</td>
  <td><code>BirthdayBiasRule</code></td>
  <td>6개 모두 <code>≤ 31</code></td>
  <td><code>1·7·13·22·29·31</code></td>
</tr>
<tr>
  <td align="center">2️⃣</td>
  <td><code>ArithmeticSequenceRule</code></td>
  <td>동일 공차의 완전 등차수열</td>
  <td><code>3·6·9·12·15·18</code></td>
</tr>
<tr>
  <td align="center">3️⃣</td>
  <td><code>LongRunRule</code></td>
  <td>5개 이상 연속</td>
  <td><code>10·11·12·13·14·40</code></td>
</tr>
<tr>
  <td align="center">4️⃣</td>
  <td><code>SingleDecadeRule</code></td>
  <td>한 십의자리 버킷에 5개 이상<br/><sub>(<code>1–9</code>·<code>10–19</code>·<code>20–29</code>·<code>30–39</code>·<code>40–45</code>)</sub></td>
  <td><code>1·3·5·7·9·40</code></td>
</tr>
<tr>
  <td align="center">5️⃣</td>
  <td><code>PastWinningRule</code></td>
  <td>과거 1등 조합과 완전 동일<br/><sub>(<code>PastWinningCache</code>)</sub></td>
  <td>DB 의 모든 회차 조합</td>
</tr>
</table>

> [!NOTE]
> 캐시는 애플리케이션 기동 시 1회 적재되고, 수집 완료 후 발행되는 `WinningNumbersCollectedEvent` 로 자동 재적재됩니다.
> 시도 횟수가 `KRAFT_RECOMMEND_MAX_ATTEMPTS` 를 초과하면 `LOTTO_GENERATION_TIMEOUT` (HTTP **503**) 으로 응답합니다.

<br/>

## 4. 빠른 시작

### 🐳 4.1 Docker Compose · _권장_

```bash
cp .env.example .env
# .env 안의 KRAFT_ADMIN_PASSWORD / KRAFT_DB_PASSWORD 등 비밀값을 반드시 교체하세요.

docker compose up --build
```

| 서비스 | 주소 |
| :--- | :--- |
| 🌐 애플리케이션 | http://localhost:8080 |
| 🗄️ MariaDB | `localhost:3306` (DB `kraft_lotto`, 사용자 `kraft`) |

### 💻 4.2 로컬 개발 · _수동_ (PowerShell 예시)

```powershell
# 1) MariaDB 가 떠 있다고 가정
$env:KRAFT_DB_URL      = "jdbc:mariadb://localhost:3306/kraft_lotto"
$env:KRAFT_DB_USER     = "kraft"
$env:KRAFT_DB_PASSWORD = "kraft"

# 2) local 프로파일 (Mock 외부 API + REST Docs 정적 문서)
$env:SPRING_PROFILES_ACTIVE = "local"
./gradlew bootRun
```

📖 API Docs · <http://localhost:8080/docs/index.html>

### 🧪 4.3 테스트 / 빌드

```bash
./gradlew test           # 단위 + WebMvc + Persistence(IT) + ArchUnit
./gradlew build          # 전체 검증
./gradlew bootJar        # 실행 가능 JAR
```

> [!TIP]
> Persistence IT (`WinningNumberRepositoryIT`) 는 Testcontainers MariaDB 를 사용합니다.
> Docker 가 없으면 `@Testcontainers(disabledWithoutDocker = true)` 로 자동 비활성화되므로,
> 일반 단위/슬라이스 테스트는 **Docker 없이도** 정상 실행됩니다.

<br/>

## 5. 환경 변수

| 변수 | 기본값 | 설명 |
| :--- | :--- | :--- |
| `SPRING_PROFILES_ACTIVE` | _(없음)_ | `local` · `prod` · `test` · `it` |
| `KRAFT_DB_URL` | `jdbc:mariadb://localhost:3306/kraft_lotto` | JDBC URL |
| `KRAFT_DB_USER` | `kraft` | DB 사용자 |
| `KRAFT_DB_PASSWORD` | `kraft` | DB 비밀번호 |
| `KRAFT_ADMIN_USERNAME` | _(없음, 필수)_ | 관리자 Basic Auth ID |
| `KRAFT_ADMIN_PASSWORD` | _(없음, 필수)_ | 관리자 Basic Auth PW |
| `KRAFT_API_CLIENT` | `mock` | **`mock`** \| **`dhlottery`** — 그 외 값은 `mock` 으로 폴백 |
| `KRAFT_API_URL` | `https://www.dhlottery.co.kr/common.do` | 외부 API base URL |
| `KRAFT_API_CONNECT_TIMEOUT_MS` | `2000` | 외부 API 연결 타임아웃(ms) |
| `KRAFT_API_READ_TIMEOUT_MS` | `3000` | 외부 API 응답 읽기 타임아웃(ms) |
| `KRAFT_API_MAX_RETRIES` | `2` | 외부 API 네트워크 오류 재시도 횟수 |
| `KRAFT_API_RETRY_BACKOFF_MS` | `200` | 재시도 간 대기 시간(ms) |
| `KRAFT_RECOMMEND_MAX_ATTEMPTS` | `100000` | 추천 생성 최대 시도 횟수 |

> [!WARNING]
> 운영(`prod`)에서는 `KRAFT_ADMIN_USERNAME` / `KRAFT_ADMIN_PASSWORD` 의 **기본값을 두지 않습니다.**
> 환경변수 또는 secret store 로 **반드시** 주입해야 합니다.

<br/>

## 6. 프로파일 정책

| 프로파일 | DataSource | 외부 API 기본 | API 문서 | Actuator 노출 |
| :--- | :--- | :--- | :---: | :--- |
| 🟢 **`local`** | MariaDB (env)              | `mock`                       | ✅ | `health`, `info`, `metrics` |
| 🔴 **`prod`**  | MariaDB (env, 비밀값 필수) | `${KRAFT_API_CLIENT:mock}` (운영에서는 `dhlottery` 명시 권장) | ✅ (`/docs/index.html`) | `health`, `info` |
| 🟡 **`test`**  | H2 (MySQL 모드)            | `mock`                       | n/a | n/a |
| 🟣 **`it`**    | Testcontainers MariaDB     | `mock`                       | n/a | n/a |

<sub>프로파일별 별도 파일(<code>application-prod.yml</code>)은 제거되었고, 공통 <code>application.yml</code> + 환경변수 주입으로 운영 구성을 관리합니다. 운영에서 동행복권 API를 사용하려면 <code>KRAFT_API_CLIENT=dhlottery</code> 를 명시하세요.</sub>

<sub>📈 <code>/actuator/metrics/**</code> 는 ADMIN Basic Auth 가 필요합니다.</sub>

<br/>

## 7. API 레퍼런스

모든 응답은 단일 envelope 입니다.

```jsonc
// ✅ 성공
{ "success": true,  "data": { "...": "..." }, "error": null }

// ❌ 실패
{ "success": false, "data": null, "error": { "code": "ERROR_CODE", "message": "..." } }
```

### 🟢 공개 — 추천

<details open>
<summary><strong><code>POST /api/recommend</code></strong> &nbsp;·&nbsp; 추천 조합 생성</summary>

```bash
curl -X POST http://localhost:8080/api/recommend \
     -H "Content-Type: application/json" \
     -d '{"count": 5}'
```

| 필드 | 타입 | 비고 |
| :--- | :--- | :--- |
| `count` | `int?` | 1–10 (기본 5, 본문 생략 가능) |

```json
{
  "success": true,
  "data": {
    "combinations": [
      { "numbers": [3, 11, 22, 27, 35, 41] },
      { "numbers": [5, 12, 19, 28, 33, 44] }
    ]
  },
  "error": null
}
```
</details>

<details>
<summary><strong><code>GET /api/recommend/rules</code></strong> &nbsp;·&nbsp; 적용 중인 제외 규칙 목록</summary>

```bash
curl http://localhost:8080/api/recommend/rules
```
</details>

### 🟢 공개 — 당첨번호 조회

```bash
curl http://localhost:8080/api/winning-numbers/latest
curl http://localhost:8080/api/winning-numbers/1100
curl 'http://localhost:8080/api/winning-numbers?page=0&size=20'   # size 상한 100
curl http://localhost:8080/api/winning-numbers/stats/frequency    # 1~45 출현 빈도
```

### 🔴 관리자 — 수집 트리거 · _Basic Auth + `ROLE_ADMIN`_

```bash
curl -u "$KRAFT_ADMIN_USERNAME:$KRAFT_ADMIN_PASSWORD" \
     -X POST http://localhost:8080/api/admin/winning-numbers/refresh \
     -H "Content-Type: application/json" \
     -d '{"targetRound": 1103}'   # targetRound 생략 시 미추첨 회차까지 자동 수집
```

```json
{
  "success": true,
  "data": { "collected": 3, "skipped": 1, "failed": 0, "latestRound": 1103 },
  "error": null
}
```

### 🔐 보안 매트릭스

| Endpoint | 접근 |
| :--- | :--- |
| `/api/recommend/**`                  | 🟢 공개 |
| `/api/winning-numbers/**`            | 🟢 공개 |
| `/actuator/health`                   | 🟢 공개 |
| `/docs/**`                           | 🟢 공개 |
| `/api/admin/**`                      | 🔴 Basic + `ROLE_ADMIN` |
| `/actuator/metrics/**`               | 🔴 Basic + `ROLE_ADMIN` |

<sub>인증 실패 시에도 <code>ApiResponse</code> 실패 형식 (<code>UNAUTHORIZED_ADMIN</code>, HTTP 401) 으로 통일됩니다.</sub>

<br/>

## 8. 에러 코드 표

| 코드 | HTTP | 의미 |
| :--- | :---: | :--- |
| `LOTTO_INVALID_COUNT`        | `400` | 추천 개수가 1–10 범위를 벗어남 |
| `LOTTO_INVALID_NUMBER`       | `400` | 유효하지 않은 로또 번호 |
| `LOTTO_GENERATION_TIMEOUT`   | `503` | 시도 한도(`KRAFT_RECOMMEND_MAX_ATTEMPTS`) 초과 |
| `WINNING_NUMBER_NOT_FOUND`   | `404` | 요청 회차의 당첨번호 없음 |
| `EXTERNAL_API_FAILURE`       | `502` | 외부 API 호출 실패 |
| `COLLECT_FAILED`             | `500` | 수집 작업 실패 |
| `UNAUTHORIZED_ADMIN`         | `401` | 관리자 인증 실패 |
| `INTERNAL_SERVER_ERROR`      | `500` | 처리되지 않은 예외 |

<sub><code>GlobalExceptionHandler</code> 가 <code>BusinessException</code> · <code>MethodArgumentNotValidException</code> · <code>ConstraintViolationException</code> · <code>IllegalArgumentException</code> · Spring Security 예외를 모두 envelope 로 변환합니다.</sub>

<br/>

## 9. 데이터 모델 & 마이그레이션

마이그레이션은 **Flyway** (`src/main/resources/db/migration/V1__init_winning_numbers.sql`) 가 담당하며,
JPA 는 `ddl-auto=validate` 로 **검증만** 수행합니다.

```sql
CREATE TABLE winning_numbers (
    round         INT      NOT NULL PRIMARY KEY,
    draw_date     DATE     NOT NULL,
    n1..n6        INT      NOT NULL,           -- 본번호 6개 (오름차순 강제)
    bonus_number  INT      NOT NULL,           -- 본번호와 중복 금지
    first_prize   BIGINT   NOT NULL,           -- ≥ 0
    first_winners INT      NOT NULL,           -- ≥ 0
    total_sales   BIGINT   NOT NULL,           -- ≥ 0
    created_at    DATETIME NOT NULL
);
-- + 14개의 CHECK 제약 (1..45 범위 / n1<n2<...<n6 / bonus 비중복 / 음수 금지)
-- + draw_date 인덱스
```

> [!NOTE]
> 도메인 `LottoCombination` · `WinningNumber` 가 동일한 불변식을 **POJO 단계** 에서 한 번 더 강제합니다.
> _다중 방어 — Domain → JPA → Database CHECK._

<br/>

## 10. 테스트 전략

| 레이어 | 도구 | 포커스 |
| :--- | :--- | :--- |
| 🧬 도메인 단위    | JUnit 5                                  | `LottoCombination`, `WinningNumber`, 5종 규칙 |
| 🧪 애플리케이션   | JUnit 5 + Mockito                        | `RecommendService`, Collect/Query Service, API Client |
| 🌐 WebMvc 슬라이스 | `@WebMvcTest` + `MockitoBean`            | 컨트롤러 + envelope + 상태 코드 매핑 |
| 🛡️ Security 통합  | `@SpringBootTest` + 실제 필터 체인        | 401/200 흐름, ADMIN 권한 |
| 🗄️ Persistence IT | Testcontainers MariaDB + Flyway          | Repository + CHECK 제약 + 페이지네이션 |
| 🧱 아키텍처       | ArchUnit                                 | `domain ↛ Spring/JPA`, `web ↛ infrastructure` |

**테스트 코드 컨벤션**
- 🧾 메소드명 = **영어** (camelCase)
- 🇰🇷 `@DisplayName` = **한글** 한 줄 명세
- 🗂️ 클래스에도 `@DisplayName` 부여 → IDE/리포트에서 한글 그루핑

<br/>

## 11. 운영 메모

- 🔑 **비밀값**(관리자 PW, DB PW)은 코드/이미지에 하드코딩하지 마세요. env 또는 secret store 로 주입합니다.
- 🌐 운영은 `KRAFT_API_CLIENT=dhlottery` 권장. 외부 API 장애는 `EXTERNAL_API_FAILURE` 로 envelope 매핑됩니다.
- 🔄 수집(`POST /api/admin/winning-numbers/refresh`) 후 `WinningNumbersCollectedEvent` 가 발행되어 `PastWinningCache` 가 갱신됩니다.
- 🧱 DB 변경은 Flyway 마이그레이션으로만 가능합니다 (`V{n}__*.sql`).
- 📈 `/actuator/metrics` 는 ADMIN 인증이 필요하므로 모니터링 시스템이 자격증명을 보유해야 합니다.
- 🛑 `LOTTO_GENERATION_TIMEOUT` 이 자주 발생하면 규칙 강도/`KRAFT_RECOMMEND_MAX_ATTEMPTS` 를 재검토하세요.

<br/>

## 12. 디렉터리 트리

```text
kraft-lotto/
├─ build.gradle.kts            ─ Spring Boot 4.0.5 / Java 25 / 의존성
├─ settings.gradle.kts         ─ rootProject.name = "kraft-lotto"
├─ Dockerfile                  ─ 멀티스테이지(JDK 25 → JRE 25, 비루트)
├─ docker-compose.yml          ─ MariaDB + 앱 (.env 사용)
├─ .env.example                ─ 필요 환경변수 템플릿
└─ src/
   ├─ main/
   │  ├─ java/com/kraft/lotto/
   │  │  ├─ KraftLottoApplication.java
   │  │  ├─ feature/
   │  │  │  ├─ recommend/      { domain · application · web · web/dto }
   │  │  │  └─ winningnumber/  { domain · application · infrastructure · event · web · web/dto }
   │  │  ├─ infra/             { config · security }
   │  │  └─ support/           { ApiResponse · ErrorCode · BusinessException · GlobalExceptionHandler }
   │  └─ resources/
   │     ├─ application.yml         ─ 공통 기본 + 환경변수 기반 운영/로컬 설정
   │     ├─ application-local.yml   ─ 로컬 개발 오버라이드(mock, 캐시/로그 디버깅)
   │     └─ db/migration/V1__init_winning_numbers.sql
   └─ test/                    ─ 100+ tests (단위 · WebMvc · Security · IT · ArchUnit)
```

---

<div align="center">

### 🎰 **KraftLotto**

_당첨이 아니라 회피로._

<sub>Made with ☕ Java 25 · 🍃 Spring Boot 4 · 🗄️ MariaDB 11.8</sub>

</div>
