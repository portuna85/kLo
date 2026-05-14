
# kLo (Kraft Lotto) — 코드 리뷰 & 개선 보고서

> 분석 대상 : `https://github.com/portuna85/kLo.git` (2026-05-14 기준 `main` HEAD)  
> 기술 스택 : Java 25 · Spring Boot 4.0.5 · MariaDB 11 · Flyway · Caffeine/Redis · ShedLock · Resilience4j · ArchUnit · Testcontainers

---

## 종합 평가

| 카테고리 | 점수 | 비고 |
|:---|:---:|:---|
| 아키텍처 / 계층 분리 | ⭐⭐⭐⭐⭐ | ArchUnit 강제 포함, 매우 깔끔 |
| 테스트 전략 | ⭐⭐⭐⭐ | Testcontainers + ArchUnit + REST Docs |
| 운영 성숙도 | ⭐⭐⭐⭐ | ShedLock · Circuit Breaker · Audit · OTLP |
| 도메인 모델링 | ⭐⭐⭐⭐ | Record 기반 불변 객체 · DB CHECK 제약 |
| 코드 품질 디테일 | ⭐⭐⭐ | Integer 비교 · 자가호출 캐시 등 손볼 곳 존재 |
| 문서화 | ⭐⭐⭐⭐ | 한국어 JavaDoc · README 모두 충실 |

전반적으로 사이드 프로젝트 수준을 넘어서는 운영 성숙도를 갖추고 있습니다.  
아래 항목들은 **우선순위 순**으로 정리하였습니다.

---

## 🔴 즉시 수정 권장 — 실제 버그

### 1. `WinningNumberPersister.isSame()` — Integer `==` 비교 오류

**파일** : `src/main/java/.../application/WinningNumberPersister.java`

```java
// ❌ 현재 코드 — 박싱 객체 참조 비교
&& existing.getN1() == incoming.getN1()
...
&& existing.getFirstWinners() == incoming.getFirstWinners()
```

`Integer`는 `-128 ~ 127` 범위만 캐시됩니다.  
`firstWinners`(1등 당첨자 수)가 128 이상이면 객체 참조가 달라져 **항상 `false`** → 매 수집마다 `UPDATED` 판정 → DB write + 캐시 강제 evict가 반복됩니다.

```java
// ✅ 수정 — equals() 또는 언박싱 사용
private static boolean isSame(WinningNumberEntity existing, WinningNumberEntity incoming) {
    return existing.getDrawDate().equals(incoming.getDrawDate())
            && Objects.equals(existing.getN1(),           incoming.getN1())
            && Objects.equals(existing.getN2(),           incoming.getN2())
            && Objects.equals(existing.getN3(),           incoming.getN3())
            && Objects.equals(existing.getN4(),           incoming.getN4())
            && Objects.equals(existing.getN5(),           incoming.getN5())
            && Objects.equals(existing.getN6(),           incoming.getN6())
            && Objects.equals(existing.getBonusNumber(),  incoming.getBonusNumber())
            && Objects.equals(existing.getFirstPrize(),   incoming.getFirstPrize())
            && Objects.equals(existing.getFirstWinners(), incoming.getFirstWinners())
            && Objects.equals(existing.getTotalSales(),   incoming.getTotalSales())
            && Objects.equals(existing.getFirstAccumAmount(), incoming.getFirstAccumAmount());
}
```

**영향** : 불필요한 DB update → `winningNumberFrequency` · `combinationPrizeHistory` 캐시 evict → `PastWinningCache` reload 반복 발생.

---

### 2. `WinningNumberQueryService` — `@Cacheable` 자가 호출 우회

**파일** : `src/main/java/.../application/WinningNumberQueryService.java`

```java
// ❌ 자가 호출은 프록시를 거치지 않으므로 @Cacheable 무시
public FrequencySummaryDto frequencySummary() {
    List<NumberFrequencyDto> frequencies = frequency();                    // 캐시 미적용
    CombinationPrizeHistoryDto lowSixHistory = combinationPrizeHistory(…); // 캐시 미적용
    ...
}
```

