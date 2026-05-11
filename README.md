# kLo

로또 6/45 당첨번호 조회, 수집, 추천을 제공하는 Spring Boot 애플리케이션입니다.

## 현재 상태 (2026-05-11 기준)

- 로컬 DB 최신 반영 회차: `1223`
- 최신 추첨일: `2026-05-09`
- 자동 수집 스케줄:
  - 매주 토요일 `22:00` (Asia/Seoul)
  - 매주 일요일 `21:00` (Asia/Seoul)
- 앱 시작 시(`startup`) 최신 회차까지 자동 수집 1회 실행

## 주요 리팩토링 반영 사항

### 1) 외부 API 실패 시 로컬 폴백

`KRAFT_API_CLIENT=real` 환경에서도 외부 API 장애/차단 시 자동으로 `mock` 클라이언트로 폴백해 수집이 계속되도록 구성했습니다.

- `FailoverLottoApiClient` 도입
- 1회 실패 후 fallback 모드 활성화 (반복 지연 최소화)
- 비JSON 응답(HTML 등) 감지 및 명확한 오류 메시지 제공

### 2) 자동 수집 스케줄러

- `WinningNumberAutoCollectScheduler` 추가
- `@EnableScheduling` 활성화
- 스케줄 트리거 로그 표준화:
  - `trigger=startup`
  - `trigger=sat-22`
  - `trigger=sun-21`

### 3) 회차/조회 테스트 리팩토링

- `1200` 관련 매직넘버 상수화
- 경로 변수 기반 API 테스트 정리
- 프로퍼티 바인딩 테스트에 신규 설정 검증 추가

## 기술 스택

- Java 25
- Spring Boot 4.0.5
- Gradle 9.4.1 (Kotlin DSL)
- MariaDB 11.x
- Spring Data JPA, Flyway
- Spring Security, Actuator
- Thymeleaf, Bootstrap 5
- Docker, Docker Compose

## 프로젝트 구조

```text
src/main/java/com/kraft/lotto
  feature/recommend
  feature/winningnumber
  infra
  support
  web

src/main/resources
  application.yml
  application-local.yml
  db/migration
  static
  templates
```

## 실행 방법

### 1) 환경 변수 파일 준비

```bash
cp .env.example .env
```

Windows PowerShell:

```powershell
Copy-Item .env.example .env
```

### 2) Docker Compose 실행

```bash
docker compose up -d --build
```

- 앱: `http://localhost:8080`
- 종료: `docker compose down`

### 3) 로컬 Gradle 실행

```bash
./gradlew bootRun
```

Windows PowerShell:

```powershell
.\gradlew.bat bootRun
```

## 빌드/테스트

```bash
./gradlew clean bootJar
./gradlew test
```

## 주요 API

- `GET /api/winning-numbers/latest`: 최신 당첨번호 조회
- `GET /api/winning-numbers/{round}`: 회차별 당첨번호 조회
- `GET /api/winning-numbers`: 당첨번호 페이징 조회
- `GET /api/winning-numbers/stats/frequency`: 번호 빈도 조회
- `POST /api/winning-numbers/refresh`: 당첨번호 수집 트리거 (관리자 토큰 필요)
- `POST /api/recommend`: 추천 번호 생성
- `GET /api/recommend/rules`: 추천 제외 규칙 조회

## 자동 수집 설정

`application.yml` 기본값:

- `kraft.collect.auto.enabled=true`
- `kraft.collect.auto.zone=Asia/Seoul`
- `kraft.collect.auto.cron.saturday-22=0 0 22 * * SAT`
- `kraft.collect.auto.cron.sunday-21=0 0 21 * * SUN`

환경변수 오버라이드:

- `KRAFT_COLLECT_AUTO_ENABLED`
- `KRAFT_COLLECT_AUTO_ZONE`
- `KRAFT_COLLECT_AUTO_CRON_SATURDAY_22`
- `KRAFT_COLLECT_AUTO_CRON_SUNDAY_21`

## 외부 API/폴백 설정

- `KRAFT_API_CLIENT=real|mock`
- `KRAFT_API_FALLBACK_TO_MOCK_ON_FAILURE=true|false`
- `KRAFT_API_MOCK_LATEST_ROUND=<회차>`

권장(로컬 개발):

```env
KRAFT_API_CLIENT=real
KRAFT_API_FALLBACK_TO_MOCK_ON_FAILURE=true
KRAFT_API_MOCK_LATEST_ROUND=1223
```

## 보안/운영 포인트

- `POST /api/winning-numbers/refresh`는 관리자 토큰 필요
- 추천/수집 API rate limit 적용
- Actuator readiness/liveness 제공
- Docker healthcheck 포함

## 추천 기능 안내

추천 기능은 당첨번호 예측 시스템이 아닙니다.
무작위 조합 기반으로 특정 편향 패턴을 피하는 보조 도구입니다.

## 라이선스

MIT License
