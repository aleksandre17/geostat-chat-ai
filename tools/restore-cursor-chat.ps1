# Run once with Cursor fully closed if restored chat does not respond.
$ErrorActionPreference = 'Stop'
$fix = Join-Path $PSScriptRoot 'cursor-chat-fix.py'
if (-not (Test-Path $fix)) {
  Write-Error "Missing $fix"
}
$cursor = Get-Process -Name 'Cursor' -ErrorAction SilentlyContinue
if ($cursor) {
  Write-Host 'Closing Cursor...'
  $cursor | Stop-Process -Force
  Start-Sleep -Seconds 2
}
python $fix
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$ws = 'C:\Users\Test-User\CursorProjects\geostat-chat-ai\geostat-chat-ai.code-workspace'
$cursorExe = Join-Path ${env:LOCALAPPDATA} 'Programs\cursor\Cursor.exe'
if (Test-Path $cursorExe) {
  Start-Process $cursorExe -ArgumentList "`"$ws`""
  Write-Host "Opened $ws"
} else {
  Write-Host 'Done. Open geostat-chat-ai.code-workspace in Cursor manually.'
}
