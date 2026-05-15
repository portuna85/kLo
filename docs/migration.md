# Migration Guide

## Baseline

- Flyway 사용
- `baseline-on-migrate=false`
- `baseline-version=1`

## Add Migration

1. `src/main/resources/db/migration`에 `V<n>__description.sql` 추가
2. 롤백 전략(수동/보정 스크립트) PR에 명시
3. 대용량 변경은 배치/인덱스 영향 검토

## Deployment Order

1. 애플리케이션 배포 전 DB 백업
2. 애플리케이션 기동 시 Flyway 자동 적용
3. readiness 통과 확인

## Safety

- prod에서 `clean-disabled=true` 유지
- out-of-order는 기본 비활성 유지
