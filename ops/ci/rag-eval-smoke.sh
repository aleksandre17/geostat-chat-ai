#!/usr/bin/env bash
# RAG-L08 — golden query eval (bash wrapper for CI)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
if command -v pwsh &>/dev/null; then
  pwsh -File "$ROOT/ops/ci/rag-eval-smoke.ps1" "$@"
elif command -v powershell &>/dev/null; then
  powershell -File "$ROOT/ops/ci/rag-eval-smoke.ps1" "$@"
else
  echo "[rag-eval] ERROR: PowerShell required" >&2
  exit 1
fi
