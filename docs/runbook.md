# Runbook

## Deploy

```bash
./scripts/deploy/build-and-up.sh
./scripts/deploy/wait-readiness.sh
./scripts/deploy/smoke-test.sh
```

## Rollback

```bash
./scripts/deploy/rollback.sh
```

## Health Checks

- readiness: `/actuator/health/readiness`
- liveness: `/actuator/health/liveness`

## Common Incidents

- DB 연결 실패: `KRAFT_DB_URL`, DB 컨테이너 상태 확인
- Admin API 401: `KRAFT_ADMIN_API_TOKENS`/`KRAFT_ADMIN_API_TOKEN_HASHES` 확인
- Rate limit 429: 클라이언트 IP/윈도우 정책 확인

## Virtual Thread / JDBC Pinning Check (KST 기준 운영 점검)

- 주기: 배포 직후, 장애 대응 시
- 확인 지표:
  - `/actuator/metrics/jvm.threads.live`
  - `/actuator/metrics/http.server.requests`
  - DB pool 사용률(Hikari)
- 징후:
  - 요청 지연 증가 + DB 커넥션 장시간 점유
  - 스레드 수 급증 대비 처리량 정체
- 조치:
  - 외부 API timeout/retry 값 점검
  - DB 인덱스/슬로우 쿼리 확인
  - 필요 시 rate-limit/스케줄러 강도 완화
