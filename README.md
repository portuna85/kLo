# Kraft Lotto

Kraft Lotto는 로또 6/45 당첨번호를 조회·수집하고, 편향 패턴을 피한 무작위 조합 추천 API와 웹 화면을 제공하는 Spring Boot 애플리케이션입니다.

## 주요 기능

- **당첨번호 조회**: 최신 회차, 특정 회차, 페이지 목록, 번호별 출현 빈도 통계 제공
- **당첨번호 수집**: 동행복권 API 또는 Mock Client 기반 수집 지원 (관리자 API 전용)
- **추천 시스템**: 1~10개 조합 생성, 다양한 제외 규칙(생일 편향, 과거 당첨 번호 등) 기반 필터링
- **보안 및 제한**: 관리자 전용 API 토큰 인증, 주요 API IP 기반 Rate Limit 적용
- **운영 편의성**: Spring Boot Actuator를 통한 헬스체크(외부 API 연동 상태 포함), Flyway를 이용한 DB 마이그레이션, Docker/Compose 지원

## 기술 스택

- **언어 및 프레임워크**: Java 25, Spring Boot 4.0.5
- **보안 및 운영**: Spring Security, Spring Boot Actuator
- **데이터베이스**: MariaDB (운영), H2 (테스트), Spring Data JPA, Flyway
- **빌드 도구**: Gradle Kotlin DSL
- **인프라**: Docker, Docker Compose, GitHub Actions (CI/CD)

## 실행 준비

`.env.example`을 참고해 실행 환경 변수를 준비합니다.

```bash
cp .env.example .env
```

필수 DB 환경 변수는 다음과 같습니다.

```env
KRAFT_DB_URL=jdbc:mariadb://localhost:3306/kraft_lotto
KRAFT_DB_USER=kraft
KRAFT_DB_PASSWORD=change-me
```

외부 API 관련 기본값은 `application.yml`에 정의되어 있으며, 필요 시 환경 변수로 덮어쓸 수 있습니다.

```env
KRAFT_API_CLIENT=mock
KRAFT_API_URL=https://www.dhlottery.co.kr/common.do
KRAFT_API_MAX_RETRIES=2
KRAFT_API_RETRY_BACKOFF_MS=200
```

당첨번호 수집 트리거는 관리자 API이므로 별도 토큰을 설정해야 합니다.

```env
# 운영 배포 시 반드시 GitHub Secrets에 등록 필요
KRAFT_ADMIN_API_TOKEN=change-me-admin-token
KRAFT_ADMIN_TOKEN_HEADER=X-Kraft-Admin-Token
```

> ⚠️ 운영 배포에서는 실제 관리자 토큰 값을 코드나 .env에 직접 입력하지 않고, GitHub Secrets(KRAFT_ADMIN_API_TOKEN)로 등록해야 합니다.
>
> 예시: GitHub 저장소 Settings → Secrets and variables → Actions → New repository secret → Name: `KRAFT_ADMIN_API_TOKEN`, Value: 실제 토큰

## 로컬 실행

```bash
./gradlew bootRun
```

기본 프로파일은 `local`입니다. 애플리케이션은 정적 웹 UI와 REST API를 함께 제공합니다.

## 테스트

```bash
./gradlew test
```

테스트는 H2 in-memory DB와 `test` 프로파일 설정을 사용합니다.

## API 요약

| Method | Path | 설명 |
| --- | --- | --- |
| `GET` | `/` | 웹 화면 |
| `POST` | `/api/recommend` | 추천 조합 생성 |
| `GET` | `/api/recommend/rules` | 추천 제외 규칙 조회 |
| `GET` | `/api/winning-numbers/latest` | 최신 당첨번호 조회 |
| `GET` | `/api/winning-numbers/{round}` | 특정 회차 조회 |
| `GET` | `/api/winning-numbers` | 당첨번호 목록 조회 |
| `GET` | `/api/winning-numbers/stats/frequency` | 번호별 출현 빈도 |
| `POST` | `/api/winning-numbers/refresh` | 당첨번호 수집 트리거(관리자 토큰 필요) |
| `GET` | `/actuator/health` | 헬스체크 |

## 보안 정책

- `/`, 정적 리소스, 조회 API, 추천 API, health, docs만 명시적으로 허용합니다.
- 명시 허용되지 않은 요청은 `denyAll` 정책으로 차단합니다.
- `POST /api/winning-numbers/refresh`는 `X-Kraft-Admin-Token` 관리자 토큰 헤더가 필요합니다.
- `POST /api/recommend`와 `POST /api/winning-numbers/refresh`는 IP 기반 rate limit을 적용하며, endpoint별 제한값과 `Retry-After` 응답 헤더를 사용합니다.
- 기본 제한값은 추천 API `30회/60초`, 수집 API `10회/300초`입니다.
- 신뢰 가능한 프록시(루프백/사설/link-local)에서 들어온 요청에 한해 `X-Forwarded-For`의 첫 번째 IP를 클라이언트 IP로 사용합니다.

## 당첨번호 수집 정책

- 한 번에 하나의 수집 작업만 실행합니다.
- 이미 저장된 최신 회차 이하 `targetRound` 요청은 외부 API를 호출하지 않고 건너뛴 결과를 반환합니다.
- 회차별 저장 실패는 전체 수집을 중단하지 않고 실패 회차 목록에 포함합니다.
- 외부 API 호출 자체가 실패하면 부분 진행 이벤트를 발행한 뒤 비즈니스 예외로 종료합니다.

## 추천 정책

- `count`는 1~10 범위만 허용합니다.
- 운영 환경은 `SecureRandom` 기반 번호 생성기를 사용합니다.
- 추천기는 제외 규칙을 통과한 중복 없는 조합만 반환합니다.
- 추천 결과는 당첨 확률 향상을 보장하지 않습니다.

## 빌드 및 문서 생성

- 일반 실행 jar 빌드(문서 미포함):

```bash
./gradlew clean bootJar
```

- REST Docs 포함 jar 빌드:

```bash
./gradlew clean bootJarWithDocs
```

- REST Docs HTML만 별도 생성:

```bash
./gradlew clean asciidoctor
```

- Docker 빌드는 일반 bootJar만 사용합니다.

## Docker Compose

```bash
docker compose up --build
```

Compose 실행 전 `.env`에 DB 및 애플리케이션 환경 변수를 설정하세요.
