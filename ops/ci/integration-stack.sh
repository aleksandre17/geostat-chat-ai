#!/bin/bash
# Project CI: full stack integration — manifest stack.composeModules + CI health matrix
# Manifest: geostat.ops.json → ci.*, stack.composeDir, stack.composeModules
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
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.yml}"

echo "[ci] stack dir=$STACK_REL compose=$COMPOSE_FILE"

bash "$PKG/ci/prepare-integration-env.sh"

if command -v python3 &>/dev/null; then
  python3 "$PKG/compose/build.py"
elif command -v py &>/dev/null; then
  py -3 "$PKG/compose/build.py"
else
  python "$PKG/compose/build.py"
fi

DEPLOY_ENV="$(geostat_secrets_root)/deploy.env"
ENV_ARGS=()
[[ -f "$DEPLOY_ENV" ]] && ENV_ARGS+=(--env-file "$DEPLOY_ENV")

echo "[ci] docker compose up (-f $COMPOSE_FILE) in $STACK_REL..."
(
  cd "$STACK_DIR"
  docker compose "${ENV_ARGS[@]}" -f "$COMPOSE_FILE" up -d --build
)

WAIT_STACK="$(geostat_read_manifest_field ci.waitStackHealth "kits/geostat-kit/ci/wait-stack-health.sh")"
bash "$ROOT/$WAIT_STACK"

echo "[ci] docker compose down..."
(
  cd "$STACK_DIR"
  docker compose "${ENV_ARGS[@]}" -f "$COMPOSE_FILE" down -v
)
echo "[ci] Integration stack passed."
