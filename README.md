# Kraft Lotto

Kraft Lotto는 동행복권 로또(6/45) 데이터를 수집하고, 추천 번호와 통계 화면을 제공하는 Spring Boot 서비스입니다.

## 주요 기능
- 당첨번호 수집/조회
  - 최신 회차 조회
  - 회차별 조회
  - 페이징 목록 조회
- 번호별 출현 빈도 통계
- 저빈도 6개 번호 조합 기준 1등/2등 이력 조회
- 관리자 수집 API(단건, 누락 수집, 범위 백필)

## 기술 스택
- Java 25
- Spring Boot 4.0.5
- Gradle 9.x
- MariaDB + Flyway
- Caffeine Cache
- Thymeleaf + Bootstrap

## 실행 방법
1. 환경 변수 준비
```bash
cp .env.example .env
```
Windows:
```powershell
Copy-Item .env.example .env
```

2. 앱 실행
```bash
./gradlew bootRun
```
Windows:
```powershell
.\gradlew.bat bootRun
```

## Docker Compose
```bash
docker compose up -d --build
```

완전 초기화 후 재빌드:
```bash
docker compose down --volumes --remove-orphans --rmi all
docker compose build --no-cache
docker compose up -d --force-recreate
```

## 핵심 API
- `GET /api/winning-numbers/latest`
- `GET /api/winning-numbers/{round}`
- `GET /api/winning-numbers?page=0&size=20`
- `GET /api/winning-numbers/stats/frequency-summary`
- `POST /admin/lotto/draws/collect-next`
- `POST /admin/lotto/draws/backfill?from=1&to=1223`
- `POST /admin/lotto/jobs/backfill?from=1&to=1223`
- `GET /admin/lotto/jobs/{jobId}`

## 운영 참고
- 관리자 API는 `X-Kraft-Admin-Token` 헤더가 필요합니다.
- 실데이터 수집은 `.env`의 `KRAFT_API_CLIENT=real` 설정이 필요합니다.
- 2026-05-12 기준 동행복권 최신 수집 회차는 `1223회`입니다.
