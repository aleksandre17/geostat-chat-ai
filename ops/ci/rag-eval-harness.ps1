#Requires -Version 5.1
# RAG-U12 — full golden-set eval harness (manifest: ci.ragEvalHarness)
param(
    [string]$RetrievalBase = $env:RETRIEVAL_BASE_URL,
    [string]$IngestionBase = $env:INGESTION_URL,
    [string]$ChatBase = $env:CHAT_BASE_URL,
    [string]$Corpus = $env:EVAL_CORPUS,
    [double]$MaxRegression = 0.05,
    [int]$MinQueries = 150,
    [switch]$WriteBaseline,
    [switch]$DryRun,
    [switch]$SkipMinQueries,
    [string]$ReferenceBaseline = '',
    [double]$ReferenceMaxRegression = 0.0
)

$ErrorActionPreference = 'Stop'

if (-not $RetrievalBase) { $RetrievalBase = 'http://127.0.0.1:8092' }
if (-not $IngestionBase) { $IngestionBase = 'http://127.0.0.1:8093' }
if (-not $ChatBase) { $ChatBase = 'http://127.0.0.1:8090' }
if (-not $Corpus) { $Corpus = 'geostat-portal' }

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..')
Set-Location $repoRoot

$env:RETRIEVAL_BASE_URL = $RetrievalBase
$env:INGESTION_URL = $IngestionBase
$env:CHAT_BASE_URL = $ChatBase
$env:EVAL_CORPUS = $Corpus

$pyArgs = @(
    'ops/ci/run-eval.py',
    '--corpus', $Corpus,
    '--retrieval-base', $RetrievalBase,
    '--ingestion-base', $IngestionBase,
    '--chat-base', $ChatBase,
    '--baseline', 'ops/eval/baseline.json',
    '--max-regression', $MaxRegression
)

if ($WriteBaseline) { $pyArgs += '--write-baseline' }
if ($DryRun) { $pyArgs += '--dry-run' }
if ($ReferenceBaseline) {
    $pyArgs += @('--reference-baseline', $ReferenceBaseline, '--reference-max-regression', $ReferenceMaxRegression)
}
if (-not $SkipMinQueries -and $MinQueries -gt 0) {
    $pyArgs += @('--min-queries', $MinQueries)
}

Write-Host "== RAG-U12 eval harness ==" -ForegroundColor Cyan
Write-Host "  corpus=$Corpus retrieval=$RetrievalBase ingestion=$IngestionBase"

python @pyArgs
exit $LASTEXITCODE
