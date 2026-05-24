# P6-migrate preflight - structured layout + optional SSH dry-run
# Usage: ops/ci/p6-migrate-preflight.ps1 [-SkipMigrateDryRun]

param(
    [switch]$SkipMigrateDryRun
)

$ErrorActionPreference = "Stop"
$root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Set-Location $root

$py = Get-Command python -ErrorAction SilentlyContinue
if (-not $py) { $py = Get-Command python3 -ErrorAction SilentlyContinue }
if (-not $py) { throw "Python required for preflight" }

$env:GEOSTAT_PROJECT_ROOT = $root
$env:PYTHONPATH = Join-Path $root "kits\geostat-kit"

Write-Host "=== P6-migrate preflight ===" -ForegroundColor Cyan

& $py (Join-Path $root "kits\geostat-kit\lib\migrate_layout_preflight.py")
if ($LASTEXITCODE -ne 0) {
    Write-Host "[FAIL] fix .env.deploy (DEPLOY_LAYOUT=structured, DEPLOY_PATH)" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "Legacy flat -> structured renames:" -ForegroundColor Cyan
& $py (Join-Path $root "kits\geostat-kit\lib\migrate_layout_names.py")

if (-not $SkipMigrateDryRun) {
    Write-Host ""
    Write-Host "=== layout migrate --dry-run (DEPLOY_SERVER + SSH) ===" -ForegroundColor Cyan
    $geostat = Join-Path $root "tools\geostat.ps1"
    & powershell -ExecutionPolicy Bypass -File $geostat layout migrate --dry-run --prod
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[warn] dry-run failed - check deploy.env / SSH" -ForegroundColor Yellow
        exit $LASTEXITCODE
    }
}

Write-Host ""
Write-Host "[OK] preflight complete." -ForegroundColor Green
Write-Host "  Apply:  geostat layout migrate --prod" -ForegroundColor Green
Write-Host "  Deploy: geostat stack-deploy --prod" -ForegroundColor Green
