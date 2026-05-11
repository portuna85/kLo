# kLo

**kLo**는 로또 6/45 당첨번호 조회, 수집, 통계, 규칙 기반 번호 추천을 제공하는 Spring Boot 애플리케이션입니다.

저장된 회차 데이터를 REST API로 조회하고, 관리자 전용 API와 스케줄러를 통해 당첨번호를 수집·보정할 수 있습니다. 추천 기능은 설정 가능한 규칙과 무작위 조합을 기반으로 하며, 당첨 예측이나 당첨 확률 향상을 보장하지 않습니다.

---

## 프로젝트 개요

kLo는 로또 6/45 회차별 당첨번호를 MariaDB에 저장하고, 이를 조회·분석·활용할 수 있도록 구성한 서버 애플리케이션입니다.

애플리케이션은 Spring Boot 기반의 전통적인 계층형 구조를 따릅니다. REST API 계층, 애플리케이션 서비스 계층, 도메인/규칙 처리 계층, 영속성 계층, 외부 API 연동 계층이 분리되어 있으며, Flyway를 통해 데이터베이스 스키마를 관리합니다.

로컬 개발 환경은 Docker Compose로 실행할 수 있고, 운영 환경에서는 `prod` 프로파일과 환경 변수 기반 설정 주입을 전제로 합니다.

## 핵심 기능

- 최신 로또 6/45 당첨번호 조회
- 특정 회차 당첨번호 조회
- 당첨번호 목록 페이징 조회
- 저장된 당첨번호 기반 번호별 빈도 통계 제공
- 규칙 기반 추천 번호 생성
- 추천 제외 규칙 조회
- 관리자 토큰 기반 수동 수집·보정 API 제공
- 회차별 갱신, 다음 회차 수집, 누락 회차 수집, 구간 백필 지원
- `Asia/Seoul` 기준 자동 수집 스케줄 지원
- 실제 API 클라이언트와 mock API 클라이언트 전환 지원
- 로컬 프로파일에서 외부 API 실패 시 mock fallback 설정 가능
- Spring Security 기반 관리자 API 보호
- 추천/수집 API rate limit 적용
- Actuator health, readiness, liveness, metrics 엔드포인트 제공
- Docker 및 Docker Compose 기반 로컬 실행 지원

## 기술 스택

| 구분 | 기술 |
| --- | --- |
| Language | Java 25 |
| Framework | Spring Boot 4.0.5 |
| Build | Gradle 9.4.1, Kotlin DSL |
| Web | Spring MVC, Thymeleaf, Bootstrap 5, WebJars |
| Persistence | Spring Data JPA, MariaDB 11.x, Flyway |
| Security | Spring Security, Admin Token Filter, Rate Limit Filter |
| Observability | Spring Boot Actuator, Micrometer |
| Test | JUnit 5, Spring Boot Test, Spring Security Test, REST Docs, Testcontainers, H2, ArchUnit |
| Runtime | Docker, Docker Compose, Eclipse Temurin 25 |

## 아키텍처 요약

```text
Client / Browser
  -> Spring MVC Controller
  -> Application Service
  -> Domain / Rule Logic
  -> Repository / External API Client
  -> MariaDB / DHLottery-compatible API endpoint
```

주요 패키지 구성은 기능 단위와 인프라 단위로 나뉩니다.

| 경로 | 역할 |
| --- | --- |
| `feature/winningnumber` | 당첨번호 도메인, 조회, 수집, 스케줄링, API |
| `feature/recommend` | 추천 규칙, 추천 생성, 추천 API |
| `infra` | 설정, 보안 필터, 외부 API 클라이언트, 인프라 구성 |
| `support` | 공통 API 응답, 예외 처리 등 공통 지원 코드 |
| `web` | Thymeleaf 기반 화면 진입 컨트롤러 |
| `resources/db/migration` | Flyway 데이터베이스 마이그레이션 |

## 설치 방법

### 사전 준비

- JDK 25
- Docker 및 Docker Compose
- Git

### 저장소 복제

```bash
git clone https://github.com/portuna85/kLo.git
cd kLo
```

### 환경 변수 파일 생성

```bash
cp .env.example .env
```

Windows PowerShell:

