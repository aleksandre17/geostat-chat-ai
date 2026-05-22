# geostat-kit — სრული სახელმძღვანო (პალეტი + პროექტი)

**ერთი ფაილი** იმისთვის, რომ `kits/geostat-kit` პაკეტი და ეს monorepo სწორად იმუშაოს. პაკეტის ჩატვირთვა: **[KITS-PACKAGE.md](KITS-PACKAGE.md)**.

ღრმა თემები (ცალკე): [GEOSTAT-INIT.md](GEOSTAT-INIT.md) (**`geostat init`** — სრული bootstrap), [ADOPTION-LINE.md](../kits/geostat-kit/docs/ADOPTION-LINE.md) (ახალი პროექტის გადმოტანა), [GOLDEN-PATHS.md](../kits/geostat-kit/docs/GOLDEN-PATHS.md) (frontend), [GOLDEN-PATHS-BACKEND.md](../kits/geostat-kit/docs/GOLDEN-PATHS-BACKEND.md) (backend).

---

## 0. სწრაფი bootstrap — `geostat init`

ახალი repo-სთვის ან ops-ის ჩასმისთვის:

```powershell
.\tools\geostat.ps1 init
```

რას აკეთებს: scaffold (`tools/`, `secrets` examples, `infra/`, `deploy/`, `ops.config*`, `geostat.ops.json`) → seed env ფაილები → `catalog.full.json` → `compose-gen` → checklist.

| Flag | როდის |
|------|--------|
| `-MinimalCatalog` | მხოლოდ API, worker-ის გარეშე |
| `-SkipComposeGen` | მხოლოდ ხე + seed |
| `-ForceExamples` | არსებული env-ების გადაწერა |

სრული აღწერა: **[GEOSTAT-INIT.md](GEOSTAT-INIT.md)** · ტექნიკა: [toolkit/init/README.md](../kits/geostat-kit/toolkit/init/README.md)

---

## 1. რა არის პალეტი და რა არის პროექტი

```
┌─────────────────────────────────────────────────────────────┐
│  kits/geostat-kit          ← პალეტი (გადატანადი)         │
│  lib · compose · toolkit · drivers · cli · ci · contracts   │
└────────────────────────────┬────────────────────────────────┘
                             │ geostat.ops.json (კონტრაქტი)
┌────────────────────────────▼────────────────────────────────┐
│  geostat-chat-ai (შენი repo)                                │
│  ops/config/ · ops/compose/catalog.json · apps/backend/ apps/frontend/   │
│  tools/geostat.ps1 · ops/ci/ · GENERATED compose          │
└────────────────────────────┬────────────────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────────┐
│  აპლიკაცია (Spring Boot, Vite, …)                            │
└─────────────────────────────────────────────────────────────┘
```

| სად რჩება | რა |
|----------|-----|
| **პაკეტში** | SSH/deploy ლოგიკა, drivers, `compose/build.py`, CLI router |
| **პროექტში** | API keys, `DEPLOY_SERVER`, `catalog.json`, აპის კოდი, გენერირებული yml |
| **არასოდეს პაკეტში** | `deploy.env`, production secrets, `docker-compose*.yml` პროექტისთვის |

---

## 2. წინაპირობები (აუცილებელი)

| ხელი | სად გამოიყენება |
|------|----------------|
| **Python 3.10+** | `geostat compose-gen`, nginx-gen, package tests |
| **Docker + Docker Compose** | `be/fe compose`, deploy, CI |
| **PowerShell** | Windows: `tools/geostat.ps1`, frontend driver |
| **Git Bash** | Windows: `be` (java-boot), `stack-deploy` |
| **Node.js + npm** | frontend build (`dist/`) |
| **Java 21 + Gradle** | backend `bootJar` |
| **rsync** | Git for Windows `usr\bin\rsync.exe` — `fe dev`, `be dev` |
| **SSH** | remote deploy / manage |