Spring `@Cacheable`은 **AOP 프록시 경유 호출**에서만 동작합니다.  
`frequencySummary()` 엔드포인트는 매번 1200+ 행 풀스캔을 2회 실행합니다.

```java
// ✅ 방법 1 — self-injection
@Lazy
@Autowired
private WinningNumberQueryService self;

public FrequencySummaryDto frequencySummary() {
    List<NumberFrequencyDto> frequencies = self.frequency();
    CombinationPrizeHistoryDto lowSixHistory = self.combinationPrizeHistory(lowSixNumbers);
    ...
}

// ✅ 방법 2 — 캐시 대상 메서드를 별도 @Service 빈으로 분리
```

---

### 3. `WinningNumberAutoCollectScheduler` — `@ConditionalOnProperty` 중복

**파일** : `src/main/java/.../application/WinningNumberAutoCollectScheduler.java`

```java
// ❌ @ConditionalOnProperty 는 @Repeatable 이 아님
@ConditionalOnProperty(prefix = "kraft.lotto.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "kraft.collect.auto",    name = "enabled", havingValue = "true", matchIfMissing = true)
public class WinningNumberAutoCollectScheduler { ... }
```

두 번째 어노테이션은 무시되어 **둘 중 하나만 평가**됩니다. 의도한 AND 조건이 보장되지 않습니다.

```java
// ✅ SpEL로 AND 조건 명시
@ConditionalOnExpression(
    "${kraft.lotto.scheduler.enabled:true} and ${kraft.collect.auto.enabled:true}"
)
public class WinningNumberAutoCollectScheduler { ... }
```

---

## 🟠 성능 / 효율 개선

### 4. `combinationPrizeHistory()` — 캐시 미스 시 매번 풀스캔

**파일** : `WinningNumberQueryService.java`

캐시 키가 입력 조합별이라 처음 보는 조합마다 전체 `winning_numbers` 테이블을 메모리에 올려 순회합니다.  
SQL 집계로 전환하거나, `PastWinningCache` 패턴처럼 이벤트 기반 인메모리 스냅샷으로 전환하면 DB hit를 0으로 줄일 수 있습니다.

```sql
-- ✅ SQL 집계 예시
SELECT
    COUNT(CASE WHEN main_matches = 6 THEN 1 END) AS first_prize_count,
    COUNT(CASE WHEN main_matches = 5 AND bonus_match = 1 THEN 1 END) AS second_prize_count
FROM (
    SELECT
        (CASE WHEN n1 IN (:nums) THEN 1 ELSE 0 END
         + CASE WHEN n2 IN (:nums) THEN 1 ELSE 0 END
         + ... ) AS main_matches,
        CASE WHEN bonus_number IN (:nums) THEN 1 ELSE 0 END AS bonus_match
    FROM winning_numbers
) sub
```

---

### 5. `PastWinningCacheLoader.reload()` — 동기 이벤트 핸들러

**파일** : `src/main/java/.../application/PastWinningCacheLoader.java`

`@EventListener`는 기본 동기 모드입니다. 수집 컨트롤러 응답이 cache reload 완료까지 대기합니다.  
현재 1200개 정도면 빠르지만, 회차가 늘거나 DB가 느려지면 응답 지연으로 이어집니다.

```java
// ✅ 트랜잭션 커밋 이후 비동기 reload
@Async
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onCollected(WinningNumbersCollectedEvent event) {
    log.info("PastWinningCache reload triggered ...");
    reload();
}
```

또한 `findAllCombinationsOrderByRoundAsc()`가 반환하는 `List<Object[]>` 캐스팅 대신 인터페이스 프로젝션을 사용하면 가독성이 개선됩니다.

```java
// WinningNumberRepository에 추가
interface CombinationRow {
    Integer getN1(); Integer getN2(); Integer getN3();
    Integer getN4(); Integer getN5(); Integer getN6();
}

@Query("select w.n1 as n1, w.n2 as n2, ... from WinningNumberEntity w order by w.round asc")
List<CombinationRow> findAllCombinationsProjected();
```

