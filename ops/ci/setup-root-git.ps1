# Optional: initialize monorepo root git (keeps apps nested repos until migrated)
# Usage: .\ops\ci\setup-root-git.ps1 [-KeepNestedRepos]

param([switch]$KeepNestedRepos)

$Root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Set-Location $Root

if (Test-Path (Join-Path $Root ".git")) {
    Write-Host "  Root .git already exists — nothing to do."
    exit 0
}

Write-Host ""
Write-Host "  Initializing root git at $Root" -ForegroundColor Cyan
git init

if (-not $KeepNestedRepos) {
    Write-Host ""
    Write-Host "  Nested repos detected:" -ForegroundColor Yellow
    @("apps\frontend\.git", "apps\backend\.git") | ForEach-Object {
        if (Test-Path (Join-Path $Root $_)) { Write-Host "    - $_" }
    }
    Write-Host "  See docs/MONOREPO.md for submodule/subtree migration."
    Write-Host "  Re-run with -KeepNestedRepos to skip this notice."
}

Write-Host ""
Write-Host "  Next: git add . && git commit (after reviewing ops/config/ are ignored)" -ForegroundColor Green
Write-Host ""
