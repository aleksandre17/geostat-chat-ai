# Hybrid 4 — JAR boot (Windows-safe: avoids Gradle includeBuild JAR lock)
# Usage: .\ops\ci\hybrid-jar-boot.ps1 [-Build] [-Services ing,ret,be] [-ShowConsole]
# Services run in background (no popup windows). Logs: ops/ci/logs/*.log
param(
    [switch]$Build,
    [switch]$ShowConsole,
    [ValidateSet('ing', 'ret', 'be', 'all')]
    [string[]]$Services = @('all')
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$Gw = Join-Path $Root 'apps\backend\gradlew.bat'
$Launch = Join-Path $PSScriptRoot 'hybrid-jar-launch.ps1'
$LogDir = Join-Path $PSScriptRoot 'logs'
New-Item -ItemType Directory -Force -Path $LogDir | Out-Null

function Test-PortOpen([int]$Port) {
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $iar = $client.BeginConnect('127.0.0.1', $Port, $null, $null)
        $ok = $iar.AsyncWaitHandle.WaitOne(300)
        if (-not $ok) { return $false }
        $client.EndConnect($iar) | Out-Null
        return $true
    } catch {
        return $false
    } finally {
        $client.Dispose()
    }
}

function Wait-Port([int]$Port, [int]$TimeoutSec = 90, [string]$LogFile = $null) {
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        if (Test-PortOpen $Port) { return $true }
        Start-Sleep -Milliseconds 500
    }
    if ($LogFile -and (Test-Path -LiteralPath $LogFile)) {
        Write-Host "[hybrid-jar] last log lines ($LogFile):" -ForegroundColor Yellow
        Get-Content -LiteralPath $LogFile -Tail 15 -ErrorAction SilentlyContinue | ForEach-Object {
            Write-Host "  $_" -ForegroundColor DarkGray
        }
    }
    return $false
}

function Test-PortInUse([int]$Port) {
    return (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1) -ne $null
}

function Start-JarService(
    [string]$Label,
    [string[]]$EnvFiles,
    [string]$WorkDir,
    [string]$Jar,
    [string]$SpringProfiles,
    [switch]$ChatApiDbExclude) {

    $logName = ($Label -replace '[:\\]', '-') + '.log'
    $logFile = Join-Path $LogDir $logName

    $psArgs = @(
        '-NoProfile',
        '-ExecutionPolicy', 'Bypass',
        '-File', $Launch,
        '-WorkDir', $WorkDir,
        '-Jar', $Jar,
        '-Label', $Label,
        '-LogFile', $logFile
    )
    if ($EnvFiles -and $EnvFiles.Count -gt 0) {
        $psArgs += '-EnvFiles'
        $psArgs += ($EnvFiles -join ',')
    }
    if ($SpringProfiles) {
        $psArgs += @('-SpringProfiles', $SpringProfiles)
    }
    if ($ChatApiDbExclude) {
        $psArgs += '-ChatApiDbExclude'
    }

    $startParams = @{
        FilePath     = 'powershell.exe'
        ArgumentList = $psArgs
        PassThru     = $true
    }
    if (-not $ShowConsole) {
        $startParams.WindowStyle = 'Hidden'
    }

    $proc = Start-Process @startParams
    Write-Host "[hybrid-jar] started $Label (pid $($proc.Id), log $logName)" -ForegroundColor Cyan
    return @{ Process = $proc; LogFile = $logFile }
}

$want = if ($Services -contains 'all') { @('ing', 'ret', 'be') } else { $Services }

if ($Build -or -not (Test-Path (Join-Path $Root 'apps\ingestion-service\build\libs\ingestion-service-0.1.0-SNAPSHOT.jar'))) {
    Write-Host '[hybrid-jar] prebuild...' -ForegroundColor Cyan
    Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force
    Start-Sleep -Seconds 2
    Set-Location (Join-Path $Root 'apps\ingestion-service')
    & $Gw build -x test --no-daemon
    Set-Location (Join-Path $Root 'apps\retrieval-service')
    & $Gw build -x test --no-daemon
    Set-Location (Join-Path $Root 'apps\backend')
    & $Gw build -x test --no-daemon
}

$ingJar = Join-Path $Root 'apps\ingestion-service\build\libs\ingestion-service-0.1.0-SNAPSHOT.jar'
$retJar = Join-Path $Root 'apps\retrieval-service\build\libs\retrieval-service-0.1.0-SNAPSHOT.jar'
$beJar = Join-Path $Root 'apps\backend\build\libs\geostat-chat-ai-2.0.0-SNAPSHOT.jar'

if ($want -contains 'ing') {
    if (Test-PortInUse 8093) {
        Write-Host '[hybrid-jar] ingestion already on 8093 — skip' -ForegroundColor DarkGray
    } else {
        $ing = Start-JarService -Label 'ingestion:8093' `
            -EnvFiles @(Join-Path $Root 'ops\config\ingestion\.env.dev') `
            -WorkDir (Join-Path $Root 'apps\ingestion-service') `
            -Jar $ingJar
        if (-not (Wait-Port 8093 120 $ing.LogFile)) { throw 'ingestion did not start on 8093' }
    }
}

if ($want -contains 'ret') {
    if (Test-PortInUse 8092) {
        Write-Host '[hybrid-jar] retrieval already on 8092 — skip' -ForegroundColor DarkGray
    } else {
        $ret = Start-JarService -Label 'retrieval:8092' `
            -EnvFiles @(Join-Path $Root 'ops\config\retrieval\.env.dev') `
            -WorkDir (Join-Path $Root 'apps\retrieval-service') `
            -Jar $retJar
        if (-not (Wait-Port 8092 120 $ret.LogFile)) { throw 'retrieval did not start on 8092' }
    }
}

if ($want -contains 'be') {
    if (Test-PortInUse 8090) {
        Write-Host '[hybrid-jar] chat-api already on 8090 — skip' -ForegroundColor DarkGray
    } else {
        $be = Start-JarService -Label 'chat-api:8090' `
            -EnvFiles @(
                (Join-Path $Root 'ops\config\backend\.env.prod'),
                (Join-Path $Root 'ops\config\backend\.env.dev')
            ) `
            -WorkDir (Join-Path $Root 'apps\backend') `
            -Jar $beJar `
            -SpringProfiles 'local' `
            -ChatApiDbExclude
        if (-not (Wait-Port 8090 90 $be.LogFile)) { throw 'chat-api did not start on 8090' }
    }
}

Write-Host '[hybrid-jar] stack ready: 8093 ingestion, 8092 retrieval, 8090 chat-api' -ForegroundColor Green
Write-Host "[hybrid-jar] logs: $LogDir" -ForegroundColor DarkGray
Write-Host '  Get-Content ops\ci\logs\ingestion-8093.log -Tail 30 -Wait' -ForegroundColor DarkGray
