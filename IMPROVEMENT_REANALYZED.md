# kLo 개선사항 재분석 보고서

검토 대상: `portuna85/kLo` / `main`  
검토 방식: GitHub 저장소 파일 직접 조회 기준, `git clone` 제외  
작성일: 2026-05-14  
평가 초점: 이미 반영된 개선사항을 제외하고, 현재 코드 기준으로 남은 고도화·최적화 항목만 재정리

---

## 1. 총평

현재 kLo는 이전 보고서 작성 시점보다 훨씬 더 고도화되어 있다. `LottoCollectionConfiguration`을 통한 수집 컴포넌트 Bean 분리, `test`/`integrationTest` 태그 분리, PIT 설정, OpenAPI contract drift guard, 관리자 토큰 hash 지원, deploy script 분리, UTF-8 검사, frequency summary table 등이 이미 반영되어 있다.

따라서 기존 `IMPROVEMENT.md`의 여러 항목은 더 이상 현재 상태와 맞지 않는다. 특히 다음 항목은 “해야 할 일”이 아니라 “이미 상당 부분 완료된 일”로 재분류해야 한다.

- 수집 서비스 조립 책임 분리
- 관리자 토큰 hash/id 검증
- CD workflow script 분리
- Java test와 integration test 분리
- PIT mutation test 설정
- legacy API deprecation header
- OpenAPI endpoint drift guard
- UTF-8 검사 스크립트
- frequency summary table 도입

현재 프로젝트 수준은 **8.1 / 10** 정도로 판단한다. 단순 개인 프로젝트가 아니라, 운영을 전제로 한 소규모 서비스 후보 수준이다. 남은 병목은 문서화가 아니라 **계층 순도, rollback 정확성, 통계 refresh 구조, 비동기 backfill 관리, CI 범위, 설정 정합성**이다.

---

## 2. 현재 수준 재평가

| 영역 | 점수 | 판단 |
|---|---:|---|
| 백엔드 구조 | 8.4 / 10 | feature 기반 구조와 domain/infrastructure/web 분리는 좋다. 다만 application 계층이 web DTO에 의존하는 문제가 남아 있다. |
| 수집/스케줄링 | 8.2 / 10 | single/range/gate/command 분리와 scheduler lock이 좋다. 다만 backfill job 상태 관리와 cancel/shutdown 정책이 약하다. |
| 추천 엔진 | 8.0 / 10 | constraint-aware generator, maxAttempts, rejection metric이 있다. rule별 관측성은 부족하다. |
| 통계/성능 | 7.8 / 10 | frequency summary table이 도입됐다. 그러나 summary 조회와 갱신이 같은 service method에 섞여 있다. |
| 보안 | 8.3 / 10 | token hash, constant-time 비교, audit log, rate-limit이 있다. actuator/prometheus 접근 제어와 Redis 정책 정합성은 보완해야 한다. |
| 프론트엔드 | 6.9 / 10 | 정적 ES module 구조가 개선됐다. API path 상수화, response typedef, E2E 확대가 남아 있다. |
| 테스트 | 8.2 / 10 | 단위/통합/계약/아키텍처/PIT 설정이 좋다. E2E, PIT, perf가 정규 CI에 들어가 있지는 않다. |
| CI/CD | 8.0 / 10 | script 분리와 SHA tag 기반 배포 방향은 좋다. rollback state 처리에 실제 오류 가능성이 있다. |
| 설정/환경변수 | 7.8 / 10 | 외부화가 잘 되어 있다. prod Redis 기본값과 CD 주입값의 정책 충돌이 남아 있다. |

---

## 3. 완료된 개선사항으로 재분류할 항목

### 3.1 수집 컴포넌트 Bean 분리

`LottoCollectionConfiguration`이 `LottoSingleDrawCollector`, `LottoRangeCollector`, `LottoCollectionGate`, `LottoCollectionCommandService`를 Bean으로 구성한다. 기존 문서의 “서비스 조립 책임을 Spring Configuration으로 이동”은 대부분 완료된 항목이다.

