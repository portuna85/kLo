# kLo 개선 작업 단위 (Work Units)

> **최종 갱신**: 2026-05-14
> **저장소**: `portuna85/kLo` (main)
> **본 문서의 성격**: 각 항목은 **독립 실행 가능한 작업 단위(unit)** 입니다.
> 단위 하나 = PR 하나 = 이슈 하나를 원칙으로 합니다.

---

## 0. 사용법

- 작업 시작 시 **인덱스 표의 체크박스를 in-progress(`[~]`)** 로 변경
- 완료 시 본 문서에서 해당 단위를 **삭제**하고 커밋 메시지에 단위 ID를 명시
  예: `git commit -m "feat(collect): apply distributed lock [#001]"`
- 새 항목 추가 시 다음 빈 번호를 사용 (#001 → #002 → ...)
- 우선순위는 **P1**(다음 sprint) / **P2**(중기) / **P3**(장기)

---

## 1. 작업 단위 인덱스

| ID | 우선 | 영역 | 제목 | 상태 |
|:---:|:---:|---|---|:---:|
| #001 | P1 | 백엔드 | LottoCollectionGate 분산 동시성 (ShedLock) | ☐ |
| #002 | P1 | 백엔드 | BackfillJobService 잡 상태 DB 영속화 | ☐ |
| #003 | P1 | 백엔드 | Transaction 경계 명시 | ☐ |
| #004 | P1 | 보안 | HSTS 헤더 적용 | ☐ |
| #005 | P1 | 운영 | Redis compose 서비스 추가 | ☐ |
| #006 | P1 | 도메인 | 추천 엔진 결정성·성능 보증 | ☐ |
| #007 | P1 | 프론트 | JS 모듈 mount 구조화 | ☐ |
| #008 | P2 | 보안 | 보안 헤더 통합 테스트 | ☐ |
| #009 | P2 | 운영 | Profile 정책 명확화 (`KRAFT_ENVIRONMENT`) | ☐ |
| #010 | P2 | 운영 | Docker image SHA 기반 tag 전략 | ☐ |
| #011 | P2 | 백엔드 | 외부 API client resilience 통합 | ☐ |
| #012 | P2 | 백엔드 | Error model 상세 구조 정교화 | ☐ |
| #013 | P2 | 백엔드 | ArchUnit 경계 규칙 확대 | ☐ |
| #014 | P2 | 프론트 | 폼 검증 즉시 피드백 | ☐ |
| #015 | P2 | 프론트 | 접근성 보강 (aria-busy / aria-live) | ☐ |
| #016 | P2 | 테스트 | E2E 실패 시나리오 확대 | ☐ |
| #017 | P2 | 테스트 | 계약 테스트 강화 (OpenAPI ↔ REST Docs) | ☐ |
| #018 | P2 | DB | Migration 규칙 문서화 + from-scratch 테스트 | ☐ |
| #019 | P2 | 관찰성 | Metric/Alert 기준 문서화 | ☐ |
| #020 | P2 | 문서 | `SECURITY.md` 신설 | ☐ |
| #021 | P2 | 문서 | `CONTRIBUTING.md` 신설 | ☐ |
| #022 | P2 | 문서 | `docs/ARCHITECTURE.md` 신설 | ☐ |
| #023 | P2 | 문서 | `docs/RUNBOOK.md` 신설 | ☐ |
| #024 | P3 | 백엔드 | LottoRecommender 시도 횟수 yml 외부화 | ☐ |
| #025 | P3 | 백엔드 | `isUsableSummary` 방어적 검증 | ☐ |
| #026 | P3 | 운영 | Logback `discardingThreshold` 조정 | ☐ |
| #027 | P3 | 품질 | Spotless / Checkstyle / shellcheck 도입 | ☐ |
| #028 | P3 | DB | 조회 성능 인덱스 재검토 | ☐ |
| #029 | P3 | 프론트 | Vite 도입 및 OpenAPI→TS 타입 생성 | ☐ |
| #030 | P3 | 문서 | 환경변수 표준표 + `docs/configuration.md` | ☐ |
| #031 | P3 | 운영 | Java 21 LTS 백포팅 옵션 검토 | ☐ |

---

## 2. 작업 단위 상세

### 🔴 P1 — 다음 sprint

---

#### #001 — LottoCollectionGate 분산 동시성

| 항목 | 내용 |
|---|---|
| 우선순위 | **P1** · 백엔드 |
| 의존성 | — |
| 브랜치 안 | `feat/collect-gate-shedlock` |
| 커밋 메시지 안 | `feat(collect): apply distributed lock to collection gate [#001]` |

**문제** — `AtomicBoolean` 단일 JVM 기준 → 멀티 인스턴스에서 admin 동시 수집 미보호.

**변경 파일**
- `feature/winningnumber/application/LottoCollectionGate.java`
- `infra/config/SchedulerLockConfig.java` (기존 `JdbcTemplateLockProvider` 재사용)
- `feature/winningnumber/application/LottoCollectionGateTest.java`

**작업 단계**
1. `LockProvider`를 `LottoCollectionGate` 생성자에 주입
2. `AtomicBoolean.compareAndSet` 분기를 `LockConfiguration` 기반 분산 락으로 치환
3. 락 획득 실패 시 `BusinessException(ErrorCode.TOO_MANY_REQUESTS, "already running")`
4. 2-인스턴스 시뮬레이션 IT 추가 (`@Sql`로 동일 lock row를 미리 점유)

**완료 조건 (DoD)**
- [ ] `lotto_collect_gate` 키로 ShedLock 락 동작
- [ ] 락 획득 실패 → 429 응답
- [ ] 단일 JVM 회귀 테스트 통과
- [ ] 2-인스턴스 동시 호출 시 1개만 실행되는 IT 통과

**참고 스니펫**
```java
LockConfiguration cfg = new LockConfiguration(
    Instant.now(), "lotto_collect_gate",
    Duration.ofMinutes(10), Duration.ofSeconds(5));
return lockProvider.lock(cfg)
    .map(lock -> { try { return run(task); } finally { lock.unlock(); } })
    .orElseThrow(() -> new BusinessException(
        ErrorCode.TOO_MANY_REQUESTS, "already running"));
```

---

#### #002 — BackfillJobService 잡 상태 DB 영속화

| 항목 | 내용 |
|---|---|
| 우선순위 | **P1** · 백엔드 |
| 의존성 | — |
| 브랜치 안 | `feat/backfill-job-persistence` |

**문제** — `ConcurrentHashMap` 인메모리 → 재시작 시 잡 상태 유실 + 운영 추적 불가.

**변경 파일**
- `src/main/resources/db/migration/V6__create_backfill_jobs.sql` (신규)
- `feature/winningnumber/infrastructure/BackfillJobEntity.java` (신규)
- `feature/winningnumber/infrastructure/BackfillJobRepository.java` (신규)
- `feature/winningnumber/application/BackfillJobService.java` (수정)
- `feature/winningnumber/application/BackfillJobStartupHook.java` (신규)

**작업 단계**
1. Flyway V6 migration 작성
2. JPA Entity·Repository 추가
3. `BackfillJobService`에서 ConcurrentHashMap → Repository 호출로 교체
4. `ApplicationReadyEvent` 리스너에서 `RUNNING` → `FAILED("interrupted_by_restart")` 마킹
5. 통합 테스트로 재시작 시나리오 검증

**완료 조건 (DoD)**
- [ ] V6 migration이 `migrationFromScratchIT`에서 통과
- [ ] 잡 생성/조회 시 DB row 존재 확인
- [ ] 재시작 후 RUNNING 잡이 FAILED로 마킹됨
- [ ] 기존 BackfillJobServiceTest 회귀 통과

**참고 — V6 SQL**
```sql
CREATE TABLE backfill_jobs (
    job_id       VARCHAR(36)  NOT NULL,
    from_round   INT          NOT NULL,
    to_round     INT          NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    started_at   DATETIME     NULL,
    completed_at DATETIME     NULL,
    result_json  LONGTEXT     NULL,
    error_msg    VARCHAR(500) NULL,
    CONSTRAINT pk_backfill_jobs PRIMARY KEY (job_id)
);
CREATE INDEX idx_backfill_jobs_status ON backfill_jobs(status);
```

---

#### #003 — Transaction 경계 명시

| 항목 | 내용 |
|---|---|
| 우선순위 | **P1** · 백엔드 |
| 의존성 | — |
| 브랜치 안 | `refactor/transaction-boundary` |

**문제** — read-only / write 경계가 코드 레벨에서 일관되지 않음. 외부 API 호출이 DB transaction 내부에서 일어날 위험.

**변경 파일**
- `feature/winningnumber/application/WinningNumberQueryService.java`
- `feature/winningnumber/application/WinningNumberPersister.java`
- `feature/winningnumber/application/LottoSingleDrawCollector.java`
- `feature/winningnumber/application/LottoRangeCollector.java`

**작업 단계**
1. 모든 조회 서비스에 클래스 레벨 `@Transactional(readOnly = true)` 적용
2. 저장 메서드에만 메서드 레벨 `@Transactional` 명시 (readOnly 미설정)
3. 외부 API 호출이 `@Transactional` 메서드 외부에서 일어나도록 메서드 분리
4. range/backfill은 회차별 단위 transaction으로 chunk 처리
5. ArchUnit 규칙 추가: `*Collector`는 `RestClient` 호출 전후로 transaction 분리

**완료 조건 (DoD)**
- [ ] 모든 query service에 read-only 적용
- [ ] 저장 흐름에서 외부 API call이 transaction 내부에 없음 (코드 리뷰)
- [ ] 기존 Repository IT 회귀 통과
- [ ] ArchUnit 규칙 1개 추가

---

#### #004 — HSTS 헤더 적용

| 항목 | 내용 |
|---|---|
| 우선순위 | **P1** · 보안 |
| 의존성 | #008 (테스트와 함께 묶어도 됨) |
| 브랜치 안 | `feat/security-hsts` |

**문제** — HTTPS 강제 헤더 부재.

**변경 파일**
- `infra/security/SecurityConfig.java`
- `infra/security/SecurityHeaderIT.java` (신규 또는 #008과 통합)

**작업 단계**
1. `SecurityFilterChain`의 `.headers()`에 HSTS 추가
2. prod profile 전용으로 한정 (local에서 HTTPS 미사용 케이스 보호)
3. IT로 응답 헤더 확인

**완료 조건 (DoD)**
- [ ] `Strict-Transport-Security: max-age=31536000; includeSubDomains` 응답에 포함 (prod)
- [ ] local profile에서는 미포함 또는 짧은 max-age
- [ ] IT 통과

**참고 스니펫**
```java
.headers(h -> h.httpStrictTransportSecurity(hsts -> hsts
    .includeSubDomains(true)
    .maxAgeInSeconds(31_536_000)))
```

---

#### #005 — Redis compose 서비스 추가

| 항목 | 내용 |
|---|---|
| 우선순위 | **P1** · 운영 |
| 의존성 | — |
| 브랜치 안 | `feat/compose-redis` |

**문제** — 다중 인스턴스 rate limit을 위해 Redis 옵션이 있으나 compose에 정의 없음.

**변경 파일**
- `docker-compose.yml`
- `.env.example`
- `README.md` (Redis 활성화 절차 1단락)

**작업 단계**
1. `docker-compose.yml`에 `redis` 서비스 (profile: `redis`)
2. `.env.example`에 `COMPOSE_PROFILES=redis` 주석 예시 + `REDIS_HOST` 등 변수
3. README에 "다중 인스턴스 운영 시 Redis 활성화" 짧은 절 추가

**완료 조건 (DoD)**
- [ ] `docker compose --profile redis up`으로 redis 정상 기동
- [ ] app이 `redis` profile로 부팅 시 redis에 접속 성공
- [ ] 기본 `docker compose up`(profile 미지정) 시에는 redis 미기동

**참고 스니펫**
```yaml
redis:
  image: redis:7-alpine
  profiles: ["redis"]
  container_name: kraft-lotto-redis
  restart: unless-stopped
  command: ["redis-server", "--save", "", "--appendonly", "no"]
  networks: [kraft-net]
  healthcheck:
    test: ["CMD", "redis-cli", "ping"]
    interval: 10s
    timeout: 3s
    retries: 5
```

---

#### #006 — 추천 엔진 결정성·성능 보증

| 항목 | 내용 |
|---|---|
| 우선순위 | **P1** · 도메인 |
| 의존성 | — |
| 브랜치 안 | `feat/recommend-determinism` |

**문제** — `maxAttempts` 안에서 랜덤 생성 후 규칙으로 제외하는 구조 → 필터가 강해질수록 rejection rate↑·timeout 위험.

**변경 파일**
- `feature/recommend/application/RandomLottoNumberGenerator.java`
- `feature/recommend/application/ConstraintAwareLottoNumberGenerator.java`
- `feature/recommend/application/RecommendConfiguration.java`
- `feature/recommend/application/LottoRecommenderPerformanceTest.java` (신규)

**작업 단계**
1. `Random` 주입을 통한 seed 기반 generator 옵션
2. `ConstraintAwareLottoNumberGenerator`를 기본 전략으로 승격
3. `kraft.recommend.rejection.rate` Micrometer Counter 추가
4. rejection rate 임계치(예: 0.5) 초과 시 WARN 로그
5. rule set별 최악 성능 테스트(타임 boxed)

**완료 조건 (DoD)**
- [ ] seed 고정 시 동일 추천 결과 재현되는 unit test
- [ ] `ConstraintAware...`가 기본 Bean으로 노출
- [ ] rejection rate metric이 `/actuator/metrics`에 노출
- [ ] 모든 rule on 상태에서도 100 reqs 평균 50ms 이내 (성능 테스트)

---

#### #007 — JS 모듈 mount 구조화

| 항목 | 내용 |
|---|---|
| 우선순위 | **P1** · 프론트 |
| 의존성 | — |
| 브랜치 안 | `refactor/js-mount-structure` |

**문제** — `app.js`가 `DOMContentLoaded`에서 5개 feature를 직접 초기화 → 초기화 순서·DOM id 결합 강함.

**변경 파일**
- `src/main/resources/static/js/app.js`
- `src/main/resources/static/js/features/*.js` (이동)
- `src/main/resources/static/js/dom-ids.js` (신규)
- `src/test/js/app.test.js` (신규)

**작업 단계**
1. `static/js/features/` 디렉터리로 recommend/winning/frequency/pagination/theme 이동
2. 각 feature에 `mount(root)` export 시그니처 통일
3. DOM id 문자열을 `dom-ids.js` 상수로 추출
4. `app.js`는 `mount()` 호출 5줄로 축소
5. mount 함수의 단위 테스트 (root 없을 때 noop 등)

**완료 조건 (DoD)**
- [ ] `app.js` 50줄 이하
- [ ] 각 feature가 `mount(root)`만 노출 (default export)
- [ ] DOM id 하드코딩 0건 (grep 검증)
- [ ] 기존 vitest 회귀 통과

---

### 🟡 P2 — 중기

---

#### #008 — 보안 헤더 통합 테스트

| 항목 | 내용 |
|---|---|
| 우선순위 | **P2** · 보안 |
| 의존성 | #004 |
| 브랜치 안 | `test/security-headers` |

**변경 파일**
- `infra/security/SecurityHeaderIT.java` (신규)

**완료 조건 (DoD)**
- [ ] `/` 응답에 CSP 포함 + `unsafe-inline` 미포함 검증
- [ ] admin endpoint 401 시 JSON error schema 검증
- [ ] CSRF ignoring 범위가 의도한 API에만 적용되는지 검증
- [ ] HSTS 헤더 검증 (#004 결과 포함)

---

#### #009 — Profile 정책 명확화 (`KRAFT_ENVIRONMENT`)

| 항목 | 내용 |
|---|---|
| 우선순위 | **P2** · 운영 |
| 의존성 | — |
| 브랜치 안 | `refactor/kraft-environment` |

**문제** — `RequiredConfigValidator`가 container=prod / host=local 강제 → staging·preview·test container 확장 어려움.

**변경 파일**
- `infra/config/RequiredConfigValidator.java`
- `infra/config/EnvironmentResolver.java` (신규)
- README 환경변수 절

**완료 조건 (DoD)**
- [ ] `KRAFT_ENVIRONMENT=local|test|staging|prod` 인식
- [ ] container 여부와 environment가 독립적으로 결정됨
- [ ] staging profile 허용
- [ ] validator error message에 예외 설정 방법 명시

---

#### #010 — Docker image SHA 기반 tag 전략

| 항목 | 내용 |
|---|---|
| 우선순위 | **P2** · 운영 |
| 의존성 | — |
| 브랜치 안 | `ci/image-sha-tag` |

**변경 파일**
- `.github/workflows/cd.yml`
- `scripts/deploy/*.sh`

**완료 조건 (DoD)**
- [ ] `kraft-lotto-app:${GITHUB_SHA}` 빌드
- [ ] 이전 SHA를 `.deploy-state`에 기록 → rollback 시 참조
- [ ] 배포 로그에 image digest 출력
- [ ] `previous` tag 의존 제거

---

#### #011 — 외부 API client resilience 통합

| 항목 | 내용 |
|---|---|
| 우선순위 | **P2** · 백엔드 |
| 의존성 | — |
| 브랜치 안 | `refactor/resilience4j-unify` |

**문제** — `DhLotteryApiClient`의 자체 retry와 Resilience4j 의존성이 혼재.

**변경 파일**
- `feature/winningnumber/application/DhLotteryApiClient.java`
- `feature/winningnumber/application/FailoverLottoApiClient.java`
- `infra/config/ResilienceConfig.java` (신규 또는 수정)

**완료 조건 (DoD)**
- [ ] retry는 Resilience4j `@Retry`로 일원화 (또는 의존성 제거 후 직접 구현 유지)
- [ ] HTTP status / parse error / not-drawn이 별개 예외 타입
- [ ] mock fallback이 prod profile에서 기본 비활성
- [ ] timeout·retry 정책 README 표 1개 추가

---

#### #012 — Error model 상세 구조 정교화

| 항목 | 내용 |
|---|---|
| 우선순위 | **P2** · 백엔드 |
| 의존성 | — |
| 브랜치 안 | `feat/error-model-detail` |

**변경 파일**
- `support/ApiError.java`
- `support/GlobalExceptionHandler.java`
- `support/ErrorCodeHttpStatusMappingTest.java` (신규)

**완료 조건 (DoD)**
- [ ] validation error에 `field`, `path`, `rejectedValue`, `reason` 배열 포함
- [ ] rate limit error에 `retryAfterSeconds`
- [ ] external API failure에 `upstreamStatus`, `retriable`
- [ ] 모든 ErrorCode의 HTTP status 매핑 테스트

---

#### #013 — ArchUnit 경계 규칙 확대

| 항목 | 내용 |
|---|---|
| 우선순위 | **P2** · 백엔드 |
| 의존성 | — |
| 브랜치 안 | `test/archunit-boundaries` |

**변경 파일**
- `src/test/java/.../ArchitectureTest.java`

**완료 조건 (DoD)**
- [ ] `..domain..`은 Spring/JPA/Web 의존 금지
- [ ] `..application..`은 `..web..` 의존 금지
- [ ] `..web..`은 repository 직접 접근 금지
- [ ] `..infrastructure..`는 web DTO 의존 금지

---

#### #014 — 폼 검증 즉시 피드백

| 항목 | 내용 |
|---|---|
| 우선순위 | **P2** · 프론트 |
| 의존성 | #007 (선행하면 좋음) |
| 브랜치 안 | `feat/form-validation` |

**변경 파일**
- `static/js/features/recommend.js`
- `static/js/features/winning.js`
- `templates/*.html` (input min/max 속성)
- `src/test/js/form-validation.test.js` (신규)

**완료 조건 (DoD)**
- [ ] count/round/pagination size 입력 시 즉시 invalid state 표시
- [ ] 서버 validation error의 field가 해당 input에 매핑됨
- [ ] vitest 추가

---

#### #015 — 접근성 보강 (aria-busy / aria-live)

| 항목 | 내용 |
|---|---|
| 우선순위 | **P2** · 프론트 |
| 의존성 | — |
| 브랜치 안 | `feat/a11y-aria` |

**변경 파일**
- `static/js/ui.js`
- `templates/*.html`

**완료 조건 (DoD)**
- [ ] loading skeleton에 `aria-busy="true"`, `role="status"`
- [ ] 오류 메시지 영역에 `aria-live="polite"`
- [ ] color만으로 의미 전달하는 케이스 0건 (수동 검토)
- [ ] axe-core 또는 Playwright a11y 스캔 추가

---

#### #016 — E2E 실패 시나리오 확대

| 항목 | 내용 |
|---|---|
| 우선순위 | **P2** · 테스트 |
| 의존성 | — |
| 브랜치 안 | `test/e2e-failure-scenarios` |

**변경 파일**
- `src/test/e2e/recommend-failure.spec.js` (신규)
- `src/test/e2e/winning-failure.spec.js` (신규)
- `src/test/e2e/mobile.spec.js` (신규)

**완료 조건 (DoD)**
- [ ] 추천 API 429 UI 표시
- [ ] 추천 API validation error UI 표시
- [ ] 최신 당첨번호 API 실패 시 fallback UI
- [ ] 회차 조회 404 UI 표시
- [ ] 모바일 viewport(375x667) 주요 기능 동작

---

#### #017 — 계약 테스트 강화 (OpenAPI ↔ REST Docs)

| 항목 | 내용 |
|---|---|
| 우선순위 | **P2** · 테스트 |
| 의존성 | — |
| 브랜치 안 | `test/contract-openapi` |

**변경 파일**
- `build.gradle.kts` (springdoc 또는 openapi-generator 추가)
- `src/test/java/.../OpenApiSnippetParityTest.java` (신규)

**완료 조건 (DoD)**
- [ ] OpenAPI JSON이 CI artifact로 생성됨
- [ ] 프론트 vitest에서 mock response schema 검증
- [ ] REST Docs snippets endpoint 목록과 OpenAPI endpoint 목록 일치 검증

---

#### #018 — Migration 규칙 문서화 + from-scratch 테스트

| 항목 | 내용 |
|---|---|
| 우선순위 | **P2** · DB |
| 의존성 | — |
| 브랜치 안 | `docs/migration-rules` |

**변경 파일**
- `docs/migration-rules.md` (신규)
- `src/test/java/.../MigrationFromScratchIT.java` (신규)

**완료 조건 (DoD)**
- [ ] migration 파일 naming convention 문서화
- [ ] destructive migration 금지 룰 명시 + checklist
- [ ] index/unique constraint 설계 표 (현재 상태 기준)
- [ ] Testcontainers로 V1~V최신 from-scratch 적용 IT 통과

---

#### #019 — Metric/Alert 기준 문서화

| 항목 | 내용 |
|---|---|
| 우선순위 | **P2** · 관찰성 |
| 의존성 | #006 (rejection rate metric 필요) |
| 브랜치 안 | `docs/metrics-alerts` |

**변경 파일**
- `docs/observability.md` (신규)

**완료 조건 (DoD)** — 다음이 문서화되어 있음:
- [ ] `kraft.api.dhlottery.{call.success,call.failure,call.retry,latency}`
- [ ] `kraft.recommend.{generation.latency,rejection.rate}`
- [ ] DB connection pool / HTTP server 표준 metric
- [ ] alert 룰: readiness 3회 DOWN / external API failure 10분 평균 임계 / recommend timeout / DB pool 80%+

---

#### #020 — `SECURITY.md` 신설

| 항목 | 내용 |
|---|---|
| 우선순위 | **P2** · 문서 |
| 의존성 | — |
| 브랜치 안 | `docs/security-md` |

**완료 조건 (DoD)**
- [ ] 취약점 신고 경로(이메일/이슈)
- [ ] supported versions 표
- [ ] 응답 SLA 명시
- [ ] GitHub의 "Security policy" 탭에 노출 확인

---

#### #021 — `CONTRIBUTING.md` 신설

| 항목 | 내용 |
|---|---|
| 우선순위 | **P2** · 문서 |
| 의존성 | — |
| 브랜치 안 | `docs/contributing-md` |

**완료 조건 (DoD)**
- [ ] 커밋 메시지 convention (`type(scope): subject [#N]`)
- [ ] PR 체크리스트
- [ ] 로컬 개발 환경 셋업 (`./gradlew bootRun` 등)
- [ ] 코드 스타일 가이드 (또는 #027 참조)

---

#### #022 — `docs/ARCHITECTURE.md` 신설

| 항목 | 내용 |
|---|---|
| 우선순위 | **P2** · 문서 |
| 의존성 | — |
| 브랜치 안 | `docs/architecture-md` |

**완료 조건 (DoD)**
- [ ] feature 패키지 구조 다이어그램 (mermaid)
- [ ] application/domain/infrastructure/web 계층 의존 규칙
- [ ] 주요 흐름 시퀀스 다이어그램: 수집 / 추천 / 백필
- [ ] ArchUnit 규칙(#013)과 일치 확인

---

#### #023 — `docs/RUNBOOK.md` 신설

| 항목 | 내용 |
|---|---|
| 우선순위 | **P2** · 문서 |
| 의존성 | — |
| 브랜치 안 | `docs/runbook-md` |

**완료 조건 (DoD)** — 각 시나리오에 대해 증상 / 원인 / 조치 / 검증 절차 포함:
- [ ] 502 Bad Gateway
- [ ] Flyway migration 충돌
- [ ] 백필 잡 재개
- [ ] DH 로또 API 장애 시 mock fallback
- [ ] DB connection pool 고갈
- [ ] rollback 절차

---

### 🟢 P3 — 장기

---

#### #024 — LottoRecommender 시도 횟수 yml 외부화

| | |
|---|---|
| 우선순위 | **P3** · 백엔드 |
| 브랜치 안 | `refactor/recommend-attempts-config` |

`MAX_INITIAL_PICK_ATTEMPTS`, `MAX_FIXUP_ATTEMPTS` → `KraftRecommendProperties.Rules.attempts`로 이전. 기본값은 현재 값 유지.

**DoD**: yml 변경만으로 시도 횟수 조정 가능 + 기본값 회귀 테스트.

---

#### #025 — `isUsableSummary` 방어적 검증

| | |
|---|---|
| 우선순위 | **P3** · 백엔드 |
| 브랜치 안 | `fix/usable-summary-strict-check` |

```java
// 현재: findFirst()로 첫 행만 확인
// 개선: 모든 행의 round 일치 + latest round 일치 동시 검증
summaryRows.stream().map(...::getLastCalculatedRound).distinct().count() == 1
    && summaryRows.get(0).getLastCalculatedRound() == latestRound
```

**DoD**: round mismatch 데이터 fixture로 false 반환 검증 테스트.

---

#### #026 — Logback `discardingThreshold` 조정

| | |
|---|---|
| 우선순위 | **P3** · 운영 |
| 브랜치 안 | `fix/logback-discarding-threshold` |

`FILE_ALL`/`FILE_DEBUG` Async appender: `discardingThreshold=0` → 기본값(20)으로 복원. `FILE_ERROR`/`FILE_WARN`만 0 유지.

**DoD**: `logback-spring.xml` 변경 + 부하 상황에서 ERROR/WARN 누락 0건 확인.

---

#### #027 — Spotless / Checkstyle / shellcheck 도입

| | |
|---|---|
| 우선순위 | **P3** · 품질 |
| 브랜치 안 | `ci/code-quality-tools` |

- Spotless + Palantir Java Format
- Checkstyle (또는 Error Prone)
- `scripts/deploy/*.sh`에 shellcheck CI
- markdownlint for `docs/**`

**DoD**: CI에서 4개 도구 모두 통과 + 기존 코드 일괄 포맷 PR 분리.

---

#### #028 — 조회 성능 인덱스 재검토

| | |
|---|---|
| 우선순위 | **P3** · DB |
| 브랜치 안 | `perf/query-indexes` |

- round unique index 점검
- draw date index 추가 여부
- n1~n6 번호별 통계 조회 인덱스 분석
- 조합 이력의 OR 조건이 많으면 normalized table 또는 materialized summary 검토

**DoD**: EXPLAIN 결과를 `docs/db-perf.md`에 기록 + 필요 시 V7 migration.

---

#### #029 — Vite 도입 및 OpenAPI→TS 타입 생성

| | |
|---|---|
| 우선순위 | **P3** · 프론트 |
| 브랜치 안 | `feat/frontend-build-pipeline` |
| 의존성 | #007, #017 |

`src/main/frontend/` 분리 → Vite 빌드 → 산출물을 `static/`으로 복사. OpenAPI(#017) → TypeScript 타입 자동 생성.

**DoD**: cache busting / minification / tree-shaking 동작 + Spring Boot 부팅 시 정적 자원 정상 서빙.

---

#### #030 — 환경변수 표준표 + `docs/configuration.md`

| | |
|---|---|
| 우선순위 | **P3** · 문서 |
| 브랜치 안 | `docs/configuration-md` |

| 컬럼 | 내용 |
|---|---|
| env var | 변수명 |
| profile | local/staging/prod 적용 여부 |
| required | 필수 여부 |
| default | 기본값 |
| secret | 비밀 여부 |
| 설명 | 용도 |
| 관련 property | application property 매핑 |

**DoD**: 모든 `${ENV_VAR}` 참조가 표에 1:1 매핑.

---

#### #031 — Java 21 LTS 백포팅 옵션 검토

| | |
|---|---|
| 우선순위 | **P3** · 운영 |
| 브랜치 안 | (검토 단계) |

Java 25 + Spring Boot 4.0.5의 라이브러리 호환성 매트릭스 검증 결과에 따라:

- **A안**: Java 25 유지 — 호환성 matrix 문서화 + self-hosted runner/Docker/local JDK 버전 통일
- **B안**: Java 21 LTS 백포팅 — Spring Boot 안정 라인으로 변경, Records/패턴 매칭 외 차이점 정리

**DoD**: 결정 문서 1편 + 채택 안의 ADR(Architecture Decision Record) 작성.

---

## 3. Sprint 로드맵

### Sprint 1 (다음 1주)
**#001 · #002 · #004 · #005**

분산 락 + 잡 영속화 + HSTS + Redis compose. 인프라/보안 기반 완성.

### Sprint 2 (다음 2주)
**#003 · #006 · #007 · #008**

Transaction 경계 + 추천 엔진 결정성 + 프론트 mount 구조화 + 보안 헤더 테스트.

### Sprint 3 (1개월차 후반)
**#009 · #010 · #011 · #012 · #013**

운영(profile/SHA tag) + 백엔드 정교화(resilience/error/ArchUnit).

### Sprint 4 (2개월차)
**#014 · #015 · #016 · #017 · #018 · #019**

프론트 UX + 테스트 확장 + DB/관찰성 문서화.

### Sprint 5 (2개월차 후반)
**#020 · #021 · #022 · #023**

문서 정비 4종.

### Sprint 6+ (분기)
**#024 ~ #031**

장기 개선.

---

## 4. 운영 원칙

- **1 단위 = 1 PR**: 단위를 쪼개고 합치지 말 것. 단위 의존성이 강하면 인덱스의 "의존성" 칸에 명시.
- **DoD 미충족 PR 머지 금지**: 모든 체크박스가 채워졌는지 리뷰어가 확인.
- **커밋 메시지에 단위 ID**: `[#NNN]` 접미 (예: `feat(collect): apply distributed lock [#001]`).
- **단위 완료 시 본 문서에서 제거**: 인덱스 행과 상세 섹션 모두 삭제. 이력은 git으로 남김.
- **분기 1회 점수표 재평가**: 별도 문서나 PR 본문에서 갱신.
- **P0는 본 문서에 두지 않음**: 발견 즉시 hotfix로 처리.
