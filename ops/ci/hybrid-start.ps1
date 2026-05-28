#Requires -Version 5.1
# Consumer wrapper - starts one or more services in hybrid mode with Java 21.
# The kit's Invoke-HybridJarBoot uses bare `java` (not JAVA_HOME); this script
# pins Corretto 21 on PATH before delegating to the kit CLI.
#
# Usage:
#   .\ops\ci\hybrid-start.ps1                    # starts ing, ret, be in sequence
#   .\ops\ci\hybrid-start.ps1 -Services ing      # ingestion only
#   .\ops\ci\hybrid-start.ps1 -Services ing,ret  # ingestion + retrieval
#
param(
    [string[]]$Services = @('ing', 'ret', 'be')
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent

# --- Pin Java 21 (Corretto) on PATH -----------------------------------------
$jdksBase = Join-Path $env:USERPROFILE '.jdks'
$corretto21 = Get-ChildItem $jdksBase -Directory -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -match '^corretto-21\.' } |
    Sort-Object Name -Descending |
    Select-Object -First 1
if ($corretto21) {
    $env:JAVA_HOME = $corretto21.FullName
    $env:PATH      = "$($corretto21.FullName)\bin;$env:PATH"
    Write-Host "[hybrid-start] JAVA_HOME -> $($corretto21.FullName)" -ForegroundColor DarkCyan
} else {
    Write-Warning "[hybrid-start] Corretto 21 not found in $jdksBase - using system java"
}

# --- Verify Java version -----------------------------------------------------
$javaVer = & java -version 2>&1 | Select-String 'version' | Select-Object -First 1
Write-Host "[hybrid-start] java: $javaVer" -ForegroundColor DarkCyan

# --- Start each service ------------------------------------------------------
$geostat = Join-Path $Root 'tools\geostat.ps1'
foreach ($svc in $Services) {
    Write-Host ""
    Write-Host "=== hybrid boot: $svc ===" -ForegroundColor Cyan
    & $geostat hybrid boot $svc
    if ($LASTEXITCODE -ne 0) {
        Write-Error "[hybrid-start] $svc exited with code $LASTEXITCODE"
    }
}
