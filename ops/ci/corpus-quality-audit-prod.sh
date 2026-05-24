#!/usr/bin/env bash
# OPS-02 prod — corpus quality audit via SSH (ingestion container on deploy host).
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
echo "[corpus-audit-prod] container=$ING corpus=$CORPUS"
test "$(docker inspect -f '{{.State.Running}}' "$ING" 2>/dev/null || echo false)" = "true" \
  || { echo "ERROR: ingestion container not running: $ING" >&2; exit 1; }
REPORT=$(docker exec "$ING" curl -fsS "http://127.0.0.1:8093/api/v1/ingestion/corpora/${CORPUS}/quality")
python3 - "$REPORT" <<'PY'
import json, os, sys
report = json.loads(sys.argv[1])
print("[corpus-audit-prod] documents:", report["documents"])
print("[corpus-audit-prod] pipeline:", report["pipeline"])
recs = report.get("recommendations") or []
print("[corpus-audit-prod] recommendations:", ", ".join(recs))
for url in (report.get("sampleEmptyUrls") or [])[:5]:
    print("  empty:", url)
actionable = {"CONSIDER_PLAYWRIGHT_P3_03B", "CONSIDER_RECRAWL_OPS02", "CONSIDER_REINDEX_OPS02"}
if "NO_DATA" in recs:
    print("[corpus-audit-prod] WARN: no parsed documents — run full crawl on prod")
    sys.exit(2 if os.environ.get("AUDIT_STRICT") == "1" else 0)
if actionable.intersection(recs):
    print("[corpus-audit-prod] ACTION required — see recommendations")
    sys.exit(2 if os.environ.get("AUDIT_STRICT") == "1" else 0)
print("[corpus-audit-prod] OK")
PY
REMOTE
