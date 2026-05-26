#Requires -Version 5.1
# P1-15 step 0 — capture real YAML-era retrieval baseline (manifest: ci.ragEvalFreezeYamlBaseline)
param(
    [string]$RetrievalBase = $env:RETRIEVAL_BASE_URL,
    [string]$IngestionBase = $env:INGESTION_URL,
    [string]$ChatBase = $env:CHAT_BASE_URL,
    [string]$Corpus = $env:EVAL_CORPUS,
    [int]$MinQueries = 150,
    [switch]$SkipCatalogCheck,
    [switch]$AllowEnrichmentOn
)

$ErrorActionPreference = 'Stop'

if (-not $RetrievalBase) { $RetrievalBase = 'http://127.0.0.1:8092' }
if (-not $IngestionBase) { $IngestionBase = 'http://127.0.0.1:8093' }
if (-not $ChatBase) { $ChatBase = 'http://127.0.0.1:8090' }
if (-not $Corpus) { $Corpus = 'geostat-portal' }

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..')
Set-Location $repoRoot

$frozenPath = Join-Path $repoRoot 'ops/eval/baseline.yaml-frozen.json'

Write-Host '== P1-15 step 0: freeze YAML-era retrieval baseline ==' -ForegroundColor Cyan
Write-Host "  corpus=$Corpus chat=$ChatBase retrieval=$RetrievalBase"
Write-Host "  Output: $frozenPath"
Write-Host ''
Write-Host 'Prerequisites:' -ForegroundColor Yellow
Write-Host '  - GEOSTAT_CHAT_CATALOG_SOURCE=yaml (NOT derived)'
Write-Host '  - INGESTION_ENRICHMENT_ENABLED=false (YAML-era stack)'
Write-Host '  - Golden set loaded (Flyway V14, >=150 queries)'
Write-Host ''

foreach ($pair in @(
        @{ Label = 'ingestion'; Url = "$IngestionBase/actuator/health"; Expect = 'UP' },
        @{ Label = 'retrieval'; Url = "$RetrievalBase/health"; Expect = 'UP' },
        @{ Label = 'chat-api'; Url = "$ChatBase/health/liveness"; Expect = 'UP' }
    )) {
    $resp = Invoke-RestMethod -Uri $pair.Url -TimeoutSec 15
    $json = $resp | ConvertTo-Json -Compress
    if ($json -notmatch $pair.Expect) {
        throw "$($pair.Label) not healthy at $($pair.Url)"
    }
    Write-Host "[OK] $($pair.Label) health" -ForegroundColor Green
}

if (-not $SkipCatalogCheck) {
    $catalog = Invoke-RestMethod -Uri "$ChatBase/api/v1/chat/catalog/status" -TimeoutSec 15
    Write-Host "Catalog source=$($catalog.source) derived=$($catalog.derived)"
    if ($catalog.derived) {
        throw 'Chat catalog must be yaml - restart chat-api with GEOSTAT_CHAT_CATALOG_SOURCE=yaml before freezing'
    }
    Write-Host '[OK] catalog source is yaml' -ForegroundColor Green
}

$queries = Invoke-RestMethod -Uri "$IngestionBase/api/v1/ingestion/corpora/$Corpus/evaluation-queries" -TimeoutSec 30
$queryCount = @($queries).Count
Write-Host "Golden set: $queryCount queries (min=$MinQueries)"
if ($queryCount -lt $MinQueries) {
    throw ('Need >= {0} evaluation queries - run Flyway V14' -f $MinQueries)
}

$readiness = Invoke-RestMethod -Uri "$IngestionBase/api/v1/ingestion/corpora/$Corpus/derivation-readiness" -TimeoutSec 30
Write-Host "  enrichment=$($readiness.enrichmentEnabled) aggregation=$($readiness.aggregationEnabled)"
if ($readiness.enrichmentEnabled -and -not $AllowEnrichmentOn) {
    throw 'Enrichment is ON - freeze only on YAML-era stack (or pass -AllowEnrichmentOn if intentional)'
}

if (Test-Path $frozenPath) {
    $existing = Get-Content $frozenPath -Raw | ConvertFrom-Json
    if ($existing.generatedAt) {
        Write-Warning "Overwriting existing frozen baseline from $($existing.generatedAt)"
    } else {
        Write-Warning 'Overwriting seed baseline.yaml-frozen.json with live metrics'
    }
}

$env:RETRIEVAL_BASE_URL = $RetrievalBase
$env:INGESTION_URL = $IngestionBase
$env:CHAT_BASE_URL = $ChatBase
$env:EVAL_CORPUS = $Corpus

python ops/ci/run-eval.py `
    --corpus $Corpus `
    --retrieval-base $RetrievalBase `
    --ingestion-base $IngestionBase `
    --chat-base $ChatBase `
    --min-queries $MinQueries `
    --write-yaml-frozen

if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host ''
Write-Host 'YAML frozen baseline saved.' -ForegroundColor Green
Write-Host 'Next (after derivation prep):'
Write-Host '  .\ops\ci\rag-derivation-cutover-prep.ps1 -ApproveAllClusters'
Write-Host '  GEOSTAT_CHAT_CATALOG_SOURCE=derived + JDBC URL'
Write-Host '  .\ops\ci\rag-eval-gate.ps1 -CompareYamlReference -RunCatalogSmoke'
exit 0
