# B-07 — full RAG question → answer + B-19 telemetry/feedback smoke
param(
    [string]$ChatUrl = "http://127.0.0.1:8090",
    [string]$RetrievalUrl = "http://127.0.0.1:8092",
    [string]$Message = "statistika",
    [switch]$RequireRetrievalHits
)

$ErrorActionPreference = "Stop"

function Test-Port($port) {
    (Test-NetConnection -ComputerName 127.0.0.1 -Port $port -WarningAction SilentlyContinue).TcpTestSucceeded
}

Write-Host "== Chat RAG E2E smoke (B-07 / B-19) ==" -ForegroundColor Cyan

foreach ($p in @(8090, 8092)) {
    if (-not (Test-Port $p)) { throw "Port $p not reachable (start be + ret?)" }
}

$searchBody = '{"text":"statistika","locale":"ka","maxChunks":3,"corpusName":"geostat-portal"}'
$chunks = Invoke-RestMethod -Method Post -Uri "$RetrievalUrl/api/v1/retrieval/search" `
    -ContentType "application/json" -Body $searchBody
if ($chunks.Count -lt 1) {
    throw "Retrieval returned no chunks - run rag-pipeline-smoke first"
}
Write-Host "Retrieval OK: $($chunks.Count) chunks"

$sessionId = "b07-smoke-$(Get-Date -Format 'yyyyMMddHHmmss')"
$chatBody = (@{ message = $Message; sessionId = $sessionId } | ConvertTo-Json -Compress)
$chat = Invoke-RestMethod -Method Post -Uri "$ChatUrl/api/chat" `
    -ContentType "application/json" -Body $chatBody -TimeoutSec 90

if (-not $chat.intro) { throw "Chat returned empty intro" }
if (-not $chat.turnId) { throw "Chat missing turnId (B-19 telemetry)" }
if (-not $chat.responseType) { throw "Chat missing responseType (R-04)" }

Write-Host "Chat OK: turnId=$($chat.turnId) responseType=$($chat.responseType) grounded=$($chat.grounded) sourceCount=$($chat.sourceCount) items=$($chat.items.Count)"

if ($RequireRetrievalHits -and -not $chat.grounded) {
    throw "Expected grounded=true when RETRIEVAL_ENABLED=true"
}

$feedbackBody = (@{
    turnId    = $chat.turnId
    sessionId = $chat.sessionId
    rating    = "up"
    comment   = "b07-smoke"
} | ConvertTo-Json -Compress)

$fb = Invoke-WebRequest -Method Post -Uri "$ChatUrl/api/chat/feedback" `
    -ContentType "application/json" -Body $feedbackBody -TimeoutSec 30
if ($fb.StatusCode -ne 202) {
    throw "Feedback expected HTTP 202, got $($fb.StatusCode)"
}
Write-Host "Feedback OK: HTTP $($fb.StatusCode)"

Write-Host "Chat RAG E2E smoke PASSED" -ForegroundColor Green
