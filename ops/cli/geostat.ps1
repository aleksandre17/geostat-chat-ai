# Project CLI entry — forwards all arguments to geostat-kit (native pass-through)
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$forward = if ($args.Count -gt 0) { @($args) } else { @('help') }
& (Join-Path $Root "kits\geostat-kit\cli\geostat.ps1") @forward
exit $LASTEXITCODE
