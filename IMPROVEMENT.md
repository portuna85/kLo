# kLo 프로젝트 개선 분석 보고서

검토 대상: `portuna85/kLo` / 기본 브랜치 `main`  
검토 방식: `git clone` 없이 GitHub 저장소 파일 조회와 코드 검색 결과를 기준으로 분석  
작성일: 2026-05-14

## 1. 총평

kLo는 로또 당첨번호 수집, 조회, 통계, 추천 기능을 제공하는 Spring Boot 기반 서비스이다. 단순 예제 수준을 넘어서 운영 배포, 관리자 API 보호, 스케줄링, 외부 API failover, rate limit, Docker Compose, CI/CD, REST Docs, 단위/통합/프론트 테스트까지 갖춘 상태다.

프로젝트 수준은 **중상급 개인/소규모 실서비스 프로토타입**으로 판단한다. 백엔드는 설계 의식이 높고 운영 안전장치도 많은 편이다. 다만 프론트는 정적 ES module 기반으로 유지보수 한계가 있고, 설정/배포는 기능은 많지만 복잡도가 높으며, Java 25와 Spring Boot 4.0.5 조합은 최신성이 강한 대신 운영 보수성 측면에서는 리스크가 있다.

## 2. 영역별 수준 평가

| 영역 | 수준 | 평가 |
|---|---:|---|
| 백엔드 구조 | 8 / 10 | feature 단위 패키징, application/domain/infrastructure/web 분리, 예외/응답 공통화, 스케줄/락/헬스체크/메트릭 반영이 좋다. 다만 일부 서비스 조립 책임이 서비스 내부에 남아 있고, API 버전 정책과 도메인 경계가 더 명확해질 필요가 있다. |
| 도메인/비즈니스 로직 | 7.5 / 10 | 추천 규칙과 당첨번호 모델이 비교적 분리되어 있다. 다만 추천 로직이 확률적 생성과 필터링 중심이라 결정성/재현성/성능 보증을 더 명시해야 한다. |
| 프론트엔드 | 5.5 / 10 | 정적 JS 모듈로 구현되어 배포 단순성은 좋다. 그러나 상태 관리, 컴포넌트화, 접근성, API 타입 공유, 번들링/최적화 측면에서 확장성이 낮다. |
| 테스트 | 7.5 / 10 | JUnit, Mockito, Testcontainers, ArchUnit, Vitest, Playwright가 존재한다. 테스트 종류는 우수하나 커버리지 기준이 단일 70%이고, mutation/security/performance 테스트는 없다. |
| 설정/운영 | 7 / 10 | Dockerfile, Compose, 환경 검증, healthcheck, CI/CD, 로그/메트릭이 갖춰져 있다. 다만 CD workflow가 길고 서버 의존성이 강하며, `.env.example` 인코딩 손상 문제가 있다. |
| 보안 | 7 / 10 | 관리자 토큰, CSP, stateless security, rate limit이 존재한다. 다만 관리자 토큰 인증 방식은 운영 수준에서 rotation/audit/hash 저장으로 강화해야 한다. |
| 문서화 | 6.5 / 10 | README와 REST Docs 흐름이 있다. 운영 runbook, 장애 대응, API versioning, 환경변수 표준 문서가 더 필요하다. |

## 3. 파악한 프로젝트 구조

### 3.1 루트 및 빌드/런타임 파일