남은 것은 production class 안의 `LottoCollectionService.forTest(...)` 제거 또는 test fixture 이동 정도다.

### 3.2 테스트 태그 분리와 PIT 설정

`build.gradle.kts`는 일반 `test`에서 `excludeTags("it")`, `integrationTest`에서 `includeTags("it")`를 사용한다. PIT plugin과 mutation threshold도 설정되어 있다. 기존 문서의 “mutation test 도입”은 “CI 적용 범위 결정”으로 수정해야 한다.

### 3.3 관리자 token hash 지원

`KraftAdminProperties`는 `api-token-hashes`를 지원하고, `AdminApiTokenFilter`는 `id:sha256hex` 기반 hash 검증을 수행한다. 기존 문서의 “관리자 토큰 hash 검증 도입”은 완료된 항목이다.

남은 개선은 token rotation 운영 방식, hash-only 운영 전환, token id naming convention 정도다.

### 3.4 CD script 분리

CD workflow는 이미 `scripts/deploy/*.sh` 호출 구조로 바뀌었다. 기존 문서의 “CD workflow 분리”는 완료된 항목이다.

남은 개선은 script 자체의 정확성, rollback 검증, shellcheck, 배포 상태 파일 관리다.

### 3.5 API deprecation과 contract guard

`LegacyApiDeprecationHeaderFilter`와 `ApiContractDriftTest`가 추가되어 있다. 기존 문서의 “legacy API deprecation header 추가”와 “계약 테스트 추가”는 일부 완료된 항목이다.

---

## 4. P0 개선사항

### P0-1. application 계층의 web DTO 의존 제거

현재 가장 중요한 구조 문제다. 여러 application class가 `feature.*.web.dto` 타입을 직접 import하거나 반환한다.

확인된 예:

- `WinningStatisticsService` → `NumberFrequencyDto`, `FrequencySummaryDto`, `CombinationPrizeHistoryDto`, `CombinationPrizeHitDto`
- `WinningNumberQueryService` → `WinningNumberDto`, `WinningNumberPageDto`, 통계 DTO
- `RecommendService` → `RecommendResponse`, `CombinationDto`, `RuleDto`
- `LottoCollectionService`, `LottoRangeCollector`, `LottoSingleDrawCollector`, `LottoCollectionCommandService`, `LottoCollectionGate`, `BackfillJobService` → `CollectResponse`, `BackfillJobStatusResponse`

문제점:

- application 계층이 HTTP 응답 모델에 결합된다.
- API v2 또는 internal API를 만들 때 service layer까지 흔들린다.
- ArchUnit이 domain 보호는 하지만 application → web 의존을 막지 않는다.
- DTO 변경이 비즈니스 테스트까지 전파된다.

개선안:

1. application result model 추가
   - `RecommendResult`
   - `RecommendedCombinationResult`
   - `RuleResult`
   - `WinningNumberResult`
   - `WinningNumberPageResult`
   - `NumberFrequencyResult`
   - `FrequencySummaryResult`
   - `CombinationPrizeHistoryResult`
   - `CollectResult`
   - `BackfillJobStatusResult`

2. web 계층에서 response DTO 변환
   - Controller 또는 `*ResponseMapper`에서 result → DTO 변환.
   - DTO는 `web.dto`에만 유지.

3. ArchUnit 규칙 추가
   - `..feature..application..`은 `..feature..web..` 의존 금지.
   - `..feature.statistics..`는 `..feature.winningnumber.web..` 의존 금지.
   - `..feature..infrastructure..`는 `..feature..web..` 의존 금지.

완료 기준:

- `src/main/java/**/application/**` 안에서 `web.dto` import 0개.
- 기존 controller 응답 스키마 유지.
- controller test는 DTO schema 검증.
- application test는 result model 검증.

---

### P0-2. CD rollback 상태 파일 처리 오류 가능성 수정

현재 deploy script는 상당히 개선됐지만 rollback state 관리에 실제 오류 가능성이 있다.

문제 흐름:

