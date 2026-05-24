# Legacy entry — forwards to ops/cli (canonical)
# CMD.exe: use tools\geostat.cmd (not geostat.ps1 — opens Notepad)
param(
    [Parameter(Position = 0)]
    [string]$Command = "help",
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$CliRest = @()
)
$Root = Split-Path $PSScriptRoot -Parent
& (Join-Path $Root "ops\cli\geostat.ps1") -Command $Command -CliRest $CliRest
exit $LASTEXITCODE
