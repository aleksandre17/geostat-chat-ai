#Requires -Version 5.1
# RAG-U12 P1-15 — eval gate orchestrator (manifest: ci.ragEvalGate)
param(
    [string]$RetrievalBase = $env:RETRIEVAL_BASE_URL,
    [string]$IngestionBase = $env:INGESTION_URL,
    [string]$ChatBase = $env:CHAT_BASE_URL,
    [string]$Corpus = $env:EVAL_CORPUS,
    [int]$MinQueries = 150,
    [double]$MaxRegression = 0.05,
    [switch]$WriteBaseline,
    [switch]$SkipLifecycleSync,
    [switch]$SkipHarness,
    [switch]$AllowNotReady,
    [switch]$RunCatalogSmoke,
    [switch]$CompareYamlReference,
    [switch]$SkipCatalogStatus
)

$ErrorActionPreference = 'Stop'

if (-not $RetrievalBase) { $RetrievalBase = 'http://127.0.0.1:8092' }
if (-not $IngestionBase) { $IngestionBase = 'http://127.0.0.1:8093' }
if (-not $ChatBase) { $ChatBase = 'http://127.0.0.1:8090' }
if (-not $Corpus) { $Corpus = 'geostat-portal' }

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..')
Set-Location $repoRoot

function Test-ServiceHealth {
    param([string]$Label, [string]$Url, [string]$ExpectFragment = 'UP')
    try {
        $resp = Invoke-RestMethod -Uri $Url -TimeoutSec 15
        $json = $resp | ConvertTo-Json -Compress
        if ($json -notmatch $ExpectFragment) {
            throw "unexpected health payload: $json"
        }
        Write-Host "[OK] $Label health" -ForegroundColor Green
    } catch {
        Write-Error ('{0} unavailable at {1} - start hybrid stack (geostat hybrid boot). {2}' -f $Label, $Url, $_.Exception.Message)
    }
}

function Test-GoldenSet {
    param([int]$Minimum)
    $queries = Invoke-RestMethod -Uri "$IngestionBase/api/v1/ingestion/corpora/$Corpus/evaluation-queries" -TimeoutSec 30
    $count = @($queries).Count
    Write-Host "Golden set: $count queries (min=$Minimum)"
    if ($count -lt $Minimum) {
        throw ('Need >= {0} evaluation queries - run Flyway V14 on ingestion DB' -f $Minimum)
    }
}

