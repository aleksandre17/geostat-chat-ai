#Requires -Version 5.1
# Derived catalog cutover smoke — chat-api with GEOSTAT_CHAT_CATALOG_SOURCE=derived (manifest: ci.chatDerivedCatalogSmoke)
param(
    [string]$ChatUrl = $env:CHAT_BASE_URL,
    [string]$IngestionUrl = $env:INGESTION_URL,
    [string]$Corpus = $env:EVAL_CORPUS,
    [string]$Message = 'inflation',
    [switch]$SkipReadiness
)

$ErrorActionPreference = 'Stop'

if (-not $ChatUrl) { $ChatUrl = 'http://127.0.0.1:8090' }
if (-not $IngestionUrl) { $IngestionUrl = 'http://127.0.0.1:8093' }
if (-not $Corpus) { $Corpus = 'geostat-portal' }

Write-Host "== Derived catalog smoke ==" -ForegroundColor Cyan
Write-Host "  chat=$ChatUrl ingestion=$IngestionUrl corpus=$Corpus"
Write-Host "  Prereq: chat-api GEOSTAT_CHAT_CATALOG_SOURCE=derived + GEOSTAT_CHAT_CATALOG_JDBC_URL"
Write-Host "  Prereq: enrichment ON, MVs refreshed (rag-derivation-cutover-prep.ps1)"

foreach ($pair in @(
        @{ Label = 'chat-api'; Url = "$ChatUrl/health"; Expect = 'UP' },
        @{ Label = 'ingestion'; Url = "$IngestionUrl/actuator/health"; Expect = 'UP' }
    )) {
    $resp = Invoke-RestMethod -Uri $pair.Url -TimeoutSec 15
    $json = $resp | ConvertTo-Json -Compress
    if ($json -notmatch $pair.Expect) {
        throw "$($pair.Label) not healthy at $($pair.Url)"
    }
    Write-Host "[OK] $($pair.Label) health" -ForegroundColor Green
}

$catalogStatus = Invoke-RestMethod -Uri "$ChatUrl/api/v1/chat/catalog/status" -TimeoutSec 15
Write-Host "Catalog source=$($catalogStatus.source) derived=$($catalogStatus.derived) reader=$($catalogStatus.derivedReaderActive)"
if (-not $catalogStatus.derived) {
    throw "Expected GEOSTAT_CHAT_CATALOG_SOURCE=derived (got $($catalogStatus.source))"
}
if (-not $catalogStatus.derivedReaderActive) {
    throw 'Derived catalog reader inactive - check GEOSTAT_CHAT_CATALOG_JDBC_URL and Flyway V9-V15'
}
Write-Host '[OK] catalog status' -ForegroundColor Green

if (-not $SkipReadiness) {
    $report = Invoke-RestMethod -Uri "$IngestionUrl/api/v1/ingestion/corpora/$Corpus/derivation-readiness" -TimeoutSec 30
    foreach ($check in $report.checks) {
        $mark = if ($check.passed) { '[OK]' } else { '[WARN]' }
        Write-Host "$mark $($check.id): $($check.detail)"
    }
    if (-not $report.readyForEvalGate) {
        Write-Warning 'Derivation not fully ready - catalog links may be empty. Run rag-derivation-cutover-prep.ps1'
    }
}

$chatBody = (@{ message = $Message; sessionId = 'derived-catalog-smoke'; locale = 'en' } | ConvertTo-Json -Compress)
$chat = Invoke-RestMethod -Method Post -Uri "$ChatUrl/api/chat" `
    -ContentType 'application/json; charset=utf-8' `
    -Body ([System.Text.Encoding]::UTF8.GetBytes($chatBody)) `
    -TimeoutSec 90

if (-not $chat.intro) { throw 'Chat returned empty intro' }
if (-not $chat.items -or $chat.items.Count -lt 1) {
    throw 'Chat returned no items - check GEOSTAT_CHAT_CATALOG_SOURCE=derived, JDBC URL, MV rows'
}

$catalogItems = @($chat.items | Where-Object {
        $_.link -and ($_.link.sourceType -eq 'catalog' -or $_.link.type -in @('portal', 'statistics', 'news', 'metadata'))
    })
Write-Host "Chat OK: $($chat.items.Count) items, catalog-like=$($catalogItems.Count)"
Write-Host "  intro: $($chat.intro.Substring(0, [Math]::Min(120, $chat.intro.Length)))..."

if ($catalogItems.Count -lt 1) {
    throw 'Expected at least one catalog link card (derived mv_portal_link / mv_specific_link)'
}

Write-Host "Derived catalog smoke PASSED" -ForegroundColor Green
exit 0
