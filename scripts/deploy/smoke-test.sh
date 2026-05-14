#!/usr/bin/env bash
set -euo pipefail

if [[ -z "${KRAFT_ADMIN_API_TOKENS:-}" ]]; then
  echo "::error::KRAFT_ADMIN_API_TOKENS is required"
  exit 1
fi

BODY=$(curl -fsS http://localhost:8080/actuator/health/readiness)
printf '%s\n' "$BODY"
printf '%s' "$BODY" | grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"'

HTTP=$(curl -s -o /dev/null -w '%{http_code}' \
  -X POST http://localhost:8080/admin/lotto/draws/collect-next \
  -H "X-Kraft-Admin-Token: ${KRAFT_ADMIN_API_TOKENS%%,*}")
if [[ "$HTTP" != "200" && "$HTTP" != "409" ]]; then
  echo "::error::Admin token smoke test failed: HTTP $HTTP"
  exit 1
fi
echo "Smoke test passed"