- `README.md`: 프로젝트 목적, API, 운영 정책, 테스트 전략 요약.
- `build.gradle.kts`: Java 25, Spring Boot 4.0.5, JPA, Flyway, Security, Redis, Caffeine, Resilience4j, ShedLock, Micrometer, Testcontainers, ArchUnit, REST Docs, Jacoco 설정.
- `settings.gradle.kts`: Gradle root project `kraft-lotto`.
- `gradlew`, `gradle/wrapper/gradle-wrapper.properties`: Gradle wrapper.
- `Dockerfile`: multi-stage Docker build, Temurin 25 JDK/JRE, non-root user, healthcheck.
- `docker-compose.yml`: MariaDB 11.8.2와 app 서비스, healthcheck, env file, bind mount logs.
- `.env.example`: 로컬/운영 환경변수 예시. 현재 한글 주석이 깨져 있어 즉시 수정 필요.
- `.gitignore`: build, IDE, logs, `.env`, node_modules 제외. `IMPROVEMENTS.md`, `kLo-analysis.md`도 제외.
- `package.json`, `package-lock.json`: 프론트 테스트용 Node/Vitest/Playwright/TypeScript 의존성.
- `tsconfig.json`: JS typecheck 설정으로 추정.
- `vitest.config.js`: JS unit test 설정.
- `playwright.config.js`: E2E test 설정.

### 3.2 백엔드 주요 패키지

- `com.kraft.lotto.KraftLottoApplication`: Spring Boot 진입점.
- `feature/recommend`
  - `web`: `RecommendController`, 요청/응답 DTO.
  - `application`: `RecommendService`, `LottoRecommender`, `LottoNumberGenerator`, `RandomLottoNumberGenerator`, `ConstraintAwareLottoNumberGenerator`, `PastWinningCacheLoader`, `RecommendConfiguration`, `RecommendRuleConfig`.
  - `domain`: `ExclusionRule`, `PastWinningRule`, `ArithmeticSequenceRule`, `BirthdayBiasRule`, `LongRunRule`, `SingleDecadeRule`, `PastWinningCache`.
- `feature/winningnumber`
  - `web`: `WinningNumberController`, `WinningNumberCollectController`, `AdminLottoDrawController`, `AdminLottoJobController`, validation, DTO.
  - `application`: `LottoCollectionService`, `LottoCollectionCommandService`, `LottoSingleDrawCollector`, `LottoRangeCollector`, `WinningNumberQueryService`, `WinningNumberPersister`, `WinningNumberAutoCollectScheduler`, `LottoFetchLogRetentionScheduler`, `DhLotteryApiClient`, `MockLottoApiClient`, `FailoverLottoApiClient`, parser/config/client interface.
  - `domain`: `WinningNumber`, `LottoCombination`.
  - `infrastructure`: `WinningNumberEntity`, `WinningNumberRepository`, `LottoFetchLogEntity`, `LottoFetchLogRepository`, `LottoFetchStatus`.
  - `event`: `WinningNumbersCollectedEvent`.
- `infra/config`: Jackson, properties binding, datasource URL auto-fix, dotenv post-processor, required config validator, scheduler lock config.
- `infra/security`: Spring Security config, admin token filter, recommend/collect rate limit filter, API paths.
- `infra/health`: 외부 로또 API health indicator.
- `support`: `ApiResponse`, `ApiError`, `BusinessException`, `ErrorCode`, `GlobalExceptionHandler`, `ClientIpResolver`.

### 3.3 리소스/문서/DB

- `src/main/resources/application.yml`: 공통 설정. local/prod profile 파일도 CI에서 존재를 검증한다.
- `src/main/resources/META-INF/spring.factories`: EnvironmentPostProcessor 등록.
- `src/main/resources/static/js/*`: 정적 JS 프론트.
- `src/main/resources/static/css/*`, `templates/*`: 정적 UI 및 Thymeleaf 화면으로 추정.
- `src/docs/asciidoc/index.adoc`: REST Docs/AsciiDoc 문서 생성 대상.
- `src/main/resources/db/migration/*`: Flyway migration으로 추정.

### 3.4 테스트

- Java 단위 테스트: domain, application, config, security, health, controller 테스트가 확인된다.
- 통합 테스트: `WinningNumberRepositoryIT` 등 Testcontainers/MariaDB 성격의 테스트가 있다.
- 아키텍처 테스트: `ArchitectureTest`가 존재한다.
- 프론트 unit test: `src/test/js/api.test.js`, `pagination.test.js`.
- E2E test: `src/test/e2e/home.spec.js`.
- 테스트 리소스: `src/test/resources/application-test.yml`, `application-it.yml`.