---

## 3. სრული ხე რეპოში — რა **უნდა** არსებობდეს

```text
geostat-chat-ai/                          ← GEOSTAT_PROJECT_ROOT
│
├── geostat.ops.json                       ← აუცილებელი კონტრაქტი
├── tools/
│   ├── geostat.ps1                        ← delegate → kits/geostat-kit/cli/
│   └── geostat.sh                         ← optional (Git Bash entry)
│
├── kits/
│   └── geostat-kit/                       ← პაკეტი (submodule | copy) — [KITS-PACKAGE.md](KITS-PACKAGE.md)
│
├── ops/
│   ├── config/                            ← env + SSH (gitignored)
│   │   ├── deploy.env
│   │   ├── frontend/  (.env.dev, .env.prod, …)
│   │   └── backend/   (.env.dev, google-credentials.json, …)
│   ├── compose/
│   │   ├── catalog.json
│   │   └── stack/                         ← GENERATED docker-compose*.yml
│   ├── cli/geostat.ps1
│   └── ci/integration-stack.sh
│
├── apps/frontend/                              ← module: node-vite
│   ├── ops.config.ps1
│   ├── docker-compose.yml                 ← GENERATED
│   ├── docker-compose.override.yml        ← GENERATED
│   ├── docker-compose.prod.yml            ← GENERATED
│   ├── package.json, src/, public/
│   ├── src/Dockerfile                     ← dev + production targets
│   ├── nginx.conf.template                ← nginx-gen
│   └── logs/
│
└── apps/backend/                               ← module: java-boot
    ├── ops.config.sh
    ├── ops.modules                        ← GENERATED
    ├── docker-compose.dev.yml             ← GENERATED
    ├── docker-compose.prod.yml            ← GENERATED
    ├── gradlew, settings.gradle.kts, build.gradle.kts
    ├── src/Dockerfile                     ← prod JAR image (be deploy)
    ├── src/Dockerfile.dev                 ← local compose dev build
    ├── src/Dockerfile.dev.remote          ← be dev workspace
    ├── worker/                            ← optional boot module
    ├── shared/                            ← library (ops.modules)
    └── logs/
```

### რა **არ უნდა** ხელით შეცვალო

ფაილები `# GENERATED` — მხოლოდ `ops/compose/catalog.json` + `geostat compose-gen`.

---

## 4. `geostat.ops.json` (root)

ფაილი: [geostat.ops.json](../geostat.ops.json)

| ველი | ამ პროექტში | მნიშვნელობა |
|------|-------------|-------------|
| `package` | `kits/geostat-kit` | სად არის პალეტი |
| `secrets` | `secrets` | env root |
| `compose.catalog` | `ops/compose/catalog.json` | compose generator input |
| `compose.syncModules` | `apps/backend/ops.modules` | java-boot registry sync |
| `stack.composeDir` | `ops/compose/stack` | `geostat stack up` |
| `cli.aliases` | `fe`→frontend, `be`→backend |  ბრძანებები |
| `modules.backend.type` | `java-boot` | driver id ([registry.json](../kits/geostat-kit/drivers/registry.json)) |
| `modules.frontend.type` | `node-vite` | driver id |
| `stackDeploy.steps` | be deploy all + fe deploy dist | full remote stack |

**წესი:** `modules.<id>.type` ყოველთვის ხელით — არ გამოიცნოს ფოლდერის სახელიდან.

---

## 5. `ops/config/` — სავალდებულო კონფიგები

**Canonical index:** [CONFIG.md](CONFIG.md) — ყველა ფაილი, Spring/Vite/compose წყაროები.

### 5.1 `ops/config/deploy.env` (საერთო)

