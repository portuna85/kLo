# KraftLotto

로또 6/45 **인기·중복 패턴 회피형 추천** API 와 회차 조회/수집 기능을 제공하는 Spring Boot 백엔드 MVP 애플리케이션입니다.

> ⚠️ KraftLotto 는 **당첨 확률을 높이는 도구가 아닙니다.** 과거 1등 조합과 동일하거나 지나치게 편향된 조합(생일 편중, 등차수열, 연속번호, 동일 십의 자리 몰림)을 회피하는 것이 목적입니다.

---

## 1. 기술 스택

- Java 25 LTS / Spring Boot 4.0.5
- Gradle Kotlin DSL (Wrapper 9.4.1)
- MariaDB 11.8 LTS / Flyway / Spring Data JPA
- Spring Security (Basic Auth, ADMIN role)
- Spring Validation / Actuator / SpringDoc OpenAPI
- JUnit 5 / Mockito / Testcontainers (MariaDB) / ArchUnit

## 2. 패키지 구조 (Feature-First + Hexagonal-lite)

```
com.kraft.lotto
├── feature
│   ├── recommend         # 추천 도메인 / 규칙 / 서비스 / API
│   └── winningnumber     # 당첨번호 도메인 / 수집 / 조회 / API
├── support               # ApiResponse, ErrorCode, BusinessException, GlobalExceptionHandler
└── infra                 # 설정(@ConfigurationProperties), Security
```

원칙:

- domain 계층은 Spring/JPA/Web 의존성을 가지지 않습니다.
- Controller 는 Entity 를 직접 반환하지 않고 DTO + `ApiResponse<T>` 만 반환합니다.
- 외부 API 접근은 `LottoApiClient` port 인터페이스로 분리되어 있습니다 (`MockLottoApiClient`, `DhLotteryApiClient`).

## 3. 빠른 실행

### 3.1 로컬 (H2/Mock 없이) — Docker Compose

```bash
cp .env.example .env
# .env 의 KRAFT_ADMIN_PASSWORD 등 비밀값을 수정하세요.

docker compose up --build
```

- 앱: http://localhost:8080
- MariaDB: localhost:3306 (사용자 `kraft`)

### 3.2 로컬 개발 (수동)

```bash
# 1) MariaDB 가 떠 있다고 가정
export KRAFT_DB_URL=jdbc:mariadb://localhost:3306/kraft_lotto
export KRAFT_DB_USER=kraft
export KRAFT_DB_PASSWORD=kraft

# 2) 프로파일 local 로 실행 (Mock 외부 API + Swagger 활성화)
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

### 3.3 테스트 / 빌드

```bash
./gradlew clean test    # 단위 + WebMvc + Persistence(Testcontainers) 테스트
./gradlew clean build   # 최종 검증
```

> Persistence 통합 테스트는 Docker 가 실행 중이어야 합니다 (Testcontainers MariaDB).

## 4. 환경 변수

| 변수 | 기본값 | 설명 |
|------|--------|------|
| `SPRING_PROFILES_ACTIVE` | (없음) | `local` / `prod` / `test` 등 |
| `KRAFT_DB_URL` | `jdbc:mariadb://localhost:3306/kraft_lotto` | JDBC URL |
| `KRAFT_DB_USER` | `kraft` | DB 사용자 |
| `KRAFT_DB_PASSWORD` | `kraft` | DB 비밀번호 |
| `KRAFT_ADMIN_USERNAME` | `admin` (prod 에서는 필수) | 관리자 Basic Auth 사용자명 |
| `KRAFT_ADMIN_PASSWORD` | `admin` (prod 에서는 필수) | 관리자 Basic Auth 비밀번호 |
| `KRAFT_API_CLIENT` | `mock` | `mock` 또는 `real` (동행복권) |
| `KRAFT_API_URL` | `https://www.dhlottery.co.kr/common.do` | 외부 API base URL |
| `KRAFT_RECOMMEND_MAX_ATTEMPTS` | `100000` | 추천 생성 최대 시도 횟수 |

