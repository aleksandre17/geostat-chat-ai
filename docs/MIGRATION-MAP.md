# გადაწყობის რუკა — სად იყო → რას აკეთებდა → სად არის ახლა

ეს დოკუმენტი აღწერს **root layout** მიგრაციას (`geostat-kit` → `geostat-kit`, `apps` + `kits` + `ops`).  
სტატუსი: **აქტიური** (geostat-chat-ai). დეტალი: [ROOT-LAYOUT.md](ROOT-LAYOUT.md), [adr/008-root-layout-consolidation.md](adr/008-root-layout-consolidation.md).

---

## Root ხე

| ძველი path | რას აკეთებდა | ახალი path | შენიშვნა |
|------------|--------------|------------|----------|
| `apps/frontend/` | Vite UI, `docker-compose*.yml`, nginx template | `apps/frontend/` | კოდი აქაა; root `apps/frontend/` — ძველი stub (წაშლა, თუ დარჩა) |
| `apps/backend/` | Spring API + worker, compose, Gradle | `apps/backend/` | იგივე; root `apps/backend/` — ცარიელი stub |
| `kits/geostat-kit/` → `kits/geostat-kit/` | Ops toolkit (CLI, drivers, compose-gen, tests) | `kits/geostat-kit/` | პაკეტი root-ზე აღარაა `packages/`-ში |
| `ops/config/` | env, SSH, `deploy.env`, GCP keys (gitignored) | `ops/config/` | იგივე შიგთავსი |
| `ops/compose/catalog.json` | compose-ის ერთადერთი წყარო (შაბლონები) | `ops/compose/catalog.json` | `infra/` წაშლილია |
| `ops/compose/stack/*.yml` | full-stack Docker (API + worker + UI) | `ops/compose/stack/*.yml` | GENERATED; `deploy/` წაშლილია |
| `tools/geostat.ps1` | ყოველდღიური CLI შესვლა | `ops/cli/geostat.ps1` | root `tools/geostat.ps1` — **shim** |
| `tools/geostat.sh` | bash CLI | `ops/cli/geostat.sh` | `./tools/geostat.sh` → shim |
| `ops/ci/integration-stack.sh` | CI: backend compose smoke | `ops/ci/integration-stack.sh` | `scripts/` წაშლილია |
| `ops/ci/setup-root-git.ps1` | ერთჯერადი root `git init` | `ops/ci/setup-root-git.ps1` | |
| `logs/` (root) | ზოგჯერ ცარიელი | — | `apps/backend/logs`, `apps/frontend/logs` |
| `deploy/README.md` | stack ინსტრუქცია | `ops/compose/stack/README.md`, [COMPOSE.md](COMPOSE.md) | |

---

## `ops/config/` → `ops/config/`

| ძველი | რას აკეთებდა | ახალი |
|--------|--------------|--------|
| `ops/config/deploy.env` | SSH, `DEPLOY_PROJECT`, `COMPOSE_*`, პორტები | `ops/config/deploy.env` |
| `ops/config/deploy.env.example` | შაბლონი | `ops/config/deploy.env.example` |
| `ops/config/frontend/.env.dev` / `.env.prod` | Vite / UI env | `ops/config/frontend/...` |
| `ops/config/frontend/.env.deploy` | remote static deploy | `ops/config/frontend/.env.deploy` |
| `ops/config/frontend/nginx.env` | nginx-gen | `ops/config/frontend/nginx.env` |
| `ops/config/backend/.env.dev` / `.env.prod` | Spring + API keys | `ops/config/backend/...` |
| `ops/config/backend/google-credentials.json` | GCP | `ops/config/backend/google-credentials.json` |
| `ops/config/ssh/` | SSH keys, config | `ops/config/ssh/` |
| `ops/config/profiles/` | worker off, legacy names, GCP | `ops/config/profiles/` |

---

## Compose (catalog targets → გენერირებული ფაილები)

| ძველი target | რას აკეთებდა | ახალი target |
|--------------|--------------|--------------|
| `backend/docker-compose.dev.yml` | API + worker dev | `apps/backend/docker-compose.dev.yml` |
| `backend/docker-compose.prod.yml` | API + worker prod | `apps/backend/docker-compose.prod.yml` |
| `frontend/docker-compose*.yml` | UI compose | `apps/frontend/docker-compose*.yml` |
| `ops/compose/stack/docker-compose.yml` | სრული stack dev | `ops/compose/stack/docker-compose.yml` |
| `ops/compose/stack/docker-compose.prod.yml` | სრული stack prod | `ops/compose/stack/docker-compose.prod.yml` |
| `backend/ops.modules` | Gradle sync | `apps/backend/ops.modules` |

ბრძანება: `.\tools\geostat.ps1 compose-gen` (კითხულობს `ops/compose/catalog.json`).

---

## `geostat.ops.json` (manifest v2)