function Test-QueryAnalyze {
    $bodyObj = @{ text = 'inflation'; locale = 'en' }
    $body = $bodyObj | ConvertTo-Json -Compress
    $resp = Invoke-RestMethod -Method Post -Uri "$ChatBase/api/v1/chat/query:analyze" `
        -ContentType 'application/json; charset=utf-8' `
        -Body ([System.Text.Encoding]::UTF8.GetBytes($body)) `
        -TimeoutSec 30
    if (-not $resp.intent) {
        throw 'query:analyze returned no intent'
    }
    Write-Host "[OK] chat-api query:analyze intent=$($resp.intent)" -ForegroundColor Green
}

function Test-DerivationReadiness {
    param([switch]$AllowNotReady)
    $report = Invoke-RestMethod -Uri "$IngestionBase/api/v1/ingestion/corpora/$Corpus/derivation-readiness" -TimeoutSec 30
    foreach ($check in $report.checks) {
        $mark = if ($check.passed) { '[OK]' } else { '[FAIL]' }
        $color = if ($check.passed) { 'Green' } else { 'Yellow' }
        Write-Host "$mark $($check.id): $($check.detail)" -ForegroundColor $color
    }
    Write-Host "  enrichment=$($report.enrichmentEnabled) aggregation=$($report.aggregationEnabled) ready=$($report.readyForEvalGate)"
    if (-not $report.readyForEvalGate -and -not $AllowNotReady) {
        throw 'Derivation readiness failed - fix gates above before eval (or -AllowNotReady for YAML-only baseline)'
    }
}

function Test-CatalogStatus {
    param([switch]$RequireDerived)
    $status = Invoke-RestMethod -Uri "$ChatBase/api/v1/chat/catalog/status" -TimeoutSec 15
    Write-Host "Catalog source=$($status.source) derived=$($status.derived) reader=$($status.derivedReaderActive) jdbc=$($status.jdbcConfigured)"
    if ($RequireDerived) {
        if (-not $status.derived) {
            throw "Expected GEOSTAT_CHAT_CATALOG_SOURCE=derived (got $($status.source))"
        }
        if (-not $status.derivedReaderActive) {
            throw 'Derived catalog reader inactive - set GEOSTAT_CHAT_CATALOG_JDBC_URL and Flyway V9-V15'
        }
    }
    Write-Host '[OK] catalog status' -ForegroundColor Green
}

Write-Host "== RAG-U12 eval gate (P1-15) ==" -ForegroundColor Cyan
Write-Host "  corpus=$Corpus retrieval=$RetrievalBase ingestion=$IngestionBase chat=$ChatBase"

Test-ServiceHealth -Label 'ingestion' -Url "$IngestionBase/actuator/health"
Test-ServiceHealth -Label 'retrieval' -Url "$RetrievalBase/health"
Test-ServiceHealth -Label 'chat-api' -Url "$ChatBase/health/liveness"
Test-GoldenSet -Minimum $MinQueries
Test-DerivationReadiness -AllowNotReady:$AllowNotReady
Test-QueryAnalyze

if (-not $SkipCatalogStatus) {
    Test-CatalogStatus -RequireDerived:($RunCatalogSmoke -or $CompareYamlReference)
}

if ($RunCatalogSmoke) {
    $smokeArgs = @(
        '-File', (Join-Path $PSScriptRoot 'chat-derived-catalog-smoke.ps1'),
        '-IngestionUrl', $IngestionBase,
        '-ChatUrl', $ChatBase,
        '-Corpus', $Corpus,
        '-SkipReadiness'
    )
    & powershell @smokeArgs
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

if (-not $SkipLifecycleSync) {
    Write-Host "Syncing Qdrant lifecycle metadata (serveState/pageKind/scoreBoost)..." -ForegroundColor Cyan
    $sync = Invoke-RestMethod -Method Post -Uri "$IngestionBase/api/v1/ingestion/corpora/$Corpus/lifecycle:sync-qdrant" -TimeoutSec 600
    Write-Host "  processed=$($sync.documentsProcessed) updated=$($sync.metadataUpdated) dropped=$($sync.droppedRemoved)"
}

if ($SkipHarness) {
    Write-Host 'SkipHarness set - prerequisites OK; run rag-eval-harness.ps1 manually.' -ForegroundColor Yellow
    exit 0
}

$gateArgs = @(
    '-File', (Join-Path $PSScriptRoot 'rag-eval-harness.ps1'),
    '-RetrievalBase', $RetrievalBase,
    '-IngestionBase', $IngestionBase,
    '-ChatBase', $ChatBase,
    '-Corpus', $Corpus,
    '-MaxRegression', $MaxRegression,
    '-MinQueries', $MinQueries
)
if ($WriteBaseline) { $gateArgs += '-WriteBaseline' }
if ($CompareYamlReference) {
    $yamlFrozen = Join-Path $repoRoot 'ops/eval/baseline.yaml-frozen.json'
    $gateArgs += @('-ReferenceBaseline', $yamlFrozen, '-ReferenceMaxRegression', '0')
}

& powershell @gateArgs
$code = $LASTEXITCODE
if ($code -ne 0) { exit $code }

Write-Host ""
Write-Host "Eval gate passed. Cutover checklist (owner):" -ForegroundColor Cyan
Write-Host "  1. hit@5 vs ops/eval/baseline.yaml-frozen.json (use -CompareYamlReference)"
Write-Host "  2. Derived catalog smoke (-RunCatalogSmoke) when chat-api source=derived"
Write-Host "  3. owner OK -> keep GEOSTAT_CHAT_CATALOG_SOURCE=derived"
Write-Host "  4. Only then delete topics.yaml (spec section 17)"
exit 0
