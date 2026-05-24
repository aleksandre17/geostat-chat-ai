#!/usr/bin/env bash
# OPS-02 — queue vector reindex for all parsed documents in a corpus (prod via SSH).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
CFG="$ROOT/ops/config/ssh/config"
HOST="${1:-geostat-deploy}"
CORPUS="${AUDIT_CORPUS:-geostat-portal}"
ING="${INGESTION_CONTAINER:-geostat-chat-ai-ingestion}"

ssh -F "$CFG" "$HOST" bash -s "$CORPUS" "$ING" <<'REMOTE'
set -euo pipefail
CORPUS="$1"
ING="$2"
echo "[corpus-reindex-prod] container=$ING corpus=$CORPUS"
docker exec "$ING" curl -fsS -X POST "http://127.0.0.1:8093/api/v1/ingestion/corpora/${CORPUS}/reindex"
echo
REMOTE
