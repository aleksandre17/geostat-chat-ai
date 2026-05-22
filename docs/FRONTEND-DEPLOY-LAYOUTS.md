# Frontend deploy layouts (implemented)

Backend equivalent: [BACKEND-DEPLOY-LAYOUTS.md](./BACKEND-DEPLOY-LAYOUTS.md) (`runtime/` + `workspace/`).

Simulation: `.\tools\geostat.ps1 layout --frontend`  
Full report (all scenarios, inner folders): [FRONTEND-LAYOUT-SIMULATION-FULL.md](./FRONTEND-LAYOUT-SIMULATION-FULL.md)  
Regenerate: `.\tools\geostat.ps1 layout --frontend -Markdown -OutFile docs/FRONTEND-LAYOUT-SIMULATION-FULL.md`

## Structured paths (`DEPLOY_LAYOUT=structured`)

Base: `ops/config/frontend/.env.deploy` → `DEPLOY_PATH=/home/.../geostat/frontend`

| Mode | Path |
|------|------|
| **dist / sync** | `{DEPLOY_PATH}/static/{container}/` |
| **remote dev** | `{DEPLOY_PATH}/compose/dev/{container}/` |
| **remote prod** | `{DEPLOY_PATH}/compose/prod/{container}/` |

Example: `/home/administrator/geostat/frontend/static/geostat-chat-ai-app/`

Legacy flat layout: set `DEPLOY_LAYOUT=flat` (uses `{DEPLOY_PATH}/{container}/`).

## Fixes by scenario

| ID | Fix |
|----|-----|
| A1-A4 | Unchanged; local = repo + compose |
| A5 local | Docker port `HOST_PORT:80` (was wrongly HOST:HOST) |
| B1 dist | `-Environment dev\|prod`; `npm run build:dev` or prod; uploads `.env.runtime.json`, `.geostat-deploy.json` |
| B2 sync | Also uploads `nginx.conf`; auto `nginx -s reload` |
| C1/C2 remote | Separate dev/prod directories; `.env.dev`/`.env.prod` on server; archives under `{SERVER_BASE}/deploy-staging/` |

## Secrets (`ops/config/frontend/.env.deploy`)

```env
DEPLOY_HOST_PORT=5177
DEPLOY_PATH=/home/administrator/geostat/frontend
DEPLOY_LAYOUT=structured
DEPLOY_PATH_MODE=base
```

`DEPLOY_PATH_MODE=full` — use path as-is (no `/static/...` suffix).

## Remote dev (Windows → Linux, no dist build)

See **[DEV-REMOTE.md](./DEV-REMOTE.md)**.

```powershell
.\tools\geostat.ps1 fe dev bootstrap -Environment dev
.\tools\geostat.ps1 fe dev watch
```

## Commands (static / prod-like)

```powershell
# Prod UI (typical)
.\tools\geostat.ps1 fe deploy dist -Environment prod

# Dev UI build to server (staging)
.\tools\geostat.ps1 fe deploy dist -Environment dev

# Quick patch
.\tools\geostat.ps1 fe deploy sync -Environment prod

# Auto static UI (npm build each save) — see docs/FE-WATCH.md
.\tools\geostat.ps1 fe deploy watch -Environment dev
# First time: .\tools\geostat.ps1 fe deploy dist -Environment dev

# Auto remote dev (rsync only): .\tools\geostat.ps1 fe dev watch

# Staging with compose on server
.\tools\geostat.ps1 fe deploy remote -Environment dev

.\tools\geostat.ps1 fe manage reload
.\tools\geostat.ps1 fe manage config
```

## Migration from old flat deploy

Old files may live at `{DEPLOY_PATH}/{container}/`. New deploys use `static/` or `compose/`. After first `dist` deploy, use only the new path; optionally remove the old directory on the server.

## Package

Logic: `kits/geostat-kit/toolkit/powershell/Deploy-Path.ps1`  
Driver: `kits/geostat-kit/drivers/node-vite/ps1/deploy.ps1`
