#!/usr/bin/env bash
set -euo pipefail

mkdir -p deploy-state

image_ref="${KRAFT_APP_IMAGE_REF:-kraft-lotto-app}"
image_tag="${KRAFT_APP_IMAGE_TAG:-local}"
deployed_ref="${image_ref}:${image_tag}"

if [[ -f deploy-state/current.env ]]; then
  cp deploy-state/current.env deploy-state/previous.env
fi

docker compose down --remove-orphans || true
docker compose ps || true

current_image="$(docker inspect --format='{{.Config.Image}}' kraft-lotto-app 2>/dev/null || true)"
if [[ -n "$current_image" ]] && docker image inspect "$current_image" >/dev/null 2>&1; then
  docker tag "$current_image" "${image_ref}:previous" || true
  current_digest="$(docker inspect --format='{{index .RepoDigests 0}}' "$current_image" 2>/dev/null || true)"
  {
    echo "PREVIOUS_IMAGE=$current_image"
    echo "PREVIOUS_DIGEST=$current_digest"
  } > deploy-state/previous.env
fi

export GRADLE_BUILD_ARGS="-PuseExternalSnippets=true -x test -x integrationTest"
docker compose up -d --build --remove-orphans
docker compose ps

deployed_image="$(docker inspect --format='{{.Config.Image}}' kraft-lotto-app 2>/dev/null || true)"
if [[ -n "$deployed_image" ]]; then
  docker tag "$deployed_image" "${image_ref}:latest" || true
  docker tag "$deployed_image" "${deployed_ref}" || true
  echo "Deployed image: $deployed_image"
  deployed_digest="$(docker inspect --format='{{index .RepoDigests 0}}' "$deployed_image" 2>/dev/null || true)"
  {
    echo "CURRENT_IMAGE=$deployed_image"
    echo "CURRENT_IMAGE_REF=${image_ref}"
    echo "CURRENT_IMAGE_TAG=${image_tag}"
    echo "CURRENT_IMAGE_LATEST=${image_ref}:latest"
    echo "CURRENT_IMAGE_VERSIONED=${deployed_ref}"
    echo "CURRENT_DIGEST=$deployed_digest"
  } > deploy-state/current.env
fi