---

### 6. `LottoRecommender` — reject-sampling 비효율

**파일** : `src/main/java/.../application/LottoRecommender.java`

규칙이 빡빡해질수록 reject 비율이 높아져 `maxAttempts=5000`에 도달할 위험이 있습니다.  
예시: BirthdayBiasRule + ArithmeticSequenceRule + LongRunRule + SingleDecadeRule이 동시에 적용될 때  
이론적 통과 확률이 10% 미만이면 5000회 시도로도 10개 조합이 나오지 않을 수 있습니다.

개선 방향:

- 생성 시 각 십의 자리 버킷(`SingleDecadeRule`)과 연속 길이(`LongRunRule`)를 미리 반영한 **constraint-aware 샘플러**로 교체하면 reject 율을 90% 이상 줄일 수 있습니다.
- 단기 대응: `maxAttempts` 로깅에 reject 비율 메트릭을 추가(`kraft.recommend.rejection.rate`)하여 운영 중 모니터링.

---

### 7. 가상 스레드 + JPA pinning 모니터링

**파일** : `src/main/resources/application.yml`

```yaml
spring.threads.virtual.enabled: true
```

Java 25 + Spring Boot 4에서 가상 스레드 지원이 개선됐지만, Hibernate 내부 `synchronized` 블록에서 OS thread pinning이 발생할 수 있습니다.  
JVM 옵션으로 pinning 이벤트를 로깅하여 실제 영향을 측정하세요.

```bash
# Dockerfile JAVA_OPTS에 추가
-Djdk.tracePinnedThreads=full
```

---

## 🟡 보안 / 운영 개선

### 8. Tomcat remoteip 내부 프록시 범위 협소

**파일** : `src/main/resources/application.yml`

```yaml
server:
  tomcat:
    remoteip:
      internal-proxies: 127\.0\.0\.1|::1|192\.168\.0\.\d{1,3}
```

Docker 네트워크 (`172.17.x`, `172.18.x`) 또는 사내망 (`10.x`)을 경유하는 경우  
nginx가 주입한 `X-Forwarded-For`가 Tomcat에서 **무시**됩니다.

```yaml
# ✅ 환경변수로 외부 주입 가능하게
server:
  tomcat:
    remoteip:
      internal-proxies: ${KRAFT_INTERNAL_PROXIES:127\\.0\\.0\\.1|::1|172\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}}
```

---

### 9. Rate Limit 인메모리 모드 Capacity 정책

**파일** : `src/main/java/.../security/RecommendRateLimitFilter.java`

```java
static final int MAX_TRACKED_IPS = 50_000;
```

IP 버킷이 가득 차면 **신규 IP 전체를 차단**합니다. 대규모 DDoS 시 정상 사용자까지 차단될 수 있습니다.  
prod 환경에서는 Redis 모드 기본 활성화를 권장합니다.

```yaml
# ✅ application-prod.yml
kraft:
  recommend:
    rate-limit:
      redis:
        enabled: true
```

---

### 10. CD 배포 — `KRAFT_ADMIN_API_TOKENS` 누락 방지 강화

**파일** : `.github/workflows/cd.yml`

기존 `printRequiredEnvVars` + `Validate required production secrets` 단계가 이미 잘 동작합니다.  
추가로 **토큰 형식 검증**(최소 길이 / 엔트로피)을 삽입하면 재발을 원천 차단할 수 있습니다.

```bash
# ✅ Validate required production secrets 단계 마지막에 추가
token_val="$(printf '%s' "$ALL_SECRETS_JSON" | jq -r '.KRAFT_ADMIN_API_TOKENS // ""')"
if [ ${#token_val} -lt 32 ]; then
  echo "::error::KRAFT_ADMIN_API_TOKENS is too short (min 32 chars)"
  exit 1
fi
```

