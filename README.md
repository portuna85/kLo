# Kraft Lotto

Spring Boot 기반 로또(6/45) 추천 및 당첨번호 조회 서비스입니다.

## Tech Stack
- Java 25
- Spring Boot 4.0.5
- Gradle 9.x
- MariaDB, Flyway
- Redis(선택), Caffeine
- Spring REST Docs, Asciidoctor
- JUnit 5, Mockito, Testcontainers, ArchUnit

## Modules
- `feature.recommend`: 번호 추천 도메인/서비스/API
- `feature.winningnumber`: 당첨번호 수집/조회/관리 API
- `infra`: 보안, 설정, 헬스체크, 공통 인프라
- `support`: 공통 응답/예외 처리

## Prerequisites
- JDK 25
- Docker / Docker Compose (로컬 DB 컨테이너 사용 시)

## Run
### 1) 환경 변수 준비
프로젝트 루트에 `.env`를 준비합니다.

```bash
cp .env.example .env
```

Windows:

```powershell
Copy-Item .env.example .env
```

### 2) 애플리케이션 실행

```bash
./gradlew bootRun
```

Windows:

```powershell
.\gradlew.bat bootRun
```

기본 프로파일은 `local`이며, 운영은 `prod` 프로파일을 사용합니다.

## Test

```bash
./gradlew test
```

Windows:

```powershell
.\gradlew.bat test
```

## Build

```bash
./gradlew clean build
```

Windows:

```powershell
.\gradlew.bat clean build
```

기본 실행 아티팩트:
- `build/libs/app.jar`

문서 포함 아티팩트:
- `./gradlew bootJarWithDocs`
- `build/libs/app-with-docs.jar`

## Configuration Profiles
- `src/main/resources/application.yml`: 공통 기본 설정
- `src/main/resources/application-local.yml`: 로컬 개발 설정
- `src/main/resources/application-prod.yml`: 운영 오버라이드

## CI/CD
- CI: `.github/workflows/ci.yml`
  - Java 25로 빌드/테스트 수행
  - `app.jar` 내 프로파일 리소스 포함 검증
- CD: `.github/workflows/cd.yml`
  - self-hosted runner에서 Docker Compose 배포
  - 운영 시크릿 검증 및 readiness/smoke test 수행

## API Docs
테스트/문서 생성 후 정적 문서가 포함됩니다.
- Asciidoctor 출력: `build/docs/asciidoc`
- `bootJarWithDocs` 빌드 시 `/static/docs`로 패키징

## Project Structure

```text
src/main/java/com/kraft/lotto
  feature/
  infra/
  support/
  web/

src/main/resources
  application.yml
  application-local.yml
  application-prod.yml
  db/migration/
  static/
  templates/
```

## Notes
- 관리자 API 토큰(`KRAFT_ADMIN_API_TOKEN`)은 운영에서 필수입니다.
- 민감 정보는 저장소에 커밋하지 않고 환경 변수/시크릿으로 주입합니다.