| ცვლადი | აუცილებელი | მაგალითი (ამ პროექტი) |
|--------|------------|----------------------|
| `DEPLOY_SERVER` | remote ops-ისთვის | `administrator@192.168.1.199` |
| SSH key/config | optional | `ops/config/ssh/` — [ops/config/ssh/README.md](../ops/config/ssh/README.md) |
| `DEPLOY_PROJECT` | remote path segment | `geostat` |
| `DEPLOY_SERVER_BASE` | optional | `/home/administrator` |
| `DOCKER_NETWORK` | optional | `geostat-net` (ან auto slug) |
| `COMPOSE_*_SERVICE` | legacy სერვერზე | `compose-gen`-ის შემდეგ |

### 5.2 `ops/config/frontend/.env.deploy`

| ცვლადი | მნიშვნელობა |
|--------|-------------|
| `DEPLOY_PATH` | base, напр. `/home/.../geostat/frontend` |
| `DEPLOY_LAYOUT` | `structured` (რეკომენდებული) |
| `DEPLOY_HOST_PORT` | host port nginx/Vite |
| `DEPLOY_PATH_MODE` | `base` ან `full` |

### 5.3 `ops/config/backend/.env.deploy`

| ცვლადი | მნიშვნელობა |
|--------|-------------|
| `DEPLOY_PATH` | `/home/.../geostat/backend` |
| `DEPLOY_LAYOUT` | **`structured`** — სავალდებულო `be dev`-ისთვის |
| `DEPLOY_PATH_MODE` | `base` |

### 5.4 `ops/config/*/env.dev` და `.env.prod`

| ფაილი | frontend | backend |
|-------|----------|---------|
| `.env.dev` | `VITE_API_URL`, dev ports | `API_PORT`, `GEMINI_*`, `GCP_*`, `API_INTERNAL_URL` |
| `.env.prod` | prod build URL | prod keys + worker internal URL |

**Spring (bootRun / IDE):** `application-local.yml` → `optional:file:../ops/config/backend/.env.dev`; GCP → `ops/config/backend/google-credentials.json`. აღარ სჭირდება `.env.dev` კოპირება `apps/backend/`-ში.

---

## 6. სხვა პროექტები სერვერზე — შეეხება? წაშლის?

**ჩვეულებრივი deploy (`be deploy`, `fe deploy dist`, `be dev sync`) — არა.** ოპერაციები მუშაობენ მხოლოდ იმ path-ებზე, რაც `secrets`-შია. სხვა პროექტის `/home/otherapp/...` ან სხვა container-ები **არ ეხება**.

| ოპერაცია | რა შეიძლება სერვერზე |
|----------|---------------------|
| `be deploy` / `fe deploy dist` | `mkdir`, `scp`, `compose up` **მხოლოდ** `DEPLOY_PATH/.../runtime/` ან `static/` |
| `be dev` / `fe dev` rsync | მხოლოდ `workspace/` ან `compose/dev/{container}/` (`rsync --delete` = **მხოლოდ ამ დირექტორიაში** ფაილები იშლება, არა მთელი დისკი) |
| `be manage stop/restart` | მხოლოდ აღმოჩენილი სერვისის container |
| **`be manage nuke`** / **`fe manage delete`** | **დიახ, სახიფრო** — წაშლის **მხოლოდ** იმ სერვისის `remote_path`-ს (მაგ. `.../runtime/geostat-chat-ai-api/`) |
| **`be manage all nuke`** | ყველა **ამ repo-ს** deployed backend სერვისის path + images **მხოლოდ** ამ compose სახელებზე (`manage_prune_deployed_images`; არა global `docker image prune`) |
| `migrate-backend-layout.sh` | მხოლოდ `$DEPLOY_PATH_BASE/*` ქვეშ (არა `/home` მთლიანად) |
| `fe deploy remote` | არქივი: `{SERVER_BASE}/deploy-staging/` — თუ ორ repo იყენებს **იგივე** `deploy-staging`-ს, არქივები შეიძლება დაერთგეს (გამოიყენე განსხვავებული `DEPLOY_PROJECT` ან staging სახელი) |