> 운영(`prod`) 프로파일에서는 `KRAFT_ADMIN_USERNAME` / `KRAFT_ADMIN_PASSWORD` 를 **반드시 환경변수로 주입**해야 합니다. 기본값을 두지 않습니다.

## 5. 프로파일별 정책

| 프로파일 | DataSource | 외부 API | Swagger UI | Actuator 노출 |
|----------|------------|----------|------------|--------------|
| `local`  | MariaDB (env 주입) | mock 기본 | ✅ 활성 | health, info, metrics |
| `prod`   | MariaDB (env 주입, 비밀값 필수) | real 기본 | ❌ 비활성 (`/v3/api-docs`, `/swagger-ui` 모두 OFF) | health, info |
| `test`   | H2 (MySQL 모드) | mock | n/a | n/a |

`metrics` 등 민감한 actuator endpoint 는 `/api/admin/**` 와 동일하게 ADMIN Basic Auth 가 필요합니다.

## 6. API 사용 예시

모든 응답은 다음 envelope 를 사용합니다.

```json
{ "success": true, "data": { ... }, "error": null }
```

### 6.1 추천 생성

```bash
curl -X POST http://localhost:8080/api/recommend \
     -H "Content-Type: application/json" \
     -d '{"count": 5}'
```

### 6.2 적용 중인 제외 규칙 목록

```bash
curl http://localhost:8080/api/recommend/rules
```

### 6.3 회차 조회

```bash
curl http://localhost:8080/api/winning-numbers/latest
curl http://localhost:8080/api/winning-numbers/1100
curl 'http://localhost:8080/api/winning-numbers?page=0&size=20'
curl http://localhost:8080/api/winning-numbers/stats/frequency
```

### 6.4 관리자 수동 수집 (Basic Auth)

```bash
curl -u "$KRAFT_ADMIN_USERNAME:$KRAFT_ADMIN_PASSWORD" \
     -X POST http://localhost:8080/api/admin/winning-numbers/refresh
```

응답 예:

```json
{ "success": true, "data": { "collected": 3, "skipped": 1, "failed": 0 }, "error": null }
```

## 7. 보안 요약

| Endpoint | 인증 |
|----------|------|
| `/api/recommend/**` | 공개 |
| `/api/winning-numbers/**` | 공개 |
| `/actuator/health` | 공개 |
| `/swagger-ui/**`, `/v3/api-docs/**` | 공개 (단, `prod` 에서는 비활성) |
| `/api/admin/**` | Basic Auth + ROLE_ADMIN |
| `/actuator/metrics/**` | Basic Auth + ROLE_ADMIN |

인증 실패 시에도 `ApiResponse` 실패 형식(`UNAUTHORIZED_ADMIN`, HTTP 401)으로 통일됩니다.

## 8. 운영 메모

- 비밀값(관리자 비밀번호, DB 비밀번호)은 코드/이미지에 하드코딩하지 말고 환경변수 또는 secret store 로 주입하세요.
- 기본 `KRAFT_API_CLIENT=real` 로 운영하며, 외부 API 장애는 `EXTERNAL_API_FAILURE` / `COLLECT_FAILED` 로 응답 envelope 에 매핑됩니다.
- 수집 완료 후 `WinningNumbersCollectedEvent` 가 발행되어 `PastWinningCache` 가 갱신됩니다.
- DB 마이그레이션은 Flyway (`src/main/resources/db/migration`) 가 담당하며, JPA `ddl-auto=validate` 로 검증만 수행합니다.

## 9. 디렉터리 / 파일 참고

- `Dockerfile` — 멀티스테이지 빌드, JRE 25 런타임, 비루트 사용자
- `docker-compose.yml` — MariaDB + 앱 (env 는 `.env` 사용)
- `.env.example` — 필요한 환경변수 목록
- `src/main/resources/application.yml` — 공통 기본값
- `src/main/resources/application-local.yml` — 로컬 개발 프로파일
- `src/main/resources/application-prod.yml` — 운영 프로파일 (Swagger OFF, actuator 최소 노출)