## 4. 최우선 개선 항목 요약

| 우선순위 | 단위 | 개선 항목 | 이유 |
|---|---|---|---|
| P0 | 설정/문서 | `.env.example` 한글 인코딩 깨짐 수정 | 신규 개발자 온보딩과 운영 설정 문서의 신뢰성을 바로 훼손한다. |
| P0 | 보안 | 관리자 토큰 저장/검증 방식 강화 | plain token 비교 중심이면 유출·rotation·감사 추적이 취약하다. |
| P0 | CI/CD | CD workflow 분리와 rollback 검증 자동화 | 현재 CD가 길고 self-hosted runner/서버 상태에 강하게 결합되어 있다. |
| P1 | 백엔드 | application service 조립 책임 제거 | `LottoCollectionService`가 하위 collaborator를 직접 조립해 테스트성과 DI 투명성이 떨어진다. |
| P1 | API | `/api`와 `/api/v1` 혼재 정책 정리 | 호환성은 좋지만 장기적으로 클라이언트 혼선과 테스트 중복을 만든다. |
| P1 | 프론트 | 정적 JS 모듈 체계를 더 엄격하게 구조화 | 현재는 작은 규모에 적합하나 기능 증가 시 결합도와 DOM 의존성이 빠르게 증가한다. |
| P1 | 테스트 | 통합/E2E/성능 테스트 기준 강화 | 현재 테스트 종류는 많으나 운영 병목과 장애 시나리오 검증은 부족하다. |
| P2 | 운영 | 관측성 대시보드/알람 문서화 | 메트릭은 있으나 실제 운영 기준과 알람 룰이 문서화되어야 한다. |

## 5. 백엔드 개선 사항

### 5.1 서비스 조립 책임을 Spring Configuration으로 이동

현재 `LottoCollectionService` 생성자 안에서 `LottoSingleDrawCollector`, `LottoRangeCollector`, `LottoCollectionCommandService`, `LottoCollectionGate`를 직접 조립한다. 이는 동작상 문제는 없지만 application service가 factory 역할까지 수행한다.

개선안:

- `LottoCollectionConfiguration`을 만들고 collaborator를 `@Bean`으로 분리한다.
- `LottoCollectionService`는 `LottoCollectionCommandService`만 주입받게 한다.
- 테스트용 `forTest` factory는 fixture builder로 이동한다.

기대 효과:

- 구성과 비즈니스 로직 분리.
- 단위 테스트 fixture 간소화.
- decorator, tracing, transaction, retry 정책 적용이 쉬워진다.

### 5.2 transaction 경계 명시

수집, 저장, 조회, 백필에 대해 `@Transactional(readOnly = true)`와 write transaction 경계를 명시해야 한다.

권장:

- `WinningNumberQueryService`: `@Transactional(readOnly = true)`.
- `WinningNumberPersister`, `LottoSingleDrawCollector` 저장 구간: write transaction.
- range/backfill은 회차별 transaction 또는 chunk transaction으로 명시.

주의점:

- 외부 API 호출을 DB transaction 내부에서 수행하지 않도록 한다.
- batch insert/update 정책과 unique constraint 충돌 처리를 명확히 한다.

### 5.3 API 버전 정책 정리

SecurityConfig에서 legacy `/api/recommend`, `/api/winning-numbers/**`와 `/api/v1/**`가 함께 허용된다. 호환성은 장점이지만 정책이 문서화되어 있지 않으면 API 표면이 커진다.

개선안:

- Public API canonical path를 `/api/v1`로 고정.
- legacy path는 deprecation 응답 헤더 추가.
- README와 REST Docs에 지원 종료 정책 명시.
- controller 테스트는 canonical path 중심으로 줄이고 legacy는 호환성 테스트만 유지.

