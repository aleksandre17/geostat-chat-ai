# P5-03 — chat catalog + RAG unified items smoke (Hybrid ④ or stack)
# Prereqs: retrieval indexed corpus; chat-api with RETRIEVAL_ENABLED=true on 8090
param(
    [string]$ChatUrl = "http://127.0.0.1:8090",
    [string]$RetrievalUrl = "http://127.0.0.1:8092",
    [string]$Message = "statistika",
    [switch]$RequireSourceLink
)

$ErrorActionPreference = "Stop"

function Test-Port($port) {
    (Test-NetConnection -ComputerName 127.0.0.1 -Port $port -WarningAction SilentlyContinue).TcpTestSucceeded
}

Write-Host "== Chat catalog+RAG smoke (P5-03) ==" -ForegroundColor Cyan

foreach ($p in @(8090, 8092)) {
    if (-not (Test-Port $p)) { throw "Port $p not reachable (start be + ret?)" }
}

$searchBody = '{"text":"statistika","locale":"ka","maxChunks":3,"corpusName":"geostat-portal"}'
$chunks = Invoke-RestMethod -Method Post -Uri "$RetrievalUrl/api/v1/retrieval/search" `
    -ContentType "application/json" -Body $searchBody
if ($chunks.Count -lt 1) {
    throw "Retrieval returned no chunks - run rag-pipeline-smoke or hybrid E2E first"
}
Write-Host "Retrieval OK: $($chunks.Count) chunks"

$chatBody = (@{ message = $Message; sessionId = "p503-smoke" } | ConvertTo-Json -Compress)
$chat = Invoke-RestMethod -Method Post -Uri "$ChatUrl/api/chat" `
    -ContentType "application/json" -Body $chatBody -TimeoutSec 90

if (-not $chat.intro) { throw "Chat returned empty intro" }
if (-not $chat.items -or $chat.items.Count -lt 1) { throw "Chat returned no items" }

$sourceCount = @($chat.items | Where-Object { $_.link.sourceType -eq "rag" -or $_.link.type -eq "source" }).Count
$catalogCount = $chat.items.Count - $sourceCount
Write-Host "Chat OK: $($chat.items.Count) items (catalog=$catalogCount source=$sourceCount)"
Write-Host "  intro: $($chat.intro.Substring(0, [Math]::Min(100, $chat.intro.Length)))..."

if ($RequireSourceLink -and $sourceCount -lt 1) {
    throw "Expected at least one type=source item (CatalogRagLinkMerger / RAG)"
}

Write-Host "Chat catalog+RAG smoke PASSED" -ForegroundColor Green
