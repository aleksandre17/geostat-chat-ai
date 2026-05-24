# სერვერის deploy ხე — `geostat` გლობალური root

განახლება: **2026-05-22**  
დაკავშირება: [INFRA-DATA-STORES.md](INFRA-DATA-STORES.md) · [DOCKER-ECOSYSTEM.md](DOCKER-ECOSYSTEM.md) · [HYBRID-DEV-ARCHITECTURE.md](HYBRID-DEV-ARCHITECTURE.md) · [BACKEND-DEPLOY-LAYOUTS.md](../BACKEND-DEPLOY-LAYOUTS.md)

**სტატუსი:** დამტკიცებული (D-10, D-11). **იმპლემენტაცია infra slug:** done (2026-05-22).

---

## 1. რა არის root

`/home/administrator/geostat/` = **გლობალური სერვერის root** (`DEPLOY_PROJECT=geostat` in `ops/config/deploy.env`).

აქ ყველა consumer repo ურთავს **საერთო** `frontend/` და `backend/` base-ს; **ინფრა — პროექტზე ცალკე**:

```text
/home/administrator/geostat/          ← გლობალური root (DEPLOY_PROJECT)
├── frontend/                         ← fe DEPLOY_PATH (ყველა პროექტი)
│   └── static|compose/.../<container>/
├── backend/                          ← be DEPLOY_PATH (ყველა პროექტი)
│   ├── runtime/<container>/          ← JAR + compose
│   └── workspace/<container>/
└── infra/
    ├── geostat-chat-ai/             ← consumer A — სრული Postgres/Redis/Qdrant
    │   ├── .env.runtime
    │   └── compose/
    └── myshop/                       ← consumer B — სრული stack (სხვა პორტები)
        ├── .env.runtime
        └── compose/
```

**არა:** `geostat/backend/infra/` · **არა** ერთი shared `geostat/infra/compose/` ყველა პროექტისთვის.

---

## 2. `DEPLOY_PATH` — თითო მოდულის base

| მოდული | ფაილი | მაგალითი (ეს repo) |
|--------|-------|---------------------|
| Frontend | `ops/config/frontend/.env.deploy` | `DEPLOY_PATH=/home/administrator/geostat/frontend` |
| Backend (all java-boot) | `ops/config/backend/.env.deploy` | `DEPLOY_PATH=/home/administrator/geostat/backend` |
| Retrieval / Ingestion | `ops/config/retrieval/.env.deploy` (optional) | **იგივე** `.../backend` + `DEPLOY_LAYOUT=structured` — ან მხოლოდ backend `.env.deploy` + manifest `stack.deployBaseSecretsModule` |
| Infra | `ops/config/infra/.env.deploy` | `DEPLOY_PATH=/home/administrator/geostat/infra/geostat-chat-ai` |

საერთო SSH: `ops/config/deploy.env` — `DEPLOY_SERVER`, `DEPLOY_PROJECT=geostat`.

| ცვლადი | მნიშვნელობა |
|--------|-------------|
| `INFRA_SLUG` | consumer-ის სახელი (ჩვეულებრივ repo folder: `geostat-chat-ai`) |
| `INFRA_PREFIX` | Docker `container_name` პრეფიქსი (`geostat-chat-ai-postgres`) |
| `INFRA_COMPOSE_PROJECT` | compose project + volume prefix |
| `DOCKER_NETWORK` | ქსელი **ამ** პროექტის აპებისთვის (`geostat-chat-ai-net`) |

`geostat infra` fallback (თუ `DEPLOY_PATH` ცარიელია):  
`{DEPLOY_SERVER_BASE}/{DEPLOY_PROJECT}/infra/{INFRA_SLUG}` → `/home/administrator/geostat/infra/geostat-chat-ai`.

---

## 3. არტიფაქტი — რა სჭირდება / არა

| ფენა | სერვერზე რა მიდის | არტიფაქტი (JAR / dist) |
|------|-------------------|------------------------|
| **backend** (api + retrieval + ingestion) | `runtime/<container>/app.jar` + compose | **კი** |
| **frontend** | `static/<container>/dist/` ან compose | **კი** |
| **infra** | `infra/<slug>/compose/` + `.env.runtime` | **არა** — Docker Hub images |

ინფრა = **გაშვება** (`docker compose up`), არა ბილდი.

---

## 4. პორტები და კონფლიქტი (რამდენიმე პროექტი ერთ host-ზე)

ყოველი `infra/<slug>/` — **საკუთარი** host bind (მხოლოდ `127.0.0.1`):

| INFRA_SLUG | POSTGRES_PORT | REDIS_PORT | QDRANT_HTTP | შენიშვნა |
|------------|---------------|------------|-------------|----------|
| `geostat-chat-ai` | 5432 | 6379 | 6333 | პირველი პროექტი |
| `myshop` | 5433 | 6380 | 6335 | მეორე — `.env.dev`-ში იგივე |
| … | +1 | +1 | +2 | ცხრილი consumer repo-ში |

