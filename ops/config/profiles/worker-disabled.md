# Profile: embedded backend worker disabled

Use when the **worker role** is a separate manifest module (e.g. `ingestion`) — not `apps/backend/worker`.

**This repo:** already `"worker": false` in `ops/compose/catalog.json`.

1. Edit `ops/compose/catalog.json`:

```json
"features": {
  "worker": false
}
```

2. Regenerate compose + sync `ops.modules`:

```powershell
.\tools\geostat.ps1 compose-gen
```

3. Remove worker line from deploy targets if you deploy `all` — only API (+ optional app) remain in generated `docker-compose.*.yml`.

4. Optional: comment `WORKER_PORT` / `API_INTERNAL_URL` worker references in `ops/config/backend/.env.*` if unused.

Re-enable: set `"worker": true` and run `compose-gen` again.
