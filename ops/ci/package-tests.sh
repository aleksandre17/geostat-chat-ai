#!/bin/bash
# Consumer CI: geostat-kit package tests against this repo's manifest.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
PKG="$ROOT/kits/geostat-kit"
export GEOSTAT_PROJECT_ROOT="$ROOT"
export GEOSTAT_KIT_ROOT="$PKG"
export PYTHONPATH="$PKG"

bash "$PKG/ci/prepare-integration-env.sh"
python3 "$PKG/compose/build.py"
python3 "$PKG/lib/validate_manifest.py"

python3 -m pytest "$PKG/tests" --tb=short -q \
  --deselect kits/geostat-kit/tests/test_compose_identity.py::test_project_context_compose_names