### 5.4 추천 엔진 결정성/성능 보증 강화

추천 엔진은 `maxAttempts` 안에서 랜덤 생성 후 규칙으로 제외하는 구조다. 필터 규칙이 강해질수록 rejection rate가 높아지고 timeout 가능성이 커진다.

개선안:

- seed 기반 재현 가능한 generator 옵션 추가.
- rejection rate 임계치 초과 시 경고 로그/metric tag 추가.
- `maxAttempts`, `count`, rule set별 최악 성능 테스트 추가.
- `ConstraintAwareLottoNumberGenerator`를 기본 전략으로 승격해 불필요 후보 생성을 줄인다.

### 5.5 외부 API client resilience 정리

`DhLotteryApiClient`는 retry, content-type/body validation, metric을 포함한다. 다만 build.gradle에는 Resilience4j가 있으나 실제 retry/circuit-breaker 적용은 코드에서 직접 구현된 retry와 섞일 가능성이 있다.

개선안:

- retry는 Resilience4j Retry/CircuitBreaker로 통합하거나, 직접 retry 구현이면 의존성 제거.
- 외부 API HTTP status, parse error, not drawn 상태를 타입으로 분리.
- API client timeout/retry 정책을 운영 문서에 표로 정리.
- mock fallback은 prod에서 기본 금지, emergency flag로만 허용.

### 5.6 error model 정교화

공통 `ApiResponse`, `ApiError`, `ErrorCode`, `GlobalExceptionHandler`가 있으므로 기본 구조는 좋다. 개선할 부분은 클라이언트가 처리하기 쉬운 오류 상세 구조다.

개선안:

- validation error에 field/path/rejectedValue/reason 배열 제공.
- rate limit error에 retryAfterSeconds 포함.
- external API failure에 upstreamStatus, retriable 여부 포함.
- error code별 HTTP status 매핑 테스트 추가.

### 5.7 domain/infrastructure 경계 강화

현재 feature 패키징은 적절하다. 다음 단계에서는 domain에서 infrastructure 타입을 절대 알지 않도록 ArchUnit 규칙을 더 엄격히 해야 한다.

권장 ArchUnit 규칙:

- `..domain..`은 Spring/JPA/Web 의존 금지.
- `..application..`은 `..web..` 의존 금지.
- `..web..`은 repository 직접 접근 금지.
- `..infrastructure..`는 web DTO 의존 금지.

## 6. 프론트엔드 개선 사항

### 6.1 정적 JS 구조 정리

현재 `app.js`가 DOMContentLoaded에서 theme, recommend, winning, frequency, pagination 초기화를 호출한다. 작은 앱에는 적합하지만 기능이 늘면 초기화 순서와 DOM id 결합이 문제된다.

개선안:

- `modules/` 또는 `features/` 기준으로 정리.
- 각 feature는 `mount(root)` 함수만 노출.
- DOM id 문자열은 상수화.
- API response typedef를 별도 `types.d.ts` 또는 JSDoc typedef로 통합.

### 6.2 사용자 입력 검증을 프론트에서도 수행

추천 count, round, pagination size/page 등은 서버 검증이 기본이지만 프론트에서도 즉시 피드백을 제공해야 한다.

개선안:

- input min/max와 JS validation 동시 적용.
- form submit 전에 invalid state 표시.
- 서버 validation error의 field 정보를 UI에 매핑.

### 6.3 접근성 개선

로또 번호 공(ball) UI는 시각적으로는 좋지만 스크린리더가 의미를 제대로 읽지 못할 수 있다.

개선안:

- 번호 조합 row에 `aria-label="추천 조합 1: 3, 12, 18, ..."` 추가.
- loading skeleton에 `aria-busy`, `role="status"` 적용.
- 오류 메시지 영역에 `aria-live="polite"` 적용.
- color만으로 번호 의미를 전달하지 않도록 텍스트 보강.

