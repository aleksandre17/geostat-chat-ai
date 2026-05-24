# Hybrid ④ — delegates to geostat-kit (P0-kit-12)
# Usage: .\ops\ci\hybrid-boot-app.ps1 -Service ing|ret|be|fe
# Prefer: tools\geostat.cmd hybrid boot fe|be|ret|ing  (or geostat fe run)
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('ing', 'ret', 'be', 'fe')]
    [string]$Service
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$geostat = Join-Path $Root 'tools\geostat.ps1'
& $geostat hybrid boot $Service
exit $LASTEXITCODE
