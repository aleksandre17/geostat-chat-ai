#!/bin/bash
# Patch seeded ops/config for full-stack Docker CI (infra + stack on geostat-chat-ai-net).
# Called after kits/geostat-kit/ci/prepare-integration-env.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
CI_PASSWORD="${GEOSTAT_CI_INFRA_PASSWORD:-geostat-dev-change-me}"
export GEOSTAT_CI_INFRA_PASSWORD="$CI_PASSWORD"

patch_env() {
  local file="$1"
  shift
  [[ -f "$file" ]] || return 0
  while [[ $# -ge 2 ]]; do
    local key="$1"
    local value="$2"
    shift 2
    if grep -q "^${key}=" "$file"; then
      sed -i.bak "s|^${key}=.*|${key}=${value}|" "$file"
    else
      printf '\n%s=%s\n' "$key" "$value" >>"$file"
    fi
  done
  rm -f "${file}.bak"
}

INFRA_ENV="$ROOT/ops/config/infra/.env.dev"
INGESTION_ENV="$ROOT/ops/config/ingestion/.env.dev"
RETRIEVAL_ENV="$ROOT/ops/config/retrieval/.env.dev"

patch_env "$INFRA_ENV" \
  POSTGRES_PASSWORD "$CI_PASSWORD" \
  RABBITMQ_PASSWORD "$CI_PASSWORD"

patch_env "$INGESTION_ENV" \
  SPRING_PROFILES_ACTIVE "dev,docker" \
  INGESTION_EVENTS_ENABLED "true" \
  POSTGRES_PASSWORD "$CI_PASSWORD" \
  RABBITMQ_PASSWORD "$CI_PASSWORD" \
  QDRANT_URL "http://qdrant:6333"

patch_env "$RETRIEVAL_ENV" \
  QDRANT_URL "http://qdrant:6333"

echo "[ci] Stack integration env patched (docker network, events=true)"
