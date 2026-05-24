#Requires -Version 5.1
# OPS-02 completion — wait for crawl idle, reindex, smoke (attach to running crawl OK)
param(
    [string]$Corpus = 'geostat-portal',
    [int]$MaxHours = 12,
    [switch]$SkipSmoke
)

$ErrorActionPreference = 'Stop'
$baseIngestion = if ($env:INGESTION_URL) { $env:INGESTION_URL } else { 'http://127.0.0.1:8093' }
$Root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent

Write-Host '== OPS-02 complete: wait idle -> reindex -> smoke ==' -ForegroundColor Cyan

& "$PSScriptRoot/wait-corpus-crawl-idle.ps1" -Corpus $Corpus -MaxHours $MaxHours

Write-Host 'Reindex parsed documents...' -ForegroundColor Cyan
$reindex = Invoke-RestMethod -Method Post -Uri "$baseIngestion/api/v1/ingestion/corpora/$Corpus/reindex"
Write-Host "  documentsQueued=$($reindex.documentsQueued)"

$q = Invoke-RestMethod -Uri "$baseIngestion/api/v1/ingestion/corpora/$Corpus/quality"
Write-Host ("  corpus: parsed={0} chunks={1} vectorCoverage={2}" -f `
    $q.documents.parsed, $q.pipeline.totalChunks, $q.pipeline.vectorCoverageRate)

if (-not $SkipSmoke) {
    & "$PSScriptRoot/rag-eval-smoke.ps1" -Strict -MinPassRate 0.85
    if ($LASTEXITCODE -ne 0) { throw 'RAG eval failed' }
    & "$PSScriptRoot/rag-chat-broad-smoke.ps1" -Strict -MinPassRate 0.75
    if ($LASTEXITCODE -ne 0) { throw 'Chat broad smoke failed' }
}

Write-Host 'OPS-02 full corpus pipeline COMPLETE.' -ForegroundColor Green
