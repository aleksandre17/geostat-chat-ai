#!/usr/bin/env bash
# B-07 — full RAG question → answer + B-19 telemetry/feedback (bash/CI)
set -euo pipefail

CHAT_URL="${CHAT_URL:-http://127.0.0.1:8090}"
RETRIEVAL_URL="${RETRIEVAL_URL:-http://127.0.0.1:8092}"
MESSAGE="${SMOKE_CHAT_MESSAGE:-statistika}"
REQUIRE_HITS="${REQUIRE_RETRIEVAL_HITS:-0}"
CORPUS="${SMOKE_CORPUS:-geostat-portal}"

echo "[b07-smoke] chat=$CHAT_URL retrieval=$RETRIEVAL_URL"

SEARCH_PAYLOAD=$(SMOKE_CORPUS="$CORPUS" python3 - <<'PY'
import json, os
print(json.dumps({
    "text": os.environ.get("SMOKE_SEARCH_TEXT", "statistika"),
    "locale": "ka",
    "maxChunks": 3,
    "corpusName": os.environ["SMOKE_CORPUS"],
}))
PY
)

CHUNKS=$(curl -fsS -X POST "$RETRIEVAL_URL/api/v1/retrieval/search" \
  -H "Content-Type: application/json" -d "$SEARCH_PAYLOAD")
HIT_COUNT=$(python3 -c "import json,sys; print(len(json.loads(sys.argv[1])))" "$CHUNKS")
echo "[b07-smoke] retrieval chunks=$HIT_COUNT"
if [[ "$HIT_COUNT" -lt 1 ]]; then
  echo "[b07-smoke] ERROR: no retrieval chunks (run rag-pipeline-smoke first)" >&2
  exit 1
fi

SESSION_ID="b07-smoke-$(date +%s)"
CHAT_PAYLOAD=$(python3 - <<PY
import json
print(json.dumps({"message": "$MESSAGE", "sessionId": "$SESSION_ID"}))
PY
)

CHAT_RESP=$(curl -fsS -X POST "$CHAT_URL/api/chat" \
  -H "Content-Type: application/json" -d "$CHAT_PAYLOAD")
echo "$CHAT_RESP" > /tmp/b07-chat.json

python3 - <<PY
import json, os, sys
chat = json.load(open("/tmp/b07-chat.json"))
if not chat.get("turnId"):
    print("[b07-smoke] ERROR: missing turnId", file=sys.stderr); sys.exit(1)
if chat.get("responseType") is None:
    print("[b07-smoke] ERROR: missing responseType", file=sys.stderr); sys.exit(1)
grounded = chat.get("grounded")
source_count = chat.get("sourceCount", 0)
if os.environ.get("REQUIRE_RETRIEVAL_HITS") == "1" and not grounded:
    print("[b07-smoke] ERROR: expected grounded=true when RAG enabled", file=sys.stderr); sys.exit(1)
print(f"[b07-smoke] turnId={chat['turnId']} responseType={chat.get('responseType')} grounded={grounded} sourceCount={source_count} items={len(chat.get('items') or [])}")
PY

FB_PAYLOAD=$(python3 - <<'PY'
import json
chat = json.load(open("/tmp/b07-chat.json"))
print(json.dumps({
    "turnId": chat["turnId"],
    "sessionId": chat.get("sessionId", ""),
    "rating": "up",
    "comment": "b07-smoke",
}))
PY
)

HTTP=$(curl -fsS -o /dev/null -w '%{http_code}' -X POST "$CHAT_URL/api/chat/feedback" \
  -H "Content-Type: application/json" -d "$FB_PAYLOAD")
if [[ "$HTTP" != "202" ]]; then
  echo "[b07-smoke] ERROR: feedback HTTP $HTTP (expected 202)" >&2
  exit 1
fi
echo "[b07-smoke] feedback HTTP 202"
echo "[b07-smoke] PASSED"
