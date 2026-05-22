# Configuration index (canonical)

Single map of **where every config lives** and **who reads it**. Real values stay in `ops/config/` only.

## Layers

```text
geostat.ops.json          manifest (modules, compose catalog, adapters)
    ↓
ops/config/                  env + credentials (gitignored)
    ↓
ops/compose/catalog.json → GENERATED docker-compose*.yml
    ↓
apps/frontend | apps/backend    app code + ops.config.* (no secrets)
```

## `geostat init` — ახალი პროექტის bootstrap

ერთი ბრძანება ჩააგდებს ops სტრუქტურას და შექმნის env ფაილებს examples-დან; შემდეგ `compose-gen`. სრული აღწერა: **[GEOSTAT-INIT.md](GEOSTAT-INIT.md)**.

```powershell
.\tools\geostat.ps1 init              # სრული stack catalog + seed + compose-gen
.\tools\geostat.ps1 init -MinimalCatalog   # მხოლოდ API catalog
```

**არ გადაწერს** არსებულ `deploy.env`, `.env.dev`, SSH keys (გარდა `-ForceExamples`).

---

## `tools/` vs `kits/geostat-kit/` vs `ops/ci/`

| Path | რა არის | ვინ იყენებს | გადმოწერისას |
|------|---------|-------------|--------------|
| **`tools/geostat.ps1`** | CLI shim → `ops/cli/` | ყოველდღე: `init` / `fe` / `be` / `stack` | `init` — ახალი repo |
| **`kits/geostat-kit/`** | Reusable ops **პაკეტი** (submodule/copy) | ყველა პროექტი | იხ. [KITS-PACKAGE.md](KITS-PACKAGE.md) |
| **`ops/ci/`** | პროექტის CI smoke (`integration-stack.sh`) | GitHub Actions / ლოკალური | `geostat.ops.json` → `ci.integration` |

```text
ყოველდღიური მუშაობა:
  .\tools\geostat.ps1  →  kits/geostat-kit/cli + drivers + toolkit

CI (GitHub / ლოკალური smoke):
  ops/ci/integration-stack.sh     ← manifest: role=api, modules.*.path, secretsModule
       ↓ იძახებს
  kits/geostat-kit/ci/prepare-integration-env.sh
  kits/geostat-kit/compose/build.py
  kits/geostat-kit/ci/wait-health.sh

სრული აღწერა: [CI.md](CI.md)

Manifest შემოწმება: `.\tools\geostat.ps1 validate` (იხ. [kits/geostat-kit/docs/MATURITY.md](../kits/geostat-kit/docs/MATURITY.md))
```

| ფაილი `ops/ci/`-ში | საჭიროა? |
|---------------------|----------|
| `integration-stack.sh` | **კი**, compose integration CI-სთვის |
| `setup-root-git.ps1` | optional — root `git init` ერთხელ |

**არ დაამატო** deploy/manage/compose ლოგიკა `ops/ci/`-ში — ყველაფერი `kits/geostat-kit`-შია.

დამატებით:

| Path | რა არის |
|------|---------|
| **`ops/compose/stack/`** | Full-stack **generated** YAML — `geostat stack` (ლოკალური API+UI) |
| **`ops/config/deploy.env`** | SSH + `DEPLOY_PROJECT` — **არა** ფოლდერი `deploy/` |

## File map

| File | Purpose | Read by |
|------|---------|---------|
| `geostat.ops.json` | Modules, stack, nginx/embed adapters, CI | `geostat` CLI |
| `ops/config/deploy.env` | `DEPLOY_SERVER`, `DEPLOY_PROJECT`, compose names | Ops deploy/manage, compose-gen |
| `ops/config/ssh/` | Private key + optional `config` (gitignored) | OpenSSH — see [../ops/config/ssh/README.md](../ops/config/ssh/README.md) |
| `ops/config/ssh/config.example` | SSH Host alias template | Copy → `ops/config/ssh/config` |
| `ops/config/.env.example` | Full env contract (documentation) | Humans |
| `ops/config/frontend/.env.dev` | `VITE_API_URL`, dev ports | Vite (`envDir`), `fe compose`, stack |
| `ops/config/frontend/.env.prod` | Prod build URLs | Vite `--mode prod`, stack prod |
| `ops/config/frontend/.env.deploy` | `DEPLOY_PATH`, `DEPLOY_LAYOUT`, `DEPLOY_HOST_PORT` | `fe deploy`, `fe dev`, `fe manage` |
| `ops/config/frontend/.env.deploy.example` | Template for `.env.deploy` | Copy on setup |
| `ops/config/frontend/nginx.env` | CSP `frame-ancestors` | `geostat nginx-gen` |
| `ops/config/frontend/embed.env.example` | Embed host docs | Team reference |
| `ops/config/backend/.env.dev` | API keys, ports, internal URLs | Spring (local), Docker `env_file` |
| `ops/config/backend/.env.prod` | Production keys | Spring prod profile, Docker prod |
| `ops/config/backend/.env.deploy` | Remote deploy paths (`structured`) | `be deploy`, `be dev`, `be manage` |
| `ops/config/backend/.env.deploy.example` | Template for `.env.deploy` | Copy on setup |
| `ops/config/backend/google-credentials.json` | GCP Speech | Docker volume, `GoogleCloudConfig` |
| `ops/config/profiles/*.example` | Legacy server / worker-off snippets | Merge into `deploy.env` |
| `ops/compose/catalog.json` | Compose service templates | `geostat compose-gen` |
| `apps/frontend/ops.config.ps1` | FE ops overrides | `geostat fe` |
| `apps/backend/ops.config.sh` | BE ops overrides | `geostat be` |
| `apps/frontend/vite.config.js` | `envDir: ../../ops/config/frontend` | Vite |
| `apps/backend/src/main/resources/application*.yml` | Spring profiles | Spring Boot |
| `apps/frontend/.env.example` | Pointer → `ops/config/frontend` | Developers |
| `apps/backend/.env.example` | Pointer → `ops/config/backend` | Developers |

