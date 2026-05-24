#!/bin/bash
# OPS-02 / P3-03b — corpus quality metrics beyond rag-pipeline-smoke (ADR-010).
# Requires ingestion-service (db profile) + Postgres with crawled corpus.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
PKG="$ROOT/kits/geostat-kit"
export GEOSTAT_PROJECT_ROOT="$ROOT"
export GEOSTAT_KIT_ROOT="$PKG"

# shellcheck source=../../kits/geostat-kit/lib/project.sh
source "$PKG/lib/project.sh"

WAIT="$PKG/ci/wait-health.sh"
INGESTION_PORT="${INGESTION_PORT:-8093}"
INGESTION_URL="${INGESTION_URL:-http://127.0.0.1:${INGESTION_PORT}}"
CORPUS_NAME="${AUDIT_CORPUS:-${SMOKE_CORPUS:-geostat-portal}}"
STRICT="${AUDIT_STRICT:-0}"
export AUDIT_STRICT="$STRICT"

echo "[corpus-audit] ingestion=$INGESTION_URL corpus=$CORPUS_NAME strict=$STRICT"

bash "$WAIT" "$INGESTION_URL/actuator/health" "UP" 120

REPORT=$(curl -fsS "$INGESTION_URL/api/v1/ingestion/corpora/${CORPUS_NAME}/quality")

python3 - "$REPORT" <<'PY'
import json, sys

report = json.loads(sys.argv[1])
print("[corpus-audit] corpus:", report["corpusName"])
print("[corpus-audit] documents:", report["documents"])
print("[corpus-audit] pipeline:", report["pipeline"])
recs = report.get("recommendations") or []
print("[corpus-audit] recommendations:", ", ".join(recs))
samples = report.get("sampleEmptyUrls") or []
if samples:
    print("[corpus-audit] sample empty URLs:")
    for url in samples[:5]:
        print("  -", url)
latest = report.get("latestCrawlRun")
if latest:
    print("[corpus-audit] latest crawl:", latest.get("status"), latest.get("stats"))

actionable = {
    "CONSIDER_PLAYWRIGHT_P3_03B",
    "CONSIDER_RECRAWL_OPS02",
    "CONSIDER_REINDEX_OPS02",
}
if "NO_DATA" in recs:
    print("[corpus-audit] WARN: no parsed documents — run rag-pipeline-smoke or full crawl first")
    sys.exit(2 if __import__("os").environ.get("AUDIT_STRICT") == "1" else 0)
if actionable.intersection(recs):
    print("[corpus-audit] ACTION: review recommendations above (P3-03b Playwright or OPS-02 reindex)")
    sys.exit(2 if __import__("os").environ.get("AUDIT_STRICT") == "1" else 0)
print("[corpus-audit] corpus quality OK")
PY

exit $?
