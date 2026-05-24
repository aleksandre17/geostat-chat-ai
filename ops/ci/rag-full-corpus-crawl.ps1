#Requires -Version 5.1
# OPS-02 — full-site dual-locale crawl (production policy, no dev cap)
param(
    [string]$Corpus = 'geostat-portal',
    [switch]$SkipReindex,
    [switch]$Strict,
    [int]$MaxWaitHours = 12
)

$ErrorActionPreference = 'Stop'
$baseIngestion = if ($env:INGESTION_URL) { $env:INGESTION_URL } else { 'http://127.0.0.1:8093' }

Write-Host '== OPS-02 full corpus crawl (full-site policy) ==' -ForegroundColor Cyan
python "$PSScriptRoot/rag-full-corpus-policy.py"

function Get-CrawlActive {
    $json = py -3 "$PSScriptRoot/crawl-status.py" | ConvertFrom-Json
    $running = if ($json.runs.running) { [int]$json.runs.running } else { 0 }
    $pending = if ($json.runs.pending) { [int]$json.runs.pending } else { 0 }
    return @{ Active = ($running + $pending); Json = $json }
}

function Start-CrawlIfIdle($seedUrl, [bool]$FullRecrawl) {
    $state = Get-CrawlActive
    if ($state.Active -gt 0) {
        Write-Host "Crawl already active ($($state.Active) runs) — attaching to existing job"
        return
    }
    $body = @{ corpusName = $Corpus; seedUrl = $seedUrl; fullRecrawl = $FullRecrawl } | ConvertTo-Json
    try {
        $job = Invoke-RestMethod -Method Post -Uri "$baseIngestion/api/v1/ingestion/jobs" `
            -ContentType 'application/json' -Body $body
        Write-Host "Crawl seed $seedUrl -> job $($job.jobId)"
    } catch {
        if ($_.Exception.Response.StatusCode.value__ -eq 409) {
            Write-Host 'Crawl already active (409) — attaching'
        } else { throw }
    }
}

$corpusInfo = Invoke-RestMethod -Uri "$baseIngestion/api/v1/ingestion/corpora"
$portal = $corpusInfo | Where-Object { $_.name -eq $Corpus } | Select-Object -First 1
$seeds = if ($portal -and $portal.seedUrls -and $portal.seedUrls.Count -ge 1) {
    @($portal.seedUrls)
} else {
    @('https://www.geostat.ge/ka', 'https://www.geostat.ge/en')
}

# Single full-recrawl from primary seed; /en discovered via link discovery (dual-locale)
Start-CrawlIfIdle $seeds[0] $true
if ($seeds.Count -gt 1 -and (Get-CrawlActive).Active -eq 0) {
    Start-CrawlIfIdle $seeds[1] $false
}

Write-Host 'Waiting for full-site crawl + autoContinue to finish...' -ForegroundColor Cyan
& "$PSScriptRoot/wait-corpus-crawl-idle.ps1" -Corpus $Corpus -MaxHours $MaxWaitHours

if (-not $SkipReindex) {
    Write-Host 'Reindex parsed documents...'
    $reindex = Invoke-RestMethod -Method Post -Uri "$baseIngestion/api/v1/ingestion/corpora/$Corpus/reindex"
    Write-Host "  documents=$($reindex.documentsQueued)"
}

Write-Host 'Full corpus crawl completed.' -ForegroundColor Green

if ($Strict) {
    & "$PSScriptRoot/rag-eval-smoke.ps1" -Strict -MinPassRate 0.85
    if ($LASTEXITCODE -ne 0) { throw 'RAG eval failed after full crawl' }
    & "$PSScriptRoot/rag-chat-broad-smoke.ps1" -Strict -MinPassRate 0.75
    if ($LASTEXITCODE -ne 0) { throw 'Chat broad smoke failed after full crawl' }
}
