# Kraft Lotto

Kraft Lotto는 Spring Boot 기반의 로또 추천/당첨번호 조회 서비스입니다.
백엔드 API, 서버 렌더링 프론트엔드(Thymeleaf), 배치성 수집 기능, 보안/운영 구성을 한 저장소에서 관리합니다.

## 1. 프로젝트 분석 요약

### 백엔드
- 런타임: Java 25, Spring Boot 4.0.5
- 핵심 모듈: `feature.recommend`, `feature.winningnumber`, `infra`, `support`
- 주요 API 컨트롤러(6개)
  - `RecommendController`
  - `WinningNumberController`
  - `WinningNumberCollectController`
  - `AdminLottoDrawController`
  - `AdminLottoJobController`
  - `IndexController` (웹 진입점)
- 주요 서비스 레이어(5개)
  - `RecommendService`
  - `WinningNumberQueryService`
  - `WinningNumberCollectService`
  - `LottoCollectionService`
  - `BackfillJobService`
- 데이터/인프라
  - Spring Data JPA + Flyway + MariaDB
  - Redis(레이트리밋/캐시), Caffeine 캐시
  - 외부 API 연동: 동행복권 계열 클라이언트 + Failover/CircuitBreaker
  - 스케줄링: ShedLock 기반 분산 락
- 운영/관측
  - Spring Actuator, Micrometer + OpenTelemetry OTLP
  - Logstash Logback Encoder

### 프론트엔드
- 렌더링: Thymeleaf 템플릿
- UI: Bootstrap 5 + Bootstrap Icons
- 스크립트: Vanilla JavaScript (`static/js/app.js`)
- 스타일: 커스텀 CSS (`static/css/app.css`)
- 제공 화면 기능
  - 추천 번호 생성
  - 최신 당첨번호 조회
  - 회차별 조회
  - 번호별 출현 빈도
  - 당첨번호 목록 페이징
  - 관리자 수집 트리거 모달

### 테스트
- 테스트 소스: `src/test/java` 하위 26개 파일
- 테스트 메서드: 총 160개
- 테스트 구성
  - 단위 테스트: 도메인/서비스/보안 필터
  - 슬라이스 테스트: WebMvcTest 기반 컨트롤러
  - 통합 테스트: 저장소/보안/설정 바인딩
  - 아키텍처 테스트: ArchUnit

## 2. 기술 스택
- Language: Java 25
- Framework: Spring Boot 4.0.5
- Build: Gradle
- DB: MariaDB, H2(test)
- Migration: Flyway
- Cache/Rate Limit: Redis(선택적 rate-limit 인프라), Caffeine
- Docs: Spring REST Docs, Asciidoctor
- Test: JUnit 5, Spring Test, Testcontainers, ArchUnit

## 3. 로컬 실행

### 필수
- JDK 25
- Docker(선택: MariaDB 컨테이너 사용 시; 현재 기본 `docker-compose.yml`에는 Redis 서비스가 없습니다)

### 로컬 .env 작성 주의사항
- 로컬 실행 전 `cp .env.example .env`로 예시 파일을 복사한 뒤 placeholder 값을 실제 로컬 값으로 바꾸세요.
- `.env`에는 DB 비밀번호와 관리자 토큰이 들어가므로 커밋하지 마세요.
- `docker compose up -d`로 실행할 때 DB 호스트는 `mariadb`를 사용하고, 호스트 OS에서 `./gradlew bootRun`으로 직접 실행할 때는 로컬 DB 접속 값으로 조정하세요.
- Redis rate-limit은 기본 비활성입니다. `KRAFT_RECOMMEND_RATE_LIMIT_REDIS_ENABLED=false`가 기본값이며, 기본 로컬 실행은 Redis 없이 동작합니다.
- 현재 `docker-compose.yml`에는 Redis 서비스가 없으므로 로컬에서는 `KRAFT_RECOMMEND_RATE_LIMIT_REDIS_ENABLED=false`를 유지하세요.
- Redis rate-limit을 활성화하려면 운영자가 애플리케이션 외부에 별도 Redis 인프라(예: 관리형 Redis 또는 별도 Redis 컨테이너/클러스터)를 준비하고 애플리케이션이 접속할 수 있도록 설정해야 합니다.

### 실행
```bash
./gradlew bootRun
```
Windows:
```powershell
.\gradlew.bat bootRun
```

## 4. 테스트 실행
```bash
./gradlew test
```
Windows:
```powershell
.\gradlew.bat test
```

## 5. 저장소 구조
```text
src/main/java/com/kraft/lotto
  feature/recommend
  feature/winningnumber
  infra
  support
  web

src/main/resources
  templates
  static
  db/migration
```
