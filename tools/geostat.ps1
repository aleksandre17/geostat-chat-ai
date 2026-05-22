# Legacy entry — forwards to ops/cli (canonical)
$Root = Split-Path $PSScriptRoot -Parent
$forward = if ($args.Count -gt 0) { @($args) } else { @('help') }
& (Join-Path $Root "ops\cli\geostat.ps1") @forward
exit $LASTEXITCODE
