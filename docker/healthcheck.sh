#!/bin/sh
set -eu

URL="${KRAFT_HEALTHCHECK_URL:-http://localhost:8080/actuator/health/liveness}"
TIMEOUT="${KRAFT_HEALTHCHECK_TIMEOUT_SECONDS:-2}"
BODY_FILE="/tmp/kraft-healthcheck-body.$$"

cleanup() {
  rm -f "$BODY_FILE"
}
trap cleanup EXIT

HTTP_CODE="$(curl -sS --max-time "$TIMEOUT" -o "$BODY_FILE" -w '%{http_code}' "$URL")" || {
  RC=$?
  echo "healthcheck request failed: url=$URL rc=$RC"
  [ -s "$BODY_FILE" ] && cat "$BODY_FILE"
  exit "$RC"
}

if [ "$HTTP_CODE" != "200" ]; then
  echo "healthcheck returned HTTP $HTTP_CODE: url=$URL"
  [ -s "$BODY_FILE" ] && cat "$BODY_FILE"
  exit 1
fi

if ! grep -q '"status":"UP"' "$BODY_FILE"; then
  echo "healthcheck response is not UP: url=$URL"
  cat "$BODY_FILE"
  exit 1
fi

exit 0
