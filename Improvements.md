# kLo 테스트 코드 리팩토링 가이드

> 기준 커밋: `b37ae83` (2026-05-11)  
> 분석 파일: 18개 테스트 파일 / 2,885 라인

---

## 목차

1. [문제 요약](#1-문제-요약)
2. [R1 — DisplayName 깨짐 / "test" 문자열](#r1--displayname-깨짐--test-문자열)
3. [R2 — 필드 선언 위치 문제 (DhLotteryApiClientTest)](#r2--필드-선언-위치-문제)
4. [R3 — @Disabled 테스트 처리](#r3--disabled-테스트-처리)
5. [R4 — 누락된 테스트 (FailoverLottoApiClient)](#r4--누락된-테스트-failoverlottoapiclient)
6. [R5 — 누락된 테스트 (LottoApiHealthIndicator)](#r5--누락된-테스트-lottoapihealthindicator)
7. [R6 — @Nested 미적용 — 대형 테스트 클래스 구조화](#r6--nested-미적용)
8. [R7 — SecurityIntegrationTest @SpringBootTest 비용](#r7--securityintegrationtest-springboottest-비용)
9. [R8 — WinningNumberCollectServiceTest — 픽스처 중복](#r8--winningnumbercollectservicetest--픽스처-중복)
10. [잘 구현된 테스트 패턴](#잘-구현된-테스트-패턴)
11. [작업 순서](#작업-순서)

---

## 1. 문제 요약

| 분류 | 파일 | 문제 |
|------|------|------|
| R1 | `WinningNumberCollectServiceTest` | `@DisplayName("test")` 5건, 한글 깨짐 6건 |
| R2 | `DhLotteryApiClientTest` | `client` 필드 선언이 테스트 메서드 중간에 위치 |
| R3 | `DhLotteryApiClientTest` | `@Disabled` 테스트 2건 — 검증 공백 |
| R4 | — | `FailoverLottoApiClient` 테스트 파일 없음 |
| R5 | — | `LottoApiHealthIndicator` 테스트 파일 없음 (P0 신규 구현) |
| R6 | `DhLotteryApiClientTest`, `WinningNumberCollectServiceTest` | `@Nested` 미적용으로 대형 클래스 가독성 저하 |
| R7 | `SecurityIntegrationTest` | `@SpringBootTest` 전체 컨텍스트 — `@WebMvcTest`로 교체 가능 |
| R8 | `WinningNumberCollectServiceTest` | `sample()` 픽스처 공유 부족, 반복 stubbing |

---

## R1 — DisplayName 깨짐 / "test" 문자열

**파일:** `WinningNumberCollectServiceTest.java`

**문제**

`@DisplayName("test")` 5건과 `@DisplayName("???")`으로 표시되는 한글 인코딩 깨짐 6건이 존재한다.  
CI 리포트에서 테스트 목적을 전혀 파악할 수 없고, 실패 시 어떤 동작을 검증하다 실패했는지 알기 어렵다.

**현재 코드 (문제 예시)**

```java
// 한글 깨짐
@DisplayName("targetRound ?? ?????API ?? empty ???????????? ??????")
void collectsUntilApiReturnsEmptyWhenNoTargetRound() { ... }

// "test" 의미 없는 이름
@DisplayName("test")
void wrapsExternalApiExceptionAsBusinessException() { ... }

@DisplayName("test")
void doesNotPublishEventWhenCollectedIsZero() { ... }
```

**수정 방향**

```java
@DisplayName("targetRound 미지정 시 API가 empty를 반환할 때까지 수집한다")
void collectsUntilApiReturnsEmptyWhenNoTargetRound() { ... }

@DisplayName("외부 API 예외는 EXTERNAL_API_FAILURE BusinessException으로 래핑된다")
void wrapsExternalApiExceptionAsBusinessException() { ... }

@DisplayName("수집 건수가 0이면 이벤트를 발행하지 않는다")
void doesNotPublishEventWhenCollectedIsZero() { ... }
```

**전체 수정 목록 (WinningNumberCollectServiceTest)**

| 메서드명 | 현재 DisplayName | 수정 DisplayName |
|----------|-----------------|-----------------|
| `collectsUntilApiReturnsEmptyWhenNoTargetRound` | `targetRound ?? ?????...` | `targetRound 미지정 시 API가 empty를 반환할 때까지 수집한다` |
| `collectsUpToTargetRoundOnly` | `targetRound ?? ???...` | `targetRound 지정 시 해당 회차까지만 수집한다` |
| `countsExistingRoundAsSkipped` | `test` | `이미 존재하는 회차는 skipped로 집계된다` |
| `countsSaveFailureAsFailedAndContinues` | `?????????failed...` | `저장 실패는 failed로 집계하고 다음 회차를 계속 처리한다` |
| `wrapsExternalApiExceptionAsBusinessException` | `test` | `외부 API 예외는 EXTERNAL_API_FAILURE BusinessException으로 래핑된다` |
| `throwsInvalidTargetRoundWhenTargetRoundIsNonPositive` | `test` | `targetRound가 0 이하이면 LOTTO_INVALID_TARGET_ROUND를 던진다` |
| `returnsSkippedWhenTargetRoundIsAlreadyCollected` | `targetRound ?? ??? ???...` | `targetRound가 이미 수집된 회차이면 API 호출 없이 skipped를 반환한다` |
| `rejectsConcurrentCollectExecution` | `???????? ??? ???...` | `동시 수집 실행은 TOO_MANY_REQUESTS로 거부된다` |
| `doesNotPublishEventWhenCollectedIsZero` | `test` | `수집 건수가 0이면 이벤트를 발행하지 않는다` |
| `returnsTruncatedAndNextRoundWhenMaxRoundsPerCallExceeded` | `test` | `호출당 최대 회차 수 초과 시 truncated=true와 nextRound를 반환한다` |
| `returnsNotDrawnTrueWhenTargetRoundIsNotDrawn` | `targetRound?? ??...` | `targetRound가 미추첨 회차이면 notDrawn=true를 반환한다` |

**근본 원인:** 파일 저장 시 인코딩 불일치 (UTF-8 BOM 또는 EUC-KR). IDE 파일 인코딩을 UTF-8로 통일하고 재작성 필요.

---

## R2 — 필드 선언 위치 문제

**파일:** `DhLotteryApiClientTest.java`

**문제**

`client` 필드 선언이 클래스 최상단이 아니라 **115번째 줄 테스트 메서드들 사이**에 위치한다.  
클래스 도입부에서 의존 대상을 파악하기 어렵고, 선언 이전에 정의된 테스트들이 이 필드를 참조하여  
Java 언어 규칙상은 문제없지만 가독성상 혼란을 준다.

**현재 코드**

```java
class DhLotteryApiClientTest {

    @Test
    void parseThrowsWhenNumberFieldIsString() {
        // ... client 사용 (115번째 줄에 선언됨)
    }

    // ... 여러 테스트 ...

    private final DhLotteryApiClient client =  // ← 115번째 줄, 테스트 사이에 위치
            new DhLotteryApiClient(null, new ObjectMapper(), "http://localhost");

    @Test
    void parseConvertsValidResponseToDomain() { ... }
```

**수정 방향**

```java
class DhLotteryApiClientTest {

    // 필드는 항상 클래스 최상단에 선언
    private final DhLotteryApiClient client =
            new DhLotteryApiClient(null, new ObjectMapper(), "http://localhost");

    @Test
    void parseThrowsWhenNumberFieldIsString() { ... }

    @Test
    void parseConvertsValidResponseToDomain() { ... }
```

---

## R3 — @Disabled 테스트 처리

**파일:** `DhLotteryApiClientTest.java`

**문제**

`fetch()` 재시도 로직 검증 테스트 2건이 `@Disabled`로 비활성화되어 있다.  
사유는 "RestClient exchange 람다 기반 호출은 통합 테스트에서 검증한다"이지만,  
실제로 해당 통합 테스트가 존재하지 않아 **재시도 로직이 현재 어디서도 검증되지 않는다.**

```java
@Disabled("RestClient exchange 람다 기반 호출은 통합 테스트에서 검증한다.")
@Test
void fetchRetriesOnNetworkFailure() { ... }

@Disabled("RestClient exchange 람다 기반 호출은 통합 테스트에서 검증한다.")
@Test
void fetchThrowsWhenRetryExhausted() { ... }
```

**처리 방안 (둘 중 선택)**

**옵션 A — exchange 람다를 단위 테스트 가능한 구조로 변경 (권장)**

`DhLotteryApiClient`의 `exchange()` 호출 부분을 별도 메서드로 추출하면  
람다 자체를 mock 없이 단위 테스트 가능해진다.

```java
// DhLotteryApiClient 내부에 추출 메서드 추가
ApiRawResponse doFetch(int round) {
    URI uri = buildUri(round);
    return restClient.get().uri(uri).exchange((req, res) -> {
        int status = res.getStatusCode().value();
        String body = StreamUtils.copyToString(res.getBody(), StandardCharsets.UTF_8);
        String ct   = res.getHeaders().getContentType() == null ? null
                    : res.getHeaders().getContentType().toString();
        return new ApiRawResponse(status, ct, body);
    });
}

// 테스트에서 doFetch를 override하거나 spy 사용
```

**옵션 B — WireMock 통합 테스트 추가**

```java
@WireMockTest
class DhLotteryApiClientWireMockIT {

    @Test
    void fetchRetriesOnNetworkFailure(WireMockRuntimeInfo wmRuntimeInfo) {
        WireMock.stubFor(WireMock.get(WireMock.anyUrl())
            .inScenario("retry")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(WireMock.aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER))
            .willSetStateTo("retry-1"));

        WireMock.stubFor(WireMock.get(WireMock.anyUrl())
            .inScenario("retry")
            .whenScenarioStateIs("retry-1")
            .willReturn(WireMock.aResponse()
                .withStatus(200)
                .withBody(VALID_RESPONSE_BODY)));

        DhLotteryApiClient client = new DhLotteryApiClient(
            ..., wmRuntimeInfo.getHttpBaseUrl(), 2, 0, null);
        Optional<WinningNumber> result = client.fetch(1102);
        assertThat(result).isPresent();
    }
}
```

---

## R4 — 누락된 테스트 (FailoverLottoApiClient)

**파일:** `FailoverLottoApiClientTest.java` (신규 생성 필요)

**문제**

`FailoverLottoApiClient`는 장애 시 폴백 전환이라는 핵심 운영 로직을 담당하지만  
테스트 파일이 존재하지 않는다.

**추가해야 할 테스트 케이스**

```java
@ExtendWith(MockitoExtension.class)
@DisplayName("FailoverLottoApiClient")
class FailoverLottoApiClientTest {

    @Mock LottoApiClient primary;
    @Mock LottoApiClient fallback;

    FailoverLottoApiClient client;

    @BeforeEach
    void setUp() {
        client = new FailoverLottoApiClient(primary, fallback);
    }

    @Test
    @DisplayName("정상 시 primary를 호출한다")
    void delegatesToPrimaryWhenHealthy() {
        when(primary.fetch(1)).thenReturn(Optional.of(sample(1)));
        assertThat(client.fetch(1)).isPresent();
        verifyNoInteractions(fallback);
    }

    @Test
    @DisplayName("primary 실패 시 fallback으로 전환한다")
    void switchesToFallbackOnPrimaryFailure() {
        when(primary.fetch(1)).thenThrow(new LottoApiClientException("down"));
        when(fallback.fetch(1)).thenReturn(Optional.of(sample(1)));
        assertThat(client.fetch(1)).isPresent();
    }

    @Test
    @DisplayName("primary 실패 후 이후 호출도 fallback을 사용한다")  // R4-현재 버그
    void staysInFallbackAfterActivation() {
        when(primary.fetch(1)).thenThrow(new LottoApiClientException("down"));
        when(fallback.fetch(anyInt())).thenReturn(Optional.empty());

        client.fetch(1); // fallback 활성화
        client.fetch(2); // 이후 호출도 fallback 사용 (현재 영구 고착)

        verify(primary, times(1)).fetch(anyInt()); // primary는 1번만 호출
        verify(fallback, times(2)).fetch(anyInt()); // fallback은 2번 호출
    }

    // P1-2 수정(복구 로직) 후 추가
    @Test
    @DisplayName("fallback 쿨다운 이후 primary를 재시도한다")
    void retriesPrimaryAfterCooldown() { ... }
}
```

---

## R5 — 누락된 테스트 (LottoApiHealthIndicator)

**파일:** `LottoApiHealthIndicatorTest.java` (신규 생성 필요)

**문제**

P0-2에서 신규 구현 예정인 `LottoApiHealthIndicator`의 테스트가 필요하다.

**추가해야 할 테스트 케이스**

```java
@ExtendWith(MockitoExtension.class)
@DisplayName("LottoApiHealthIndicator")
class LottoApiHealthIndicatorTest {

    @Mock LottoApiClient lottoApiClient;

    LottoApiHealthIndicator indicator;

    @BeforeEach
    void setUp() {
        indicator = new LottoApiHealthIndicator(lottoApiClient);
    }

    @Test
    @DisplayName("외부 API 호출 성공 시 UP을 반환한다")
    void returnsUpWhenApiIsReachable() {
        when(lottoApiClient.fetch(1)).thenReturn(Optional.empty());
        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    @DisplayName("외부 API 호출 실패 시 DOWN을 반환한다")
    void returnsDownWhenApiIsUnreachable() {
        when(lottoApiClient.fetch(1)).thenThrow(new LottoApiClientException("timeout"));
        Health result = indicator.health();
        assertThat(result.getStatus()).isEqualTo(Status.DOWN);
        assertThat(result.getDetails()).containsKey("error");
    }
}
```

---

## R6 — @Nested 미적용

**파일:** `DhLotteryApiClientTest.java`, `WinningNumberCollectServiceTest.java`

**문제**

두 클래스 모두 10개 이상의 테스트가 단일 레벨에 나열되어 있다.  
`ExclusionRulesTest`는 `@Nested`를 잘 활용하고 있으나,  
나머지 대형 테스트 클래스에는 적용되지 않아 관련 테스트끼리 묶어 읽기 어렵다.

**수정 방향 — DhLotteryApiClientTest**

```java
@DisplayName("DhLotteryApiClient")
class DhLotteryApiClientTest {

    private final DhLotteryApiClient client =
            new DhLotteryApiClient(null, new ObjectMapper(), "http://localhost");

    @Nested
    @DisplayName("parse() — 정상 파싱")
    class ParseSuccess {
        @Test
        @DisplayName("정상 응답을 도메인으로 변환한다")
        void convertsValidResponseToDomain() { ... }
    }

    @Nested
    @DisplayName("parse() — 필드 유효성 검증")
    class ParseFieldValidation {
        @Test
        @DisplayName("숫자 필드가 문자열이면 예외를 던진다")
        void throwsWhenNumberFieldIsString() { ... }

        @Test
        @DisplayName("숫자 필드가 null이면 예외를 던진다")
        void throwsWhenNumberFieldIsNull() { ... }

        @Test
        @DisplayName("숫자 필드가 boolean이면 예외를 던진다")
        void throwsWhenNumberFieldIsBoolean() { ... }

        @Test
        @DisplayName("숫자 필드가 소수이면 예외를 던진다")
        void throwsWhenNumberFieldIsDecimal() { ... }

        @Test
        @DisplayName("long 범위 초과 금액 필드이면 예외를 던진다")
        void throwsWhenLongFieldOverflows() { ... }
    }

    @Nested
    @DisplayName("parse() — 응답 구조 검증")
    class ParseResponseValidation {
        @Test
        @DisplayName("returnValue가 fail이면 예외를 던진다")
        void throwsWhenReturnValueIsFail() { ... }

        @Test
        @DisplayName("필수 필드가 누락되면 예외를 던진다")
        void throwsWhenRequiredFieldIsMissing() { ... }

        @Test
        @DisplayName("응답 회차 불일치 시 예외를 던진다")
        void throwsWhenRoundMismatches() { ... }
    }

    @Nested
    @DisplayName("fetch() — 재시도 로직")
    class FetchRetry {
        // @Disabled 해제 후 이동 (R3 참고)
    }
}
```

**수정 방향 — WinningNumberCollectServiceTest**

```java
@DisplayName("WinningNumberCollectService")
class WinningNumberCollectServiceTest {

    @Nested
    @DisplayName("collect() — 정상 수집")
    class CollectNormal { ... }

    @Nested
    @DisplayName("collect() — 입력 유효성")
    class CollectValidation { ... }

    @Nested
    @DisplayName("collect() — 동시성")
    class CollectConcurrency { ... }

    @Nested
    @DisplayName("collect() — 이벤트 발행")
    class CollectEvent { ... }
}
```

---

## R7 — SecurityIntegrationTest @SpringBootTest 비용

**파일:** `SecurityIntegrationTest.java`

**문제**

`@SpringBootTest`로 전체 ApplicationContext를 로딩하지만,  
실제로 검증하는 것은 Security 필터 체인과 MockMvc 응답이다.  
`@WebMvcTest`로 교체하면 DB 연결, Testcontainers, 전체 빈 초기화 없이 동일한 검증이 가능해  
테스트 시작 시간이 크게 줄어든다.

**현재 코드**

```java
@SpringBootTest          // 전체 컨텍스트 로딩 — DB, Flyway, 모든 빈
@ActiveProfiles("test")
class SecurityIntegrationTest {

    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy springSecurityFilterChain;

    @MockitoBean WinningNumberQueryService queryService;
    @MockitoBean WinningNumberCollectService collectService;

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context)
                .addFilters(springSecurityFilterChain)
                .build();
    }
```

**수정 방향**

```java
@WebMvcTest(controllers = {
        WinningNumberController.class,
        WinningNumberCollectController.class,
        RecommendController.class
})
@ActiveProfiles("test")
@DisplayName("Security 통합 테스트")
class SecurityIntegrationTest {

    @Autowired MockMvc mockMvc;   // 자동 구성, FilterChain 포함

    @MockitoBean WinningNumberQueryService queryService;
    @MockitoBean WinningNumberCollectService collectService;
    @MockitoBean RecommendService recommendService;
    @MockitoBean PastWinningCacheLoader cacheLoader;
```

**주의:** `@WebMvcTest`는 Security 설정 자동 적용 여부를 `@Import(SecurityConfig.class)`로  
명시해야 할 수 있다. 프로파일 설정에 따라 `KraftAdminProperties` 빈 주입도 필요.

---

## R8 — WinningNumberCollectServiceTest — 픽스처 중복

**파일:** `WinningNumberCollectServiceTest.java`

**문제**

`sample(int round)` 헬퍼는 있지만, `when(repository.findMaxRound()).thenReturn(...)`,  
`when(repository.existsByRound(...)).thenReturn(...)`,  
`when(repository.save(...)).thenAnswer(...)` stubbing이 테스트마다 반복된다.

**수정 방향 — 공통 stubbing 헬퍼 추출**

```java
// 반복되는 패턴을 메서드로 추출
private void givenDbHasRounds(int... rounds) {
    Set<Integer> stored = new HashSet<>(Arrays.stream(rounds).boxed().toList());
    when(repository.findMaxRound()).thenAnswer(inv ->
            stored.isEmpty() ? Optional.empty() : Optional.of(Collections.max(stored)));
    when(repository.existsByRound(anyInt())).thenAnswer(inv ->
            stored.contains((int) inv.getArgument(0)));
    when(repository.save(any())).thenAnswer(inv -> {
        WinningNumberEntity e = inv.getArgument(0);
        stored.add(e.getRound());
        return e;
    });
}

// 사용 예
@Test
void collectsUntilApiReturnsEmptyWhenNoTargetRound() {
    givenDbHasRounds(1100);
    when(lottoApiClient.fetch(1101)).thenReturn(Optional.of(sample(1101)));
    when(lottoApiClient.fetch(1102)).thenReturn(Optional.of(sample(1102)));
    when(lottoApiClient.fetch(1103)).thenReturn(Optional.empty());

    CollectResponse result = service.collect(null);

    assertThat(result.collected()).isEqualTo(2);
}
```

---

## 잘 구현된 테스트 패턴

유지하거나 다른 테스트에 적용하면 좋은 패턴들이다.

| 파일 | 잘된 점 |
|------|---------|
| `ExclusionRulesTest` | `@Nested`로 규칙별 명확한 그룹화, 각 케이스 독립 |
| `AdminApiTokenFilterTest` | `MockHttpServletRequest/Response` 직접 사용으로 빠른 단위 테스트 |
| `WinningNumberRepositoryIT` | `@Testcontainers`로 실제 MariaDB 11.8 검증, `@DirtiesContext`로 격리 |
| `ArchitectureTest` | ArchUnit으로 레이어 의존 규칙 강제 — 리팩토링 가드 |
| `KraftPropertiesBindingTest` | `@SpringBootTest` + `@ActiveProfiles("test")`로 바인딩 실검증 |
| `WinningNumberCollectServiceTest` | `CountDownLatch` 기반 동시성 테스트 (rejectsConcurrentCollectExecution) |
| `SecurityIntegrationTest` | Rate limit 30회 반복 후 429 검증 — 경계값 테스트 |

---

## 작업 순서

```
1단계 (즉시) — WinningNumberCollectServiceTest DisplayName 수정 (R1)
               인코딩 통일 (UTF-8), "test" → 의미있는 한글 이름

2단계 (즉시) — DhLotteryApiClientTest 필드 위치 이동 (R2)
               client 필드를 클래스 최상단으로 이동

3단계 (P0 구현과 동시) — LottoApiHealthIndicatorTest 신규 작성 (R5)
               P0-2 구현 직후 테스트 함께 커밋

4단계 — FailoverLottoApiClientTest 신규 작성 (R4)
         P1-2 복구 로직 구현과 함께 작성

5단계 — @Disabled 테스트 처리 결정 (R3)
         WireMock 의존성 추가 or 내부 구조 리팩토링 선택 후 활성화

6단계 — @Nested 그룹화 적용 (R6)
         DhLotteryApiClientTest, WinningNumberCollectServiceTest

7단계 — SecurityIntegrationTest @WebMvcTest 전환 (R7)
         전환 후 테스트 통과 여부 확인 필수

8단계 — WinningNumberCollectServiceTest 픽스처 헬퍼 추출 (R8)
```

---

> 최종 업데이트: 2026-05-11