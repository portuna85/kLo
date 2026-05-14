# kLo (Kraft Lotto)

Spring Boot 기반의 로또 데이터 수집/조회/추천 서비스입니다.

## 핵심 기능
- 당첨번호 수집
  - 최신 회차 수집, 누락 회차 수집, 범위 백필, 특정 회차 새로고침
- 당첨번호 조회
  - 최신/회차별 조회, 페이징 조회
- 통계
  - 번호 빈도, 조합 1·2등 당첨 이력
- 추천
  - 규칙 기반 제외 + 조합 생성
- 운영 기능
  - 관리자 토큰 보호, 요청 속도 제한, 스케줄 수집, 백필 잡 관리

## 기술 스택
- Java 25
- Spring Boot 4.0.5
- Spring Data JPA / Hibernate
- MariaDB 11.8.2
- Flyway
- Caffeine Cache
- Resilience4j Circuit Breaker
- Micrometer / Actuator
- JUnit5 / Mockito / Testcontainers / ArchUnit

## 실행 방법
### 1) 환경 파일 준비
`.env.example`을 복사해 `.env`를 만듭니다.

```bash
cp .env.example .env
```

### 2) 필수 값 설정
최소 다음 값은 반드시 설정해야 합니다.
- `KRAFT_DB_NAME`
- `KRAFT_DB_USER`
- `KRAFT_DB_PASSWORD`
- `KRAFT_DB_ROOT_PASSWORD`
- `KRAFT_ADMIN_API_TOKENS`

### 3) 실행
```bash
docker compose up -d --build
```

### 4) 확인
- App: `http://localhost:8080`
- Health: `http://localhost:8080/actuator/health/readiness`
- Docs: `http://localhost:8080/docs/index.html`

## 주요 엔드포인트
### Public
- `POST /api/recommend`
- `GET /api/winning-numbers/latest`
- `GET /api/winning-numbers/{round}`
- `GET /api/winning-numbers`
- `GET /api/winning-numbers/stats/frequency`
- `GET /api/winning-numbers/stats/frequency-summary`
- `GET /api/winning-numbers/stats/combination-prize-history`

### Admin (토큰 필요)
헤더: `X-Kraft-Admin-Token`

- `POST /admin/lotto/draws/collect-next`
- `POST /admin/lotto/draws/collect-missing`
- `POST /admin/lotto/draws/{drwNo}/refresh`
- `POST /admin/lotto/draws/backfill?from=&to=`
- `POST /admin/lotto/jobs/backfill?from=&to=`
- `GET /admin/lotto/jobs/{jobId}`

## 테스트
```bash
./gradlew test
```

## 로깅
기본 로그 경로: `./logs`
- `kraft.log`
- `kraft-warn.log`
- `kraft-error.log`
- `kraft-debug.log`
- `kraft-json.log`

## 라이선스
MIT
