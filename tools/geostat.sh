#!/bin/bash
# Legacy entry — forwards to ops/cli
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
exec bash "$ROOT/ops/cli/geostat.sh" "$@"
