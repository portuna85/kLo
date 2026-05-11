# 서버 작업 체크리스트 (배포 후)

기준: 2026-05-11 배포 (`main`, 커밋 `b37ae83`)

## 1) 배포 직후 필수 확인

1. 애플리케이션 컨테이너 상태 확인

```bash
docker compose ps
```

정상 기준:
- `kraft-lotto-app` = `Up (healthy)`
- `kraft-lotto-mariadb` = `Up (healthy)`

2. 앱 로그에서 자동 수집 시작/완료 로그 확인

```bash
docker compose logs app --since 10m
```

찾아야 할 로그 키워드:
- `auto-collect start trigger=startup`
- `auto-collect done trigger=startup`

3. API 헬스 체크

```bash
curl -s http://localhost:8080/actuator/health
curl -s http://localhost:8080/actuator/health/readiness
```

4. 최신 회차 조회 확인

```bash
curl -s http://localhost:8080/api/winning-numbers/latest
```

## 2) 서버 환경변수 점검 (.env)

아래 항목이 반드시 의도대로 설정되어 있는지 확인:

```env
SPRING_PROFILES_ACTIVE=prod
KRAFT_API_CLIENT=real
KRAFT_API_FALLBACK_TO_MOCK_ON_FAILURE=true
KRAFT_API_MOCK_LATEST_ROUND=1223
KRAFT_ADMIN_API_TOKEN=<서버 보안값>
KRAFT_ADMIN_TOKEN_HEADER=X-Kraft-Admin-Token
```

주의:
- 운영에서 외부 API를 항상 신뢰할 수 없다면 `fallback=true` 유지 권장
- 실제 최신 회차가 1223을 넘으면 `KRAFT_API_MOCK_LATEST_ROUND` 값을 함께 올려야 로컬/차단 환경에서 최신 반영이 지속됨

## 3) 자동 수집 스케줄 확인

현재 스케줄(Asia/Seoul):
- 토요일 22:00
- 일요일 21:00

설정 키:

```env
KRAFT_COLLECT_AUTO_ENABLED=true
KRAFT_COLLECT_AUTO_ZONE=Asia/Seoul
KRAFT_COLLECT_AUTO_CRON_SATURDAY_22=0 0 22 * * SAT
KRAFT_COLLECT_AUTO_CRON_SUNDAY_21=0 0 21 * * SUN
```

변경 후 적용:

```bash
docker compose up -d app
```

## 4) 수동 강제 업데이트 방법

관리자 토큰으로 수동 수집 트리거:

```bash
curl -X POST http://localhost:8080/api/winning-numbers/refresh \
  -H "Content-Type: application/json" \
  -H "X-Kraft-Admin-Token: <KRAFT_ADMIN_API_TOKEN>" \
  -d '{"targetRound":1223}'
```

최신까지 자동 수집(타겟 미지정):

```bash
curl -X POST http://localhost:8080/api/winning-numbers/refresh \
  -H "X-Kraft-Admin-Token: <KRAFT_ADMIN_API_TOKEN>"
```

## 5) DB 검증 쿼리

```bash
docker compose exec -T mariadb mariadb -u<DB_USER> -p<DB_PASSWORD> -e "use kraft_lotto; select max(round) as max_round, count(*) as cnt from winning_numbers;"
```

정상 기준:
- `max_round`가 API 최신 회차와 일치
- `cnt`가 비정상적으로 줄어들지 않음

## 6) 장애 시 우선 조치

1. 외부 API 연결 실패/타임아웃
- 증상: `EXTERNAL_API_FAILURE`, `Connect timed out`
- 조치:
  - `KRAFT_API_FALLBACK_TO_MOCK_ON_FAILURE=true` 확인
  - `KRAFT_API_MOCK_LATEST_ROUND` 최신으로 상향
  - 앱 재기동

2. `TOO_MANY_REQUESTS` (수집 이미 실행 중)
- 증상: refresh 호출 시 동시실행 거절
- 조치:
  - 자동수집 완료 대기 후 재시도
  - `docker compose logs app --since 5m`로 완료 로그 확인

3. 최신 회차가 기대보다 낮음
- 조치:
  - `latest` API 확인
  - DB `max(round)` 확인
  - mock 상한(`KRAFT_API_MOCK_LATEST_ROUND`) 확인

## 7) 배포 후 운영 루틴 (권장)

- 매주 일요일 21:10에 아래 3가지만 확인:
  1. `latest` API 회차/추첨일
  2. `auto-collect done trigger=sun-21` 로그
  3. DB `max(round)`

- 월 1회:
  - 로그/볼륨 정리
  - 이미지 취약점 점검
  - `.env` 비밀값 교체 주기 점검

## 8) 변경 파일 요약 (이번 배포 관련)

- `WinningNumberAutoCollectScheduler` 추가
- `FailoverLottoApiClient` 추가
- `DhLotteryApiClient` 비JSON 응답 처리 개선
- `KraftApiProperties` 확장
- `application.yml` / `application-local.yml` / `application-test.yml` 설정 확장
- `README.md` 전면 재작성

---
운영자는 이 문서를 기준으로 서버 점검/조치를 수행하면 됩니다.
