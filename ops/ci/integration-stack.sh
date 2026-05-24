#!/bin/bash
# Project CI: infra + full stack integration — manifest stack.composeModules + CI health matrix
# Manifest: geostat.ops.json → ci.*, stack.composeDir, stack.infra.services
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
PKG="$ROOT/kits/geostat-kit"
export GEOSTAT_PROJECT_ROOT="$ROOT"
export GEOSTAT_KIT_ROOT="$PKG"

# shellcheck source=../../kits/geostat-kit/lib/project.sh
source "$PKG/lib/project.sh"
# shellcheck source=../../kits/geostat-kit/lib/env.sh
source "$PKG/lib/env.sh"

STACK_REL="$(geostat_read_manifest_field stack.composeDir "ops/compose/stack")"
STACK_DIR="$ROOT/$STACK_REL"
INFRA_DIR="$(geostat_infra_compose_dir)"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.yml}"
COMPOSE_OVERRIDE="${COMPOSE_OVERRIDE:-$ROOT/ops/ci/stack.integration.override.yml}"
INFRA_ENV="$ROOT/ops/config/infra/.env.dev"

infra_service_ids() {
  cd "$PKG" && PYTHONPATH="$PKG" python3 -c "
from lib.project_context import ProjectContext
services = ProjectContext.discover().manifest.get('stack', {}).get('infra', {}).get('services', [])
for sid in services:
    print(sid)
"
}

build_infra_compose_args() {
  INFRA_COMPOSE_ARGS=(-f "$INFRA_DIR/docker-compose.base.yml")
  local sid frag
  while IFS= read -r sid; do
    [[ -n "$sid" ]] || continue
    frag="$INFRA_DIR/services/${sid}.yml"
    if [[ ! -f "$frag" ]]; then
      echo "[ci] ERROR: missing infra fragment $frag" >&2
      exit 1
    fi
    INFRA_COMPOSE_ARGS+=(-f "$frag")
  done < <(infra_service_ids)
}

infra_down() {
  [[ -d "$INFRA_DIR" ]] || return 0
  build_infra_compose_args
  (
    cd "$INFRA_DIR"
    docker compose "${INFRA_COMPOSE_ARGS[@]}" --env-file "$INFRA_ENV" down -v
  ) || true
}

stack_down() {
  (
    cd "$STACK_DIR"
    docker compose "${ENV_ARGS[@]}" -f "$COMPOSE_FILE" -f "$COMPOSE_OVERRIDE" down -v
  ) || true
}

cleanup() {
  stack_down
  infra_down
}
trap cleanup EXIT

echo "[ci] stack dir=$STACK_REL compose=$COMPOSE_FILE infra=$INFRA_DIR"

bash "$PKG/ci/prepare-integration-env.sh"
bash "$ROOT/ops/ci/seed-stack-integration-env.sh"

if command -v python3 &>/dev/null; then
  python3 "$PKG/compose/build.py"
elif command -v py &>/dev/null; then
  py -3 "$PKG/compose/build.py"
else
  python "$PKG/compose/build.py"
fi

[[ -f "$INFRA_ENV" ]] || { echo "[ci] ERROR: missing $INFRA_ENV" >&2; exit 1; }

build_infra_compose_args

echo "[ci] docker compose infra up..."
(
  cd "$INFRA_DIR"
  docker compose "${INFRA_COMPOSE_ARGS[@]}" --env-file "$INFRA_ENV" up -d --wait
)

bash "$PKG/ci/wait-health.sh" "http://127.0.0.1:${QDRANT_HTTP_PORT:-6333}/healthz" "-" "${CI_HEALTH_TIMEOUT:-120}"

DEPLOY_ENV="$(geostat_secrets_root)/deploy.env"
ENV_ARGS=()
[[ -f "$DEPLOY_ENV" ]] && ENV_ARGS+=(--env-file "$DEPLOY_ENV")

echo "[ci] docker compose stack up (-f $COMPOSE_FILE + integration override) in $STACK_REL..."
(
  cd "$STACK_DIR"
  docker compose "${ENV_ARGS[@]}" -f "$COMPOSE_FILE" -f "$COMPOSE_OVERRIDE" up -d --build
)

WAIT_STACK="$(geostat_read_manifest_field ci.waitStackHealth "kits/geostat-kit/ci/wait-stack-health.sh")"
bash "$ROOT/$WAIT_STACK"

if [[ "${RUN_RAG_SMOKE:-}" == "1" ]]; then
  bash "$ROOT/ops/ci/rag-pipeline-smoke.sh"
  export RETRIEVAL_ENABLED=true
  export REQUIRE_RETRIEVAL_HITS=1
  bash "$ROOT/ops/ci/chat-rag-e2e-smoke.sh"
  if [[ "${RUN_RAG_EVAL:-}" == "1" ]]; then
    bash "$ROOT/ops/ci/rag-eval-smoke.sh" -Strict
  fi
fi

echo "[ci] Integration stack passed."
