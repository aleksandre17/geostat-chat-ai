# Hybrid E2E smoke — tunnel + ingestion + Qdrant + retrieval (+ optional chat-api)
# Prereqs: geostat infra tunnel; services on 8093/8092/8090
param(
    [int]$MaxPages = 3,
    [switch]$SkipChat
)

$ErrorActionPreference = "Stop"
$baseIngestion = "http://127.0.0.1:8093"
$baseRetrieval = "http://127.0.0.1:8092"
$baseChat = "http://127.0.0.1:8090"

function Test-Port($port) {
    (Test-NetConnection -ComputerName 127.0.0.1 -Port $port -WarningAction SilentlyContinue).TcpTestSucceeded
}

Write-Host "== Hybrid E2E smoke ==" -ForegroundColor Cyan
foreach ($p in @(5432, 6333, 8093, 8092)) {
    if (-not (Test-Port $p)) { throw "Port $p not reachable (tunnel/services?)" }
}
Write-Host "Infra + ingestion + retrieval ports OK"

if ($MaxPages -gt 0) {
    python "$PSScriptRoot/e2e-limit-corpus.py" 2>$null
}

$jobBody = '{"corpusName":"geostat-portal","seedUrl":"https://www.geostat.ge/ka","fullRecrawl":false}'
$job = Invoke-RestMethod -Method Post -Uri "$baseIngestion/api/v1/ingestion/jobs" -ContentType "application/json" -Body $jobBody
Write-Host "Crawl job $($job.jobId) state=$($job.state)"

$deadline = (Get-Date).AddMinutes(5)
do {
    Start-Sleep -Seconds 5
    $status = Invoke-RestMethod -Uri "$baseIngestion/api/v1/ingestion/jobs/$($job.jobId)"
    Write-Host "  $($status.state) $($status.message)"
} while ($status.state -notin @("completed", "failed") -and (Get-Date) -lt $deadline)

if ($status.state -ne "completed") { throw "Crawl did not complete: $($status.state)" }

$collections = Invoke-RestMethod -Uri "http://127.0.0.1:6333/collections"
if ($collections.result.collections.Count -lt 1) { throw "No Qdrant collections after crawl" }
Write-Host "Qdrant collections: $($collections.result.collections.name -join ', ')"

$searchBody = '{"text":"statistika","locale":"ka","maxChunks":3,"corpusName":"geostat-portal"}'
$chunks = Invoke-RestMethod -Method Post -Uri "$baseRetrieval/api/v1/retrieval/search" -ContentType "application/json" -Body $searchBody
if ($chunks.Count -lt 1) { throw "Retrieval returned no chunks" }
Write-Host "Retrieval OK: $($chunks.Count) chunks (top score $($chunks[0].score))"

if (-not $SkipChat) {
    if (-not (Test-Port 8090)) {
        Write-Warning "Chat-api not on 8090 — skip (start with RETRIEVAL_ENABLED=true)"
    } else {
        $chatBody = '{"message":"statistika","sessionId":"e2e-smoke"}'
        $chat = Invoke-RestMethod -Method Post -Uri "$baseChat/api/chat" -ContentType "application/json" -Body $chatBody -TimeoutSec 90
        if (-not $chat.intro) { throw "Chat returned empty intro" }
        Write-Host "Chat-api OK: $($chat.intro.Substring(0, [Math]::Min(80, $chat.intro.Length)))..."
    }
}

Write-Host "E2E smoke PASSED" -ForegroundColor Green
