#!/usr/bin/env bash
set -euo pipefail

CONTAINER_NAME="${CONTAINER_NAME:-kraft-lotto-app}"
HEALTH_URL="${HEALTH_URL:-http://localhost:8080/actuator/health/readiness}"
MAX_ATTEMPTS="${MAX_ATTEMPTS:-60}"
SLEEP_SECONDS="${SLEEP_SECONDS:-5}"

echo "Waiting for app readiness: $HEALTH_URL"
for i in $(seq 1 "$MAX_ATTEMPTS"); do
  STATUS=$(docker inspect --format='{{if .State.Health}}{{.State.Health.Status}}{{else}}no-healthcheck{{end}}' "$CONTAINER_NAME" 2>/dev/null || echo "missing")
  RESPONSE=$(curl -sS --connect-timeout 3 --max-time 5 -w '\n%{http_code}' "$HEALTH_URL" 2>/dev/null || true)
  HTTP_CODE=$(printf '%s' "$RESPONSE" | tail -n1)
  if ! printf '%s' "$HTTP_CODE" | grep -Eq '^[0-9]{3}$'; then
    HTTP_CODE="000"
    BODY=""
  else
    BODY=$(printf '%s' "$RESPONSE" | sed '$d' | tr -d '\n')
  fi
  echo "[$i/$MAX_ATTEMPTS] docker-health=$STATUS http=$HTTP_CODE body=$BODY"

  if [[ "$HTTP_CODE" == "200" ]] && printf '%s' "$BODY" | grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"'; then
    echo "App is ready"
    exit 0
  fi
  sleep "$SLEEP_SECONDS"
done

echo "::error::Readiness check timed out"
docker compose ps || true
docker compose logs --tail=200 app || true
docker compose logs --tail=200 mariadb || true
exit 1
