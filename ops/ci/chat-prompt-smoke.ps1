# B-27 — chat prompt contract smoke (schema fields, no raw JSON leak on stream intro path)
param(
    [string]$ChatUrl = "http://127.0.0.1:8090",
    [string]$Message = "რა არის ინფლაცია"
)

$ErrorActionPreference = "Stop"

function Test-Port($port) {
    (Test-NetConnection -ComputerName 127.0.0.1 -Port $port -WarningAction SilentlyContinue).TcpTestSucceeded
}

Write-Host "== Chat prompt contract smoke (B-27) ==" -ForegroundColor Cyan

if (-not (Test-Port 8090)) { throw "Port 8090 not reachable (start chat-api?)" }

$encodedMessage = [uri]::EscapeDataString($Message)
$chat = Invoke-RestMethod -Method Get -Uri "$ChatUrl/api/chat?message=$encodedMessage&sessionId=b27-smoke" `
    -TimeoutSec 120

if (-not $chat.intro) { throw "Missing intro" }
if ($chat.intro -match '^\s*\{') { throw "Intro looks like raw JSON: $($chat.intro)" }
if (-not $chat.PSObject.Properties['responseType']) { throw "Missing responseType (R-01)" }
if (-not $chat.PSObject.Properties['grounded']) { throw "Missing grounded flag" }

Write-Host "Chat OK: responseType=$($chat.responseType) grounded=$($chat.grounded) items=$($chat.items.Count)"
Write-Host "  intro: $($chat.intro.Substring(0, [Math]::Min(120, $chat.intro.Length)))"
Write-Host "Chat prompt contract smoke PASSED" -ForegroundColor Green
