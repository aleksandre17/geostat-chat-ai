#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
CFG="$ROOT/ops/config/ssh/config"
HOST="${1:-geostat-deploy}"

ssh -F "$CFG" "$HOST" bash <<'REMOTE'
set -e
echo '{"message":"statistika","sessionId":"b19-smoke"}' > /tmp/chat-smoke.json
docker cp /tmp/chat-smoke.json geostat-chat-ai-api:/tmp/chat-smoke.json
RESP=$(docker exec geostat-chat-ai-api curl -s -m 120 -X POST http://127.0.0.1:8090/api/chat \
  -H 'Content-Type: application/json' -d @/tmp/chat-smoke.json)
echo "$RESP" | head -c 500
echo
TURN=$(echo "$RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('turnId',''))")
GROUNDED=$(echo "$RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('grounded', False))")
SOURCES=$(echo "$RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('sourceCount', 0))")
test -n "$TURN" || { echo "FAIL: no turnId"; exit 1; }
echo "OK turnId=$TURN grounded=$GROUNDED sourceCount=$SOURCES"
FB=$(docker exec geostat-chat-ai-api curl -s -o /dev/null -w '%{http_code}' -X POST http://127.0.0.1:8090/api/chat/feedback \
  -H 'Content-Type: application/json' \
  -d "{\"turnId\":\"$TURN\",\"sessionId\":\"b19-smoke\",\"rating\":\"up\"}")
test "$FB" = "202" || { echo "FAIL: feedback HTTP $FB"; exit 1; }
echo "OK feedback HTTP 202"
REMOTE