```powershell
Copy-Item .env.example .env
```

`.env` 파일을 생성한 뒤 데이터베이스 비밀번호와 관리자 토큰을 반드시 변경하세요.

로컬 Docker 개발 환경의 기본 프로파일은 `local`이며, 기본 API 클라이언트는 `mock`입니다. 실제 동행복권 호환 엔드포인트를 사용하려면 다음 값을 설정합니다.

```env
KRAFT_API_CLIENT=real
```

## 환경 변수

| 변수 | 필수 | 설명 | 기본값 / 예시 |
| --- | --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | 예 | 활성 Spring 프로파일 | `local` |
| `KRAFT_APP_PORT` | 아니오 | 애플리케이션 컨테이너의 호스트 매핑 포트 | `8080` |
| `KRAFT_DB_PORT` | 아니오 | MariaDB 컨테이너의 호스트 매핑 포트 | `3306` |
| `KRAFT_DB_NAME` | 예 | MariaDB 데이터베이스 이름 | `kraft_lotto` |
| `KRAFT_DB_URL` | 예 | Spring datasource JDBC URL | `jdbc:mariadb://mariadb:3306/kraft_lotto...` |
| `KRAFT_DB_USER` | 예 | 데이터베이스 사용자명 | `kraft` |
| `KRAFT_DB_PASSWORD` | 예 | 데이터베이스 비밀번호 | `change-me` |
| `KRAFT_DB_ROOT_PASSWORD` | 예 | MariaDB root 비밀번호 | `change-me-root` |
| `KRAFT_DB_LOCAL_HOST` | 아니오 | 컨테이너 외부 실행 시 DB 호스트 치환 값 | `localhost` |
| `KRAFT_IN_CONTAINER` | 아니오 | 컨테이너 내부 실행 여부 | `false` / `true` |
| `KRAFT_API_CLIENT` | 아니오 | 당첨번호 API 클라이언트 모드 | `mock` 또는 `real` |
| `KRAFT_API_URL` | 아니오 | 당첨번호 API 기본 URL | `https://www.dhlottery.co.kr/common.do` |
| `KRAFT_API_CONNECT_TIMEOUT_MS` | 아니오 | 외부 API 연결 제한 시간 | `3000` |
| `KRAFT_API_READ_TIMEOUT_MS` | 아니오 | 외부 API 읽기 제한 시간 | `5000` |
| `KRAFT_API_MAX_RETRIES` | 아니오 | 외부 API 재시도 횟수 | `2` |
| `KRAFT_API_RETRY_BACKOFF_MS` | 아니오 | 외부 API 재시도 간격 | `700` |
| `KRAFT_API_FALLBACK_TO_MOCK_ON_FAILURE` | 아니오 | 실제 API 실패 시 mock fallback 사용 여부 | `false`, local에서는 `true` |
| `KRAFT_API_MOCK_LATEST_ROUND` | 아니오 | mock 클라이언트의 최신 회차 | `1200` |
| `KRAFT_ADMIN_API_TOKEN` | 관리자 API 사용 시 예 | 보호된 수집 API 호출용 토큰 | `change-me-admin-token` |
| `KRAFT_ADMIN_TOKEN_HEADER` | 아니오 | 관리자 토큰을 전달할 헤더 이름 | `X-Kraft-Admin-Token` |
| `KRAFT_LOTTO_SCHEDULER_ENABLED` | 아니오 | 로또 스케줄러 활성화 여부 | `true` |
| `KRAFT_COLLECT_AUTO_ENABLED` | 아니오 | 자동 수집 작업 활성화 여부 | `true` |
| `KRAFT_COLLECT_AUTO_ZONE` | 아니오 | 자동 수집 스케줄 타임존 | `Asia/Seoul` |
| `KRAFT_COLLECT_AUTO_CRON_SATURDAY_21_10` | 아니오 | 토요일 1차 수집 스케줄 | `0 10 21 ? * SAT` |
| `KRAFT_COLLECT_AUTO_CRON_SATURDAY_21_RETRY` | 아니오 | 토요일 재시도 스케줄 | `0 20,40 21 ? * SAT` |
| `KRAFT_COLLECT_AUTO_CRON_SATURDAY_22_10` | 아니오 | 토요일 추가 재시도 스케줄 | `0 10 22 ? * SAT` |
| `KRAFT_COLLECT_AUTO_CRON_SUNDAY_06_10` | 아니오 | 일요일 보정 수집 스케줄 | `0 10 6 ? * SUN` |
| `KRAFT_COLLECT_AUTO_CRON_DAILY_09_00` | 아니오 | 매일 누락 회차 보정 스케줄 | `0 0 9 * * *` |
| `KRAFT_RECOMMEND_MAX_ATTEMPTS` | 아니오 | 추천 조합 생성 최대 시도 횟수 | `5000` |
| `KRAFT_RECOMMEND_RATE_LIMIT_MAX_REQUESTS` | 아니오 | 추천 API rate limit 요청 수 | `30` |
| `KRAFT_RECOMMEND_RATE_LIMIT_WINDOW_SECONDS` | 아니오 | 추천 API rate limit 시간 창 | `60` |
| `KRAFT_COLLECT_RATE_LIMIT_MAX_REQUESTS` | 아니오 | 수집 API rate limit 요청 수 | `10` |
| `KRAFT_COLLECT_RATE_LIMIT_WINDOW_SECONDS` | 아니오 | 수집 API rate limit 시간 창 | `300` |
| `KRAFT_LOG_PATH` | 아니오 | 로그 저장 경로 | `./logs` |
| `KRAFT_HEALTHCHECK_URL` | 아니오 | Docker healthcheck URL | `http://localhost:8080/actuator/health/readiness` |
| `KRAFT_HEALTHCHECK_TIMEOUT_SECONDS` | 아니오 | Docker healthcheck 제한 시간 | `3` |