### 6.4 빌드 없는 프론트 전략의 한계 보완

현재 프론트는 별도 번들링 없이 정적 모듈로 운영된다. 단순 배포에는 유리하지만 cache busting, minification, tree-shaking, dependency management가 제한된다.

선택지:

- 단기: 파일명 또는 query hash 기반 cache busting을 Thymeleaf에서 처리.
- 중기: Vite를 도입해 `src/main/frontend`로 분리하고 산출물을 `static/`으로 복사.
- 장기: API schema 기반 타입 생성(OpenAPI -> TypeScript)을 도입.

## 7. 테스트 개선 사항

### 7.1 커버리지 정책 세분화

현재 Jacoco 70% 기준은 출발점으로 적절하나 전체 평균만으로는 핵심 로직 누락을 놓칠 수 있다.

개선안:

- domain/application 패키지 최소 80~85%.
- infra/config/security 최소 70%.
- controller는 주요 status code/response schema 중심.
- generated/config DTO는 제외 기준 명시.

### 7.2 mutation test 도입

추천 규칙, 회차 검증, 당첨번호 validation은 조건문이 많아 mutation test 효과가 크다.

권장 도구:

- PIT Mutation Testing.
- 대상: `feature/recommend/domain`, `feature/winningnumber/domain`, `support/ErrorCode` 주변.

### 7.3 E2E 테스트 확대

현재 `home.spec.js`가 존재한다. 기능별 happy path 외에 실패 시나리오가 필요하다.

추가 시나리오:

- 추천 API 429 표시.
- 추천 API validation error 표시.
- 최신 당첨번호 API 실패 시 fallback UI.
- 회차 조회 404 표시.
- 모바일 viewport에서 주요 기능 동작.

### 7.4 계약 테스트 추가

백엔드 API와 정적 JS가 직접 결합되어 있으므로 contract drift가 발생하기 쉽다.

개선안:

- OpenAPI JSON을 CI artifact로 생성.
- 프론트 테스트에서 mock response schema 검증.
- REST Docs snippets와 OpenAPI 문서의 endpoint 목록 일치 검증.

## 8. 설정/운영/DevOps 개선 사항

### 8.1 `.env.example` 인코딩 손상 수정

현재 `.env.example`의 한글 주석이 깨져 있다. 이는 가장 먼저 수정해야 할 문서 품질 문제다.

조치:

- 파일을 UTF-8 without BOM으로 재저장.
- 깨진 주석을 정상 한국어로 복원.
- CI에 `file -bi .env.example` 또는 간단한 UTF-8 decode check 추가.
- Windows 편집 환경을 고려해 `.editorconfig` 추가.

권장 `.editorconfig`:

```ini
root = true

[*]
charset = utf-8
end_of_line = lf
insert_final_newline = true
trim_trailing_whitespace = true

[*.{md,yml,yaml}]
trim_trailing_whitespace = false
```

### 8.2 CD workflow 분리

현재 `.github/workflows/cd.yml`은 secret 검증, `.env` 생성, artifact 다운로드, Docker Compose 배포, readiness 확인, rollback까지 한 파일에 들어 있다. 기능은 좋지만 유지보수 난도가 높다.

개선안:

- `scripts/deploy/validate-env.sh`
- `scripts/deploy/render-env.sh`
- `scripts/deploy/wait-readiness.sh`
- `scripts/deploy/rollback.sh`
- workflow는 각 script 호출만 담당.

기대 효과:

- 로컬에서 배포 스크립트 재현 가능.
- shellcheck 적용 가능.
- workflow 가독성 개선.

### 8.3 Docker image tag 전략 개선

현재 compose image는 `kraft-lotto-app:local` 중심이다. 운영 배포에서는 commit SHA 기반 tag가 필요하다.

개선안:

- `kraft-lotto-app:${GITHUB_SHA}` build.
- `kraft-lotto-app:previous` rollback 대신 이전 SHA 기록.
- 이미지 digest를 배포 로그에 출력.
- 장기적으로 GHCR 또는 private registry 사용.

