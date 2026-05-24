# Backend deploy layouts

Simulation: `.\tools\geostat.ps1 layout --backend`  
Full report: [BACKEND-LAYOUT-SIMULATION-FULL.md](./BACKEND-LAYOUT-SIMULATION-FULL.md)  
Regenerate: `.\tools\geostat.ps1 layout --backend -Markdown -OutFile docs/BACKEND-LAYOUT-SIMULATION-FULL.md`

Package policy: [kits/geostat-kit/docs/GOLDEN-PATHS-BACKEND.md](../kits/geostat-kit/docs/GOLDEN-PATHS-BACKEND.md)

Related: [BACKEND-DEV-REMOTE.md](./BACKEND-DEV-REMOTE.md), [BE-DEPLOY-WATCH.md](./BE-DEPLOY-WATCH.md), [kits/geostat-kit/docs/REMOTE-DEV-JAR-FLOW.md](../kits/geostat-kit/docs/REMOTE-DEV-JAR-FLOW.md)

---

## რას ნიშნავს `DEPLOY_LAYOUT=structured`?

ეს არის **სერვერზე ფოლდერების ორგანიზაციის რეჟიმი** `ops/config/backend/.env.deploy`-ში. იგივე SSH host (`DEPLOY_PATH=/home/.../geostat/backend`), მაგრამ სერვისები **როლის მიხედვით** ცალკე ქვეფოლდერში.

### ძველი: `flat` (legacy)

`DEPLOY_LAYOUT=flat` **ან** `.env.deploy` საერთოდ არა — ყველაფერი პირდაპირ base-ის ქვეშ:

```text
/home/administrator/geostat/backend/
  geostat-chat-ai-api/          ← პირდაპირ აქ (flat)
    app.jar
    Dockerfile
    .env.prod
    docker-compose.prod.yml
    logs/
  geostat-chat-ai-worker/
    ...
```

მუშაობს, მაგრამ **ერთ path-ზე** ვერ გაიყოფა „JAR deploy“ და „source dev (rsync + bootRun)“.

### ახალი: `structured` (რეკომენდებული)

```text
/home/administrator/geostat/backend/
  runtime/                       ← be deploy, be deploy watch (api, retrieval, ingestion)
    geostat-chat-ai-api/
    geostat-chat-ai-retrieval/
    geostat-chat-ai-ingestion/
  workspace/                     ← be dev bootstrap / sync / watch
    geostat-chat-ai-api/
      gradlew, src/, shared/, worker/, .env.dev, docker-compose.workspace.yml
    ...
```

