# Configuration Guide

## Source of Truth

- 애플리케이션 기본값: `application.yml`
- 환경별 오버라이드: `application-local.yml`, `application-prod.yml`
- 실행 시 주입값: `.env` 또는 CI/CD secret

## Required Variables

- DB: `KRAFT_DB_URL`, `KRAFT_DB_USER`, `KRAFT_DB_PASSWORD`
- Admin: `KRAFT_ADMIN_API_TOKENS` 또는 `KRAFT_ADMIN_API_TOKEN_HASHES`
- Profile: `SPRING_PROFILES_ACTIVE`

## Logging Controls

- `KRAFT_LOG_ROOT_LEVEL_LOCAL` (기본 `INFO`)
- `KRAFT_LOG_ROOT_LEVEL_PROD` (기본 `INFO`)
- `KRAFT_LOG_PATH` (기본 `./logs`)

## Recommend/Rate Limit

- `KRAFT_RECOMMEND_MAX_ATTEMPTS`
- `KRAFT_RECOMMEND_INITIAL_PICK_MAX_ATTEMPTS`
- `KRAFT_RECOMMEND_FIXUP_MAX_ATTEMPTS`
- `KRAFT_RECOMMEND_RATE_LIMIT_MAX_REQUESTS`
- `KRAFT_RECOMMEND_RATE_LIMIT_WINDOW_SECONDS`
- Redis 사용 시 `KRAFT_RECOMMEND_RATE_LIMIT_REDIS_ENABLED=true`

## Legacy API Header Policy (KST 운영 기준)

- `KRAFT_API_LEGACY_DEPRECATION_VALUE`
- `KRAFT_API_LEGACY_SUNSET_VALUE`
- `KRAFT_API_LEGACY_EMIT_AFTER_SUNSET`
