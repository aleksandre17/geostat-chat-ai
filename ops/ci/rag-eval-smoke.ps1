#Requires -Version 5.1
# RAG-L08 — golden queries from ingestion.evaluation_query via API
param(
    [string]$RetrievalBase = $env:RETRIEVAL_BASE_URL,
    [string]$IngestionBase = $env:INGESTION_URL,
    [string]$Corpus = 'geostat-portal',
    [double]$MinPassRate = 0.85,
    [switch]$Strict
)

$ErrorActionPreference = 'Stop'
if (-not $RetrievalBase) { $RetrievalBase = 'http://127.0.0.1:8092' }
if (-not $IngestionBase) { $IngestionBase = 'http://127.0.0.1:8093' }

function Get-FallbackEvalQueries {
    return @(
        @{ locale = 'ka'; queryText = 'statistika'; expectUrl = 'geostat.ge'; minChunks = 1 },
        @{ locale = 'ka'; queryText = 'ინფლაცია'; expectUrl = 'geostat.ge'; minChunks = 1 },
        @{ locale = 'en'; queryText = 'inflation'; expectUrl = 'geostat.ge'; minChunks = 1 },
        @{ locale = 'en'; queryText = 'statistics'; expectUrl = 'geostat.ge'; minChunks = 1 }
    )
}

$evalQueries = Get-FallbackEvalQueries
try {
    $fromDb = Invoke-RestMethod -Uri "$IngestionBase/api/v1/ingestion/corpora/$Corpus/evaluation-queries" -TimeoutSec 15
    if ($fromDb -and @($fromDb).Count -gt 0) {
        $evalQueries = @($fromDb)
        Write-Host "Loaded $($evalQueries.Count) evaluation queries from ingestion DB"
    }
} catch {
    Write-Warning "Using fallback eval queries (ingestion API unavailable: $($_.Exception.Message))"
}

$passed = 0
foreach ($q in $evalQueries) {
    $text = $q.queryText
    $locale = $q.locale
    $expect = $q.expectUrl
    $minChunks = if ($q.minChunks) { [int]$q.minChunks } else { 1 }
    $body = @{ text = $text; locale = $locale; maxChunks = 3; corpusName = $Corpus } | ConvertTo-Json
    $resp = Invoke-RestMethod -Method Post -Uri "$RetrievalBase/api/v1/retrieval/search" `
        -ContentType 'application/json' -Body $body
    $count = @($resp).Count
    $hit = $false
    if ($count -ge $minChunks) {
        foreach ($chunk in $resp) {
            if (-not $expect -or $chunk.sourceUrl -like "*$expect*") { $hit = $true; break }
        }
    }
    if ($hit) {
        Write-Host "[OK] ${locale}: ${text} -> $count chunks"
        $passed++
    } else {
        Write-Warning "[FAIL] ${locale}: ${text} -> $count chunks (expect URL *${expect}*, min=$minChunks)"
    }
}

Write-Host "RAG eval recall@k: $passed / $($evalQueries.Count) = $([math]::Round(100.0 * $passed / [Math]::Max(1, $evalQueries.Count), 1))% (min=$([math]::Round(100 * $MinPassRate, 0))%)"
Write-Host "RAG eval: $passed / $($evalQueries.Count) passed"
$rate = $passed / [Math]::Max(1, $evalQueries.Count)
if ($Strict -and $rate -lt $MinPassRate) { exit 1 }
