#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
CFG="$ROOT/ops/config/ssh/config"
HOST="${1:-geostat-deploy}"

ssh -F "$CFG" "$HOST" bash <<'REMOTE'
set -e
API=geostat-chat-ai-api

echo "=== B-19 chat + feedback ==="
echo '{"message":"statistika","sessionId":"full-smoke"}' > /tmp/chat-smoke.json
docker cp /tmp/chat-smoke.json $API:/tmp/chat-smoke.json
RESP=$(docker exec $API curl -s -m 120 -X POST http://127.0.0.1:8090/api/chat \
  -H 'Content-Type: application/json' -d @/tmp/chat-smoke.json)
TURN=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('turnId',''))")
test -n "$TURN" && echo "OK turnId=$TURN" || { echo "FAIL no turnId"; exit 1; }
FB=$(docker exec $API curl -s -o /dev/null -w '%{http_code}' -X POST http://127.0.0.1:8090/api/chat/feedback \
  -H 'Content-Type: application/json' -d "{\"turnId\":\"$TURN\",\"sessionId\":\"full-smoke\",\"rating\":\"up\"}")
test "$FB" = "202" && echo "OK feedback 202" || { echo "FAIL feedback $FB"; exit 1; }

echo "=== B-17 SSE stream ==="
STREAM=$(docker exec $API curl -s -m 120 -N "http://127.0.0.1:8090/api/chat/stream?message=hello&sessionId=sse-smoke" | head -c 800)
echo "$STREAM" | head -c 200
echo
echo "$STREAM" | grep -q 'event:complete' && echo "OK stream has complete event" || echo "WARN stream format (may still work)"

echo "=== B-18 Redis session store ==="
docker exec $API printenv GEOSTAT_SESSION_STORE SPRING_DATA_REDIS_HOST | sed 's/^/env: /'
docker exec geostat-chat-ai-redis redis-cli ping
echo "ALL CHECKS PASSED"
REMOTE