| ქვეფოლდერი | ბრძანებები | რა ხდება |
|------------|------------|----------|
| **`runtime/`** | `be deploy`, `be deploy watch`, `be manage` | Windows-ზე **bootJar** → `app.jar` scp → JRE image სერვერზე |
| **`workspace/`** | `be dev bootstrap`, `be dev sync`, `be dev watch` | მთელი **apps/backend/** rsync → Gradle **bootRun** კონტეინერში |

### Frontend-ის ანალოგი

| Backend (`structured`) | Frontend (`structured`) |
|------------------------|-------------------------|
| `runtime/` + `app.jar` | `static/` + `dist/` |
| `workspace/` + rsync | `compose/dev/` + rsync |
| `be deploy watch` | `fe deploy watch` |
| `be dev watch` | `fe dev watch` |

---

## რას ნიშნავს **flat → runtime** (მიგრაცია)?

თუ სერვერზე **უკვე** გაქვს ძველი ხე `backend/geostat-chat-ai-api/` (flat), მიგრაცია აკეთებს:

```text
backend/geostat-chat-ai-api/     →    backend/runtime/geostat-chat-ai-api/
```

სკრიპტი: `kits/geostat-kit/toolkit/deploy/migrate-backend-layout.sh`

- მუშაობს მხოლოდ როცა `DEPLOY_LAYOUT=structured`
- **`--dry-run`** — აჩვენებს `mv`-ებს, არაფერს არ ცვლის
- გარეშე — სერვერზე გადაიტანს flat დირექტორიებს `runtime/`-ში

თუ flat ფოლდერი აღარ არის (უკვე deploy გაქვს `runtime/`-ში), dry-run ცარიელია — ეს ნორმალურია.

**წესი:** ერთ host-ზე არ აურიო flat path და `runtime/...` იმავე სერვისისთვის. აირჩიე ერთი golden path; ძველი flat წაშალე მხოლოდ ახალი რეჟიმის შემოწმების შემდეგ.

---

## Secrets

ფაილი: `ops/config/backend/.env.deploy` (gitignored; ნიმუში: `.env.deploy.example`)

```env
DEPLOY_PATH=/home/administrator/geostat/backend
DEPLOY_LAYOUT=structured
DEPLOY_PATH_MODE=base
```

| ცვლადი | მნიშვნელობა |
|--------|-------------|
| `DEPLOY_PATH` | მხოლოდ **base** (`.../backend`), არა სრული სერვისის path |
| `DEPLOY_LAYOUT=structured` | `runtime/` + `workspace/` (სავალდებულო `be dev`-ისთვის) |
| `DEPLOY_LAYOUT=flat` | legacy: `{DEPLOY_PATH}/{container}/` |
| `DEPLOY_PATH_MODE=full` | `DEPLOY_PATH` უკვე სრული სერვისის დირექტორიაა (suffix არ ემატება) |

საერთო SSH: `ops/config/deploy.env` — `DEPLOY_SERVER`, `DEPLOY_PROJECT`.

**`.env.deploy` გარეშე:** deploy იყენებს flat fallback-ს: `{SERVER_BASE}/{project}/backend/{container}/` (არა `geostat/<moduleId>/`).

**არა:** ცარიელი `geostat/retrieval/` ან `geostat/ingestion/` root-ზე — legacy `REMOTE=$PROJECT/$target` artifact; structured deploy მხოლოდ `backend/runtime/<container>/`.

---

## სამი loop (არ აურიო)

| მიზანი | ბრძანება | სერვერის path |
|--------|----------|----------------|
| Prod/staging JAR | `be deploy <svc> --prod\|--dev` | `runtime/{container}/` |
| JAR auto (Windows-ზე Gradle) | `be deploy watch` | `runtime/{container}/` |
| Source dev (Linux bootRun) | `be dev bootstrap` → `be dev watch` | `workspace/{container}/` |
| ლოკალური dev | `./gradlew bootRun`, `be compose up` | repo `apps/backend/` (SSH არა) |

დეტალი:

- JAR loop: [BE-DEPLOY-WATCH.md](./BE-DEPLOY-WATCH.md)
- Source dev: [BACKEND-DEV-REMOTE.md](./BACKEND-DEV-REMOTE.md)
- Dockerfile-ები: [REMOTE-DEV-JAR-FLOW.md](../kits/geostat-kit/docs/REMOTE-DEV-JAR-FLOW.md)

---

## ბრძანებები ()

```bash
# Structured JAR deploy (პირველად ან მიგრაციის შემდეგ)
./tools/geostat.sh be deploy geostat-chat-ai-api --dev

# JAR watch (runtime/)
./tools/geostat.sh be deploy watch geostat-chat-ai-api --dev

# Remote dev (workspace/) — საჭიროა DEPLOY_LAYOUT=structured
./tools/geostat.sh be dev bootstrap geostat-chat-ai-api
./tools/geostat.sh be dev watch

# Manage — ეძებს runtime/, შემდეგ legacy flat
./tools/geostat.sh be manage geostat-chat-ai-api status --dev
```

```bash
# მიგრაცია flat → runtime (სერვერზე)
bash kits/geostat-kit/toolkit/deploy/migrate-backend-layout.sh --dry-run --dev
bash kits/geostat-kit/toolkit/deploy/migrate-backend-layout.sh --dev
```

```powershell
# ლოკალური smoke (pytest + .env.deploy)
bash kits/geostat-kit/scripts/backend-ops-smoke.sh
```

---

## `be manage` და path discovery

`be manage` პოულობს სერვისებს ასე:

1. ლოკალური `docker-compose.{dev|prod}.yml` → compose service key
2. სერვერზე პირველი არსებული path:
   - `{DEPLOY_PATH}/runtime/{container}/docker-compose.<env>.yml`
   - `{DEPLOY_PATH}/workspace/{container}/...` *(მხოლოდ dev workspace — manage ძირითად runtime)*
   - `{DEPLOY_PATH}/{container}/...` *(legacy flat)*

---

## Package / ტესტები

| კომპონენტი | Path |
|-----------|------|
| Path resolver (Python) | `kits/geostat-kit/lib/deploy_paths.py` |
| Path resolver (Bash) | `kits/geostat-kit/toolkit/deploy/deploy-path.sh` |
| Deploy watch | `toolkit/deploy/deploy-watch.sh` |
| Dev remote | `toolkit/deploy/dev-remote.sh` |
| Migrate | `toolkit/deploy/migrate-backend-layout.sh` |
| Tests | `tests/test_backend_deploy_paths.py`, `test_backend_dev_remote.py`, `test_backend_deploy_watch.py`, `test_backend_smoke.py` |

---

## Migration checklist

1. შექმენი/შეამოწმე `ops/config/backend/.env.deploy` (`DEPLOY_LAYOUT=structured`).
2. `migrate-backend-layout.sh --dry-run` (თუ ძველი flat არსებობს).
3. `be deploy <svc> --dev` ან `--prod` → artifacts `runtime/`-ში.
4. `be manage <svc> status` — path `runtime/...` უნდა ჩანდეს.
5. Optional: `be dev bootstrap` → `workspace/`.
6. ძველი `apps/backend/{container}/` წაშალე სერვერზე, როცა ახალი რეჟიმი დადასტურდა.
