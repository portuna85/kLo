# Kraft Lotto

Kraft Lotto는 동행복권 로또(6/45) 데이터를 수집하고, 추천 번호와 통계 정보를 제공하는 Spring Boot 서비스입니다.

## 핵심 기능
- 당첨번호 수집/조회
  - 최신 회차 조회
  - 회차별 조회
  - 페이지 조회
- 번호 출현 빈도 통계
  - 1~45 번호별 누적 출현 횟수
  - 하위 6개 저빈도 번호 조합 기준 `1등/2등 당첨 이력` 조회
- 추천 번호 생성
  - 과거 당첨 조합 제외
  - 편향 완화 규칙 기반 조합 추천
- 운영/관리
  - 관리자 수집 API
  - 자동 수집 스케줄러
  - 헬스체크 및 공통 예외 응답

## 기술 스택
- Java 25
- Spring Boot 4.0.5
- Gradle 9.x
- MariaDB, Flyway
- Redis(선택), Caffeine
- JUnit 5, Mockito, Testcontainers

## 실행 환경
- JDK 25
- Docker / Docker Compose (DB 컨테이너 사용 시)

## 빠른 시작
1. 환경 변수 파일 준비

```bash
cp .env.example .env
```

Windows:

```powershell
Copy-Item .env.example .env
```

2. 애플리케이션 실행

```bash
./gradlew bootRun
```

Windows:

```powershell
.\gradlew.bat bootRun
```

기본 프로파일은 `local`입니다.

## 테스트/빌드

```bash
./gradlew test
./gradlew clean build
```

Windows:

```powershell
.\gradlew.bat test
.\gradlew.bat clean build
```

## 주요 API
- `GET /api/winning-numbers/latest`
- `GET /api/winning-numbers/{round}`
- `GET /api/winning-numbers?page=0&size=20`
- `GET /api/winning-numbers/stats/frequency`
- `GET /api/winning-numbers/stats/combination-prize-history?numbers=3&numbers=5&numbers=12&numbers=25&numbers=27&numbers=42`

## 설정 파일
- `src/main/resources/application.yml`
- `src/main/resources/application-local.yml`
- `src/main/resources/application-prod.yml`
- `.env.example`

## CI/CD
- CI: `.github/workflows/ci.yml`
- CD: `.github/workflows/cd.yml`

## 보안/운영 주의사항
- 운영에서는 `KRAFT_ADMIN_API_TOKEN`이 필수입니다.
- 민감 정보는 저장소에 커밋하지 않고 시크릿/환경 변수로 주입하세요.
