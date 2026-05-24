#Requires -Version 5.1
# RAG-L01…L09 — dual-locale crawl, reindex, eval (hybrid ④)
param(
    [int]$MaxPagesPerSeed = 5,
    [string]$Corpus = 'geostat-portal',
    [switch]$FullRecrawl,
    [switch]$SkipChat,
    [switch]$Strict
)

$ErrorActionPreference = 'Stop'
$baseIngestion = if ($env:INGESTION_URL) { $env:INGESTION_URL } else { 'http://127.0.0.1:8093' }
$baseRetrieval = if ($env:RETRIEVAL_BASE_URL) { $env:RETRIEVAL_BASE_URL } else { 'http://127.0.0.1:8092' }
$baseChat = if ($env:CHAT_API_URL) { $env:CHAT_API_URL } else { 'http://127.0.0.1:8090' }

function Test-Port($port) {
    (Test-NetConnection -ComputerName 127.0.0.1 -Port $port -WarningAction SilentlyContinue).TcpTestSucceeded
}

function Wait-CrawlJob($jobId) {
    $deadline = (Get-Date).AddMinutes(8)
    do {
        Start-Sleep -Seconds 5
        $status = Invoke-RestMethod -Uri "$baseIngestion/api/v1/ingestion/jobs/$jobId"
        Write-Host "  job $jobId -> $($status.state) $($status.message)"
    } while ($status.state -notin @('completed', 'failed', 'cancelled') -and (Get-Date) -lt $deadline)
    if ($status.state -ne 'completed') {
        throw "Crawl job $jobId ended with state $($status.state)"
    }
}

function Start-SeedCrawl($seedUrl) {
    if ($FullRecrawl) {
        python "$PSScriptRoot/rag-dev-crawl-policy.py" 2>$null
    } elseif ($MaxPagesPerSeed -gt 0) {
        python "$PSScriptRoot/e2e-limit-corpus.py" 2>$null
    }
    $body = @{ corpusName = $Corpus; seedUrl = $seedUrl; fullRecrawl = [bool]$FullRecrawl } | ConvertTo-Json
    $job = Invoke-RestMethod -Method Post -Uri "$baseIngestion/api/v1/ingestion/jobs" `
        -ContentType 'application/json' -Body $body
    Write-Host "Crawl seed $seedUrl -> job $($job.jobId)"
    Wait-CrawlJob $job.jobId
}

Write-Host '== RAG locale pipeline (dual seed + reindex + eval) ==' -ForegroundColor Cyan
foreach ($p in @(5432, 6333, 8093, 8092)) {
    if (-not (Test-Port $p)) { throw "Port $p not reachable (tunnel/services?)" }
}

$corpusInfo = Invoke-RestMethod -Uri "$baseIngestion/api/v1/ingestion/corpora"
$portal = $corpusInfo | Where-Object { $_.name -eq $Corpus } | Select-Object -First 1
if ($portal -and $portal.seedUrls -and $portal.seedUrls.Count -ge 2) {
    foreach ($seed in $portal.seedUrls) { Start-SeedCrawl $seed }
} else {
    Start-SeedCrawl 'https://www.geostat.ge/ka'
    Start-SeedCrawl 'https://www.geostat.ge/en'
}

Write-Host 'Reindex parsed documents...'
$reindex = Invoke-RestMethod -Method Post -Uri "$baseIngestion/api/v1/ingestion/corpora/$Corpus/reindex"
Write-Host "  documents=$($reindex.documentsQueued) mode=$($reindex.mode)"

$collections = Invoke-RestMethod -Uri 'http://127.0.0.1:6333/collections'
Write-Host "Qdrant collections: $($collections.result.collections.name -join ', ')"

& "$PSScriptRoot/rag-eval-smoke.ps1" -RetrievalBase $baseRetrieval -IngestionBase $baseIngestion -Corpus $Corpus -Strict:$Strict
if ($LASTEXITCODE -ne 0 -and $Strict) { throw 'RAG eval smoke failed' }

if (-not $SkipChat -and (Test-Port 8090)) {
    $chatKa = Invoke-RestMethod -Method Post -Uri "$baseChat/api/chat" `
        -ContentType 'application/json' `
        -Body '{"message":"statistika","sessionId":"rag-locale","locale":"ka"}' -TimeoutSec 90
    $chatEn = Invoke-RestMethod -Method Post -Uri "$baseChat/api/chat" `
        -ContentType 'application/json' `
        -Body '{"message":"statistics","sessionId":"rag-locale-en","locale":"en"}' -TimeoutSec 90
    if (-not $chatKa.intro -or -not $chatEn.intro) { throw 'Chat-api bilingual smoke failed' }
    Write-Host "Chat KA/EN OK"
}

Write-Host 'RAG locale pipeline PASSED' -ForegroundColor Green
