# Secrets — ერთიანი ხელწერა

ყველა რეალური მნიშვნელობა **მხოლოდ აქ**. `apps/frontend/` და `apps/backend/` — compose + კოდი, env არა.

**კონფიგის რუკა (canonical):** [../docs/CONFIG.md](../docs/CONFIG.md)  
**Ops bootstrap:** [../docs/GEOSTAT-INIT.md](../docs/GEOSTAT-INIT.md) — `geostat init` ქმნის env ფაილებს examples-დან  
**Ops სახელმძღვანო:** [../docs/GEOSTAT-KIT-SETUP.md](../docs/GEOSTAT-KIT-SETUP.md)

## სტრუქტურა

```
ops/config/
├── .env.example          # სრული კონტრაქტი (დოკუმენტაცია)
├── deploy.env.example
├── deploy.env            # საერთო SSH (gitignored)
├── ssh/                  # optional SSH key/config
├── profiles/             # legacy / worker-off snippets
├── frontend/
│   ├── .env.example, .env.dev, .env.prod, .env.deploy
├── backend/
│   ├── .env.example, .env.dev, .env.prod, .env.deploy
│   └── google-credentials.json
├── retrieval/            # Architecture B — RAG search (8092)
│   └── .env.example, .env.dev, .env.prod
├── ingestion/            # Architecture B — crawl/index (8093)
│   └── .env.example, .env.dev, .env.prod
└── infra/
    ├── .env.example, .env.deploy.example
    └── .env.dev, .env.deploy
```

Compose ფაილები — `apps/*/` და `ops/compose/` (არა `apps/frontend/` / `apps/backend/` ქვეშ config).

## ერთიანი წესი (frontend = backend)

| ფაილი | დანიშნულება |
|--------|-------------|
| **`.env.dev`** | ლოკალური development |
| **`.env.prod`** | production |
| **`.env.deploy`** | deploy სკრიპტები (მოდულ-სპეციფიკური) |
| **`deploy.env`** | საერთო `DEPLOY_SERVER`, optional `DEPLOY_SSH_*` |
| **`ssh/`** | პრივატული key / `config` (არა commit) — [ssh/README.md](ssh/README.md) |

### Backend layout (`ops/config/backend/.env.deploy`)

| `DEPLOY_LAYOUT` | სერვერზე |
|-----------------|----------|
| **`structured`** (რეკომენდებული) | `.../backend/runtime/{container}/` — JAR; `.../workspace/{container}/` — `be dev` |
| **`flat`** (legacy / default without file) | `.../backend/{container}/` — ყველაფერი ერთ ადგილას |

დოკი: [docs/BACKEND-DEPLOY-LAYOUTS.md](../docs/BACKEND-DEPLOY-LAYOUTS.md)

## ვინ კითხულობს

| ინსტრუმენტი | წყარო |
|-------------|--------|
| Spring `bootRun` / IDE (`local`) | `ops/config/backend/.env.dev` (import from `application-local.yml`) |
| GCP Speech | `ops/config/backend/google-credentials.json` |
| Vite (`npm run dev`) | `ops/config/frontend/.env.dev` (`--mode dev`) |
| Vite (`npm run build`) | `ops/config/frontend/.env.prod` (`--mode prod`) |
| `tools/geostat.ps1 fe compose` | `.env.dev` ან `.env.prod` |
| `tools/geostat.ps1 fe deploy` | `lib/env.ps1` → ყველა ფაილი |
| `tools/geostat.ps1 be compose` | `.env.dev` ან `.env.prod` |
| `tools/geostat.ps1 be deploy` | `ops/config/backend/.env.*`, `.env.deploy` (paths) |
| `tools/geostat.ps1 be dev` | `.env.deploy` (`DEPLOY_LAYOUT=structured`) |
| Docker compose | `../ops/config/<module>/.env.<env>` |

საერთო ბიბლიოთეკა: `kits/geostat-kit/lib/env.ps1`, `env.sh`

## პირველი დაყენება

```powershell
cd geostat-chat-ai\ops\config
copy deploy.env.example deploy.env
copy frontend\.env.example frontend\.env.dev
copy frontend\.env.example frontend\.env.prod
copy frontend\.env.deploy.example frontend\.env.deploy
copy backend\.env.example backend\.env.dev
copy backend\.env.example backend\.env.prod
copy backend\.env.deploy.example backend\.env.deploy
copy retrieval\.env.example retrieval\.env.dev
copy ingestion\.env.example ingestion\.env.dev
```

Compose ფაილები — `apps/frontend/`, `apps/backend/` (არა აქ).

## სერვერის წესები (გაზიარებული host)

სრული ცხრილი: [docs/GEOSTAT-KIT-SETUP.md §15](../docs/GEOSTAT-KIT-SETUP.md#15-სერვერის-წესები--რა-შეიძლება-რა-არ-უნდა).

| შეიძლება | არ უნდა |
|----------|---------|
| deploy/dev მხოლოდ `static/{container}/`, `runtime/{container}/`, `workspace/{container}/` | იგივე `DEPLOY_PATH` ორ პროექტზე |
| `fe manage app delete` → მხოლოდ `static/geostat-chat-ai-app/` | `DEPLOY_PATH_MODE=full` + base path = მთელი `apps/frontend/` წაშლა |
| `be manage api nuke` → მხოლოდ `runtime/geostat-chat-ai-api/` | `rm -rf .../geostat/frontend` ხელით |
| `be manage all nuke` | ყველა **ამ repo-ს** deployed backend სერვისი; images მხოლოდ ამ compose სახელებზე (არა global prune) |

`DEPLOY_PATH` = base (`.../geostat/frontend`), `DEPLOY_LAYOUT=structured`, `DEPLOY_PATH_MODE=base` — სწორი კომბინაცია.

## გარემო / პრაქტიკა

- [docs/ENVIRONMENT.md](../docs/ENVIRONMENT.md) — legacy სახელები, worker, nginx CSP, embed
- `ops/config/frontend/nginx.env` — CSP `frame-ancestors` → `tools/geostat nginx-gen`
- `ops/config/profiles/` — პროექტის მიმთითებები; ნიმუშები: `kits/geostat-kit/scaffold/profiles/`