| ველი | ძველი | ახალი |
|------|--------|--------|
| `version` | `1` (ან არა) | `2` |
| `package` | `kits/geostat-kit` | `kits/geostat-kit` |
| `secrets` | `secrets` | `ops/config` |
| `compose.catalog` | `ops/compose/catalog.json` | `ops/compose/catalog.json` |
| `compose.syncModules` | `backend/ops.modules` | `apps/backend/ops.modules` |
| `stack.composeDir` | `ops/compose/stack` | `ops/compose/stack` |
| `modules.backend.path` | `backend` | `apps/backend` |
| `modules.frontend.path` | `frontend` | `apps/frontend` |
| `adapters.nginx.template` | `frontend/nginx.conf.template` | `apps/frontend/nginx.conf.template` |
| `adapters.nginx.output` | `frontend/nginx.conf` | `apps/frontend/nginx.conf` |
| `adapters.nginx.env*` | `ops/config/frontend/nginx.env*` | `ops/config/frontend/nginx.env*` |
| `adapters.embed.envExample` | `ops/config/frontend/embed.env.example` | `ops/config/frontend/embed.env.example` |
| `ci.integration` | `ops/ci/integration-stack.sh` | `ops/ci/integration-stack.sh` |
| `ci.prepareEnv` / `waitHealth` | `kits/geostat-kit/ci/...` | `kits/geostat-kit/ci/...` |

მაგალითი: [../geostat.ops.json](../geostat.ops.json).

---

## Toolkit / სახელები (არა root ფოლდერი)

| ძველი | ახალი |
|--------|--------|
| პაკეტი `geostat-kit` | `geostat-kit` |
| env `GEOSTAT_KIT_ROOT` | `GEOSTAT_KIT_ROOT` |
| `geostat.ops.json` | იგივე ფაილი, v2 path-ები |

Deploy **ლოგიკა** (სკრიპტები) root-ზე არ გადავიდა — ისევ:

- `kits/geostat-kit/toolkit/deploy/`
- `kits/geostat-kit/drivers/` (`java-boot`, `node-vite`)
- `kits/geostat-kit/cli/`

---

## ბრძანებები (სახელი იგივე, path-ები ახალი)

| ბრძანება | რას აკეთებს | სად ეხება ახლა |
|----------|-------------|----------------|
| `.\tools\geostat.ps1 init` | scaffold + seed + compose-gen | `ops/config`, `ops/compose`, `apps/*` |
| `.\tools\geostat.ps1 compose-gen` | ყველა `docker-compose*.yml` | `ops/compose/catalog.json` → `apps/*`, `ops/compose/stack/` |
| `.\tools\geostat.ps1 stack up` | full stack local | `ops/compose/stack/` + `ops/config/deploy.env` |
| `.\tools\geostat.ps1 stack-deploy` | remote full deploy | `stackDeploy` in manifest + kit deploy |
| `.\tools\geostat.ps1 fe …` / `be …` | მოდული ops | `apps/frontend`, `apps/backend` |
| `.\tools\geostat.ps1 nginx-gen` | `nginx.conf` | `apps/frontend/` + `ops/config/frontend/nginx.env` |

შესვლა: `tools/geostat.ps1` (shim) ან პირდაპირ `ops/cli/geostat.ps1`.

---

## რა წაიშალა / რა დარჩა

| სტატუსი | path |
|---------|------|
| **წაშლილი** (junction ან დუბლიკატი) | root `ops/config/`, `packages/`, `infra/`, `deploy/`, `scripts/` |
| **Shim (დარჩა)** | `tools/geostat.ps1` → `ops/cli/geostat.ps1` |
| **წასაშლელი ნაშენი** | root `apps/frontend/`, `apps/backend/` — ცარიელი/stub; რეალური კოდი მხოლოდ `apps/`-ში |

```powershell
# როცა IDE/Node აღარ ჭერს ფაილებს:
Remove-Item -Recurse -Force .\frontend, .\backend -ErrorAction SilentlyContinue
```

---

## ახალი root (სქემა)

```text
geostat-chat-ai/
├── apps/
│   ├── apps/frontend/          # ყოფილი apps/frontend/
│   └── apps/backend/           # ყოფილი apps/backend/
├── kits/
│   └── geostat-kit/       # ყოფილი kits/geostat-kit (geostat-kit)
├── ops/
│   ├── config/            # ყოფილი ops/config/
│   ├── compose/
│   │   ├── catalog.json   # ყოფილი ops/compose/catalog.json
│   │   └── stack/         # ყოფილი ops/compose/stack/
│   ├── cli/               # ძირითადი geostat.ps1
│   └── ci/                # ყოფილი ops/ci/
├── tools/
│   └── geostat.ps1        # shim → ops/cli
├── docs/
│   └── MIGRATION-MAP.md   # ეს ფაილი
└── geostat.ops.json
```

---

## დაკავშირებული დოკუმენტები

| თემა | ფაილი |
|------|--------|
| პაკეტის ჩატვირთვა | [KITS-PACKAGE.md](KITS-PACKAGE.md) |
| ახალი ლეიაუტი | [ROOT-LAYOUT.md](ROOT-LAYOUT.md) |
| ADR | [adr/008-root-layout-consolidation.md](adr/008-root-layout-consolidation.md) |
| კონფიგი / env | [CONFIG.md](CONFIG.md) |
| Compose | [COMPOSE.md](COMPOSE.md) |
| `geostat init` | [GEOSTAT-INIT.md](GEOSTAT-INIT.md) |
| Kit adoption | [../kits/geostat-kit/docs/ADOPTION-LINE.md](../kits/geostat-kit/docs/ADOPTION-LINE.md) |