**Do not** put API keys or `DEPLOY_SERVER` in `kits/geostat-kit/`, `apps/frontend/src`, or `apps/backend/src`.

## Profile → env file

| Run mode | Frontend | Backend |
|----------|----------|---------|
| Vite on host | `ops/config/frontend/.env.dev` | — |
| `./gradlew bootRun` (profile `local`) | — | `ops/config/backend/.env.dev` (Spring import) |
| `geostat be compose` dev | `ops/config/frontend/.env.dev` | `ops/config/backend/.env.dev` via compose |
| `geostat stack -Prod` | `.env.prod` | `.env.prod` |
| Remote deploy | `.env.deploy` + `deploy.env` | `.env.deploy` + `deploy.env` |

## Spring Boot (backend)

| Profile | Env import (optional file) | Docker |
|---------|---------------------------|--------|
| `local` | `../ops/config/backend/.env.dev` | — |
| `dev` | same (optional; compose injects vars) | `env_file: ../ops/config/backend/.env.dev` |
| `prod` | `../ops/config/backend/.env.prod` | `env_file: ../ops/config/backend/.env.prod` |

Working directory for `bootRun` / IDE: **`apps/backend/`** (so `../ops/config/backend/...` resolves).

GCP credentials order: `GOOGLE_APPLICATION_CREDENTIALS` → `../ops/config/backend/google-credentials.json` → legacy `backend/google-credentials.json`.

## First-time setup

```powershell
# რეკომენდებული — სრული ops bootstrap (იხ. GEOSTAT-INIT.md)
.\tools\geostat.ps1 init
# შემდეგ შეავსე checklist: DEPLOY_SERVER, API keys, google-credentials.json
```

ხელით (იგივე ნაბიჯები, ცალ-ცალკე):

```powershell
cd secrets
copy deploy.env.example deploy.env
# SSH: ops/config/ssh/README.md
copy frontend\.env.example frontend\.env.dev
copy frontend\.env.example frontend\.env.prod
copy frontend\.env.deploy.example frontend\.env.deploy
copy backend\.env.example backend\.env.dev
copy backend\.env.example backend\.env.prod
copy backend\.env.deploy.example backend\.env.deploy
copy frontend\nginx.env.example frontend\nginx.env
.\tools\geostat.ps1 compose-gen
```

## Related docs

| Topic | Doc |
|-------|-----|
| `scripts/` detail | [../scripts/README.md](../scripts/README.md) |
| `tools/` CLI | [../tools/README.md](../tools/README.md) |
| Ops package | [../kits/geostat-kit/README.md](../kits/geostat-kit/README.md) |
| Env variable list | [ENV.md](ENV.md) |
| Legacy / CSP / embed | [ENVIRONMENT.md](ENVIRONMENT.md) |
| **`geostat init`** | [GEOSTAT-INIT.md](GEOSTAT-INIT.md) |
| Ops + deploy tree | [GEOSTAT-KIT-SETUP.md](GEOSTAT-KIT-SETUP.md) |
| Secrets layout | [../ops/config/README.md](../ops/config/README.md) |
| Server safety rules | [GEOSTAT-KIT-SETUP.md §15](GEOSTAT-KIT-SETUP.md#15-სერვერის-წესები--რა-შეიძლება-რა-არ-უნდა) |

Verify paths: `.\tools\geostat.ps1 layout`
