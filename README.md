# kLo

로또 6/45 당첨번호 조회, 수집, 추천을 제공하는 Spring Boot 애플리케이션입니다.

추천 기능은 당첨번호 예측이 아니라 무작위 조합 생성과 편향 회피를 위한 참고 도구입니다.

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
  db/migration
  static
  templates
```

## 빠른 실행

### 1) 환경 변수 파일 준비

```bash
cp .env.example .env
```

### 2) Docker Compose 실행

```bash
docker compose up --build
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

## 빌드와 테스트

```bash
./gradlew clean bootJar
./gradlew test
```

## 주요 API

- `GET /api/winning-numbers/latest`: 최신 당첨번호 조회
- `GET /api/winning-numbers/{round}`: 회차별 당첨번호 조회
- `GET /api/winning-numbers`: 당첨번호 페이징 조회
- `GET /api/winning-numbers/stats/frequency`: 번호 빈도 조회
- `POST /api/winning-numbers/refresh`: 당첨번호 수집 트리거 (관리자 보호)
- `POST /api/recommend`: 추천 번호 생성
- `GET /api/recommend/rules`: 추천 제외 규칙 조회

## 보안/운영 포인트

- `POST /api/winning-numbers/refresh`는 관리자 토큰 필수
- 추천/수집 API는 rate limit 적용
- Actuator readiness/liveness 제공
- Docker healthcheck 포함
- CD는 CI 성공 이후에만 실행되도록 구성

## 추천 기능 안내

추천은 통계 기반 예측 시스템이 아닙니다. 아래 목적의 필터형 무작위 추천입니다.

- 과거 당첨 조합 재사용 회피
- 특정 편향 패턴(생일 편향, 연속수 과다 등) 회피
- 사용자 선택 다양성 보조

## 환경 변수 핵심 항목

- `SPRING_PROFILES_ACTIVE`
- `KRAFT_DB_URL`, `KRAFT_DB_USER`, `KRAFT_DB_PASSWORD`
- `KRAFT_ADMIN_API_TOKEN`, `KRAFT_ADMIN_TOKEN_HEADER`
- `KRAFT_API_CLIENT` (`mock` 또는 `real`)
- `KRAFT_RECOMMEND_MAX_ATTEMPTS`
- `KRAFT_RECOMMEND_RULE_BIRTHDAY_THRESHOLD`
- `KRAFT_RECOMMEND_RULE_LONG_RUN_THRESHOLD`
- `KRAFT_RECOMMEND_RULE_DECADE_THRESHOLD`

전체 샘플은 `.env.example`를 참고하세요.

## Java 버전

이 프로젝트는 Java 25 기준으로 작성되었습니다.

- 로컬 빌드/실행 시 JDK 25 권장
- toolchain 미탐지 시 `JAVA_HOME` 또는 Gradle toolchain 설정 확인

## 라이선스

MIT License
