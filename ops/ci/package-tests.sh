#!/bin/bash
# Consumer CI: geostat-kit package tests against this repo's manifest.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
PKG="$ROOT/kits/geostat-kit"
export GEOSTAT_PROJECT_ROOT="$ROOT"
export GEOSTAT_KIT_ROOT="$PKG"
export PYTHONPATH="$PKG"
cd "$ROOT"

bash "$PKG/ci/prepare-integration-env.sh"
python3 "$PKG/compose/build.py"
python3 "$PKG/lib/validate_manifest.py"
python3 -m pytest kits/geostat-kit/tests --tb=short -q