또한 배포 완료 후 smoke test로 admin endpoint를 한 번 호출하는 단계를 추가하면 토큰 설정 여부를 즉시 확인할 수 있습니다.

```bash
# ✅ Wait for readiness 이후 smoke test 단계 추가
HTTP=$(curl -s -o /dev/null -w '%{http_code}' \
  -X POST http://localhost:8080/admin/lotto/draws/collect-next \
  -H "X-Kraft-Admin-Token: ${KRAFT_ADMIN_API_TOKENS%%,*}")
[ "$HTTP" = "200" ] || [ "$HTTP" = "409" ] || {
  echo "::error::Admin token smoke test failed: HTTP $HTTP"
  exit 1
}
```

---

### 11. CI 스크립트 — PowerShell 의존성 제거

**파일** : `.github/workflows/ci.yml`

```yaml
# ❌ Linux runner에서 pwsh 설치 필요
- run: pwsh ./scripts/verify-prod-profile-in-jar.ps1 -JarPath build/libs/app-with-docs.jar
```

```yaml
# ✅ bash 한 줄로 대체
- name: Verify prod profile resources in jar
  run: |
    unzip -l build/libs/app-with-docs.jar \
      | grep -E 'BOOT-INF/classes/application-(prod|local)\.yml' \
      | wc -l | xargs test 2 -eq
```

---

### 12. Admin Audit Log — IP 해석 불일치

**파일** : `AdminApiTokenFilter.java`

`auditLog()`가 `request.getRemoteAddr()`만 사용하지만, 실제 클라이언트 IP는 프록시 뒤에서는 다릅니다.  
`RecommendRateLimitFilter.resolveClientIp()`와 동일한 로직을 공유 유틸로 추출하세요.

```java
// ✅ 공유 유틸 예시
// src/main/java/.../support/ClientIpResolver.java
public final class ClientIpResolver {
    public static String resolve(HttpServletRequest req, KraftRecommendRateLimitProperties props) { ... }
}
```

---

## 🟢 코드 품질 / 유지보수

### 13. `@Autowired` 단일 생성자 중복 제거

Spring 4.3+부터 단일 생성자는 자동 주입됩니다.  
아래 클래스의 주 생성자 `@Autowired` 어노테이션을 제거할 수 있습니다.

- `RecommendService`
- `PastWinningCacheLoader`
- `WinningNumberPersister`
- `LottoCollectionService`
- `BackfillJobService`
- `LottoFetchLogRetentionScheduler`

---

### 14. `DhLotteryResponseParser` — `LocalDateTime.now()` 하드코딩

**파일** : `DhLotteryResponseParser.java`

```java
// ❌ 시스템 클락 직접 호출 — 테스트 시 시간 제어 불가
return Optional.of(new WinningNumber(
    drwNo, drawDate, ..., LocalDateTime.now()
));
```

```java
// ✅ Clock 주입으로 결정적 테스트 가능
class DhLotteryResponseParser {
    private final Clock clock;
    DhLotteryResponseParser(ObjectMapper objectMapper, Clock clock) { ... }

    // LocalDateTime.now(clock) 사용
}
```

---

### 15. `MAX_ROUND` 매직 상수 산재

`3000`이 `WinningNumberQueryService`, `AdminLottoDrawController`, `WinningNumberController` 등  
여러 곳에 흩어져 있습니다.

```java
// ✅ 단일 상수로 통합
// WinningNumberQueryService 내 또는 별도 상수 클래스
public static final int MAX_ROUND = 3000;
```

`@Max(3000)` 어노테이션에서는 `@interface`가 상수 참조를 허용하지 않지만,  
`ValidRound` 커스텀 어노테이션의 `max` 기본값으로 관리하면 됩니다.

---

### 16. `CollectResponse` 생성자 오버로드 정리

현재 생성자 4개가 각기 다른 시그니처로 존재하며, 파생 필드(`dataChanged`)를 생성자마다 다르게 계산합니다.  
Builder 패턴 또는 정적 팩토리로 교체하면 의도가 명확해집니다.

