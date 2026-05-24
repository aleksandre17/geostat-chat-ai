# OPS-02 / P3-03b — corpus quality audit (Windows / hybrid dev)
param(
    [string]$Corpus = $(if ($env:AUDIT_CORPUS) { $env:AUDIT_CORPUS } else { "geostat-portal" }),
    [string]$IngestionUrl = $(if ($env:INGESTION_URL) { $env:INGESTION_URL } else { "http://127.0.0.1:8093" }),
    [switch]$Strict
)

$ErrorActionPreference = "Stop"
if ($Strict) { $env:AUDIT_STRICT = "1" }

$Root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$env:GEOSTAT_PROJECT_ROOT = $Root
$env:GEOSTAT_KIT_ROOT = Join-Path $Root "kits\geostat-kit"
$Wait = Join-Path $env:GEOSTAT_KIT_ROOT "ci\wait-health.sh"

Write-Host "[corpus-audit] ingestion=$IngestionUrl corpus=$Corpus"

if (Get-Command bash -ErrorAction SilentlyContinue) {
    & bash $Wait "$IngestionUrl/actuator/health" "UP" 120
} else {
    $health = Invoke-RestMethod -Uri "$IngestionUrl/actuator/health" -TimeoutSec 10
    if ($health.status -ne "UP") { throw "Ingestion health not UP" }
}

$report = Invoke-RestMethod -Uri "$IngestionUrl/api/v1/ingestion/corpora/$Corpus/quality"
Write-Host "[corpus-audit] documents:" ($report.documents | ConvertTo-Json -Compress)
Write-Host "[corpus-audit] pipeline:" ($report.pipeline | ConvertTo-Json -Compress)
Write-Host "[corpus-audit] recommendations:" ($report.recommendations -join ", ")

if ($report.sampleEmptyUrls -and $report.sampleEmptyUrls.Count -gt 0) {
    Write-Host "[corpus-audit] sample empty URLs:"
    $report.sampleEmptyUrls | Select-Object -First 5 | ForEach-Object { Write-Host "  - $_" }
}

$actionable = @(
    "CONSIDER_PLAYWRIGHT_P3_03B",
    "CONSIDER_RECRAWL_OPS02",
    "CONSIDER_REINDEX_OPS02"
)
$recs = @($report.recommendations)

if ($recs -contains "NO_DATA") {
    Write-Host "[corpus-audit] WARN: no parsed documents — run rag-pipeline-smoke first"
    if ($Strict) { exit 2 }
    exit 0
}

foreach ($flag in $actionable) {
    if ($recs -contains $flag) {
        Write-Host "[corpus-audit] ACTION: $flag"
        if ($Strict) { exit 2 }
        exit 0
    }
}

Write-Host "[corpus-audit] corpus quality OK"
exit 0
