# Contributing

## Layout

- `apps/frontend/` — Vite/React UI
- `apps/backend/` — Spring API
- `ops/config/` — env only (never commit real values); map: [CONFIG.md](CONFIG.md)
- `ops/compose/` — compose catalog (project)
- `kits/geostat-kit/` — reusable ops package
- `ops/ci/` — project integration tests only
- `tools/` — `geostat.ps1` → package CLI

## New repo / ops layout

Use **`.\tools\geostat.ps1 init`** — see [GEOSTAT-INIT.md](GEOSTAT-INIT.md). Do not commit generated secrets; only `*.example` in git.

## Before PR

1. Regenerate compose if you changed the catalog or `kits/geostat-kit/compose/build.py`:
   ```powershell
   .\tools\geostat.ps1 compose-gen
   ```
2. Do not hand-edit files marked `GENERATED`.
3. Copy secrets from `*.example` locally; never commit `deploy.env` or `.env.dev` with secrets.
4. Config layout: [CONFIG.md](CONFIG.md) — single source in `ops/config/`; no copies under `apps/backend/`.
5. Shared server: [GEOSTAT-KIT-SETUP.md §15](GEOSTAT-KIT-SETUP.md#15-სერვერის-წესები--რა-შეიძლება-რა-არ-უნდა).

## Commands

```powershell
.\tools\geostat.ps1 stack up -d --build
.\tools\geostat.ps1 fe deploy dist
.\tools\geostat.ps1 be deploy api --prod
```

See [ARCHITECTURE.md](ARCHITECTURE.md).