1. `build-and-up.sh`가 `deploy-state/current.env`를 `deploy-state/previous.env`로 복사한다.
2. 기존 `current.env`에는 `CURRENT_IMAGE`, `CURRENT_DIGEST`가 들어간다.
3. `rollback.sh`는 `PREVIOUS_IMAGE`, `PREVIOUS_DIGEST`를 읽는다.
4. 단순 복사하면 key 이름이 맞지 않아 rollback target을 못 찾을 수 있다.

추가 문제:

- `docker compose down` 이후 `docker inspect kraft-lotto-app`를 수행하면 container가 이미 제거되어 기존 image capture가 실패할 수 있다.

개선안:

```bash
# build-and-up.sh: compose down 이전에 현재 컨테이너 이미지 확보
current_image="$(docker inspect --format='{{.Config.Image}}' kraft-lotto-app 2>/dev/null || true)"

if [[ -f deploy-state/current.env ]]; then
  sed 's/^CURRENT_/PREVIOUS_/' deploy-state/current.env > deploy-state/previous.env
fi
```

또는 state 파일을 처음부터 아래 형식으로 통일한다.

```bash
IMAGE_REF=kraft-lotto-app
IMAGE_TAG=<sha>
IMAGE_DIGEST=<digest>
DEPLOYED_AT=<iso8601>
```

완료 기준:

- 실패 배포 후 `rollback.sh`가 이전 image로 실제 compose up 가능.
- rollback script 단위 테스트 또는 dry-run test 추가.
- `deploy-state/current.env`, `previous.env` key schema 통일.

---

### P0-3. Redis rate-limit 운영 정책 통일

현재 설정은 세 층이 서로 다르게 말한다.

- `application.yml`: Redis rate-limit 기본 false
- `application-prod.yml`: prod 기본 true
- `docker-compose.yml`: Redis 서비스 없음
- `render-env.sh`: CD에서 `KRAFT_RECOMMEND_RATE_LIMIT_REDIS_ENABLED=false` 강제

즉 prod profile 기본 정책은 Redis 사용인데, 실제 CD 배포는 Redis 미사용이다. 동작 자체는 가능하지만 운영 정책이 모호하다.

선택지 A: 단일 인스턴스 운영을 명시하고 in-memory 유지

- `application-prod.yml`의 Redis 기본값을 false로 변경.
- CD script의 false 주입과 일치시킨다.
- scale-out 시 Redis 필요하다는 주석 또는 설정 가드 추가.

선택지 B: Redis 운영을 표준으로 채택

- `docker-compose.prod.yml` 또는 external Redis 설정 추가.
- Redis healthcheck와 connection timeout 설정 추가.
- smoke test에서 Redis mode 확인.
- Redis 장애 시 strict/fallback 정책 명확화.

완료 기준:

- prod 기본값, compose, CD script가 같은 정책을 가리킴.
- `KRAFT_RECOMMEND_RATE_LIMIT_REDIS_ENABLED`가 어디서 최종 결정되는지 명확함.
- 다중 인스턴스 여부와 rate-limit 정확성 정책이 일치함.

---

## 5. P1 개선사항

### P1-1. backfill job 상태 관리 고도화

`BackfillJobService`는 in-memory `ConcurrentHashMap`과 `ThreadPoolExecutor`로 비동기 backfill job을 관리한다. 단일 인스턴스에서는 충분하지만 운영 서비스 관점에서는 다음 한계가 있다.

문제점:

- 서버 재시작 시 job 상태가 사라진다.
- job cancel 기능이 없다.
- `@PreDestroy`에서 `shutdown()`만 호출하고 graceful await/force shutdown이 없다.
- queue full이면 job 상태는 FAILED가 되지만 별도 metric이 없다.
- job state가 in-memory라 scale-out 시 조회 일관성이 없다.
- 실패 메시지에 exception message가 그대로 들어가므로 내부 구현 정보 노출 가능성이 있다.

개선안:

- `BackfillJobStore` 인터페이스 분리.
  - 단기: in-memory 구현 유지.
  - 중기: DB 기반 job table 추가.
