# Thin delegate — driver lives in geostat-kit (kit-upstream rule)
$KitRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..\..\kits\geostat-kit")).Path
& (Join-Path $KitRoot "toolkit\infra\Invoke-Infra.ps1") @args
exit $LASTEXITCODE
