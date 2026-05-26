#Requires -Version 5.1
# Phase 8 P1 — derivation cutover prep (spec section 13 API steps; manifest: ci.ragDerivationCutoverPrep)
param(
    [string]$IngestionBase = $env:INGESTION_URL,
    [string]$RetrievalBase = $env:RETRIEVAL_BASE_URL,
    [string]$ChatBase = $env:CHAT_BASE_URL,
    [string]$Corpus = $env:EVAL_CORPUS,
    [switch]$Reindex,
    [switch]$SkipAuthority,
    [switch]$SkipRemine,
    [switch]$SkipCatalogRefresh,
    [switch]$SkipLifecycleSync,
    [switch]$ApproveAllClusters,
    [string]$ApprovedBy = 'cutover-prep',
    [switch]$RunEvalGate,
    [switch]$RunCatalogSmoke,
    [switch]$CompareYamlReference,
    [switch]$AllowNotReady
)

$ErrorActionPreference = 'Stop'

if (-not $IngestionBase) { $IngestionBase = 'http://127.0.0.1:8093' }
if (-not $RetrievalBase) { $RetrievalBase = 'http://127.0.0.1:8092' }
if (-not $ChatBase) { $ChatBase = 'http://127.0.0.1:8090' }
if (-not $Corpus) { $Corpus = 'geostat-portal' }

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..')
Set-Location $repoRoot

function Show-Readiness {
    param([string]$Title)
    Write-Host ""
    Write-Host "== $Title ==" -ForegroundColor Cyan
    $report = Invoke-RestMethod -Uri "$IngestionBase/api/v1/ingestion/corpora/$Corpus/derivation-readiness" -TimeoutSec 60
    foreach ($check in $report.checks) {
        $mark = if ($check.passed) { '[OK]' } else { '[FAIL]' }
        $color = if ($check.passed) { 'Green' } else { 'Yellow' }
        Write-Host "$mark $($check.id): $($check.detail)" -ForegroundColor $color
    }
    Write-Host "  enrichment=$($report.enrichmentEnabled) aggregation=$($report.aggregationEnabled) ready=$($report.readyForEvalGate)"
    return $report
}

function Invoke-IngestionStep {
    param(
        [string]$Label,
        [string]$Method,
        [string]$Uri,
        [switch]$Optional
    )
    Write-Host ""
    Write-Host ">> $Label" -ForegroundColor Cyan
    try {
        $resp = Invoke-RestMethod -Method $Method -Uri $Uri -TimeoutSec 600
        Write-Host ($resp | ConvertTo-Json -Compress)
        return $true
    } catch {
        $msg = $_.Exception.Message
        if ($Optional -and ($msg -match '501|NOT_IMPLEMENTED|Enrichment disabled|Aggregation disabled')) {
            Write-Warning "$Label skipped (feature off or not configured): $msg"
            return $false
        }
        throw
    }
}

Write-Host "== RAG derivation cutover prep ==" -ForegroundColor Cyan
Write-Host "  corpus=$Corpus ingestion=$IngestionBase"
Write-Host "  Requires: Flyway V9-V16, INGESTION_ENRICHMENT_ENABLED=true, INGESTION_AGGREGATION_ENABLED=true"
Write-Host "  Step 0 (once, YAML era): .\ops\ci\rag-eval-freeze-yaml-baseline.ps1" -ForegroundColor Yellow

$health = Invoke-RestMethod -Uri "$IngestionBase/actuator/health/liveness" -TimeoutSec 15
if ($health.status -ne 'UP') { throw "Ingestion not UP at $IngestionBase" }
Write-Host "[OK] ingestion health" -ForegroundColor Green

$before = Show-Readiness -Title 'Readiness (before)'

if ($Reindex) {
    Invoke-IngestionStep -Label 'Reindex parsed documents (async index jobs)' -Method Post `
        -Uri "$IngestionBase/api/v1/ingestion/corpora/$Corpus/reindex" | Out-Null
    Write-Host "  Wait for index queue to drain before continuing (logs / quality audit)."
}

if (-not $SkipAuthority) {
    Invoke-IngestionStep -Label 'Authority recompute (U01e)' -Method Post -Optional `
        -Uri "$IngestionBase/api/v1/ingestion/corpora/$Corpus/authority:recompute" | Out-Null
}

    Invoke-IngestionStep -Label 'Enrichment backfill (parsed documents, async)' -Method Post -Optional `
        -Uri "$IngestionBase/api/v1/ingestion/corpora/$Corpus/enrichment:backfill" | Out-Null
Write-Host '  Waiting for enrichment coverage (poll readiness + backfill status, max 45m)...' -ForegroundColor Cyan
$deadline = (Get-Date).AddMinutes(45)
while ((Get-Date) -lt $deadline) {
    $report = Invoke-RestMethod -Uri "$IngestionBase/api/v1/ingestion/corpora/$Corpus/derivation-readiness" -TimeoutSec 60
    $summary = $report.checks | Where-Object { $_.id -eq 'summary_coverage' } | Select-Object -First 1
    $pageKind = $report.checks | Where-Object { $_.id -eq 'page_kind_coverage' } | Select-Object -First 1
    if ($summary -and $summary.passed -and $pageKind -and $pageKind.passed) {
        Write-Host '[OK] summary_coverage + page_kind_coverage gates passed' -ForegroundColor Green
        break
    }
    $jobLine = ''
    try {
        $job = Invoke-RestMethod -Uri "$IngestionBase/api/v1/ingestion/corpora/$Corpus/enrichment/status" -TimeoutSec 15
        if ($job.status -eq 'running') {
            $jobLine = " backfill=$($job.documentsProcessed)/$($job.documentsTotal)"
        }
    } catch {
        # status endpoint optional until ingestion rebuild with P1 backfill progress API
    }
    $sDetail = if ($summary) { $summary.detail } else { 'enrichment starting' }
    $pDetail = if ($pageKind) { $pageKind.detail } else { 'page_kind pending' }
    Write-Host "  $sDetail | $pDetail$jobLine - retry in 60s"
    Start-Sleep -Seconds 60
}

if (-not $SkipRemine) {
    Invoke-IngestionStep -Label 'Topic remine (U01g)' -Method Post -Optional `
        -Uri "$IngestionBase/api/v1/ingestion/corpora/$Corpus/topics:remine" | Out-Null
}