```java
// ✅ 정적 팩토리 예시
public record CollectResponse(...) {
    public static CollectResponse ofInserted(int count, int latestRound) { ... }
    public static CollectResponse ofSkipped(int count, int latestRound)  { ... }
    public static CollectResponse ofFailed(List<Integer> failedRounds, int latestRound) { ... }
}
```

---

### 17. `docker-compose.yml` MariaDB 버전 불일치

```yaml
# docker-compose.yml
image: mariadb:11.7

# README.md
MariaDB 11.8
```

둘 중 하나로 통일하고, 가능하면 이미지 태그를 패치 버전까지 고정(`mariadb:11.7.2` 등)하여  
재빌드 시 예기치 않은 업그레이드를 방지하세요.

---

## 🏗️ 아키텍처 / 설계 제안

### 18. `WinningCollectService` — `@Deprecated(forRemoval=true)` 실제 제거

`since = "0.2.0"`으로 표시된 `WinningNumberCollectService`가 여전히 코드베이스에 남아있습니다.  
ArchUnit이 `@Service`·`@Component` 등록을 막고 있어 런타임에는 문제없지만,  
`LottoCollectionService`가 안정화된 만큼 다음 마이너 릴리즈에서 함께 제거하세요.

---

### 19. `LottoCollectionService` 책임 분리 (260줄)

`collectDraw` · `collect` · `collectNextDraw` · `collectMissingDraws` · `backfill` · `refreshDraw`  
6개 진입점이 한 클래스에 있고, `AtomicBoolean running`으로 전역 단일 실행을 보장하며, 이벤트 발행까지 담당합니다.

```
LottoCollectionService (260줄)
  ├── LottoSingleDrawCollector   — 단일 회차 fetch + persist + log
  ├── LottoRangeCollector        — 범위 순회, 딜레이, 통계 집계
  └── LottoCollectionGate        — 전역 락(AtomicBoolean) + 이벤트 발행
```

---

### 20. 통계 기능 패키지 분리 검토

`CombinationPrizeHistoryDto` · `FrequencySummaryDto` 등 통계성 DTO와  
`WinningNumberQueryService`의 통계 메서드들이 `feature/winningnumber` 안에 혼재합니다.  
`PastWinningRule`이 `winningnumber.domain`을 참조하는 양방향 의존 구조도 잠재적으로 존재합니다.  
향후 규모가 커지면 `feature/statistics` 로 분리하여 read model을 별도로 관리하면  
ArchUnit 규칙도 더 명확하게 유지됩니다.

---

## 수정 우선순위 요약

| 순위 | 항목 | 유형 | 영향 |
|:---:|:---|:---:|:---|
| 1 | Integer `==` 비교 (isSame) | 🔴 버그 | DB 불필요 write + 캐시 연쇄 evict |
| 2 | `@Cacheable` 자가 호출 우회 | 🔴 버그 | 통계 엔드포인트 매번 풀스캔 |
| 3 | `@ConditionalOnProperty` 중복 | 🔴 버그 | 스케줄러 조건 한 쪽만 적용 |
| 4 | `combinationPrizeHistory` SQL화 | 🟠 성능 | DB I/O 대폭 절감 |
| 5 | `@Async` 캐시 reload | 🟠 성능 | 수집 응답 지연 방지 |
| 6 | Tomcat remoteip 범위 확장 | 🟡 보안 | Docker 네트워크 IP 추적 정확도 |
| 7 | Rate Limit Redis 기본 활성화 | 🟡 운영 | Capacity 초과 정상 사용자 차단 방지 |
| 8 | CD smoke test (admin token) | 🟡 운영 | 토큰 누락 조기 감지 |
| 9 | CI PowerShell → bash | 🟢 품질 | runner 의존성 제거 |
| 10 | `@Autowired` 정리 + Clock 주입 | 🟢 품질 | 테스트 용이성 향상 |

---

*보고서 생성일 : 2026-05-14*
