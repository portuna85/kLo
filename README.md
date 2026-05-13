# Kraft Lotto

Spring Boot 4 기반의 로또 서비스입니다. 추천 번호 생성, 당첨번호 수집/조회, 관리자 수집 작업, REST Docs 문서 제공 기능을 포함합니다.

## 주요 기능

- 로또 번호 추천 API (`POST /api/recommend`)
- 당첨번호 조회 API (`/api/winning-numbers`)
- 당첨번호 통계 API (빈도, 빈도 요약, 조합 당첨 이력)
- 관리자 수집 API (`/admin/lotto/**`) + 토큰 인증
- 자동 수집 스케줄러 + ShedLock 기반 분산 락
- Actuator 헬스체크 (`/actuator/health/readiness`)
- REST Docs 정적 문서 (`/docs/`)

## 기술 스택

- Java 25
- Spring Boot 4.0.5
- Spring Web, Validation, Security, Actuator
- Spring Data JPA, Flyway, MariaDB
- Cache(Caffeine), 선택적 Redis 기반 Rate Limit
- Micrometer + OTLP Tracing
- Thymeleaf + WebJars(Bootstrap, Bootstrap Icons)
- JUnit 5, Spring Test, Testcontainers, ArchUnit, REST Docs

## 프로젝트 구조

- `src/main/java/com/kraft/lotto/feature/recommend`: 추천 도메인/서비스/API
- `src/main/java/com/kraft/lotto/feature/winningnumber`: 당첨번호 수집/조회/스케줄러/API
- `src/main/java/com/kraft/lotto/infra/config`: 환경변수 바인딩, `.env` 로딩, 배포 필수값 검증
- `src/main/java/com/kraft/lotto/infra/security`: Rate Limit, Admin 토큰 필터
- `src/main/resources/db/migration`: Flyway 마이그레이션
- `src/docs/asciidoc`: REST Docs 원본

## 실행 전 요구사항

- JDK 25
- Docker / Docker Compose (로컬 통합 실행 시)
- MariaDB 11+ (컨테이너 외부 실행 시)

## 빠른 시작 (Docker Compose)

1. 환경파일 생성

```bash
cp .env.example .env
```

2. 필수 값 수정

- `KRAFT_DB_PASSWORD`
- `KRAFT_DB_ROOT_PASSWORD`
- `KRAFT_ADMIN_API_TOKENS`

3. 실행

```bash
docker compose up -d --build
```

4. 확인

- 앱: `http://localhost:8080/`
- 헬스: `http://localhost:8080/actuator/health/readiness`
- 문서: `http://localhost:8080/docs/`

## 로컬 실행 (컨테이너 없이)

1. `.env` 준비

- `SPRING_PROFILES_ACTIVE=local`
- `KRAFT_IN_CONTAINER=false`
- `KRAFT_DB_LOCAL_HOST=localhost`
- `KRAFT_DB_URL`이 로컬 DB를 가리키는지 확인

2. 실행

```bash
./gradlew bootRun
```

Windows:

```powershell
.\gradlew.bat bootRun
```

## 환경변수

핵심 환경변수:

- `SPRING_PROFILES_ACTIVE`: `local` 또는 `prod`
- `KRAFT_DB_NAME`, `KRAFT_DB_URL`, `KRAFT_DB_USER`, `KRAFT_DB_PASSWORD`, `KRAFT_DB_ROOT_PASSWORD`
- `KRAFT_ADMIN_API_TOKENS` (쉼표로 다중 토큰 가능)
- `KRAFT_ADMIN_TOKEN_HEADER` (기본 `X-Kraft-Admin-Token`)
- `KRAFT_API_CLIENT` (`mock` 또는 `real`)
- `KRAFT_RECOMMEND_RATE_LIMIT_MAX_REQUESTS`, `KRAFT_RECOMMEND_RATE_LIMIT_WINDOW_SECONDS`
- `KRAFT_COLLECT_RATE_LIMIT_MAX_REQUESTS`, `KRAFT_COLLECT_RATE_LIMIT_WINDOW_SECONDS`

배포 시 필수 시크릿 검증은 Gradle 태스크로 확인 가능합니다.

```bash
./gradlew -q printRequiredEnvVars
```

## 보안 및 제한

- 인증 없이 허용:
- `GET /api/winning-numbers/**`
- `POST /api/recommend`
- `GET /actuator/health/**`
- `GET /docs/**`

- 관리자 토큰 필요:
- `POST /api/winning-numbers/refresh` (레거시, deprecation header 포함)
- `/admin/**` 전체

- Rate limit 대상:
- `POST /api/recommend`
- `POST /api/winning-numbers/refresh`
- `POST /admin/lotto/draws/*` 수집 계열

## API 요약

추천:

- `POST /api/recommend`
- body: `{ "count": 1..10 }` (`null`/생략 시 기본 5)
- `GET /api/recommend/rules`

당첨번호 조회:

- `GET /api/winning-numbers/latest`
- `GET /api/winning-numbers/{round}` (`1..3000`)
- `GET /api/winning-numbers?page=0&size=20`
- `GET /api/winning-numbers/stats/frequency`
- `GET /api/winning-numbers/stats/frequency-summary`
- `GET /api/winning-numbers/stats/combination-prize-history?numbers=1,2,3,4,5,6`

관리자 수집:

- `POST /admin/lotto/draws/collect-next`
- `POST /admin/lotto/draws/collect-missing`
- `POST /admin/lotto/draws/{drwNo}/refresh`
- `POST /admin/lotto/draws/backfill?from=...&to=...`
- `POST /admin/lotto/jobs/backfill?from=...&to=...`
- `GET /admin/lotto/jobs/{jobId}`

관리자 API 호출 시 헤더:

```http
X-Kraft-Admin-Token: <token>
```

## 자동 수집 스케줄

기본 타임존은 `Asia/Seoul`입니다.

- 토요일 21:10 collect-next
- 토요일 21:20, 21:40 재시도 collect-next
- 토요일 22:10 재시도 collect-next
- 일요일 06:10 collect-missing
- 매일 09:00 collect-missing

모든 잡은 ShedLock으로 동시 실행을 제어합니다.

## 빌드 / 테스트 / 문서

전체 빌드 + 테스트:

```bash
./gradlew clean build
```

통합 테스트(@Tag("it")):

```bash
./gradlew integrationTest
```

REST Docs 생성:

```bash
./gradlew asciidoctor
```

문서 포함 JAR:

```bash
./gradlew bootJarWithDocs
```

## Docker 이미지

로컬 빌드:

```bash
docker build -t kraft-lotto-app:local .
```

특징:

- 멀티스테이지 빌드 (JDK 25 -> JRE 25)
- non-root 사용자 실행
- `/app/healthcheck.sh` 기반 컨테이너 헬스체크

## 운영 참고

- `scripts/verify-prod-profile-in-jar.ps1`: JAR에 `application*.yml` 포함 여부 검증
- `docs/deploy/nginx/*.conf`: Nginx 배포 예시
- CI: 테스트/문서/JAR 검증 자동화 (`.github/workflows/ci.yml`)
- CD: self-hosted runner에서 Compose 배포 (`.github/workflows/cd.yml`)

## 라이선스

MIT License (`LICENSE` 참고)
