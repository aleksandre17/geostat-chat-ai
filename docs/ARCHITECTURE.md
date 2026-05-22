# Architecture — geostat-chat-ai

## Root structure (3-plane model)

We organize the repo into three planes. **Today** paths are still flat at root (`apps/frontend/`, `ops/config/`, …); **target** consolidates ops under `ops/` and apps under `apps/`. Full plan: [ROOT-LAYOUT.md](ROOT-LAYOUT.md), [ADR 008](adr/008-root-layout-consolidation.md).

```text
        ┌─────────────┐
        │    docs/    │  guides, ADRs
        └─────────────┘
┌───────────────┐     ┌──────────────────────────────────────┐
│  apps/        │     │  ops/  (target)                       │
│  frontend     │     │  config/  compose/  kit/  cli/  ci/   │
│  backend      │     └──────────────────────────────────────┘
└───────────────┘              ▲
        ▲                      │ geostat.ops.json (paths)
        └──────────────────────┘
```

| Plane | Today | Target | Responsibility |
|-------|-------|--------|----------------|
| **Application** | `apps/frontend/`, `apps/backend/` | `apps/*` | UI, API, module Dockerfiles |
| **Operations** | `ops/config/`, `infra/`, `deploy/`, `kits/geostat-kit/`, `tools/`, `scripts/` | `ops/*` | Config, catalog, generated stack compose, kit, CLI |
| **Knowledge** | `docs/` | `docs/` | Documentation |

## Layers (detail)

| Layer | Path | Responsibility |
|-------|------|----------------|
| Apps | `apps/frontend/`, `apps/backend/` | UI (Vite/React), API (Spring) |
| **Ops package** | `kits/geostat-kit/` | Reusable env, deploy, compose engine, manage toolkit ([ADR 006](adr/006-geostat-kit-package.md)) |
| **Project contract** | `geostat.ops.json` | Paths to secrets, catalog, modules, adapters |
| Config | `ops/config/` | All runtime secrets (never in package) |
| Infra local | `ops/compose/stack/`, `docker-compose*.yml` | Generated + stack compose |
| Infra remote | `kits/geostat-kit/drivers/<type>` | Per-stack drivers (`java-boot`, `node-vite`, future `node-api`, …) → shared toolkit |
| Adapters | `ops/compose/`, `frontend/nginx.conf.template` | Project-only; nginx render in package `adapters/` |
| CLI | `tools/geostat.ps1` | Delegates to `kits/geostat-kit/cli/` |

## Environment model

| File | Scope |
|------|--------|
| `ops/config/<module>/.env.dev` | Local/docker development |
| `ops/config/<module>/.env.prod` | Production build/runtime |
| `ops/config/<module>/.env.deploy` | Module deploy paths/ports (frontend) |
| `ops/config/deploy.env` | Shared SSH: `DEPLOY_SERVER`, `DEPLOY_PROJECT` |

## Local vs server compose

**Local** compose files may reference `../../ops/config/...` — correct for monorepo paths.

**Server** (backend deploy) generates per-service compose via `kits/geostat-kit/toolkit/bash/gen_server_compose.py`:

- `env_file` → `./.env.dev` or `./.env.prod` (uploaded next to JAR)
- `../ops/config/...` volumes → `./google-credentials.json`, `./logs`, etc.
- `build:` removed; uses pre-built image + `app.jar`
- External networks (e.g. `geostat-net`) preserved

## Deploy flows

### Backend (`geostat be deploy`)

```
ops.modules registry → Gradle bootJar per module → scp → gen_server_compose.py
→ docker compose up in .../$DEPLOY_PROJECT/backend/<container_name>/
```

Multi-module: see [MULTI-MODULE.md](MULTI-MODULE.md). List modules: `tools/geostat be modules`.

Flags: `--dev` | `--prod`, `TARGET=backend|frontend`, service name or `all`.

### Frontend

**Structured server layout** (`DEPLOY_LAYOUT=structured` in `ops/config/frontend/.env.deploy`):

| Mode | Command | Server path |
|------|---------|-------------|
| Static prod/staging UI | `fe deploy dist` / `sync` | `{DEPLOY_PATH}/static/{service}/` (`dist/`, `nginx.conf`) |
| Static auto on save | `fe deploy watch` | same (npm build each save) |
| Remote dev (source) | `fe dev bootstrap` / `sync` / `watch` | `{DEPLOY_PATH}/compose/dev/{service}/` |
| Full tar on server | `fe deploy remote` | same compose paths (heavy; escape hatch) |
| Local only | `fe deploy local`, `fe compose up` | no SSH |

Parameter: `-Environment dev|prod` on deploy/dist/sync/watch/remote/dev.

Details: [FRONTEND-DEPLOY-LAYOUTS.md](FRONTEND-DEPLOY-LAYOUTS.md), simulation: [FRONTEND-LAYOUT-SIMULATION-FULL.md](FRONTEND-LAYOUT-SIMULATION-FULL.md).

#### Golden paths only (team policy)

Use **one** inner loop per goal. Do not mix paths on the same host without migration.

