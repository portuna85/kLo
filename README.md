# kLo (Kraft Lotto)

kLo는 로또 당첨번호를 수집하고, 조회/통계/추천 API를 제공하는 Spring Boot 서비스입니다.

## What This Project Solves
- 당첨번호 수집 자동화: 최신/누락/범위 백필/특정 회차 갱신
- 조회 API: 최신/회차별/페이징 조회
- 통계 API: 번호 빈도, 특정 조합의 1·2등 당첨 이력
- 추천 API: 규칙 기반 필터링 + 조합 생성
- 운영 안정성: 관리자 토큰 인증, rate limit, 스케줄 수집, 백필 잡 관리, 구조화 로그

## Tech Stack
- Java 25
- Spring Boot 4.0.5
- Spring Data JPA / Hibernate
- MariaDB 11.8.2
- Flyway
- Caffeine Cache
- Resilience4j Circuit Breaker
- Micrometer / Actuator
- JUnit5 / Mockito / Testcontainers / ArchUnit

## Quick Start
### 1) 환경 파일 준비
```bash
cp .env.example .env
```

### 2) 필수 환경값 설정
최소 아래 값은 반드시 채워야 합니다.
- `KRAFT_DB_NAME`
- `KRAFT_DB_USER`
- `KRAFT_DB_PASSWORD`
- `KRAFT_DB_ROOT_PASSWORD`
- `KRAFT_ADMIN_API_TOKENS`

### 3) 실행
```bash
docker compose up -d --build
```

### 4) 동작 확인
- App: `http://localhost:8080`
- Readiness: `http://localhost:8080/actuator/health/readiness`
- API Docs: `http://localhost:8080/docs/index.html`

## API Overview
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

## Developer Onboarding
### 자주 쓰는 명령어
```bash
# 전체 테스트
./gradlew test

# 애플리케이션 실행
./gradlew bootRun

# REST Docs 포함 JAR
./gradlew bootJarWithDocs
```

### 테스트 전략
- 단위 테스트: 핵심 도메인/애플리케이션 로직
- 통합 테스트: JPA/DB 제약 검증(Testcontainers)
- 아키텍처 테스트: 계층 규칙 검증(ArchUnit)

### 프로젝트 구조(개요)
- `feature/*/web`: 컨트롤러, 요청/응답 DTO
- `feature/*/application`: 유스케이스/오케스트레이션
- `feature/*/domain`: 순수 도메인 로직
- `feature/*/infrastructure`: JPA 엔티티/리포지토리

## Operations
### 로그 파일
기본 경로: `./logs`
- `kraft.log`: 전체 로그
- `kraft-warn.log`: WARN 전용
- `kraft-error.log`: ERROR 전용
- `kraft-debug.log`: DEBUG 전용
- `kraft-json.log`: JSON 구조 로그

### 운영 정책 요약
- 관리자 API는 토큰 기반 보호
- 추천/수집 엔드포인트 rate-limit 적용
- 스케줄 수집 및 백필 잡 실행 상태 추적

## License
MIT
