# Remote dev — Windows edit, Linux Docker (SPA)

For **Vite, Angular, Nx** and similar SPAs using driver `node-vite` and compose **development** target.

## Two loops (do not mix)

| Goal | Command |
|------|---------|
| **Daily dev on Linux** (no `npm build` per save) | `fe dev bootstrap` then `fe dev watch` |
| **Prod-like static on server** | `fe deploy dist` / `fe deploy sync` / **`fe deploy watch`** (builds `dist/`) |

## Prerequisites

- `ops/config/frontend/.env.deploy`: `DEPLOY_SERVER`, `DEPLOY_PATH`, `DEPLOY_LAYOUT=structured`
- `ops/config/frontend/.env.dev`: API URL, ports (e.g. `VITE_API_URL` or Angular env vars)
- **rsync**: Git for Windows (`usr\bin\rsync.exe`) or WSL
- `apps/frontend/docker-compose.override.yml`: dev target, `.:/app` volume, `develop.watch` (generated via `geostat compose-gen`)

## Commands

```powershell
# Once: upload source + env + compose up --build on Linux
.\tools\geostat.ps1 fe dev bootstrap -Environment dev

# Manual incremental sync (no container restart)
.\tools\geostat.ps1 fe dev sync

# Background: save on Windows -> rsync -> Vite/Angular HMR in container
.\tools\geostat.ps1 fe dev watch -Environment dev

# After Dockerfile / package.json change
.\tools\geostat.ps1 fe dev bootstrap -Environment dev
# or
.\tools\geostat.ps1 fe dev restart
```

Server path: `{DEPLOY_PATH}/compose/dev/{container}/` (structured layout).

## Angular / Nx

Same driver (`node-vite` in `geostat.ops.json`) if the module uses:

- `docker-compose.yml` + dev override with source volume
- Standard dirs: `src/`, `angular.json`, `projects/`, etc. (auto-watched)

Override paths in `frontend/ops.config.ps1` — see `kits/geostat-kit/toolkit/templates/ops.config.ps1.example`.

## Package

- `kits/geostat-kit/toolkit/powershell/Dev-Remote.ps1`
- `kits/geostat-kit/drivers/node-vite/ps1/dev.ps1`

## `src/Dockerfile` step-by-step

What happens to `frontend/src/Dockerfile` during bootstrap and watch (stages, volumes, when rebuild is required):

**[kits/geostat-kit/docs/REMOTE-DEV-DOCKERFILE-FLOW.md](../kits/geostat-kit/docs/REMOTE-DEV-DOCKERFILE-FLOW.md)**

See also: [GOLDEN-PATHS.md](../kits/geostat-kit/docs/GOLDEN-PATHS.md), [FE-WATCH.md](./FE-WATCH.md), [ARCHITECTURE.md](./ARCHITECTURE.md#golden-paths-only-team-policy).