**Docker container name:** `geostat-chat-ai-api`, `geostat-chat-ai-app` — თუ სერვერზე **უკვე** არსებობს container იგივე სახელით, `compose up` შეიძლება გადააწყოს/შეცვალოს **იმ** container-ს (არა ყველა container). სხვა პროექტის container სხვა სახელით არ ეხება.

**Docker network:** deploy ქმნის მხოლოდ compose-ში მითითებულ network-ს (მაგ. `geostat-net`). თუ სხვა პროექტიც იყენებს **იგივე** network სახელს — უბრალოდ „უკვე არსებობს“; არ შლის.

**შეჯამება:** სხვა პროექტები უსაფრთხოა, თუ არ გაუშვებ `nuke`/`delete` შეცდომით და container/network/port/path არ გაზიარებ. სრული წესები: [§15](#15-სერვერის-წესები--რა-შეიძლება-რა-არ-უნდა).

---

## 7. სერვერის ხე (`DEPLOY_LAYOUT=structured`) — პირველი (root) ფოლდერი

ორი მოდული — **ორი განსხვავებული ქვეფოლდერი**, არა ერთი flat dir.

### Frontend (`ops/config/frontend/.env.deploy`)

```text
/home/.../geostat/frontend/
  static/{container}/              fe deploy dist | sync | deploy watch
  compose/dev/{container}/         fe dev bootstrap | dev watch
  compose/prod/{container}/        fe deploy remote -Environment prod
```

### Backend (`ops/config/backend/.env.deploy`)

```text
/home/.../geostat/backend/
  runtime/{container}/             be deploy | be deploy watch | be manage
  workspace/{container}/           be dev bootstrap | dev sync | dev watch
```

| Legacy `flat` (`.env.deploy` გარეშე) | `.../backend/{container}/` პირდაპირ — მხოლოდ backward compat |

მიგრაცია flat→runtime: `kits/geostat-kit/toolkit/deploy/migrate-backend-layout.sh`

---

### პირველი root ფოლდერი სერვერზე (ამ პროექტი)

ყველაფერი იწყება **`ops/config/deploy.env`** + **`ops/config/*/env.deploy`**-დან, არა repo სახელიდან (`geostat-chat-ai`).

| | Path |
|---|------|
| `DEPLOY_SERVER` | `administrator@192.168.1.199` |
| `DEPLOY_PROJECT` | `geostat` (არა `geostat-chat-ai`) |
| **Frontend base** | `DEPLOY_PATH` = `/home/administrator/geostat/frontend` |
| **Backend base** | `DEPLOY_PATH` = `/home/administrator/geostat/backend` |

თუ `DEPLOY_PATH` არ დაწერე, fallback: `/home/<ssh-user>/$DEPLOY_PROJECT/frontend|backend`.

**Structured-ში „პირველი“ ქვეფოლდერი base-ის ქვეშ:**

```text
.../geostat/frontend/
  static/          ← fe deploy dist (პირველი დონე deploy artifact-ისთვის)
  compose/         ← fe dev / fe deploy remote

.../geostat/backend/
  runtime/         ← be deploy (JAR)
  workspace/       ← be dev (სორსი)
```

კონკრეტული სერვისი: `.../runtime/geostat-chat-ai-api/` (არა `.../geostat/geostat-chat-ai-api/` თუ `DEPLOY_PROJECT=geostat`).

---

## 8. Drivers — რა ბრძანებები არსებობს

### 7.1 გლობალური (`geostat` CLI)

| ბრძანება | დანიშნულება |
|----------|-------------|
| `geostat help` | მოდულები + driver types |
| `geostat compose-gen` | catalog → ყველა GENERATED yml + `ops.modules` |
| `geostat stack up -d --build` | local full stack (`ops/compose/stack/`) |
| `geostat stack -Prod up -d --build` | prod stack local |
| `geostat stack-deploy --prod` | remote: `stackDeploy.steps` |
| `geostat nginx-gen` | `frontend/nginx.conf` CSP |
| `geostat layout` | სიმულაცია (ყველა მოდული) |
| `geostat layout --frontend` | [FRONTEND-LAYOUT-SIMULATION-FULL.md](./FRONTEND-LAYOUT-SIMULATION-FULL.md) |
| `geostat layout --backend` | [BACKEND-LAYOUT-SIMULATION-FULL.md](./BACKEND-LAYOUT-SIMULATION-FULL.md) |
| `geostat mod <id> <cmd> …` | ნებისმიერი მოდული |

### 7.2 Frontend — `node-vite` (`geostat fe …`)

| ბრძანება | როდის |
|----------|-------|
| `fe compose up` | local Docker dev |
| `fe deploy dist` | static UI სერვერზე |
| `fe deploy sync` | dist patch + nginx reload |
| `fe deploy watch` | auto npm build → static |
| `fe dev bootstrap` | rsync → `compose/dev/` |
| `fe dev watch` | rsync only (Vite HMR) |
| `fe manage …` | remote container |
| `fe check` | pre-flight |

`fe watch` → CLI redirect → `fe deploy watch` (hint).

### 7.3 Backend — `java-boot` (`geostat be …`)

| ბრძანება | როდის | სერვერის path |
|----------|-------|----------------|
| `be compose up` | local dev compose | laptop |
| `be deploy <svc> --dev\|--prod` | JAR deploy | `runtime/` |
| `be deploy watch` | Gradle loop → JAR | `runtime/` |
| `be dev bootstrap` | rsync + bootRun | `workspace/` |
| `be dev sync` | rsync only | `workspace/` |
| `be dev watch` | rsync (+ DevTools reload) | `workspace/` |
| `be dev restart` | compose restart | `workspace/` |
| `be manage …` | stop/logs/status | `runtime/` (აღმოაჩენს flat-საც) |
| `be check` | pre-flight | — |
| `be modules` | ops.modules ჩვენება | — |

`be watch` → CLI hint → `be dev watch`.  
`be deploy watch` → JAR loop (`deploy watch` subcommand).

---

## 9. Golden paths — ერთი ცხრილი (გუნდის პოლიტიკა)

**წესი:** ერთ მიზანზე ერთი loop. არ აურიო `static/` და `compose/dev/` (FE) ან `runtime/` და `workspace/` (BE) იმავე host-ზე.

| მიზანი | Golden path |
|--------|-------------|
| UI dev ლოკალურად (Windows/Linux) | `cd frontend && npm run dev` |
| UI dev Docker ლოკალურად | `geostat fe compose up -d` |
| UI prod სერვერზე | `fe deploy dist` → `fe deploy sync` / `watch` |
| UI Windows→Linux (Vite container) | `fe dev bootstrap` → `fe dev watch` |
| API dev ლოკალურად | `./gradlew bootRun` ან `be compose up` |
| API JAR staging/prod | `be deploy <svc> --dev\|--prod` |
| API JAR auto (Windows Gradle) | `be deploy` → `be deploy watch` |
| API Windows→Linux (bootRun) | `be dev bootstrap` → `be dev watch` |
| Full stack local | `geostat stack up -d --build` |
| Full stack remote prod | `geostat stack-deploy --prod` |

---

## 10. `ops/compose/catalog.json`

- ერთადერთი წყარო: რომელი service რომელ `docker-compose*.yml`-ში ჩაიწერება.
- `features.worker: true|false` — worker on/off.
- `deploy.env`-ის `COMPOSE_*` ცვლადები ემბედდება templates-ში compose-gen-ის დროს.

შემდეგ:

```powershell
.\tools\geostat.ps1 compose-gen
```

გენერირდება: `apps/backend/docker-compose.*.yml`, `apps/frontend/docker-compose*.yml`, `ops/compose/stack/*`, `apps/backend/ops.modules`.

---

## 11. პალეტის შიდა რუკა (სად რა ლოგიკა)

| შენ იძახებ | პაკეტში |
|------------|---------|
| `geostat compose-gen` | `compose/build.py` |
| `geostat be deploy` | `drivers/java-boot/sh/deploy.sh` → `toolkit/deploy/*.sh` |
| `geostat be dev` | `drivers/java-boot/sh/dev.sh` → `toolkit/deploy/dev-remote.sh` |
| `geostat be deploy watch` | `deploy.sh` → `deploy-watch.sh` |
| `geostat fe deploy` | `drivers/node-vite/ps1/deploy.ps1` |
| `geostat fe dev` | `drivers/node-vite/ps1/dev.ps1` |
| Path resolution FE | `toolkit/powershell/Deploy-Path.ps1` |
| Path resolution BE | `toolkit/deploy/deploy-path.sh` |
| Manage | `toolkit/bash/manage-remote.sh` + driver `manage.sh` |

Driver registry: [kits/geostat-kit/drivers/registry.json](../kits/geostat-kit/drivers/registry.json)

---

## 12. პირველი გაშვება — checklist

### A. ლოკალური სეტაპი (ერთხელ)

- [ ] `kits/geostat-kit` არსებობს
- [ ] `.\tools\geostat.ps1 init` (ახალი პროექტი) ან scaffold ხელით
- [ ] `tools/geostat.ps1` delegate მუშაობს
- [ ] `ops/config/deploy.env` შევსებულია (`DEPLOY_SERVER`, `DEPLOY_PROJECT`)
- [ ] `ops/config/frontend/.env.dev`, `.env.prod`, `.env.deploy`
- [ ] `ops/config/backend/.env.dev`, `.env.prod`, `.env.deploy` (`DEPLOY_LAYOUT=structured`)
- [ ] `ops/config/backend/google-credentials.json` (თუ GCP სჭირდება)
- [ ] `geostat compose-gen`
- [ ] `bash kits/geostat-kit/tests/run-kit-tests.sh` — package tests
- [ ] `bash kits/geostat-kit/scripts/backend-ops-smoke.sh` — backend config smoke

### B. ლოკალური stack

```powershell
.\tools\geostat.ps1 stack up -d --build
```

### C. Remote backend (structured)

```bash
.\tools\geostat.ps1 be check geostat-chat-ai-api --no-build
.\tools\geostat.ps1 be deploy geostat-chat-ai-api --dev
.\tools\geostat.ps1 be manage geostat-chat-ai-api status --dev
```

### D. Remote frontend

```powershell
.\tools\geostat.ps1 fe deploy dist -Environment dev
.\tools\geostat.ps1 fe manage config
```

### E. Remote dev (optional)

```bash
.\tools\geostat.ps1 be dev bootstrap geostat-chat-ai-api
.\tools\geostat.ps1 fe dev bootstrap -Environment dev
```

### F. მიგრაცია (თუ ძველი flat ხე სერვერზე)

```bash
bash kits/geostat-kit/toolkit/deploy/migrate-backend-layout.sh --dry-run --dev
```

---

## 13. შემოწმება / დიაგნოსტიკა

| რა | ბრძანება |
|----|----------|
| Package tests (no SSH) | `bash kits/geostat-kit/tests/run-kit-tests.sh` |
| Backend secrets smoke | `bash kits/geostat-kit/scripts/backend-ops-smoke.sh` |
| Pre-flight backend | `geostat be check` |
| Pre-flight frontend | `geostat fe check` |
| სიმულაცია paths | `geostat layout --backend` / `--frontend` |
| Driver list | `geostat help` |

---

## 14. Dockerfile-ების რუკა (რომელი როდის)

### Frontend `frontend/src/Dockerfile`

| Stage / mode | ბრძანება |
|--------------|----------|
| `development` | `fe dev`, local `fe compose` |
| `production` + `dist/` | `fe deploy dist`, `fe deploy watch` |

### Backend

| ფაილი | ბრძანება | სად |
|-------|----------|-----|
| `src/Dockerfile` | `be deploy` | `runtime/` — COPY `app.jar` |
| `src/Dockerfile.dev` | `be compose up` | local |
| `src/Dockerfile.dev.remote` | `be dev` | `workspace/` + volume |

---

## 15. სერვერის წესები — რა შეიძლება, რა არ უნდა

გაზიარებულ სერვერზე (`/home/administrator/geostat/frontend|backend` + სხვა პროექტები). `DEPLOY_LAYOUT=structured`, `DEPLOY_PATH_MODE=base` — რეკომენდებული.

### 15.1 რა **შეიძლება** (უსაფრთხო)

| ოპერაცია | რას აკეთებს | რას **არ** შეეხება |
|----------|-------------|-------------------|
| `fe deploy dist` / `sync` | `.../frontend/static/{container}/` | `static/სხვა-აპი/`, `.../frontend/` root |
| `fe dev` / `fe dev sync` | `.../compose/dev/{container}/` (`rsync --delete` მხოლოდ აქ) | სხვა `compose/dev/*`, `static/*` |
| `be deploy` / `be deploy watch` | `.../backend/runtime/{container}/` | `runtime/სხვა-api/`, `.../backend/` root |
| `be dev` / `be dev sync` | `.../backend/workspace/{container}/` | სხვა `workspace/*`, `runtime/*` |
| `be manage api stop\|restart\|logs` | მხოლოდ `geostat-chat-ai-api` container | სხვა container-ები |
| `fe manage app stop\|restart\|undeploy` | მხოლოდ `geostat-chat-ai-app` container; ფაილები რჩება | სხვა container / ფოლდერები |
| **`be manage api nuke`** | წაშლის **მხოლოდ** `.../runtime/geostat-chat-ai-api/` | `.../backend/`, `runtime/სხვა-*` |
| **`fe manage app delete`** (`nuke`) | წაშლის **მხოლოდ** `.../static/geostat-chat-ai-app/` (ან სადაც manifest აჩვენებს) | `.../frontend/`, `static/სხვა-*` |
| `geostat layout` | ლოკალურად path-ების შემოწმება | სერვერზე ცვლილება არა |

**პირველი root (base) — მხოლოდ კონფიგი; deploy/nuke არ წერს პირდაპირ base-ში:**

- Frontend base: `/home/administrator/geostat/frontend`
- Backend base: `/home/administrator/geostat/backend`

**კონკრეტული სერვისის სამიზნე path (ამ პროექტი):**

- FE: `.../frontend/static/geostat-chat-ai-app/`
- BE: `.../backend/runtime/geostat-chat-ai-api/`

---

### 15.2 რა **არ უნდა** გააკეთო

#### სერვერი / სხვა პროექტები

| არ გააკეთო | რატომ |
|-----------|--------|
| იგივე `DEPLOY_PATH` ორ სხვადასხვა პროექტზე | ერთ ფოლდერში ფაილები და `delete`/`nuke` ერთმანეთს წაშლის |
| `DEPLOY_PATH_MODE=full` + `DEPLOY_PATH` = მთელი `.../frontend` ან `.../backend` | `fe delete` / `be nuke` შეიძლება წაშალოს **მთელ base-ს**, არა მხოლოდ `{container}/` |
| იგივე `container_name` / `APP_NAME` სხვა პროექტთან | Docker შეიძლება გადააწყოს **იმ** container-ს |
| იგივე host port (`5177`, `8090`) სხვა აპთან | კონფლიქტი, compose არ აეწყება |
| **`be manage all nuke`** | ყველა **ამ repo-ს** deployed backend სერვისი + scoped image removal (compose service names only) |
| **`fe manage delete`** / **`be manage nuke`** სხვის სერვისის სახელზე | წაშლის მხოლოდ მითითებული სერვისის path — დარწმუნდი, რომ სახელი სწორია (`app`, `api`) |
| ხელით `rm -rf` `.../geostat/frontend` ან `.../backend` | წაშლის **ყველა** პროექტს base-ის ქვეშ — გამოიყენე `manage delete`/`nuke` **ერთ** სერვისზე |
| ორ repo-ზე იგივე `{SERVER_BASE}/deploy-staging` გარეშე განსხვავების | არქივები შეიძლება დაერთგეს (`fe deploy remote`) |

#### რეპო / პალეტი (ზოგადი)

- API keys / `DEPLOY_SERVER` პაკეტში (`kits/geostat-kit`) — მხოლოდ `ops/config/`
- ხელით `# GENERATED` compose-ის რედაქტირება
- `fe dev watch` + `fe deploy watch` ერთდროულად იმავე სერვისზე იგივე path-ზე
- `be dev watch` + `be deploy watch` იგივე container name-ზე გადახურვა
- `be deploy` ყოველ save-ზე (ძალიან მძიმე — `be dev` ან `be deploy watch`)

---

### 15.3 სწრაფი შემოწმება deploy-მდე

```powershell
.\tools\geostat.ps1 layout
```

დააკვირდი: `static/geostat-chat-ai-app`, `runtime/geostat-chat-ai-api` — **არა** მხოლოდ `.../frontend` ან `.../backend` სრულად, თუ `delete`/`nuke`-ს აპირებ.

---

## 16. დოკუმენტაციის რუკა

| თემა | ფაილი |
|------|-------|
| **კონფიგის რუკა** | `docs/CONFIG.md` |
| **`geostat init`** | `docs/GEOSTAT-INIT.md` |
| **ეს ფაილი** | `docs/GEOSTAT-KIT-SETUP.md` |
| ახალი პროექტის გადმოტანა | `kits/geostat-kit/docs/ADOPTION-LINE.md` |
| Frontend golden paths | `kits/geostat-kit/docs/GOLDEN-PATHS.md` |
| Backend golden paths | `kits/geostat-kit/docs/GOLDEN-PATHS-BACKEND.md` |
| Frontend layouts | `docs/FRONTEND-DEPLOY-LAYOUTS.md` |
| Backend layouts | `docs/BACKEND-DEPLOY-LAYOUTS.md` |
| FE watch | `docs/FE-WATCH.md` |
| BE watch | `docs/BE-DEPLOY-WATCH.md` |
| FE remote dev | `docs/DEV-REMOTE.md` |
| BE remote dev | `docs/BACKEND-DEV-REMOTE.md` |
| FE simulation | `docs/FRONTEND-LAYOUT-SIMULATION-FULL.md` |
| BE simulation | `docs/BACKEND-LAYOUT-SIMULATION-FULL.md` |
| Multi-module | `docs/MULTI-MODULE.md` |
| Environment / legacy | `docs/ENVIRONMENT.md` |
| Package architecture | `kits/geostat-kit/ARCHITECTURE.md` |

---

## 17. ამ პროექტის resolved paths (მაგალითი)

| Key | Path |
|-----|------|
| SSH | `administrator@192.168.1.199` |
| Project slug | `geostat` |
| FE base | `/home/administrator/geostat/frontend` |
| FE static | `.../frontend/static/geostat-chat-ai-app` |
| FE dev | `.../frontend/compose/dev/geostat-chat-ai-app` |
| BE base | `/home/administrator/geostat/backend` |
| BE runtime API | `.../backend/runtime/geostat-chat-ai-api` |
| BE workspace API | `.../backend/workspace/geostat-chat-ai-api` |

განახლება: `.\tools\geostat.ps1 layout`