`.env`와 운영 비밀값은 저장소에 커밋하지 않습니다.

## 로컬 실행 명령어

### Docker Compose 실행

```bash
docker compose up -d --build
```

애플리케이션 접속:

```text
http://localhost:8080
```

컨테이너 중지:

```bash
docker compose down
```

컨테이너와 MariaDB 볼륨 삭제:

```bash
docker compose down -v
```

### Gradle로 실행

Docker 밖에서 직접 실행하는 경우, 필요한 환경 변수를 셸 또는 IDE에 먼저 주입해야 합니다.

```bash
./gradlew bootRun
```

Windows PowerShell:

```powershell
.\gradlew.bat bootRun
```

### 실행 JAR 빌드

```bash
./gradlew clean bootJar
```

Windows PowerShell:

```powershell
.\gradlew.bat clean bootJar
```

기본 산출물 경로:

```text
build/libs/app.jar
```

## 테스트 명령어

전체 테스트 실행:

```bash
./gradlew test
```

Windows PowerShell:

```powershell
.\gradlew.bat test
```

테스트를 제외하고 JAR 빌드:

```bash
./gradlew clean bootJar -x test
```

REST Docs 생성 및 문서 포함 JAR 빌드:

```bash
./gradlew bootJarWithDocs
```

운영 프로파일 파일 포함 여부 검증:

```powershell
.\gradlew.bat clean bootJar -x test
.\scripts\verify-prod-profile-in-jar.ps1
```

직접 확인:

```bash
jar tf build/libs/app.jar | grep application
```

기대 결과:

```text
BOOT-INF/classes/application.yml
BOOT-INF/classes/application-local.yml
BOOT-INF/classes/application-prod.yml
```

## API 개요

모든 REST API는 공통 응답 래퍼를 사용합니다.

### 공개 API

| Method | Path | 설명 |
| --- | --- | --- |
| `GET` | `/api/winning-numbers/latest` | 최신 저장 당첨번호 조회 |
| `GET` | `/api/winning-numbers/{round}` | 특정 회차 당첨번호 조회 |
| `GET` | `/api/winning-numbers?page=0&size=20` | 당첨번호 목록 페이징 조회 |
| `GET` | `/api/winning-numbers/stats/frequency` | 번호별 출현 빈도 조회 |
| `POST` | `/api/recommend` | 추천 번호 조합 생성 |
| `GET` | `/api/recommend/rules` | 추천 제외 규칙 조회 |

추천 API 요청 예시:

