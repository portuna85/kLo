# kLo 코드베이스 개선 종합 보고서

> **저장소**: [portuna85/kLo](https://github.com/portuna85/kLo) (`main`)
> **최신 업데이트**: 2026-05-15
> **작업단위 총량**: 60개 (#001~#060) — **P1** 12 / **P2** 23 / **P3** 25
> **범위**: 백엔드 / 프론트엔드 / 테스트 / CI·CD / DB·운영

---

## 📑 목차

1. [한눈에 보기 (Executive Summary)](#1-한눈에-보기-executive-summary)
2. [리포지토리 개요](#2-리포지토리-개요)
3. [백엔드 분석](#3-백엔드-분석)
4. [프론트엔드 분석](#4-프론트엔드-분석)
5. [테스트 및 품질 보증](#5-테스트-및-품질-보증)
6. [코드 품질](#6-코드-품질)
7. [문서 및 온보딩](#7-문서-및-온보딩)
8. [🆕 2026-05-15 추가 코드 리뷰 결과](#8--2026-05-15-추가-코드-리뷰-결과)
9. [우선순위 통합 로드맵 (#001~#060)](#9-우선순위-통합-로드맵-001060)
10. [코드 샘플 및 패치 예시](#10-코드-샘플-및-패치-예시)
11. [권장 도구·라이브러리·CI](#11-권장-도구라이브러리ci)
12. [리뷰 체크리스트 및 PR 템플릿](#12-리뷰-체크리스트-및-pr-템플릿)
13. [실행 명령어 예시](#13-실행-명령어-예시)

---

## 1. 한눈에 보기 (Executive Summary)

### 프로젝트 개요

**kLo**는 **Spring Boot 4** (Java 25) 기반의 로또 번호 조회·추천 서비스다.

| 영역 | 스택 |
|---|---|
| 백엔드 | Spring Boot 4, Java 25, Gradle (Kotlin DSL) |
| DB / 마이그레이션 | MariaDB / MySQL, Flyway |
| 캐시 / 락 | Caffeine, Redis(토글), ShedLock |
| 회복성 / 관측 | Resilience4j, Micrometer, OpenTelemetry, Logstash Encoder |
| 프론트엔드 | Thymeleaf + Bootstrap (Webjars), Vanilla JS |
| 테스트 | JUnit 5, Testcontainers, ArchUnit, REST Docs, Vitest, Playwright |

### 핵심 강점 ✅

- 명확한 `feature` / `domain` / `infra` **계층 분리** + ArchUnit · 계약 테스트 · REST Docs
- **Resilience4j**, **ShedLock**, **Flyway**, **Caffeine / Redis 토글**, **Bean Validation**
- `OncePerRequestFilter` 기반 **토큰 인증 + SHA-256 해시 토큰**, **Rate-limit**, **Prometheus 메트릭**, **MDC 트레이싱**
- **Multi-stage Docker** + healthcheck + non-root + **JVM 가상스레드** + 그레이스풀 셧다운
- 자동 수집/재시도/missing 보충 스케줄, `FailoverLottoApiClient`, `RequiredConfigValidator`로 startup 검증

### 🚨 즉시 조치 필요 — P1 핵심 6선

| 우선 | 항목 | 영향 |
|:---:|---|---|
| **#032** | 캐시 self-invocation 무력화 (`WinningStatisticsService`) | 매 페이지 로드마다 DB 집계 실행 — **사용자 영향 최대** |
| **#034** | CSP × 인라인 스크립트 충돌 (`header.html`) | prod 진입 시 즉시 **FOUC** + 콘솔 위반 |
| **#033** | `@TransactionalEventListener` 미통일 + `readOnly` 누락 | 데이터·캐시 일관성 결함 |
| **#036** | 낙관락 실패가 SKIPPED로 둔갑 (`WinningNumberPersister`) | **사일런트 실패** — 관찰성 결함 |
| **#042** | `DhLotteryApiClient.preview()` NPE | 외부 API 다운 시 fallback 진입 차단 |
| **#037** | CD `workflow_run` vs `workflow_dispatch` 산출물 불일치 | 같은 commit, **다른 jar** → 운영 사고 가능 |

> ⚠️ 위 6건은 다음 sprint(2주)에 우선 반영을 권장한다. 자세한 내용은 [§8](#8--2026-05-15-추가-코드-리뷰-결과) 참고.

---

## 2. 리포지토리 개요

### 파일 구조

```
kLo/
├── src/main/java/com/kraft/lotto/
│   ├── feature/
│   │   ├── winningnumber/      # 당첨번호 수집·조회
│   │   ├── recommend/          # 추천 번호 로직 + 제외 규칙
│   │   └── statistics/         # 빈도·이력 통계
│   ├── infra/                  # 설정 · 보안 · 헬스체크
│   └── support/                # 응답 포맷 · 예외 · 유틸
├── src/main/resources/
│   ├── static/                 # JS 모듈, CSS
│   ├── templates/              # Thymeleaf
│   └── docs/index.adoc         # REST Docs
├── src/test/                   # 단위 + 통합 + JS + e2e
├── build.gradle.kts
├── docker-compose.yml
├── .env.example
└── README.md
```

### 의존성 하이라이트

`build.gradle.kts`에 정의된 주요 라이브러리는 다음과 같다.

- **웹/보안**: Spring Web, Security, Validation, Actuator
- **퍼시스턴스**: JPA, Flyway, MariaDB Connector
- **캐시/회복성**: Caffeine, Redis, Resilience4j, ShedLock
- **관측**: Micrometer, OpenTelemetry, Logstash Encoder
- **문서/뷰**: SpringDoc(OpenAPI/Swagger UI), Thymeleaf, Bootstrap Webjars
- **테스트**: Spring Test, REST Docs, Testcontainers, ArchUnit

### 실행/배포

- **로컬 빌드**: `./gradlew build`
- **컨테이너 실행**: `docker compose up -d --build` → `http://localhost:8080`
- **CI/CD**: GitHub Actions `ci.yml` + `cd.yml` (workflow_run / workflow_dispatch)
- **라이선스**: MIT

### 설정

`.env.example`에 프로필·DB·외부 API·관리 토큰·스케줄러·RateLimit·로깅 환경변수가 정의되어 있다. 대표 키:

| 환경변수 | 용도 |
|---|---|
| `KRAFT_ENV` | 프로필 식별자 |
| `KRAFT_API_CLIENT` | 외부 API 클라이언트 모드 (`mock`/`real`) |
| `KRAFT_ADMIN_API_TOKENS` | 관리 API 토큰 (평문 — `apiTokenHashes` 권장) |
| `KRAFT_RECOMMEND_RATE_LIMIT_*` | 추천 API 속도 제한 |

---

## 3. 백엔드 분석

### 3.1 아키텍처 및 모듈

전형적인 Spring Boot 레이어 구조다.

```
Controller (REST API)
   ↓
Service  (비즈니스 로직)
   ↓
Repository (JPA)
   ↓
Domain / DTO (불변 모델)
```

`@SpringBootApplication`에 `@EnableScheduling`, `@EnableRetry`가 적용되어 있다.

#### 추천 모듈 (`feature.recommend`)

- `RecommendController`: `/api/recommend`, `/api/v1/recommend`
- `RecommendService.recommend(count)`: POST 요청 처리
- 제외 규칙: `BirthdayBiasRule`, `LongRunRule`, `SingleDecadeRule` 등
- `RecommendService.rules()`: 활성 규칙 목록 반환

#### 당첨번호 모듈 (`feature.winningnumber`)

| 컴포넌트 | 책임 |
|---|---|
| `WinningNumberEntity` | JPA 엔티티 (회차, 날짜, 번호1~6, 보너스, 당첨금) |
| `LottoFetchLogEntity` | 수집 시도 로그 |
| `WinningNumber` (record) | 불변 도메인, 보너스/음수 검증 |
| `LottoCombination` (record) | 6개 번호 조합, 중복/범위 검증 |
| `WinningNumberController` | `/api/winning-numbers/*` (v1 호환 포함) |
| `LottoCollectionService` + 커맨드 서비스 | 단일/범위 수집 조합 |
| `LottoApiClient` → `WinningNumberPersister` | 외부 API 호출 → DB 저장 |
| `LottoCollectionGate` | `AtomicBoolean` 동시 실행 차단 (단일 인스턴스) |
| `BackfillJobService` | 비동기 백필 (현재 인메모리 맵 — #002로 영속화 예정) |

#### 통계 모듈 (`feature.statistics`)

- `WinningStatisticsService`: 번호 빈도 + 조합 당첨 이력
- 빈도 요약 테이블(`winning_number_frequency_summary`) + Caffeine 캐시 2단 구조
- 수집 이벤트 수신 시 `@CacheEvict` 트리거 (⚠️ 트랜잭션 안전성 문제 — #033)

### 3.2 API 엔드포인트

#### 사용자 API (인증 불필요)

| Method | Path | 설명 |
|---|---|---|
| POST | `/api/recommend` (`/api/v1/recommend`) | `{count: 1~10}` 추천 조합 반환 |
| GET | `/api/recommend/rules` | 활성 제외 규칙 목록 |
| GET | `/api/winning-numbers/latest` | 최신 회차 |
| GET | `/api/winning-numbers/{round}` | 특정 회차 (1~3000) |
| GET | `/api/winning-numbers?page=&size=` | 페이징 조회 |
| GET | `/api/winning-numbers/stats/frequency` | 번호별 등장 횟수 |
| GET | `/api/winning-numbers/stats/frequency-summary` | 최저빈도 6개 + 당첨 이력 |
| GET | `/api/winning-numbers/stats/combination-prize-history?numbers=...` | 6개 조합 당첨 이력 |

#### 관리 API (`X-Kraft-Admin-Token` 헤더 필수)

| Method | Path | 설명 |
|---|---|---|
| POST | `/admin/lotto/draws/collect-next` | 최신 회차까지 순차 수집 |
| POST | `/admin/lotto/draws/collect-missing` | 누락 회차 수집 |
| POST | `/admin/lotto/draws/{drwNo}/refresh` | 특정 회차 재수집 |
| POST | `/admin/lotto/draws/backfill?from=&to=` | 동기 백필 |
| POST | `/admin/lotto/jobs/backfill?from=&to=` | 비동기 백필 잡 큐잉 |

### 3.3 보안

| 항목 | 현 상태 |
|---|---|
| 토큰 인증 | `AdminApiTokenFilter` — 평문/해시 비교, 실패 시 `UNAUTHORIZED_ADMIN_API` |
| CSRF | `/api/**`, `/admin/**`에서 비활성 (stateless) |
| 세션 | stateless |
| Rate Limit | IP별 (`recommend`, `collect`) — 인메모리 / Redis 토글 |
| 보안 헤더 | CSP, Referrer-Policy, Permissions-Policy, X-Frame-Options 적용 |
| ⚠️ 누락 | **HSTS**(#004), **COOP**, **CORP**(#049), **CSP report-uri**(#049) |

### 3.4 데이터 모델 및 영속성

- `WinningNumberRepository`: `findTopByOrderByRoundDesc()`, `findById(round)`, 페이지 조회, 빈도 조회, 조합 PRIZE 히트
- `WinningNumberFrequencySummaryEntity`: 번호별 집계 (성능 최적화)
- `LottoFetchLogRepository`: 수집 로그 + 오래된 로그 삭제

조회 서비스는 `@Transactional(readOnly = true)`, 수집/수정은 `Propagation.NOT_SUPPORTED`로 분리한다. 다만 `LottoCollectionGate`, `BackfillJobService`의 트랜잭션 경계가 코드상 명시되지 않은 부분이 있다 (#003).

### 3.5 성능 및 확장성

| 영역 | 분석 |
|---|---|
| **추천 엔진** | 랜덤 조합 → 다중 규칙 필터 방식. `maxAttempts=5000`, 초과 시 429. 결정적 알고리즘/제한 백트래킹 검토 필요 (#006) |
| **DB** | PK 조회 / 페이징 효율. 빈도 조회는 캐시·요약 테이블 활용. 인덱스 점검 필요 |
| **캐시** | Caffeine + (옵션) Redis. `@CacheEvict` 수동 무효화 |
| **확장성** | `AtomicBoolean` 동시성 제어 → **멀티 인스턴스 부적합** (ShedLock 필요 — #001) |

---

## 4. 프론트엔드 분석

### 4.1 구조 및 동작

- **렌더링**: Thymeleaf 서버사이드 + 정적 JS 보강
- **모듈**: `src/main/resources/static/js` — `api.js`, `pagination.js`, `recommend.js`, `winning.js`, `frequency.js`, `theme.js`, `ui.js`
- **상태 관리**: SPA 프레임워크 없음, vanilla JS + fetch
- **빌드**: 별도 번들러 없음 (정적 서빙)
- **테스트**: Vitest 단위 + Playwright e2e (현재 각 1~2건 수준)

### 4.2 UI/UX

- Bootstrap 기반 컴포넌트
- 폼 즉시 검증 / 피드백 부족 (#014)
- 접근성(ARIA, 키보드 내비) 보강 필요 (#015)
  - `aria-busy`, `aria-live`, role 속성 등
- 페이지네이션 / 추천 폼 모듈화 진행 중 (#007)

### 4.3 빌드 자동화 검토 (#029)

번들링·minify·캐시 무효화 자동화가 없다. Vite 도입 시 다음 이점:
- 의존성 트리·tree-shaking
- TypeScript 점진 도입 가능
- OpenAPI 스키마 → 타입 자동 생성

---

## 5. 테스트 및 품질 보증

### 5.1 커버리지 및 도구

- Jacoco 최소 커버리지 **70%** 요구 (Gradle 빌드 통합)
- Testcontainers로 MariaDB 통합 테스트
- ArchUnit으로 계층 경계 검증 (#013)
- Spring REST Docs로 API 문서 동기화

### 5.2 부족한 영역

| 영역 | 현 상태 | 개선 항목 |
|---|---|---|
| 통합 테스트 | 보안 중심, 정상 응답 검증 제한적 | 정상/오류 케이스 확장 |
| E2E | Playwright 1건 (smoke) | 추천, 페이지네이션, 토글 등 (#016) |
| 계약 테스트 | 경로 set만 검증 | 응답 스키마 (REST Docs ↔ OpenAPI) (#017) |
| JS 단위 | 2개만 존재 | `recommend.js`, `winning.js`, `frequency.js`, `theme.js`, `ui.js` (#048) |
| 캐시 회귀 | **없음** | self-invocation 회귀 테스트 (#047) |
| CI | `./gradlew check` 의존 | 린트·커버리지·Docker 빌드 통합 (#027) |

### 5.3 Flaky 위험

외부 API mock·Testcontainers 사용 통합 테스트는 네트워크·시간에 따라 변동할 수 있다. 시드 고정·격리 강화 필요.

---

## 6. 코드 품질

### 6.1 스타일·린팅

- Java/Kotlin DSL 모두 **Spotless·Checkstyle·PMD 미적용** (#027)
- 패키지·import 정렬, 주석 컨벤션 비일관
- Shell/Python 스크립트도 shellcheck/ruff 권장

### 6.2 복잡도 핫스팟

| 컴포넌트 | 위험 |
|---|---|
| `LottoRecommender` 생성 루프 | 최악 5000회 시도, 타임아웃 가능 |
| `WinningStatisticsService` 집계 | 캐시 self-invocation으로 매번 DB hit (#032) |
| `BackfillJobService` | 쓰레드풀 + 인메모리 상태 + cleanup race (#002, #060) |
| `RecommendRateLimitFilter` Redis 모드 | INCR+EXPIRE 비원자 (#009) |

### 6.3 중복·추상화

- single/range 수집 결과 처리 코드 중복
- `BusinessException` 처리 패턴이 여러 서비스에서 반복 → AOP/Advice 통일 여지
- `PastWinningCacheLoader.reload()` ↔ `WinningStatisticsService.recomputeFrequency()` 동일 read 패턴 (#Q-1)

---

## 7. 문서 및 온보딩

| 문서 | 현 상태 | 개선 |
|---|---|---|
| `README.md` | 실행 방법, API 경로 요약 | 환경변수·배포 절차 보강 |
| `docs/index.adoc` | REST Docs 수동 작성 | `/docs` 엔드포인트로 노출 + 자동화 |
| `.env.example` | 키 나열, 설명 간략 | `docs/configuration.md` 정리 (#030) |
| `docs/docs.md` | 최소 절차 | Runbook(#022) + Architecture(#023) 추가 |
| `CONTRIBUTING.md` | **없음** | 추가 (#020) |
| `SECURITY.md` | **없음** | 추가 (#021) |

---

## 8. 🆕 2026-05-15 추가 코드 리뷰 결과

> **분석 범위**: 전체 91개 Java 파일 + 8개 JS · Thymeleaf · SQL · GitHub Workflow 전수 조사
> **추가 작업단위**: #032~#060 (총 29건)

### 범례

- 🐛 **버그** · 🔒 **보안/설정** · ⚡ **성능** · 📦 **도메인** · 🎨 **프론트** · 🧪 **테스트** · 🚀 **운영** · 🧹 **청결도**
- 🔴 **P1** (즉시) · 🟡 **P2** (단기) · 🟢 **P3** (중장기)

---

### 8.1 🐛 명백한 버그 (Bug)

#### 🔴 B-2 · 캐시 self-invocation 무력화 → **#032**

**위치**: `feature/statistics/application/WinningStatisticsService.java:154`

`frequencySummary()`가 같은 빈의 `@Cacheable` 메서드를 `this`로 직접 호출. Spring AOP 프록시 미경유 → **매 요청마다 DB 집계 쿼리 실행**.

```text
사용자 영향: 메인 화면 진입 시마다 캐시 무력화 — 즉시 조치 필요
```

**조치**: 별도 빈 분리 / `AopContext.currentProxy()` / `@Lazy self-injection`.

---

#### 🔴 B-7 · CSP × 인라인 스크립트 충돌 → **#034**

**위치**: `infra/security/SecurityConfig.java:48` ↔ `templates/fragments/header.html:8-18`

CSP `script-src 'self'`인데 `header.html`에 테마 초기화 인라인 `<script>` 존재. prod 환경에서 **FOUC + 콘솔 위반**.

**조치 (택일)**:
- CSP nonce 통합 + Thymeleaf에서 `nonce="${...}"` 주입
- 인라인 스크립트를 외부 `theme-init.js`로 분리 후 `<head>`에서 동기 로드

---

#### 🔴 B-3 / B-4 · 트랜잭션 이벤트 일관성 → **#033**

**위치**: `feature/statistics/application/WinningStatisticsService.java:53, 166`

- `evictCachesOnCollected`: `@EventListener` → 수집 트랜잭션 롤백 시 캐시는 이미 비워진 상태
- 같은 이벤트를 듣는 `PastWinningCacheLoader.onCollected`는 `@TransactionalEventListener(AFTER_COMMIT)` — 일관성 깨짐
- `frequency()`: `@Cacheable @Transactional` — **`readOnly = true` 누락** (다른 두 메서드는 적용됨)

**조치**: `@TransactionalEventListener(phase = AFTER_COMMIT)`로 통일 + `readOnly = true` 추가.

---

#### 🔴 B-6 · 낙관락 실패가 SKIPPED로 둔갑 → **#036**

**위치**: `feature/winningnumber/application/WinningNumberPersister.java:64`

```java
for (int attempt = 1; attempt <= UPSERT_MAX_RETRIES_ON_OPTIMISTIC_LOCK; attempt++) {
    try { outcome = doUpsert(...); break; }
    catch (OptimisticLockingFailureException ex) {
        if (attempt == UPSERT_MAX_RETRIES_ON_OPTIMISTIC_LOCK)
            outcome = UpsertOutcome.UNCHANGED;   // ← 실패가 "변경 없음"으로 보고됨
    }
}
```

| 문제 | 영향 |
|---|---|
| 변수명 `MAX_RETRIES`인데 값 `2` → 실제 1회 재시도 | 의미 불일치 |
| 재시도 끝까지 실패 시 `UNCHANGED` 보고 | **사일런트 실패** — `SUCCESS+skipped`로 fetch_log 기록 |

**조치**: `kraft.winningnumber.optimistic_lock.failure` 메트릭 + `FAILED` 상태 전파.

---

#### 🟡 B-1 · `DhLotteryApiClient.preview()` NPE 가능성 → **#042**

**위치**: `feature/winningnumber/application/DhLotteryApiClient.java:77`

```java
throw new LottoApiClientException(
    "external API HTTP error (... preview=" + preview(response.body()) + ")", ...);
```

`response.body()`가 `null`이면 `preview()` 내부 `body.length()` 호출에서 NPE → fallback 진입 차단.

**조치**: 내부 null 가드 또는 호출부에서 `body == null ? "" : body`.

---

#### 🟢 B-8 · `LottoSingleDrawCollector` 중복 조회 + 데드 코드 → **#052**

- `winningNumberRepository.findMaxRound()`가 한 메서드 분기마다 **별도 SQL로 최대 4번** 실행
- `private CollectResponse response(CollectResponse r) { return r; }` (`:71-73`) — 항등함수, 데드 코드

---

#### 🟡 B-9 · `RecommendRateLimitFilter` Redis 비원자 → 기존 #005에 흡수

```java
Long count = redisTemplate.opsForValue().increment(key);
if (count != null && count == 1L) redisTemplate.expire(key, ...);
```

- `INCR` 직후 별도 RTT로 `EXPIRE` → 클라이언트 사망 시 TTL 미설정 키 영구 잔존
- 핫패스 RTT 2회 → 비용 증가

**조치**: Lua 스크립트(`INCR` + `EXPIRE` 원자) 또는 `SET key 1 EX <ttl> NX`.

---

#### 🟢 B-10 · 모지바케 한글 주석 잔존 → **#051**

다음 파일에 이중 인코딩 한글 주석 (UTF-8 valid bytes이지만 의미 불명):

- `LottoRecommenderTest.java:37`
- `WinningNumberQueryServiceTest.java:122,126,128`
- `RecommendControllerTest.java`
- `KraftPropertiesBindingTest.java`

`scripts/check_utf8.py`는 바이트 단위 유효성만 검사 → 검출 못 함.

**조치**: 모지바케 마커(`?뺢`, `?낫`, `?곕?` 등) 검출 룰 + staged 파일 검수.

---

#### 🟡 B-11 · `isUsableSummary` `saveAll` 동시성 비결정성

**위치**: `WinningStatisticsService.java:107` — `@Version` 없음 → 멀티 인스턴스 동시 stale 감지 시 last-write-wins. ([#Q-2 참고](#84--프로젝트-청결도))

#### 🟢 B-12 · `WinningNumberPersister.saveIfAbsent` — 미사용 메서드

데드 코드 또는 향후 사용 의도라면 문서화 필요.

---

### 8.1.1 🐛 추가 버그 (2차) — B-13~B-23

| ID | 위치 | 문제 | 매핑 |
|:---:|---|---|:---:|
| **B-13** | `application.yml` | `kraft.recommend.rate-limit.recommend` 키 누락 — env로만 변경 가능 | #054 |
| **B-14** | `LottoFetchLogRetentionScheduler` | `@SchedulerLock` 부재 + 단일 DELETE → 멀티 인스턴스 동시 실행 + 테이블 락 | **#041** |
| **B-15** | `LottoApiHealthIndicator` | 이중 SQL (`findMaxRound` + `findTopByOrderByRoundDesc`) + 이름 오해 | — |
| **B-16** | `LottoApiHealthIndicator` | `withDetail("error", ex.getMessage())` — prod 노출 위험 | **#040** |
| **B-17** | `WinningNumberCollectController` | `CollectRequest.targetRound`가 String — `{"targetRound":"1200"}` 강제 | **#043** |
| **B-18** | `LegacyApiDeprecationHeaderFilter` | `SUNSET_VALUE` 하드코딩, sunset 이후 정책 미정 | #056 |
| **B-19** | `logback-spring.xml` | `cleanHistoryOnStart=true` × 5 appender → 재시작 시 로그 소실 | #055 |
| **B-20** | `build-and-up.sh` | 미정의 `klo` systemd 서비스 + 위험한 `docker image prune -f` | **#035** / #057 |
| **B-21** | `smoke-test.sh` | 409 분기 잘못 — 코드는 429 반환 + 매 배포마다 실수집 발생 | **#035** |
| **B-22** | `RandomLottoNumberGenerator` | 데드 코드 가능성 (`ConstraintAwareLottoNumberGenerator`만 빈 등록) | #053 |
| **B-23** | `LottoCollectionService.forTest` | 프로덕션 jar에 테스트 진입점 포함 | #053 |

---

### 8.2 🔒 보안·운영 결함

| ID | 항목 | 조치 | 매핑 |
|:---:|---|---|:---:|
| **S-1** | CSP `frame-ancestors 'none'` + `X-Frame-Options` 이중 | `X-Frame-Options` 제거 | — |
| **S-2** | 누락 헤더: COOP / CORP / X-Content-Type-Options(명시) | 추가 | **#049** |
| **S-3** | Rate limit `clientIp = "unknown"` 키 폭주 | `kraft.rate_limit.unknown_client_ip` 카운터 노출 | — |
| **S-4** | 어드민 토큰 audit 로그 IP 평문 | 부분 마스킹 / 해시 | — |
| **S-5** | prod actuator `metrics`/`prometheus` 노출 정책 | 별도 management 포트 / 인증 | **#039** |
| **S-6** | `KRAFT_ADMIN_API_TOKENS` 평문 허용 | prod에서 차단 룰 + 길이 검증 | **#046** |
| **S-7** | CD `render-env.sh`가 `.env` 평문 기록 | `docker compose run -e KEY=$VAL` + `trap` | — |
| **S-8** | prod Prometheus 노출 vs SecurityConfig 정합성 | permitAll 명시 또는 차단 의도 문서화 | **#039** |
| **S-9** | `health.show-details: when-authorized` ↔ authorized 경로 부재 | 의도 정리 | — |
| **S-10** | CSP `report-uri` / `report-to` 부재 | violation 수집 endpoint | **#049** |
| **S-11** | CORS 설정 부재 (현재는 same-origin) | mobile/SPA 분리 시 대비 | — |

---

### 8.3 ⚡ 성능

| ID | 항목 | 조치 | 매핑 |
|:---:|---|---|:---:|
| **P-1** | `findPrizeHitsByNumbers` 풀스캔 × 2 (UNION ALL, 6컬럼 IN) | bitmask 컬럼 / combination_signature 컬럼 | **#038** |
| **P-2** | `recomputeFrequency` 매번 1223+ 행 6컬럼 로드 | DB쪽 `UNNEST + GROUP BY` | — |
| **P-3** | `PastWinningCacheLoader.reload()` JPA stream 미사용 | `Stream` / 페이징 | — |
| **P-4** | Caffeine `maximumSize=100` 너무 작음 (`application.yml:53`) | `maximumSize=1000~5000`, `expireAfterAccess=24h` | #059 |
| **P-5** | `WinningNumberAutoCollectScheduler` 모든 잡 동일 `lock-at-most-for=PT10M` | 잡별 분리 | — |

---

### 8.4 📦 도메인·추천 엔진

| ID | 항목 | 조치 | 매핑 |
|:---:|---|---|:---:|
| **D-1** | 십의자리 임계치 두 곳에서 다르게 구현 (`SingleDecadeRule` ↔ `ConstraintAware...`) | 공유 상수/유틸 추출 | — |
| **D-2** | `LongRunRule` ↔ 생성기 fix-up: 무작위 인덱스 교체로 효율 낮음 | "긴 연속 구간 내부" 인덱스만 후보 | #050 |
| **D-3** | `KraftRecommendProperties.Rules` 범위 검증 부재 (`decadeThreshold=1`이면 모든 요청 500) | record compact constructor 검증 | #050 |
| **D-4** | `RecommendRequest.countOrDefault()` Bean Validation 우회 | `@NotNull` 옵션 검토 | — |
| **D-5** | `BackfillJobService.start` cleanup race-prone | cleanup을 `executor.execute(...)` 이후로 이동 | — |
| **D-6** | `BackfillJobService` SUCCEEDED 결과 메모리 보존 (`jobRetention=PT6H`) | #002 영속화로 흡수 | #060 |
| **D-7** | `LottoRangeCollector` 부분 실패가 응답에 묻힘 (HTTP 200) | 207 Multi-Status / 경고 헤더 | — |
| **D-8** | `LottoCollectionGate.run()` lock 해제 전 `publishEvent` (동기 listener 블로킹) | `publish`를 try 밖으로 / async listener 통일 | — |

---

### 8.5 🎨 프론트엔드

| ID | 항목 | 조치 | 매핑 |
|:---:|---|---|:---:|
| **F-1** | `api.js` 타임아웃 10초 하드코딩 | 호출자 override | — |
| **F-2** | `pagination.js` AbortController 누수 (catch 분기) | `listState.abortCtrl = null` | — |
| **F-3** | `frequency.js` "최저빈도 6개" 매번 백엔드 호출 | B-2(#032) 해결 시 자동 회복 | — |
| **F-4** | 동일 화면 4개 API 병렬 호출 — `latest`를 `list` 첫 페이지에서 재사용 가능 | cache-friendly | — |
| **F-5** | `recommend.js` 폼 검증 즉시 피드백 부재 | 기존 #014에 흡수 | #014 |
| **F-6** | 429 `Retry-After` 헤더 무시 | 토스트에 "X초 후 재시도" | — |

---

### 8.6 🧪 테스트

| ID | 항목 | 조치 | 매핑 |
|:---:|---|---|:---:|
| **T-1** | 캐시 동작 회귀 테스트 부재 | repository hit 횟수 검증 | **#047** |
| **T-2** | `MockLottoApiClientTest.fetch(2000)` 1.5초+ | `@Tag("perf")` 분리 | — |
| **T-3** | ArchUnit 룰 미흡 | `application`→`RestClient` 금지, `domain`에서 Spring 어노테이션 금지 등 | #013 |
| **T-4** | 계약 테스트가 경로 set만 검증 | REST Docs snippet ↔ OpenAPI 스키마 매칭 | #017 |
| **T-5** | JS 테스트 2개에 불과 | `recommend.js`, `winning.js`, `frequency.js`, `theme.js`, `ui.js` 추가 | **#048** |
| **T-6** | Playwright e2e 1개 (smoke) | 추천 폼, 페이지네이션, 토글 시나리오 | #016 |

---

### 8.7 🚀 운영·배포

| ID | 항목 | 조치 | 매핑 |
|:---:|---|---|:---:|
| **O-1** | `cd.yml` workflow_run vs workflow_dispatch — 빌드 산출물 비결정성 ⚠️ | 산출물 일원화 | **#037** |
| **O-2** | Docker 이미지 태그 SHA만 사용 (롤백 어려움) | `latest` + sha 동시 / `previous` 별도 | #010 |
| **O-3** | `wait-readiness.sh` 실패 시 컨테이너 로그 미출력 | `docker compose logs --tail=200` 추가 | — |
| **O-4** | `JAVA_OPTS`에 `-XX:+HeapDumpOnOutOfMemoryError` 없음 | 추가 | **#044** |
| **O-5** | JVM 가상스레드 + JDBC 핀닝 모니터링 필요 | 메트릭/대시보드 정의 | #019 |
| **O-6** | Flyway `baseline-on-migrate`/`baseline-version` 미정의 | 명시 | **#045** |
| **O-7** | `IMPROVEMENT.md` ↔ GitHub Issues 양방향 sync 없음 | 작업단위 ID prefix `[#NNN]` 강제 | — |
| **O-8** | `package.json` 루트 + 테스트는 `src/test/js`, `src/test/e2e` | 의존성 dev-only 확인 | — |
| **O-9** | README "최신 회차" 수동 업데이트 | 자동 생성 / 동적 endpoint | #058 |
| **O-10** | `KRAFT_API_MOCK_LATEST_ROUND:1200` default — 실제 1223 | startup 시 `findMaxRound() + 1` 자동 동기화 | #058 |

---

### 8.8 🧹 프로젝트 청결도

| ID | 항목 | 조치 |
|:---:|---|---|
| **Q-1** | `recomputeFrequency` 패턴이 `PastWinningCacheLoader.reload()`와 `WinningStatisticsService`에 중복 | 공통 read-projection / 같은 read 흐름 공유 |
| **Q-2** | Caffeine + 빈도 요약 테이블 양립 — 멀티 인스턴스 동시 saveAll | DB 갱신을 `WinningNumberPersister` 또는 이벤트 리스너에서 단일화 |
| **Q-3** | `CollectResponse.dataChanged` 내부 의사결정 플래그가 응답에 노출 | `@JsonIgnore` / internal-response 모델 분리 |
| **Q-4** | 패키지 명 비대칭 (`feature/winningnumber/event/...`만 존재) | ArchUnit 패키지 명명 규약 추가 (#013) |

---

## 9. 우선순위 통합 로드맵 (#001~#060)

> **범례**: 🔴 P1(즉시) / 🟡 P2(단기) / 🟢 P3(중장기)
> **출처**: 기존 #001~#031 (IMPROVEMENT.md) + 신규 #032~#060 (2026-05-15 리뷰)

### 9.1 🔴 P1 — 즉시 조치 (다음 sprint, 2주)

| # | 영역 | 항목 | 예상 노력 |
|:---:|:---:|---|:---:|
| **#001** | 백엔드 | ShedLock 도입 (`LottoCollectionGate` 분산 락) | 중 |
| **#002** | 백엔드 | 백필 작업 상태 영속화 (DB 테이블) | 중 |
| **#003** | 백엔드 | 트랜잭션 경계 명확화 | 소~중 |
| **#004** | 보안 | HSTS 및 보안 헤더 강화 | 소 |
| **#005** | 인프라 | Redis 구성 추가 (Rate Limit) | 중 |
| **#006** | 도메인 | 추천 엔진 결정성 도입 / 백트래킹 | 중~대 |
| **#007** | 프론트 | JS 모듈 구조화 | 소 |
| **#032** | 백엔드 | 캐시 self-invocation 수정 (B-2) | 소 |
| **#033** | 백엔드 | `@TransactionalEventListener(AFTER_COMMIT)` 통일 + `readOnly` (B-3, B-4) | 소 |
| **#034** | 보안 | CSP nonce / `theme-init.js` 외부화 (B-7) | 중 |
| **#035** | 운영 | CD smoke test side-effect 제거 + 409→429 + `image prune` 안전화 (B-20, B-21) | 소 |
| **#036** | 백엔드 | 낙관락 실패 메트릭 + FAILED 전파 (B-6) | 소 |
| **#037** | 운영 | manual/auto deploy 빌드 산출물 일원화 (O-1) | 중 |

### 9.2 🟡 P2 — 단기 (Sprint 2~4)

| # | 영역 | 항목 |
|:---:|:---:|---|
| #008 | 테스트 | 보안 헤더 통합 테스트 |
| #009 | 운영 | 프로필 정책 문서화 (`KRAFT_ENV`) |
| #010 | 운영 | Docker 태깅 전략 |
| #011 | 백엔드 | 외부 API resilience 보강 |
| #012 | 백엔드 | 에러 모델 개선 |
| #013 | 백엔드 | ArchUnit 계층/패키지 규칙 강화 |
| #014 | 프론트 | 폼 즉시 검증/피드백 |
| #015 | 프론트 | 접근성(ARIA) 강화 |
| #016 | 테스트 | E2E 테스트 확대 (Playwright) |
| #017 | 테스트 | 계약 테스트 (REST Docs ↔ OpenAPI) |
| #018 | 운영 | 마이그레이션 문서화 |
| #019 | 운영 | JVM 가상스레드 핀닝 모니터링 |
| #020 | 문서 | CONTRIBUTING.md |
| #021 | 문서 | SECURITY.md |
| #022 | 문서 | Runbook |
| #023 | 문서 | Architecture |
| **#038** | 성능 | `findPrizeHitsByNumbers` bitmask/signature 컬럼 (P-1) |
| **#039** | 운영 | Prometheus 노출 vs SecurityConfig 정합성 (S-5, S-8) |
| **#040** | 보안 | `LottoApiHealthIndicator` detail `ex.message` 제외 (B-16) |
| **#041** | 백엔드 | `LottoFetchLogRetentionScheduler` `@SchedulerLock` + 배치 삭제 (B-14) |
| **#042** | 백엔드 | `DhLotteryApiClient.preview()` NPE 가드 (B-1) |
| **#043** | API | `CollectRequest.targetRound` → `Integer` + `@Min/@Max` (B-17) |
| **#044** | 운영 | `JAVA_OPTS`에 `-XX:+HeapDumpOnOutOfMemoryError` (O-4) |
| **#045** | 운영 | Flyway `baseline-on-migrate` / `baseline-version` (O-6) |
| **#046** | 보안 | prod 평문 admin token 차단 + 길이 검증 (S-6) |
| **#047** | 테스트 | 캐시 회귀 통합 테스트 (T-1) |
| **#048** | 테스트 | JS 단위 테스트 확대 (T-5) |
| **#049** | 보안 | CSP `report-uri`/`report-to` + COOP/CORP (S-2, S-10) |

### 9.3 🟢 P3 — 중장기

| # | 영역 | 항목 |
|:---:|:---:|---|
| #024 | 백엔드 | 추천 시도횟수 외부 설정화 |
| #025 | 백엔드 | 빈도 요약 검증 강화 |
| #026 | 로깅 | 로깅 임계값 조정 |
| #027 | 품질 | 린트/스타일 도구 도입 (Spotless, Checkstyle, ktlint) |
| #028 | DB | 인덱스 재검토 |
| #029 | 프론트 | Vite 도입 및 타입 생성 |
| #030 | 문서 | 환경변수 표준 및 문서화 |
| #031 | 운영 | Java 21 백포팅 검토 |
| **#050** | 도메인 | `KraftRecommendProperties.Rules` 범위 검증 + fix-up 효율화 (D-2, D-3) |
| **#051** | 청결도 | 모지바케 주석 정리 + `check_utf8.py` 보강 (B-10) |
| **#052** | 청결도 | `LottoSingleDrawCollector` 항등함수 제거 + `findMaxRound` 중복 정리 (B-8) |
| **#053** | 청결도 | `RandomLottoNumberGenerator` 데드 코드 + `forTest` 분리 (B-22, B-23) |
| **#054** | 설정 | `application.yml` `rate-limit.recommend` 키 명시 (B-13) |
| **#055** | 운영 | `cleanHistoryOnStart=false` (B-19) |
| **#056** | API | `LegacyApiDeprecationHeaderFilter` SUNSET 외부화 + RFC 9745 (B-18) |
| **#057** | 운영 | `build-and-up.sh` systemd 정리 (B-20) |
| **#058** | 운영 | `KRAFT_API_MOCK_LATEST_ROUND` default 자동 갱신 (O-9, O-10) |
| **#059** | 성능 | Caffeine 캐시 사이즈/만료 정책 재조정 (P-4) |
| **#060** | 도메인 | `BackfillJobService` 메모리 limit (#002와 연결) (D-6) |

---

## 10. 코드 샘플 및 패치 예시

### 10.1 ShedLock 적용 (#001)

`LottoCollectionGate.run`에서 기존 `AtomicBoolean` 대신 ShedLock을 사용:

```java
LockConfiguration cfg = new LockConfiguration(
    Instant.now(),
    "lotto_collect_gate",
    Duration.ofMinutes(10),
    Duration.ofSeconds(5));

lockProvider.lock(cfg)
    .map(lock -> {
        try { return task.run(); }
        finally { lock.unlock(); }
    })
    .orElseThrow(() -> new BusinessException(
        ErrorCode.TOO_MANY_REQUESTS, "already running"));
```

`JdbcTemplateLockProvider`를 `lockProvider`로 주입하고 `AtomicBoolean` 분기를 대체한다.

### 10.2 백필 잡 영속화 엔티티 (#002)

```java
@Entity
@Table(name = "backfill_jobs")
public class BackfillJobEntity {
    @Id private String jobId;
    private int fromRound;
    private int toRound;
    private String status; // QUEUED, RUNNING, SUCCEEDED, FAILED
    private Instant createdAt;
    private Instant startedAt;
    private Instant completedAt;
    private String error;
    // getter/setter 생략
}
```

`BackfillJobRepository`로 상태를 저장하고 `BackfillJobService`의 인메모리 맵을 영속화 로직으로 교체.

### 10.3 캐시 self-invocation 해결 (#032)

```java
@Service
public class WinningStatisticsService {

    @Lazy
    @Autowired
    private WinningStatisticsService self;   // ← self-proxy

    @Transactional(readOnly = true)
    public FrequencySummaryDto frequencySummary() {
        // this.frequency() ❌  →  self.frequency() ✅ (프록시 경유)
        var freq = self.frequency();
        var hist = self.combinationPrizeHistory(lowSix(freq));
        return assemble(freq, hist);
    }
}
```

또는 `@Cacheable` 메서드를 별도 빈(`StatisticsCacheFacade`)으로 분리하는 방식이 더 깔끔하다.

### 10.4 트랜잭션 이벤트 통일 (#033)

```java
// Before
@EventListener
@CacheEvict(cacheNames = {"winningNumberFrequency", "combinationPrizeHistory"}, allEntries = true)
public void evictCachesOnCollected(WinningNumbersCollectedEvent event) { }

// After
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
@CacheEvict(cacheNames = {"winningNumberFrequency", "combinationPrizeHistory"}, allEntries = true)
public void evictCachesOnCollected(WinningNumbersCollectedEvent event) { }
```

같이 `frequency()`에 `readOnly = true` 추가:

```java
@Cacheable("winningNumberFrequency")
@Transactional(readOnly = true)
public List<NumberFrequencyDto> frequency() { ... }
```

### 10.5 보안 헤더 강화 (#004)

```java
http.headers(headers -> headers
    .httpStrictTransportSecurity(hsts -> hsts
        .includeSubDomains(true)
        .maxAgeInSeconds(31_536_000))
    .crossOriginOpenerPolicy(coop -> coop
        .policy(CrossOriginOpenerPolicyHeaderWriter.CrossOriginOpenerPolicy.SAME_ORIGIN))
    .crossOriginResourcePolicy(corp -> corp
        .policy(CrossOriginResourcePolicyHeaderWriter.CrossOriginResourcePolicy.SAME_ORIGIN)));
```

### 10.6 CSP nonce + Thymeleaf (#034)

```java
// SecurityConfig
.contentSecurityPolicy(csp -> csp.policyDirectives(
    "default-src 'self'; " +
    "script-src 'self' 'nonce-{nonce}'; " +
    "style-src 'self' 'nonce-{nonce}'; " +
    "frame-ancestors 'none'; " +
    "report-uri /csp/report"))
```

```html
<!-- header.html -->
<script th:attr="nonce=${cspNonce}">
  (function() { /* theme init */ })();
</script>
```

### 10.7 Redis Rate Limit 원자화 (#005)

```java
private static final RedisScript<Long> INCR_EXPIRE = RedisScript.of("""
    local v = redis.call('INCR', KEYS[1])
    if v == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
    return v
    """, Long.class);

Long count = redisTemplate.execute(INCR_EXPIRE, List.of(key), String.valueOf(ttlSeconds));
```

### 10.8 낙관락 실패 메트릭 (#036)

```java
catch (OptimisticLockingFailureException ex) {
    if (attempt == UPSERT_MAX_RETRIES_ON_OPTIMISTIC_LOCK) {
        meterRegistry.counter("kraft.winningnumber.optimistic_lock.failure").increment();
        outcome = UpsertOutcome.FAILED;        // ← UNCHANGED 아님
        return outcome;
    }
}
```

호출자(`LottoSingleDrawCollector`)는 `FAILED` 상태를 fetch_log에 기록.

---

## 11. 권장 도구·라이브러리·CI

### 11.1 도구

| 카테고리 | 권장 |
|---|---|
| 린트/정적분석 | Spotless(Prettier), Checkstyle, PMD, SonarQube, ktlint |
| Docker 이미지 | GraalVM AOT, GitHub Container Registry, `latest` + `sha` + `previous` 태그 |
| 모니터링 | Grafana / Prometheus 대시보드, OTEL Collector |
| API 문서 | Spring REST Docs + OpenAPI 스키마 자동 비교 (#017) |
| 보안 스캔 | OWASP Dependency-Check, Trivy(이미지) |

### 11.2 CI 파이프라인 (GitHub Actions)

```yaml
jobs:
  verify:
    steps:
      - ./gradlew spotlessCheck
      - ./gradlew check                  # 테스트 + 커버리지
      - ./gradlew jacocoTestCoverageVerification
      - npm ci && npm run typecheck && npm test
      - npx playwright install --with-deps && npm run e2e
      - docker build --target test .     # 멀티스테이지로 산출물 검증
```

- 린트 실패 / 커버리지 미달 시 CI 실패
- PR 템플릿 + 체크리스트 강제 (아래 §12)

---

## 12. 리뷰 체크리스트 및 PR 템플릿

### 12.1 체크리스트

- [ ] 관련 테스트 추가 (단위 / 통합 / E2E)
- [ ] 커버리지 70% 이상 유지
- [ ] 보안 영향 검토 (헤더, 토큰, 입력 검증)
- [ ] API 변경 시 OpenAPI / REST Docs 동기화
- [ ] 환경변수 / 설정 문서 반영 (`.env.example`)
- [ ] 코드 스타일 (Spotless / Prettier) 통과
- [ ] DB 마이그레이션 시 `V<n>__...sql` 추가 + 롤백 전략

### 12.2 PR 템플릿

```markdown
## 변경 사항 설명
- 구현 내용 요약
- 관련 작업단위: #NNN

## 테스팅
- [ ] 단위 테스트 ✅
- [ ] 통합/계약 테스트 ✅
- [ ] E2E (해당 시)
- [ ] CI 통과

## 체크리스트
- [ ] 코드 스타일 (Spotless / Prettier) 통과
- [ ] 보안 영향 검토 완료
- [ ] 문서(`README`, API 명세, `.env.example`) 업데이트
- [ ] 마이그레이션 / 롤백 전략 명시

## 스크린샷 / 로그 (선택)
```

---

## 13. 실행 명령어 예시

```bash
# 로컬 빌드 / 테스트
./gradlew clean build test
./gradlew jacocoTestReport

# JS 테스트
npm ci
npm test                    # vitest
npm run e2e                 # playwright

# Docker 실행
docker compose up -d --build
# → http://localhost:8080

# jar 직접 실행
java -jar build/libs/app.jar

# API 호출 예시
curl http://localhost:8080/api/winning-numbers/latest
curl -X POST http://localhost:8080/api/recommend \
     -H "Content-Type: application/json" \
     -d '{"count": 5}'

# 관리 API
curl -X POST http://localhost:8080/admin/lotto/draws/collect-next \
     -H "X-Kraft-Admin-Token: <token>"
```

---

## 부록 — 권장 다음 sprint

> **다음 2주 sprint**: P1 핵심 6선(#032, #034, #033, #036, #042, #037)부터 처리
>
> 기존 31개 + 신규 29개 = **60개 작업단위**. P1 전체 12개는 6주짜리 큰 sprint이므로,
> 신규 P1만 먼저 추출해 한 sprint로 운영하는 것이 현실적이다.

**작성**: Claude (Anthropic)
**기준 commit**: `portuna85/kLo` main (2026-05-15)
