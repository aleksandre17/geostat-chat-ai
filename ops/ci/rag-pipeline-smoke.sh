#!/bin/bash
# P5-02 — crawl → async/sync index → retrieval search (hybrid or stack must be up)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
PKG="$ROOT/kits/geostat-kit"
export GEOSTAT_PROJECT_ROOT="$ROOT"
export GEOSTAT_KIT_ROOT="$PKG"

# shellcheck source=../../kits/geostat-kit/lib/project.sh
source "$PKG/lib/project.sh"

WAIT="$PKG/ci/wait-health.sh"
INGESTION_PORT="${INGESTION_PORT:-8093}"
RETRIEVAL_PORT="${RETRIEVAL_PORT:-8092}"
INGESTION_URL="${INGESTION_URL:-http://127.0.0.1:${INGESTION_PORT}}"
RETRIEVAL_URL="${RETRIEVAL_URL:-http://127.0.0.1:${RETRIEVAL_PORT}}"
CORPUS_NAME="${SMOKE_CORPUS:-geostat-portal}"
SEED_URL="${SMOKE_SEED_URL:-https://www.geostat.ge/ka}"
SEARCH_TEXT="${SMOKE_SEARCH_TEXT:-საქსტატი}"
JOB_TIMEOUT="${SMOKE_JOB_TIMEOUT:-600}"
INDEX_WAIT="${SMOKE_INDEX_WAIT:-120}"

echo "[rag-smoke] ingestion=$INGESTION_URL retrieval=$RETRIEVAL_URL corpus=$CORPUS_NAME"

bash "$WAIT" "$INGESTION_URL/actuator/health" "UP" 180
bash "$WAIT" "$RETRIEVAL_URL/health" "UP" 120

JOB_PAYLOAD=$(SMOKE_SEED_URL="$SEED_URL" SMOKE_CORPUS="$CORPUS_NAME" python3 - <<'PY'
import json, os
print(json.dumps({
    "corpusName": os.environ["SMOKE_CORPUS"],
    "seedUrl": os.environ.get("SMOKE_SEED_URL") or None,
    "fullRecrawl": False,
}))
PY
)

echo "[rag-smoke] POST /api/v1/ingestion/jobs"
JOB_RESP=$(curl -fsS -X POST "$INGESTION_URL/api/v1/ingestion/jobs" \
  -H "Content-Type: application/json" \
  -d "$JOB_PAYLOAD")

JOB_ID=$(python3 -c "import json,sys; print(json.loads(sys.argv[1])['jobId'])" "$JOB_RESP")
echo "[rag-smoke] jobId=$JOB_ID"

deadline=$((SECONDS + JOB_TIMEOUT))
state=""
while (( SECONDS < deadline )); do
  STATUS_RESP=$(curl -fsS "$INGESTION_URL/api/v1/ingestion/jobs/$JOB_ID")
  state=$(python3 -c "import json,sys; print(json.loads(sys.argv[1])['state'])" "$STATUS_RESP")
  message=$(python3 -c "import json,sys; print(json.loads(sys.argv[1]).get('message',''))" "$STATUS_RESP")
  echo "[rag-smoke] job state=$state ($message)"
  case "$state" in
    completed|failed|cancelled) break ;;
  esac
  sleep 5
done

if [[ "$state" != "completed" ]]; then
  echo "[rag-smoke] ERROR: crawl job did not complete (state=$state)" >&2
  exit 1
fi

SEARCH_PAYLOAD=$(SMOKE_SEARCH="$SEARCH_TEXT" SMOKE_CORPUS="$CORPUS_NAME" python3 - <<'PY'
import json, os
print(json.dumps({
    "text": os.environ["SMOKE_SEARCH"],
    "locale": "ka",
    "maxChunks": 5,
    "corpusName": os.environ["SMOKE_CORPUS"],
}))
PY
)

echo "[rag-smoke] waiting for indexed chunks (max ${INDEX_WAIT}s)..."
index_deadline=$((SECONDS + INDEX_WAIT))
hits=0
while (( SECONDS < index_deadline )); do
  SEARCH_RESP=$(curl -fsS -X POST "$RETRIEVAL_URL/api/v1/retrieval/search" \
    -H "Content-Type: application/json" \
    -d "$SEARCH_PAYLOAD")
  hits=$(python3 -c "import json,sys; print(len(json.loads(sys.argv[1])))" "$SEARCH_RESP")
  echo "[rag-smoke] retrieval hits=$hits"
  if [[ "$hits" -gt 0 ]]; then
    python3 -c "import json,sys; data=json.loads(sys.argv[1]); print('[rag-smoke] top:', data[0].get('sourceUrl',''), '|', (data[0].get('text','')[:80]+'...'))" "$SEARCH_RESP"
    echo "[rag-smoke] RAG pipeline smoke passed."
    exit 0
  fi
  sleep 3
done

echo "[rag-smoke] ERROR: no retrieval hits after job completed (check INGESTION_EVENTS_ENABLED, Qdrant, embedding)" >&2
exit 1