| Goal | Golden path | Avoid for daily work |
|------|-------------|----------------------|
| **Dev on Windows** (API on Linux OK) | `cd frontend && npm run dev` | `fe deploy watch` |
| **Dev directly on Linux** (same machine) | `npm run dev` or `geostat fe compose up -d` | `fe dev watch`, `fe deploy watch` |
| **Dev in Docker on laptop** | `geostat fe compose up -d` | `fe deploy remote` |
| **Windows edit → Linux UI (Vite/Angular in container)** | `fe dev bootstrap` then **`fe dev watch`** | `fe deploy watch`, `fe deploy remote` |
| **Prod / staging static UI on server** | `fe deploy dist` (first time), then **`fe deploy sync`** or **`fe deploy watch`** | `fe dev watch`, flat legacy path |
| **Full stack prod deploy** | `geostat stack-deploy --prod` | hand-run mixed modes |
| **Integration on laptop** | `geostat stack up -d --build` | per-module SSH trees |

**Watch commands (do not confuse):**

| Command | Meaning |
|---------|---------|
| `geostat fe deploy watch` | **Static:** `npm build` → `dist/` → nginx reload |
| `geostat fe dev watch` | **Remote dev:** rsync `src/` only, no build |
| `geostat fe watch` | Deprecated alias → `fe deploy watch` (CLI shows hint) |

See [FE-WATCH.md](FE-WATCH.md), [DEV-REMOTE.md](DEV-REMOTE.md).  
**ლოკალური vs Docker vs remote (სქემა + Run and Debug):** [kits/geostat-kit/docs/DEV-MODES.md](../kits/geostat-kit/docs/DEV-MODES.md).  
Package canonical copy: [kits/geostat-kit/docs/GOLDEN-PATHS.md](../kits/geostat-kit/docs/GOLDEN-PATHS.md).

**Deprecated / discouraged:**

| Item | Replacement |
|------|-------------|
| `DEPLOY_LAYOUT=flat` (`.../frontend/{service}/` only) | `structured` + `static/` or `compose/dev/` |
| `fe deploy remote` every save | `fe dev bootstrap` + `fe dev watch` |
| `fe deploy watch` for UI dev on Linux | `fe dev watch` |
| Top-level `fe watch` | `fe deploy watch` |

### Full stack (developer)

`geostat stack` → `kits/geostat-kit/toolkit/stack/` + `ops/compose/stack/` — both services, not SSH.

## Ops CLI contract

| Command | Backend (`geostat be manage`) | Frontend (`geostat fe manage`) |
|---------|----------------------|-------------------------|
| Lifecycle | stop, start, restart, status, rm, nuke, rebuild | status, logs, restart, stop, start, undeploy/rm, delete/nuke |
| Static dist | n/a | reload, config |
| Env | `--dev` / `--prod` | detects static vs compose on server |

See [kits/geostat-kit/contracts/MANAGE-CONTRACT.md](../kits/geostat-kit/contracts/MANAGE-CONTRACT.md). Backend manage uses `./.env.<env>` and `docker-compose.<env>.yml` on server (aligned with deploy).

## Unified CLI

Entry: `tools/geostat.ps1` → package `cli/geostat.ps1` (reads `geostat.ops.json`).

```powershell
.\tools\geostat.ps1 stack up -d --build           # local full stack (A4)
.\tools\geostat.ps1 stack-deploy --prod         # remote: backend all + frontend dist (E1)
.\tools\geostat.ps1 infra                         # server: docker network, python3-yaml
.\tools\geostat.ps1 compose-gen                   # package compose engine + catalog
.\tools\geostat.ps1 fe deploy dist -Environment prod
.\tools\geostat.ps1 fe dev bootstrap -Environment dev   # then: fe dev watch
.\tools\geostat.ps1 be deploy api --prod
.\tools\geostat.ps1 fe manage reload
```

## Multi-module backend

- Registry: `apps/backend/ops.modules`
- Embedded worker: `backend/worker/` only if `features.worker: true` in catalog (this repo: **false**; ingest = `ingestion` module)
- Guide: [MULTI-MODULE.md](MULTI-MODULE.md), ADR [003](adr/003-multi-module-network.md)

## Manage logs

Shared sources (`docker`, `app`, `errors`, `auth`, `db`, `files`): [kits/geostat-kit/contracts/README-MANAGE-LOGS.md](../kits/geostat-kit/contracts/README-MANAGE-LOGS.md).

## Environment (practice)

Legacy compose names, `deploy.env`, worker toggle, nginx CSP, embed URLs: [ENVIRONMENT.md](ENVIRONMENT.md). Profiles: [kits/geostat-kit/scaffold/profiles/](../kits/geostat-kit/scaffold/profiles/).

## Remaining (optional)

- [ ] Single git monorepo — `.\scripts\setup-root-git.ps1` + [MONOREPO.md](MONOREPO.md)
- [ ] Image registry — [ADR 004](adr/004-registry-and-iac.md)
- [ ] Terraform/Ansible — [ADR 004](adr/004-registry-and-iac.md)
- [ ] Central observability (metrics/log aggregation)

## Diagrams

Server layout simulation:

```powershell
.\tools\geostat.ps1 layout
```

See also: [ENV.md](ENV.md), [ENVIRONMENT.md](ENVIRONMENT.md), [COMPOSE.md](COMPOSE.md), [FE-WATCH.md](FE-WATCH.md), [DEV-REMOTE.md](DEV-REMOTE.md), [FRONTEND-DEPLOY-LAYOUTS.md](FRONTEND-DEPLOY-LAYOUTS.md), [kits/geostat-kit/README.md](../kits/geostat-kit/README.md), [kits/geostat-kit/docs/ADOPTION-LINE.md](../kits/geostat-kit/docs/ADOPTION-LINE.md).