Hybrid tunnel: `geostat infra tunnel` — იყენებს **ამ** repo-ს `.env.dev` პორტებს.

### Backend-თან შედარება

| | Backend | Infra |
|--|---------|-------|
| Shared base | `geostat/backend` | `geostat/infra/` (მხოლოდ დირექტორია) |
| Per consumer | `runtime/<container>/` | `infra/<INFRA_SLUG>/compose/` |
| განსხვავება | სერვისის სახელი | slug + პორტები + network |

---

## 5. მეორე consumer repo (იგივე `geostat` root)

```properties
# ops/config/deploy.env (იგივე)
DEPLOY_PROJECT=geostat
DEPLOY_SERVER=administrator@192.168.1.199

# ops/config/infra/.env.deploy (myshop repo)
INFRA_SLUG=myshop
DEPLOY_PATH=/home/administrator/geostat/infra/myshop
```

```properties
# ops/config/infra/.env.dev
INFRA_PREFIX=myshop
INFRA_COMPOSE_PROJECT=myshop-infra
DOCKER_NETWORK=myshop-net
POSTGRES_PORT=5433
REDIS_PORT=6380
QDRANT_HTTP_PORT=6335
QDRANT_GRPC_PORT=6336
```

```bash
geostat infra remote up   # მხოლოდ myshop stack
```

Backend/frontend იგივე `geostat/backend` + `geostat/frontend`; განსხვავება — **უნიკალური** `container_name` და **ამ** პროექტის infra URL/პორტები.

---

## 6. მიგრაცია: ძველი `geostat/infra/compose/`

თუ stack უკვე გაშვებულია `geostat/infra/` (ქვეფოლდერის გარეშ):

1. `geostat infra remote down` (ძველი path)
2. განაახლე `DEPLOY_PATH` → `.../infra/geostat-chat-ai`
3. `.env.dev`-ში დაამატე `INFRA_PREFIX`, `INFRA_SLUG`, საჭიროებისამებრ პორტები
4. `geostat infra remote up` — ახალი container სახელები (`geostat-chat-ai-postgres`, …)

ძველი volumes (`geostat-infra_postgres_data`) რჩება Docker-ში — საჭიროებისამებრ `docker volume ls` / manual cleanup.

---

## 7. Prod / dev

| რეჟიმი | path |
|--------|------|
| Dev remote | `geostat/infra/<INFRA_SLUG>/` |
| Prod | იგივე + `docker-compose.prod.yml` |
| Hybrid | სერვერზე ინფრა + `infra tunnel` (პორტები `.env.dev`-დან) |

---

## 8. დამტკიცებული

| ID | გადაწყვეტილება |
|----|----------------|
| **D-10** | გლობალური root `geostat/`; siblings `frontend/`, `backend/`, `infra/`; infra **არა** `backend/infra` |
| **D-11** | **ყოველ** consumer-ს საკუთარი `infra/<INFRA_SLUG>/` stack; უნიკალური prefix, network, host ports |

---

## 9. რა **არ** გავაკეთოთ

| არასორსი | რატომ |
|----------|--------|
| ერთი `geostat/infra/compose/` ყველა პროექტზე | DB/redis/qdrant კონფლიქტი და გაზიარებული მონაცემები |
| იგივე `INFRA_PREFIX` / `POSTGRES_PORT` ორ repo-ზე | Docker container/port კონფლიქტი |
| `geostat/retrieval/` ან `geostat/ingestion/` root-ზე | Legacy mkdir artifact; D-10 = `backend/runtime/<container>/` only |

---

## 10. მიმდინარე სერვერი vs target (2026-05-23)

| ფენა | Target (D-10) | Live (`192.168.1.199`) |
|------|---------------|------------------------|
| **infra** | `infra/geostat-chat-ai/compose/` | ✅ `geostat-chat-ai-postgres`, `-redis`, `-qdrant`, `-rabbitmq` |
| **backend api (structured)** | `backend/runtime/geostat-chat-ai-api/` | ✅ container `geostat-chat-ai-api` |
| **backend api (legacy name)** | — | ⚠️ container `geostat-chat-api` — **იგივე runtime path**; **არ ვაჩერებთ** |
| **frontend app (structured)** | `frontend/static/geostat-chat-ai-app/` | ✅ container `geostat-chat-ai-app` |
| **retrieval / ingestion** | `backend/runtime/geostat-chat-ai-{retrieval,ingestion}/` | ✅ structured deploy |

**P6-migrate:** ✅ applied — flat `geostat-chat-api`/`geostat-chat-app` dirs აღარ არის shared base-ზე; structured paths primary. `geostat layout migrate --prod` + `stack-deploy --prod` (2026-05-23).

**Legacy artifact cleanup:** ცარიელი `geostat/retrieval/` და `geostat/ingestion/` — `geostat layout cleanup --legacy-segments` (მხოლოდ ცარიელი dir-ები).