- `cancel(jobId)` 추가.
- `shutdownExecutor()`에서 `awaitTermination`, timeout 후 `shutdownNow` 처리.
- job status에 `CANCELLED`, `REJECTED` 추가.
- job queue depth, active count, completed/failed/rejected metric 추가.
- error message는 user-facing safe message와 internal log를 분리.

---

### P1-2. 수집 응답 모델에서 web DTO 제거

수집 계층은 현재 거의 모든 내부 component가 `CollectResponse`를 직접 반환한다. `CollectResponse`가 web DTO라면 application 내부 result로 쓰기에는 적합하지 않다.

개선안:

- `CollectResult`를 application package에 추가.
- `CollectResponse.from(CollectResult)`를 web dto에 추가.
- `LottoCollectionGate`는 `CollectResult` 기준으로 event publish 판단.
- `LottoRangeCollector`와 `LottoSingleDrawCollector`도 `CollectResult`만 사용.

완료 기준:

- `LottoSingleDrawCollector`, `LottoRangeCollector`, `LottoCollectionGate`, `LottoCollectionCommandService`, `LottoCollectionService`에서 `web.dto.CollectResponse` import 제거.

---

### P1-3. 통계 summary 조회와 갱신 책임 분리

`WinningStatisticsService.frequency()`는 summary가 있으면 읽고, 없거나 stale이면 recompute 후 save한다. 현재는 간단하지만 “조회 API 호출이 DB write를 수행할 수 있는 구조”다.

문제점:

- 캐시 miss 타이밍에 사용자 요청이 DB write를 유발한다.
- 동시 요청 시 summary 재계산 race 가능성이 있다.
- `@Transactional` write transaction이 조회 path에 걸린다.
- summary가 45개 row 모두 같은 `lastCalculatedRound`인지 검증하지 않는다.

개선안:

- `FrequencySummaryRefresher` 분리.
- `frequency()`는 read-only path로 유지.
- `WinningNumbersCollectedEvent` 수신 시 summary refresh 수행.
- stale이면 응답은 recompute하되 저장은 별도 event/async로 처리하거나 explicit refresh method에서 처리.
- `isUsableSummary()`는 45개 row 모두의 round 일치 여부 확인.

---

### P1-4. 외부 API retry와 circuit breaker 책임 정리

현재 `DhLotteryApiClient`는 자체 retry를 수행하고, `FailoverLottoApiClient`는 Resilience4j CircuitBreaker를 이용한다. 구조는 동작하지만 책임이 이중화되어 있다.

개선안:

- retry/circuit breaker 정책을 `LottoApiResiliencePolicy`로 명시.
- Resilience4j Retry를 도입해 retry와 circuit breaker를 같은 정책 레이어로 통합하거나, 현재 자체 retry를 유지한다면 circuit breaker 기준을 명확히 한다.
- fallback client는 prod에서 mock 금지 또는 explicit emergency flag로 제한.
- circuit breaker state metric 노출.
- 외부 API 실패 reason tag를 `http_4xx`, `http_5xx`, `timeout`, `parse`, `not_json`, `not_drawn` 등으로 세분화.

---

### P1-5. 추천 엔진 rule별 rejection 관측성 추가

현재 `LottoRecommender`는 rejection rate/count/attempt를 기록하지만 어떤 규칙이 탈락시켰는지 알 수 없다.

개선안:

- `ExclusionRule`에 `id()` 추가.
- `isExcluded`가 처음 match된 rule id를 반환.
- metric 추가:
  - `kraft.recommend.rejection.count{reason="duplicate"}`
  - `kraft.recommend.rejection.count{rule="pastWinning"}`
  - `kraft.recommend.rejection.count{rule="longRun"}`
  - `kraft.recommend.rejection.count{rule="singleDecade"}`
- rejection rate가 임계치를 넘으면 warn log.

---

### P1-6. E2E 테스트를 기능 기준으로 확대

현재 Playwright E2E는 home render와 주요 section visibility만 확인한다. smoke test로는 충분하지만 실제 기능 회귀를 잡기에는 약하다.

추가 시나리오:

- 추천 form submit 후 조합 row 렌더링.
- 회차 조회 정상/404 처리.
- 목록 pagination prev/next 동작.
- frequency summary 렌더링.
- API 429 mock 시 사용자 메시지 표시.
- 모바일 viewport에서 주요 section 표시.
- theme toggle 후 localStorage 반영.

CI 반영 방식:

- PR마다 full E2E가 부담되면 `main` push 또는 nightly workflow에 추가.
- 최소 smoke E2E 1개는 PR CI에 포함.

---

### P1-7. deploy script에 shellcheck와 dry-run test 추가

CD script가 분리된 것은 좋지만, script 자체 검증이 CI에 보이지 않는다.

개선안:

- `shellcheck scripts/deploy/*.sh`
- `bash -n scripts/deploy/*.sh`
- `render-env.sh` dry-run test
- `rollback.sh` state file fixture test
- `docker compose config -q`를 CI에서도 sample env로 검증

---

## 6. P2 개선사항

### P2-1. API path 상수화

프론트 JS는 `/api/v1/...` path를 각 feature module에서 직접 사용한다. 현재 canonical v1으로 맞춰져 있어 큰 문제는 아니지만 변경 비용이 남는다.

개선안:

- `src/main/resources/static/js/paths.js` 추가.
- 모든 API path를 한 곳에서 관리.
- backend `ApiPaths.java`와 path drift를 줄이기 위해 OpenAPI 기반 fixture 또는 단순 path test 추가.

---

### P2-2. 프론트 타입 정의 강화

현재 `@ts-check`와 JSDoc이 있지만 API 응답 타입이 각 파일에 흩어져 있다.

개선안:

- `src/main/resources/static/js/types.d.ts` 또는 `types.js` JSDoc typedef 추가.
- `api<T>` 호출마다 기대 타입을 명시.
- `api.test.js`에서 envelope 오류뿐 아니라 data shape 오류도 검증.

---

### P2-3. 숫자 formatting 방어 강화

`fmtNum(n)`은 `Number(n ?? 0)` 결과를 바로 format한다. `undefined`, `null`은 0으로 처리되지만, 잘못된 문자열은 `NaN`으로 표시될 수 있다.

개선안:

```js
export const fmtNum = (n) => {
  const value = Number(n);
  return Number.isFinite(value) ? numberFormatter.format(value) : '-';
};
```

---

### P2-4. cache stampede 방지

frequency summary와 cache가 도입되어 있지만, cache miss + stale summary 상황에서 동시에 여러 요청이 들어오면 중복 recompute/save 가능성이 있다.

개선안:

- `@Cacheable(sync = true)` 검토.
- summary refresh에 lock 적용.
- event-driven refresh로 사용자 요청 path에서 write 제거.
- `winningNumberFrequency` cache hit/miss metric 확인.

---

### P2-5. actuator/prometheus 접근 정책 명확화

prod profile은 `health,info,metrics,prometheus`를 노출한다. reverse proxy 또는 firewall이 없다면 metrics가 외부에 노출될 수 있다.

개선안:

- `/actuator/prometheus`는 내부망 또는 admin network만 접근.
- Spring Security matcher로 actuator endpoint별 접근 제어.
- 운영 reverse proxy ACL이 있다면 compose/deploy 설정에 명시.
- smoke test는 readiness만 외부 확인하고 prometheus는 내부 확인.

---

### P2-6. performanceSmokeTest와 PIT 실행 정책 분리

`performanceSmokeTest`와 PIT 설정은 있으나 정규 CI에 포함되지는 않는다.

권장:

- PR CI: unit + integration + frontend unit + contract
- main push: PR CI + lightweight E2E
- nightly: PIT + performanceSmokeTest + full E2E
- release: Docker build + smoke + migration from scratch

---

### P2-7. job/error metric 확대

이미 여러 metric이 있지만 다음이 추가되면 운영성이 좋아진다.

- `kraft.backfill.job.started`
- `kraft.backfill.job.completed{status}`
- `kraft.backfill.job.queue.size`
- `kraft.backfill.job.active`
- `kraft.collect.round.latency`
- `kraft.collect.round.result{status}`
- `kraft.statistics.summary.refresh{result}`
- `kraft.api.dhlottery.circuit.state`

