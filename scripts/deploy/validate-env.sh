#!/usr/bin/env bash
set -euo pipefail

if [[ -z "${ALL_SECRETS_JSON:-}" ]]; then
  echo "::error::ALL_SECRETS_JSON is required"
  exit 1
fi

./gradlew --no-daemon -q printRequiredEnvVars > .required-envs

while IFS= read -r name; do
  [[ -z "$name" ]] && continue
  if [[ "$name" == "KRAFT_ADMIN_API_TOKENS" || "$name" == "KRAFT_ADMIN_API_TOKEN_HASHES" ]]; then
    token_plain="$(printf '%s' "$ALL_SECRETS_JSON" | jq -r '.KRAFT_ADMIN_API_TOKENS // ""')"
    token_hashes="$(printf '%s' "$ALL_SECRETS_JSON" | jq -r '.KRAFT_ADMIN_API_TOKEN_HASHES // ""')"
    if [[ -z "$token_plain" && -z "$token_hashes" ]]; then
      echo "::error::Either KRAFT_ADMIN_API_TOKENS or KRAFT_ADMIN_API_TOKEN_HASHES is required for production deploy"
      exit 1
    fi
    continue
  fi
  value="$(printf '%s' "$ALL_SECRETS_JSON" | jq -r --arg key "$name" '.[$key] // ""')"
  if [[ -z "$value" ]]; then
    echo "::error::$name GitHub Secret is required for production deploy"
    exit 1
  fi
done < .required-envs

token_val="$(printf '%s' "$ALL_SECRETS_JSON" | jq -r '.KRAFT_ADMIN_API_TOKENS // ""')"
if [[ -n "$token_val" && ${#token_val} -lt 32 ]]; then
  echo "::error::KRAFT_ADMIN_API_TOKENS is too short (min 32 chars)"
  exit 1
fi