### 8.4 Java/Spring 버전 운영 리스크 관리

Java 25와 Spring Boot 4.0.5는 최신 조합이다. 최신 기능을 쓰는 것은 좋지만, 운영 안정성과 라이브러리 호환성 검증이 필요하다.

개선안:

- 운영 안정 우선이면 Java 21 LTS + 안정 Spring Boot 라인을 검토.
- Java 25 유지 시 호환성 matrix 문서화.
- self-hosted runner, Docker image, local dev JDK 버전을 모두 동일하게 고정.

### 8.5 profile 정책 완화 또는 명확화

`RequiredConfigValidator`는 container면 prod, host면 local을 강제한다. 실수 방지에는 좋지만 staging, preview, test container 같은 확장 환경에서 제약이 된다.

개선안:

- `KRAFT_ENVIRONMENT=local|test|staging|prod` 도입.
- container 여부와 profile 정책을 분리.
- staging profile 허용.
- validator error message에 예외 설정 방법 명확화.

## 9. 보안 개선 사항

### 9.1 관리자 토큰 해시 검증

관리자 API 토큰은 plain 환경변수로 들어온다. 환경변수 자체는 일반적이지만, 애플리케이션 내부 비교와 로그/디버그 노출 리스크를 줄여야 한다.

개선안:

- 토큰 원문 대신 SHA-256 또는 bcrypt 해시 목록 지원.
- constant-time comparison 적용.
- token id prefix를 도입해 audit log에 원문 없이 식별.
- rotation을 위해 복수 토큰에 `id:hash` 형식 지원.

### 9.2 rate limit 저장소 전략 정리

현재 Redis rate limit 옵션은 있으나 기본 false이고 Docker Compose에는 Redis가 없다. 단일 인스턴스에서는 in-memory도 가능하지만 다중 인스턴스 운영에서는 부정확하다.

개선안:

- 운영 prod는 Redis rate limit 필수 여부 결정.
- Redis를 쓸 경우 docker-compose.prod 또는 external Redis 설정 문서화.
- `X-Forwarded-For` 신뢰 설정은 trusted proxy 검증과 함께만 활성화.

### 9.3 보안 헤더 테스트 추가

CSP, Referrer Policy, Permissions Policy가 설정되어 있으므로 이를 테스트로 고정해야 한다.

추가 테스트:

- `/` 응답에 CSP 존재.
- script-src에 unsafe-inline 미포함 여부 확인.
- admin endpoint unauthorized 시 JSON error schema 확인.
- CSRF ignoring 범위가 의도한 API에만 적용되는지 검증.

## 10. 데이터베이스 개선 사항

### 10.1 migration 품질 기준 추가

Flyway가 사용된다. migration은 운영 데이터 보존과 직결되므로 별도 규칙이 필요하다.

개선안:

- migration 파일 naming convention 문서화.
- destructive migration 금지 룰 추가.
- index/unique constraint 설계 표 작성.
- Testcontainers로 migration from scratch 테스트.
- 운영 백업/복구 runbook 작성.

### 10.2 조회 성능 인덱스 검토

당첨번호 조회/통계/조합 이력은 회차와 번호 컬럼을 자주 사용한다.

검토 대상:

- round unique index.
- draw date index.
- n1~n6 번호별 통계 조회 방식.
- 조합 이력 조회가 OR 조건을 많이 쓰면 별도 normalized table 또는 materialized summary 검토.

## 11. 문서화 개선 사항

### 11.1 README와 운영 문서 분리

README는 현재 프로젝트 소개와 실행 정보를 제공한다. 다음 단계에서는 문서를 분리하는 것이 좋다.

권장 구조:

- `docs/architecture.md`
- `docs/api-versioning.md`
- `docs/operations-runbook.md`
- `docs/configuration.md`
- `docs/deployment.md`
- `docs/troubleshooting.md`

