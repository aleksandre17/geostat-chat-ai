#Requires -Version 5.1
# P1-15 master cutover orchestrator — freeze → prep → derived flip → gate (manifest: ci.ragP1Cutover)
param(
    [ValidateSet('status', 'freeze', 'prep', 'gate', 'run')]
    [string]$Step = 'status',
    [string]$RetrievalBase = $env:RETRIEVAL_BASE_URL,
    [string]$IngestionBase = $env:INGESTION_URL,
    [string]$ChatBase = $env:CHAT_BASE_URL,
    [string]$Corpus = $env:EVAL_CORPUS,
    [switch]$ForceFreeze,
    [switch]$SkipReindex,
    [switch]$WaitForDerived,
    [int]$WaitMinutes = 30,
    [switch]$WriteBaseline,
    [switch]$AllowNotReady
)

$ErrorActionPreference = 'Stop'

if (-not $RetrievalBase) { $RetrievalBase = 'http://127.0.0.1:8092' }
if (-not $IngestionBase) { $IngestionBase = 'http://127.0.0.1:8093' }
if (-not $ChatBase) { $ChatBase = 'http://127.0.0.1:8090' }
if (-not $Corpus) { $Corpus = 'geostat-portal' }

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..')
Set-Location $repoRoot

$frozenPath = Join-Path $repoRoot 'ops/eval/baseline.yaml-frozen.json'

function Get-FrozenBaselineMeta {
    if (-not (Test-Path $frozenPath)) {
        return @{ exists = $false; generatedAt = $null; isSeed = $true }
    }
    $json = Get-Content $frozenPath -Raw | ConvertFrom-Json
    $isSeed = -not $json.generatedAt
    return @{
        exists     = $true
        generatedAt = $json.generatedAt
        isSeed     = $isSeed
        catalogSource = $json.catalogSource
    }
}

function Show-P1CutoverStatus {
    Write-Host '== P1 cutover status ==' -ForegroundColor Cyan
    $frozen = Get-FrozenBaselineMeta
    if (-not $frozen.exists) {
        Write-Host '[TODO] Step 0: baseline.yaml-frozen.json missing' -ForegroundColor Yellow
    } elseif ($frozen.isSeed) {
        Write-Host '[TODO] Step 0: frozen baseline is seed placeholder - run freeze' -ForegroundColor Yellow
    } else {
        Write-Host "[OK] Step 0: YAML frozen baseline ($($frozen.generatedAt))" -ForegroundColor Green
    }

    try {
        $ingHealth = Invoke-RestMethod -Uri "$IngestionBase/actuator/health/liveness" -TimeoutSec 8
        Write-Host "[OK] ingestion UP" -ForegroundColor Green
        try {
            $backfill = Invoke-RestMethod -Uri "$IngestionBase/api/v1/ingestion/corpora/$Corpus/enrichment/status" -TimeoutSec 8
            if ($backfill.status -eq 'running') {
                Write-Host "  backfill=$($backfill.documentsProcessed)/$($backfill.documentsTotal) (async)" -ForegroundColor Cyan
            }
        } catch {
            # optional until ingestion rebuild with status API
        }
        $readiness = Invoke-RestMethod -Uri "$IngestionBase/api/v1/ingestion/corpora/$Corpus/derivation-readiness" -TimeoutSec 20
        Write-Host "  enrichment=$($readiness.enrichmentEnabled) aggregation=$($readiness.aggregationEnabled) ready=$($readiness.readyForEvalGate)"
        foreach ($check in $readiness.checks | Where-Object { -not $_.passed }) {
            Write-Host "  [FAIL] $($check.id): $($check.detail)" -ForegroundColor Yellow
        }
        try {
            $catalog = Invoke-RestMethod -Uri "$IngestionBase/api/v1/ingestion/catalog/status" -TimeoutSec 10
            Write-Host "  catalog MV anyStale=$($catalog.anyStale) threshold=$($catalog.staleThresholdHours)h"
        } catch {
            Write-Host '  catalog/status: aggregation OFF or V16 pending' -ForegroundColor Yellow
        }
    } catch {
        Write-Host '[WARN] ingestion not reachable - start hybrid stack' -ForegroundColor Yellow
    }

    try {
        $chatCatalog = Invoke-RestMethod -Uri "$ChatBase/api/v1/chat/catalog/status" -TimeoutSec 8
        Write-Host "  chat catalog source=$($chatCatalog.source) derived=$($chatCatalog.derived) reader=$($chatCatalog.derivedReaderActive)"
        if ($chatCatalog.derived) {
            Write-Host '[OK] Step 3: chat-api on derived catalog' -ForegroundColor Green
        } else {
            Write-Host '[TODO] Step 3: set GEOSTAT_CHAT_CATALOG_SOURCE=derived + JDBC, restart chat-api' -ForegroundColor Yellow
        }
    } catch {
        Write-Host '[WARN] chat-api not reachable' -ForegroundColor Yellow
    }

    Write-Host ''
    Write-Host 'Commands:' -ForegroundColor Cyan
    Write-Host '  .\ops\ci\rag-p1-cutover.ps1 -Step freeze'
    Write-Host '  .\ops\ci\rag-p1-cutover.ps1 -Step prep'
    Write-Host '  # flip derived + restart chat-api'
    Write-Host '  .\ops\ci\rag-p1-cutover.ps1 -Step gate [-WriteBaseline]'
    Write-Host '  .\ops\ci\rag-p1-cutover.ps1 -Step run -WaitForDerived   # prep + wait + gate'
}

