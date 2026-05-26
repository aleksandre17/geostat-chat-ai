#Requires -Version 5.1
# P0 L1 corpus quality orchestrator (manifest: ci.ragP0CorpusQuality)
param(
    [string]$IngestionBase = $env:INGESTION_URL,
    [string]$Corpus = $env:EVAL_CORPUS,
    [switch]$SkipReparse,
    [switch]$SkipAudit,
    [int]$ReparsePollSeconds = 900
)

$ErrorActionPreference = 'Stop'

if (-not $IngestionBase) { $IngestionBase = 'http://127.0.0.1:8093' }
if (-not $Corpus) { $Corpus = 'geostat-portal' }

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..')
Set-Location $repoRoot

function Test-IngestionHealth {
    $resp = Invoke-RestMethod -Uri "$IngestionBase/actuator/health" -TimeoutSec 15
    if ($resp.status -ne 'UP') {
        throw "ingestion health not UP: $($resp | ConvertTo-Json -Compress)"
    }
    Write-Host "[OK] ingestion health" -ForegroundColor Green
}

function Invoke-ReparseIfEnabled {
    if ($SkipReparse) {
        Write-Host '[SKIP] reparse (SkipReparse switch)' -ForegroundColor Yellow
        return
    }
    try {
        $resp = Invoke-RestMethod -Method Post -Uri "$IngestionBase/api/v1/ingestion/corpora/$Corpus/reparse" -TimeoutSec 30
        Write-Host "Reparse queued: $($resp | ConvertTo-Json -Compress)"
    } catch {
        if ($_.Exception.Response.StatusCode.value__ -eq 400) {
            Write-Host '[WARN] reparse skipped - parse profile disabled. Set INGESTION_PARSE_PROFILE_ENABLED=true for Phase B.' -ForegroundColor Yellow
            return
        }
        throw
    }

    $deadline = (Get-Date).AddSeconds($ReparsePollSeconds)
    do {
        Start-Sleep -Seconds 5
        $status = Invoke-RestMethod -Uri "$IngestionBase/api/v1/ingestion/corpora/$Corpus/reparse/status" -TimeoutSec 30
        Write-Host "Reparse status: $($status.status) processed=$($status.processed)/$($status.total)"
        if ($status.status -eq 'finished' -or $status.status -eq 'idle') { break }
    } while ((Get-Date) -lt $deadline)

    if ($status.status -ne 'finished' -and $status.status -ne 'idle') {
        throw "reparse did not finish within ${ReparsePollSeconds}s"
    }
}

function Test-ParseQualityGates {
    $report = Invoke-RestMethod -Uri "$IngestionBase/api/v1/ingestion/corpora/$Corpus/parse-quality" -TimeoutSec 30
    Write-Host "Parse quality (parsedDocs=$($report.parsedDocs)):"
    $failed = @()
    foreach ($gate in $report.gates) {
        $color = if ($gate.passed) { 'Green' } else { 'Red' }
        Write-Host ("  {0}: value={1:N4} target={2} passed={3}" -f $gate.id, $gate.value, $gate.target, $gate.passed) -ForegroundColor $color
        if (-not $gate.passed) { $failed += $gate.id }
    }
    if ($failed.Count -gt 0) {
        throw ("P0 gates failed: {0}" -f ($failed -join ', '))
    }
}

function Invoke-AuditScript {
    if ($SkipAudit) {
        Write-Host '[SKIP] analyze-ingestion-samples.py (SkipAudit switch)' -ForegroundColor Yellow
        return
    }
    python (Join-Path $repoRoot 'tools/analyze-ingestion-samples.py')
    if ($LASTEXITCODE -ne 0) {
        throw "analyze-ingestion-samples.py exited $LASTEXITCODE"
    }
}

Write-Host "=== P0 L1 corpus quality gate ($Corpus) ===" -ForegroundColor Cyan
Test-IngestionHealth
Invoke-ReparseIfEnabled
Test-ParseQualityGates
Invoke-AuditScript
Write-Host "=== P0 corpus quality gate PASSED ===" -ForegroundColor Green