$clusters = Invoke-RestMethod -Uri "$IngestionBase/api/v1/ingestion/corpora/$Corpus/topic-clusters" -TimeoutSec 60
$pending = @($clusters | Where-Object { -not $_.approved })
Write-Host ""
Write-Host "Topic clusters: $($clusters.Count) total, $($pending.Count) pending approval"
if ($pending.Count -gt 0 -and -not $ApproveAllClusters) {
    Write-Host "  Review: GET $IngestionBase/api/v1/ingestion/corpora/$Corpus/topic-clusters"
    Write-Host "  Approve each cluster, or re-run with -ApproveAllClusters (dev/smoke only)."
}
if ($ApproveAllClusters) {
    foreach ($cluster in $pending) {
        $id = $cluster.id
        Invoke-RestMethod -Method Post `
            -Uri "$IngestionBase/api/v1/ingestion/topic-clusters/${id}:approve?approvedBy=$ApprovedBy" `
            -TimeoutSec 60 | Out-Null
        Write-Host "[OK] approved cluster $id ($($cluster.labelKa))" -ForegroundColor Green
    }
}

if (-not $SkipCatalogRefresh) {
    Invoke-IngestionStep -Label 'Refresh catalog MVs (U02)' -Method Post -Optional `
        -Uri "$IngestionBase/api/v1/ingestion/catalog:refresh" | Out-Null
    try {
        $mvStatus = Invoke-RestMethod -Uri "$IngestionBase/api/v1/ingestion/catalog/status" -TimeoutSec 15
        if ($mvStatus.anyStale) {
            Write-Warning ('Catalog MVs report stale (> {0}h) - investigate refresh audit (V16)' -f $mvStatus.staleThresholdHours)
        } else {
            Write-Host '[OK] catalog MVs fresh' -ForegroundColor Green
        }
    } catch {
        Write-Warning 'catalog/status unavailable - run Flyway V16 + aggregation ON'
    }
}

if (-not $SkipLifecycleSync) {
    Invoke-IngestionStep -Label 'Sync Qdrant lifecycle metadata (section 17)' -Method Post `
        -Uri "$IngestionBase/api/v1/ingestion/corpora/$Corpus/lifecycle:sync-qdrant" | Out-Null
}

$after = Show-Readiness -Title 'Readiness (after)'

if (-not $after.readyForEvalGate -and -not $AllowNotReady) {
    Write-Host ""
    Write-Error 'Not ready for eval gate - fix failed checks above (enrichment backfill, cluster approval, MV refresh).'
}

Write-Host ""
Write-Host "Cutover prep API steps done. Next:" -ForegroundColor Cyan
Write-Host "  .\ops\ci\rag-eval-gate.ps1              # or -RunEvalGate on this script"
Write-Host "  .\ops\ci\rag-eval-gate.ps1 -WriteBaseline   # after hit@5 verified"
Write-Host "  owner OK -> GEOSTAT_CHAT_CATALOG_SOURCE=derived (topics.yaml stays until then)"

if ($RunCatalogSmoke) {
    $smokeArgs = @('-File', (Join-Path $PSScriptRoot 'chat-derived-catalog-smoke.ps1'),
        '-IngestionUrl', $IngestionBase, '-ChatUrl', $ChatBase, '-Corpus', $Corpus)
    if ($AllowNotReady) { $smokeArgs += '-SkipReadiness' }
    & powershell @smokeArgs
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

if ($RunEvalGate) {
    $gateArgs = @(
        '-File', (Join-Path $PSScriptRoot 'rag-eval-gate.ps1'),
        '-IngestionBase', $IngestionBase,
        '-RetrievalBase', $RetrievalBase,
        '-ChatBase', $ChatBase,
        '-Corpus', $Corpus
    )
    if ($AllowNotReady) { $gateArgs += '-AllowNotReady' }
    if ($RunCatalogSmoke) { $gateArgs += '-RunCatalogSmoke' }
    if ($CompareYamlReference) { $gateArgs += '-CompareYamlReference' }
    & powershell @gateArgs
    exit $LASTEXITCODE
}

exit 0