function Invoke-FreezeStep {
    $frozen = Get-FrozenBaselineMeta
    if ($frozen.exists -and -not $frozen.isSeed -and -not $ForceFreeze) {
        Write-Host "Frozen baseline already captured at $($frozen.generatedAt). Use -ForceFreeze to overwrite." -ForegroundColor Yellow
        return
    }
    $args = @('-File', (Join-Path $PSScriptRoot 'rag-eval-freeze-yaml-baseline.ps1'),
        '-RetrievalBase', $RetrievalBase, '-IngestionBase', $IngestionBase,
        '-ChatUrl', $ChatBase, '-Corpus', $Corpus)
    & powershell @args
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

function Invoke-PrepStep {
    $prepArgs = @(
        '-File', (Join-Path $PSScriptRoot 'rag-derivation-cutover-prep.ps1'),
        '-IngestionBase', $IngestionBase,
        '-RetrievalBase', $RetrievalBase,
        '-ChatBase', $ChatBase,
        '-Corpus', $Corpus,
        '-ApproveAllClusters'
    )
    if (-not $SkipReindex) { $prepArgs += '-Reindex' }
    if ($AllowNotReady) { $prepArgs += '-AllowNotReady' }
    & powershell @prepArgs
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

function Wait-ForDerivedCatalog {
    Write-Host "Waiting for chat-api source=derived (max ${WaitMinutes}m)..." -ForegroundColor Cyan
    $deadline = (Get-Date).AddMinutes($WaitMinutes)
    while ((Get-Date) -lt $deadline) {
        try {
            $status = Invoke-RestMethod -Uri "$ChatBase/api/v1/chat/catalog/status" -TimeoutSec 10
            if ($status.derived -and $status.derivedReaderActive) {
                Write-Host '[OK] derived catalog active' -ForegroundColor Green
                return
            }
            Write-Host "  source=$($status.source) derived=$($status.derived) - retry in 15s"
        } catch {
            Write-Host '  chat-api unreachable - retry in 15s'
        }
        Start-Sleep -Seconds 15
    }
    throw ('Timed out after {0}m - set GEOSTAT_CHAT_CATALOG_SOURCE=derived + GEOSTAT_CHAT_CATALOG_JDBC_URL and restart chat-api' -f $WaitMinutes)
}

function Invoke-GateStep {
    $gateArgs = @(
        '-File', (Join-Path $PSScriptRoot 'rag-eval-gate.ps1'),
        '-IngestionBase', $IngestionBase,
        '-RetrievalBase', $RetrievalBase,
        '-ChatBase', $ChatBase,
        '-Corpus', $Corpus,
        '-CompareYamlReference',
        '-RunCatalogSmoke'
    )
    if ($WriteBaseline) { $gateArgs += '-WriteBaseline' }
    if ($AllowNotReady) { $gateArgs += '-AllowNotReady' }
    & powershell @gateArgs
    exit $LASTEXITCODE
}

switch ($Step) {
    'status' { Show-P1CutoverStatus; exit 0 }
    'freeze' { Invoke-FreezeStep; exit 0 }
    'prep' { Invoke-PrepStep; exit 0 }
    'gate' { Invoke-GateStep }
    'run' {
        Show-P1CutoverStatus
        Write-Host ''
        Invoke-FreezeStep
        Invoke-PrepStep
        Write-Host ''
        Write-Host '>> Owner action: restart chat-api with GEOSTAT_CHAT_CATALOG_SOURCE=derived + JDBC URL' -ForegroundColor Cyan
        if ($WaitForDerived) {
            Wait-ForDerivedCatalog
            Invoke-GateStep
        } else {
            Write-Host 'When derived is live: .\ops\ci\rag-p1-cutover.ps1 -Step gate [-WriteBaseline]'
            Write-Host 'Or re-run: .\ops\ci\rag-p1-cutover.ps1 -Step run -WaitForDerived'
        }
        exit 0
    }
}