```bash
curl -X POST "http://localhost:8080/api/recommend" \
  -H "Content-Type: application/json" \
  -d '{"count":5}'
```

### 관리자 수집 API

아래 API는 관리자 토큰 헤더가 필요합니다.

| Method | Path | 설명 |
| --- | --- | --- |
| `POST` | `/api/winning-numbers/refresh` | 다음 회차 또는 지정 회차 수집 |
| `POST` | `/admin/lotto/draws/collect-next` | 다음 예상 회차 수집 |
| `POST` | `/admin/lotto/draws/collect-missing` | 누락 회차 수집 |
| `POST` | `/admin/lotto/draws/{drwNo}/refresh` | 특정 회차 갱신 |
| `POST` | `/admin/lotto/draws/backfill?from=1&to=1200` | 지정 구간 백필 |

관리자 API 요청 예시:

```bash
curl -X POST "http://localhost:8080/api/winning-numbers/refresh" \
  -H "Content-Type: application/json" \
  -H "X-Kraft-Admin-Token: ${KRAFT_ADMIN_API_TOKEN}" \
  -d '{"targetRound":"1200"}'
```

### Health Check

| Method | Path | 설명 |
| --- | --- | --- |
| `GET` | `/actuator/health` | 기본 health 상태 |
| `GET` | `/actuator/health/liveness` | liveness probe |
| `GET` | `/actuator/health/readiness` | readiness probe |

## 배포 참고 사항

- 운영 배포에서는 `SPRING_PROFILES_ACTIVE=prod`를 사용합니다.
- 비밀번호, API 토큰, 운영 DB URL은 환경 변수 또는 배포 매니페스트로 주입합니다.
- 저장소 파일에 운영 비밀값을 직접 작성하지 않습니다.
- Docker 이미지는 JDK 25로 빌드하고 JRE 25 기반 런타임에서 non-root 사용자로 실행됩니다.
- 컨테이너 내부 애플리케이션 포트는 `8080`입니다.
- 로컬 Compose 환경의 MariaDB 데이터는 Docker volume에 저장됩니다.
- Flyway 마이그레이션은 애플리케이션 시작 시 실행됩니다.
- 트래픽을 연결하기 전 `/actuator/health/readiness`를 확인합니다.
- 배포 전 `application-prod.yml`이 `build/libs/app.jar`에 포함되어 있는지 확인합니다.
- 실제 당첨번호 수집이 필요한 환경에서는 `KRAFT_API_CLIENT=real`을 명시합니다.
- 외부 API fallback 사용 여부는 운영 정책에 맞게 명시적으로 설정합니다.

## 프로젝트 구조

```text
.
├── build.gradle.kts
├── settings.gradle.kts
├── Dockerfile
├── docker-compose.yml
├── .env.example
├── docker/
│   └── healthcheck.sh
├── scripts/
│   └── verify-prod-profile-in-jar.ps1
└── src/
    ├── main/
    │   ├── java/com/kraft/lotto/
    │   │   ├── feature/
    │   │   │   ├── recommend/
    │   │   │   └── winningnumber/
    │   │   ├── infra/
    │   │   ├── support/
    │   │   └── web/
    │   └── resources/
    │       ├── application.yml
    │       ├── application-local.yml
    │       ├── application-prod.yml
    │       ├── db/migration/
    │       ├── static/
    │       └── templates/
    └── test/
        └── java/com/kraft/lotto/
```

## 향후 개선 로드맵

- REST API용 OpenAPI 문서 생성 추가
- 공개 API와 관리자 API에 대한 REST Docs 범위 확대
- 회차 검색, 통계, 추천 결과 화면 개선
- 수집 성공·실패·재시도에 대한 운영 메트릭 강화
- 테스트, 빌드, 이미지 배포, 배포 검증을 위한 CI/CD 워크플로 추가
- 필요 시 단순 관리자 토큰 방식보다 강화된 관리자 인증 모델 도입
- 백필 실행 결과와 수집 이력을 확인할 수 있는 운영용 조회 기능 추가
- 스케줄러, 외부 API 실패 처리, DB 마이그레이션에 대한 통합 테스트 보강

## 라이선스

MIT License
