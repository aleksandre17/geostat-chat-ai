#Requires -Version 5.1
# Spawned by hybrid-jar-boot.ps1 — loads env and runs java -jar (no inline -Command quoting)
param(
    [Parameter(Mandatory = $true)]
    [string]$WorkDir,
    [Parameter(Mandatory = $true)]
    [string]$Jar,
    [string[]]$EnvFiles = @(),
    [string]$Label = 'service',
    [string]$SpringProfiles,
    [switch]$ChatApiDbExclude,
    [string]$LogFile
)

$ErrorActionPreference = 'Stop'

function Import-EnvFile([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) {
        Write-Warning "Env file not found: $Path"
        return
    }
    Get-Content -LiteralPath $Path | ForEach-Object {
        if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$' -and $_ -notmatch '^\s*#') {
            $v = $Matches[2].Trim().Trim('"')
            if ($v.StartsWith("'") -and $v.EndsWith("'")) {
                $v = $v.Substring(1, $v.Length - 2)
            }
            if (-not [string]::IsNullOrWhiteSpace($v)) {
                Set-Item -Path "env:$($Matches[1])" -Value $v
            }
        }
    }
}

foreach ($file in $EnvFiles) {
    if ($file) { Import-EnvFile $file }
}

if ($SpringProfiles) {
    $env:SPRING_PROFILES_ACTIVE = $SpringProfiles
}

if ($ChatApiDbExclude) {
    $env:SPRING_FLYWAY_ENABLED = 'false'
    $env:SPRING_AUTOCONFIGURE_EXCLUDE =
        'org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration'
}

if (-not (Test-Path -LiteralPath $Jar)) {
    throw "JAR not found: $Jar"
}

Set-Location -LiteralPath $WorkDir
$banner = "[jar] $Label starting -> $Jar"
if ($LogFile) {
    $logDir = Split-Path -Parent $LogFile
    if ($logDir) { New-Item -ItemType Directory -Force -Path $logDir | Out-Null }
    $stamp = Get-Date -Format o
    "[jar] $Label starting -> $Jar ($stamp)" | Out-File -LiteralPath $LogFile -Append -Encoding utf8
    $errLog = $LogFile -replace '\.log$', '.err.log'
    Start-Process -FilePath 'java' -ArgumentList @('-jar', $Jar) -WorkingDirectory $WorkDir `
        -RedirectStandardOutput $LogFile -RedirectStandardError $errLog `
        -WindowStyle Hidden | Out-Null
    exit 0
}

Write-Host $banner -ForegroundColor Cyan
& java -jar $Jar
exit $LASTEXITCODE