---

## 7. P3 장기 개선사항

### P3-1. DB 기반 backfill job store

운영에서 backfill job 상태를 유지하려면 in-memory map보다 DB table이 낫다.

예상 table:

```sql
create table lotto_backfill_jobs (
    job_id varchar(36) primary key,
    from_round int not null,
    to_round int not null,
    status varchar(20) not null,
    created_at datetime(6) not null,
    started_at datetime(6) null,
    completed_at datetime(6) null,
    collected int not null default 0,
    updated int not null default 0,
    skipped int not null default 0,
    failed int not null default 0,
    error_code varchar(100) null,
    error_message varchar(500) null
);
```

### P3-2. 번호별 정규화 테이블 검토

frequency summary는 이미 좋은 중간 단계다. 장기적으로 조합 이력, 번호별 통계, 보너스 번호 통계가 확장되면 `winning_number_members` 테이블을 검토할 수 있다.

예:

```sql
winning_number_members (
  round int not null,
  ball int not null,
  role varchar(10) not null, -- MAIN, BONUS
  primary key (round, ball, role)
)
```

### P3-3. OpenAPI 기반 프론트 타입 생성

정적 JS 전략을 유지하더라도 OpenAPI JSON에서 typedef 또는 TypeScript declaration을 생성하면 API drift를 줄일 수 있다.

---

## 8. 우선 PR 단위

### PR 1. Fix deploy rollback state handling

목표:

- `current.env` → `previous.env` key 변환 수정.
- compose down 전 current image capture.
- rollback dry-run fixture test 추가.

검증:

```bash
bash -n scripts/deploy/*.sh
shellcheck scripts/deploy/*.sh
```

---

### PR 2. Separate application result models from web DTOs

목표:

- application layer의 `web.dto` 의존 제거.
- controller mapper 추가.
- ArchUnit 규칙 추가.

대상:

- `RecommendService`
- `WinningNumberQueryService`
- `WinningStatisticsService`
- `LottoCollectionService`
- `LottoSingleDrawCollector`
- `LottoRangeCollector`
- `LottoCollectionCommandService`
- `LottoCollectionGate`
- `BackfillJobService`

---

### PR 3. Normalize Redis rate-limit production policy

목표:

- prod 기본값과 CD 배포값 통일.
- Redis 사용/미사용 정책 명시.
- smoke test에 rate-limit mode 확인 추가.

---

### PR 4. Harden backfill job lifecycle

목표:

- cancel/rejected status 추가.
- shutdown graceful await 추가.
- queue/full/rejected metric 추가.
- safe error message 분리.

---

### PR 5. Split frequency read and refresh paths

목표:

- `frequency()` read-only 유지.
- summary refresh component 분리.
- summary row round consistency 검증.
- cache stampede 방지.

---

### PR 6. Add frontend API path and typedef layer

목표:

- `paths.js` 추가.
- 공통 typedef 추가.
- `fmtNum` 방어 로직 강화.
- API path 중복 제거.

---

### PR 7. Expand E2E and scheduled quality gates

목표:

- main/nightly E2E workflow 추가.
- PIT/nightly workflow 추가.
- performanceSmokeTest 실행 정책 추가.

---

## 9. 최종 결론

현재 프로젝트는 이미 상당히 잘 정리되어 있다. 기존 문서의 상당 부분은 최신 코드와 맞지 않으므로 교체가 필요하다.

가장 먼저 처리할 것은 다음 세 가지다.

1. **CD rollback state handling 수정**
2. **application 계층의 web DTO 의존 제거**
3. **Redis rate-limit 운영 정책 통일**

그 다음은 backfill job lifecycle, frequency summary refresh 구조, E2E/PIT/perf 실행 정책, 프론트 path/type 정리 순서가 적절하다.

이 순서대로 진행하면 프로젝트 수준은 현재 8.1에서 8.6 이상으로 올라갈 수 있다.
