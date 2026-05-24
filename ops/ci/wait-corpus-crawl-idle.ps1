#Requires -Version 5.1
# Wait until corpus has no active crawl runs and frontier queue is empty (OPS-02 completion)
param(
    [string]$Corpus = 'geostat-portal',
    [int]$PollSec = 30,
    [int]$MaxHours = 12,
    [string]$IngestionBase = 'http://127.0.0.1:8093'
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$StatusPy = Join-Path $PSScriptRoot 'crawl-status.py'
$deadline = (Get-Date).AddHours($MaxHours)
$lastDocs = -1

Write-Host "== Waiting for corpus '$Corpus' crawl idle (max ${MaxHours}h) ==" -ForegroundColor Cyan

while ((Get-Date) -lt $deadline) {
    $json = py -3 $StatusPy | ConvertFrom-Json
    $docs = [int]$json.docs
    $queued = if ($json.frontier.queued) { [int]$json.frontier.queued } else { 0 }
    $running = if ($json.runs.running) { [int]$json.runs.running } else { 0 }
    $pending = if ($json.runs.pending) { [int]$json.runs.pending } else { 0 }
    $active = $running + $pending

    $delta = if ($lastDocs -ge 0) { $docs - $lastDocs } else { 0 }
    $lastDocs = $docs
    Write-Host ("  {0:HH:mm:ss} docs={1} (+{2}) frontier_queued={3} active_runs={4}" -f (Get-Date), $docs, $delta, $queued, $active)

    if ($active -eq 0 -and $queued -eq 0) {
        Write-Host 'Crawl idle - corpus crawl complete.' -ForegroundColor Green
        exit 0
    }

    Start-Sleep -Seconds $PollSec
}

throw ('Timed out after {0}h waiting for crawl idle' -f $MaxHours)
