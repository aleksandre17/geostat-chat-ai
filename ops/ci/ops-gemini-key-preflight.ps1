# OPS-01 preflight — GEMINI_API_KEY present (no secret output)
$ErrorActionPreference = "Stop"
$root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$envProd = Join-Path $root "ops\config\backend\.env.prod"
if (-not (Test-Path $envProd)) { throw "Missing $envProd" }
$line = Select-String -Path $envProd -Pattern '^GEMINI_API_KEY=(.+)$' | Select-Object -First 1
if (-not $line -or $line.Matches.Groups[1].Value.Length -lt 10) {
    throw "GEMINI_API_KEY missing or too short in .env.prod"
}
Write-Host "OPS-01 preflight OK: GEMINI_API_KEY is set (length=$($line.Matches.Groups[1].Value.Length))" -ForegroundColor Green
Write-Host "If key was exposed: follow docs/plan/OPS-GEMINI-KEY-ROTATION.md"
