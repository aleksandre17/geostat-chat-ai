# OPS-01 — GEMINI_API_KEY rotation

Owner action when a key may have been exposed (chat, logs, commit).

## Steps

1. [Google AI Studio](https://aistudio.google.com/apikey) — revoke old key, create new key.
2. Update secrets (never commit):
   - `ops/config/backend/.env.prod` — `GEMINI_API_KEY`
   - Same key is used by retrieval + ingestion when `EMBEDDING_PROVIDER=gemini`.
3. Redeploy affected modules:
   ```powershell
   .\tools\geostat.ps1 stack-deploy --prod
   ```
   Or at minimum: chat-api, retrieval, ingestion containers.
4. Verify:
   ```powershell
   .\ops\ci\chat-prompt-smoke.ps1
   ```
5. Mark OPS-01 done in `docs/plan/BACKLOG.md` after rotation.

## Preflight (no secret output)

```powershell
$envFile = "ops/config/backend/.env.prod"
if (Select-String -Path $envFile -Pattern '^GEMINI_API_KEY=\S+' -Quiet) {
  Write-Host "GEMINI_API_KEY is set (rotate if exposed)"
} else {
  Write-Host "GEMINI_API_KEY missing"
}
```
