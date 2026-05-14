#!/usr/bin/env bash
set -euo pipefail

if [[ -z "${ALL_SECRETS_JSON:-}" ]]; then
  echo "::error::ALL_SECRETS_JSON is required"
  exit 1
fi

if [[ -z "${KRAFT_DB_NAME:-}" ]]; then
  echo "::error::KRAFT_DB_NAME is required"
  exit 1
fi

umask 077
{
  printf 'SPRING_PROFILES_ACTIVE=prod\n'
  printf 'KRAFT_APP_IMAGE_REF=%s\n' "${KRAFT_APP_IMAGE_REF:-kraft-lotto-app}"
  printf 'KRAFT_APP_IMAGE_TAG=%s\n' "${GITHUB_SHA:-local}"
  printf 'KRAFT_APP_PORT=8080\n'
  printf 'KRAFT_DB_URL=jdbc:mariadb://mariadb:3306/%s?rewriteBatchedStatements=true&useUnicode=true&characterEncoding=utf8mb4&serverTimezone=Asia/Seoul&connectTimeout=3000\n' "${KRAFT_DB_NAME}"
  printf 'KRAFT_DB_LOCAL_HOST=localhost\n'
  printf 'KRAFT_IN_CONTAINER=true\n'
  printf 'KRAFT_API_CLIENT=real\n'
  printf 'KRAFT_API_URL=https://www.dhlottery.co.kr/common.do\n'
  printf 'KRAFT_API_CONNECT_TIMEOUT_MS=2000\n'
  printf 'KRAFT_API_READ_TIMEOUT_MS=3000\n'
  printf 'KRAFT_API_MAX_RETRIES=2\n'
  printf 'KRAFT_API_RETRY_BACKOFF_MS=200\n'
  printf 'KRAFT_ADMIN_TOKEN_HEADER=X-Kraft-Admin-Token\n'
  printf 'KRAFT_RECOMMEND_MAX_ATTEMPTS=5000\n'
  printf 'KRAFT_RECOMMEND_RATE_LIMIT_MAX_REQUESTS=30\n'
  printf 'KRAFT_RECOMMEND_RATE_LIMIT_WINDOW_SECONDS=60\n'
  printf 'KRAFT_RECOMMEND_RATE_LIMIT_REDIS_ENABLED=false\n'
  printf 'KRAFT_RECOMMEND_RATE_LIMIT_REDIS_KEY_PREFIX=kraft:rate-limit\n'
  printf 'KRAFT_TRACING_ENABLED=false\n'
  printf 'KRAFT_TRACING_SAMPLING_PROBABILITY=1.0\n'
  printf 'KRAFT_OTLP_TRACING_ENDPOINT=http://localhost:4318/v1/traces\n'
  printf 'KRAFT_COLLECT_LOG_RETENTION_ENABLED=true\n'
  printf 'KRAFT_COLLECT_LOG_RETENTION_DAYS=90\n'
  printf 'KRAFT_COLLECT_LOG_RETENTION_CRON=0 30 3 * * *\n'
  printf 'KRAFT_LOTTO_SCHEDULER_LOCK_AT_MOST_FOR=PT10M\n'
  printf 'KRAFT_LOTTO_SCHEDULER_LOCK_AT_LEAST_FOR=PT10S\n'
  printf 'KRAFT_LOG_PATH=/app/logs\n'
  printf 'KRAFT_HEALTHCHECK_URL=http://localhost:8080/actuator/health/readiness\n'
  printf 'KRAFT_HEALTHCHECK_TIMEOUT_SECONDS=3\n'
} > .env

while IFS= read -r name; do
  [[ -z "$name" ]] && continue
  value="$(printf '%s' "$ALL_SECRETS_JSON" | jq -r --arg key "$name" '.[$key] // ""')"
  [[ -z "$value" ]] && continue
  printf '%s=%s\n' "$name" "$value" >> .env
done < .required-envs

docker compose --env-file .env config -q