### 11.2 환경변수 표준표 작성

필수/선택, local/prod 기본값, 보안 여부, 예시, 설명을 표로 정리한다.

필수 표 컬럼:

- env var
- profile
- required
- default
- secret 여부
- 설명
- 관련 application property

## 12. 코드 품질 개선 사항

### 12.1 formatting/lint 도입

현재 Java formatting 도구가 명시되어 있지 않다.

권장:

- Spotless + Google Java Format 또는 Palantir Java Format.
- Checkstyle 또는 Error Prone 검토.
- shellcheck for deploy scripts.
- markdownlint for docs.

### 12.2 dependency audit 도입

권장 CI 단계:

- Gradle dependency vulnerability scan.
- npm audit 또는 osv-scanner.
- Docker image scan.
- GitHub Dependabot 설정.

### 12.3 observability 기준 문서화

이미 Micrometer와 tracing 관련 설정이 있다. 실제 운영 기준으로 승격해야 한다.

권장 metric:

- `kraft.api.dhlottery.call.success/failure/retry`
- `kraft.api.dhlottery.latency`
- `kraft.recommend.generation.latency`
- `kraft.recommend.rejection.rate`
- DB connection pool metrics
- HTTP server request latency/status

권장 alert:

- readiness DOWN 3회 연속.
- external API failure rate 10분 평균 임계치 초과.
- recommend timeout 발생.
- DB pool active/max 비율 80% 이상 지속.

## 13. 개선 실행 순서

### Sprint 1: 즉시 품질 복구

1. `.env.example` UTF-8 복원.
2. `.editorconfig` 추가.
3. README에 Java/Spring/Node 버전 고정 표 추가.
4. API versioning 정책 문서 추가.
5. Security header 테스트 추가.

### Sprint 2: 구조 안정화

1. `LottoCollectionService` 조립 책임을 Configuration으로 이동.
2. transaction 경계 명시.
3. legacy API deprecation header 추가.
4. recommendation rejection rate 임계치/테스트 추가.
5. ArchUnit 규칙 확대.

### Sprint 3: 운영 강화

1. CD workflow shell script 분리.
2. Docker image tag를 commit SHA 기반으로 변경.
3. rollback runbook 작성.
4. Redis rate limit 운영 여부 결정 및 문서화.
5. metric dashboard/alert 초안 작성.

### Sprint 4: 프론트 확장성 강화

1. JS module 구조를 feature mount 방식으로 정리.
2. 접근성 속성 추가.
3. API typedef 통합.
4. E2E 실패 시나리오 추가.
5. 필요 시 Vite 도입 검토.

## 14. 권장 PR 단위

1. `fix: restore env example encoding and add editorconfig`
2. `test: assert security headers and admin auth failures`
3. `refactor: move lotto collection wiring to configuration`
4. `docs: define api versioning and environment variables`
5. `ci: split deployment shell scripts from workflow`
6. `ops: tag docker images by commit sha`
7. `test: add recommendation performance and rejection tests`
8. `feat: add admin token hash verification support`
9. `frontend: improve accessibility and typed API helpers`
10. `docs: add operations runbook and rollback guide`

## 15. 결론

이 프로젝트는 이미 단순 CRUD나 학습용 샘플을 넘어섰다. 특히 백엔드 운영 고려사항, 테스트 폭, CI/CD 구성은 좋은 편이다. 다음 성숙도 단계로 가려면 기능 추가보다 먼저 다음 세 가지를 우선해야 한다.

1. 깨진 설정 문서와 환경변수 체계 복구.
2. 백엔드 조립/transaction/API version 정책 정리.
3. CD와 운영 보안/관측성 기준의 문서화 및 자동화.

위 항목을 처리하면 현재의 “잘 만든 개인 실서비스 프로토타입”에서 “유지보수 가능한 소규모 운영 서비스” 수준으로 올라갈 수 있다.
