#Requires -Version 5.1
# Broad KA+EN chat smoke (beyond golden retrieval eval)
param(
    [string]$ChatBase = $env:CHAT_API_URL,
    [string]$QueryFile = (Join-Path $PSScriptRoot 'rag-chat-broad-queries.json'),
    [double]$MinPassRate = 0.75,
    [switch]$Strict
)

$ErrorActionPreference = 'Stop'
if (-not $ChatBase) { $ChatBase = 'http://127.0.0.1:8090' }

$queries = Get-Content $QueryFile -Raw -Encoding UTF8 | ConvertFrom-Json

$passed = 0
foreach ($q in $queries) {
    $body = @{
        message   = $q.text
        sessionId = [guid]::NewGuid().ToString()
        locale    = $q.locale
    } | ConvertTo-Json
    try {
        $resp = Invoke-RestMethod -Method Post -Uri "$ChatBase/api/chat" `
            -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes($body)) -TimeoutSec 90
        $intro = $resp.intro
        $items = @($resp.items)
        $ok = ($intro -and $intro.Length -gt 20) -and ($items.Count -gt 0)
        if ($ok) {
            Write-Host "[OK] $($q.locale): $($q.text) -> $($items.Count) items"
            $passed++
        } else {
            Write-Warning "[FAIL] $($q.locale): $($q.text) -> intro=$($intro.Length) items=$($items.Count)"
        }
    } catch {
        Write-Warning "[FAIL] $($q.locale): $($q.text) -> $($_.Exception.Message)"
    }
}

$rate = $passed / [Math]::Max(1, $queries.Count)
Write-Host "Chat broad smoke: $passed / $($queries.Count) = $([math]::Round(100 * $rate, 1))% (min=$([math]::Round(100 * $MinPassRate, 0))%)"
if ($Strict -and $rate -lt $MinPassRate) { exit 1 }
