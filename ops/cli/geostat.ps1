# Project CLI entry — forwards all arguments to geostat-kit (native pass-through)
param(
    [Parameter(Position = 0)]
    [string]$Command = "help",
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$CliRest = @()
)
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
& (Join-Path $Root "kits\geostat-kit\cli\geostat.ps1") -Command $Command -CliRest $CliRest
exit $LASTEXITCODE
