# ინფრა: Postgres, Redis, Qdrant

განახლება: **2026-05-21**  
დაკავშირება: [SERVER-DEPLOY-LAYOUT.md](SERVER-DEPLOY-LAYOUT.md) · [DOCKER-ECOSYSTEM.md](DOCKER-ECOSYSTEM.md) · [HYBRID-DEV-ARCHITECTURE.md](HYBRID-DEV-ARCHITECTURE.md) · [PROJECT-PLAN.md](PROJECT-PLAN.md) · [kits/geostat-kit/docs/DEV-MODES.md](../../kits/geostat-kit/docs/DEV-MODES.md)

ეს დოკი აღწერს **რისთვის** გვჭირდება relational/queue/vector store-ები, **სად** ცხოვრობენ (Linux Docker), და **როდის** ჩავრთოთ ფაზებში. იმპლემენტაციის ბრძანებები — შემდეგ `geostat infra` (იხ. hybrid დოკი).

---

## 1. საერთო პრინციპი (owner-architecture + kit)

| ფენა | სად | რატომ |
|------|-----|--------|
| **ინფრა** (Postgres, Redis, Qdrant) | `ops/compose/infra/` — **არა** `apps/*`-ში | ერთი ქსელი `geostat-chat-ai-net` — [DOCKER-ECOSYSTEM.md](DOCKER-ECOSYSTEM.md) |
| **სერვისები** (chat, ingestion, retrieval) | `apps/*/docker-compose.dev.yml` + stack catalog | უკვე არსებული პატერნი |
| **პაროლები / URL** | `ops/config/<module>/.env.*` + `.example` | secrets git-ში არა |

**Dev (Windows):** Docker Desktop-ზე შეიძლება ლოკალური ინფრა (რეჟიმი ②), მაგრამ **ჩვენი ძირითადი dev** = hybrid: ინფრა **remote Linux** (რეჟიმი ④).  
**Prod:** იგივე compose სტეკი Linux VPS-ზე.

---

## 2. რისთვის გვჭირდება (და რისთვის **არა**)

### PostgreSQL

| გამოყენება | სერვისი | პრიორიტეტი |
|------------|---------|------------|
| **Ingestion jobs** — სტატუსი, crawl run, URL queue metadata, შეცდომები | `ingestion-service` | **მაღალი** (P3) |
| **Crawl audit** — რა URL დაინდექსდა, ვერსია, ბოლო crawl | ingestion | საშუალო |
| **Chat metadata** (სესია id, user id თუ გამოჩნდება) — **არა** LLM ისტორია | chat-api | დაბალი (ჯერ Caffeine საკმარისია) |
| **ვექტორები** | — | **არა** — ამისთვის **Qdrant** |

Postgres **არ ცვლის** Qdrant-ს.

### Redis

| გამოყენება | სერვისი | პრიორიტეტი |
|------------|---------|------------|
| **სესიის ისტორია** — რამდენიმე chat-api ინსტანსი | chat-api | საშუალო (`ConversationHistory` → Redis) |
| **Cache** similarity / hot chunks | retrieval | optional |
| **საბოლოო ცოდნის store** | — | **არა** |

### RabbitMQ (P5 — approved)

| გამოყენება | სერვისი | შენიშვნა |
|------------|---------|----------|
| **Cross-service async** crawl done → index, reindex, future workers | ingestion → consumers | OSS self-host `ops/compose/infra`; **არა** paid SaaS |
| **chat → retrieval** | — | **არა** — sync HTTP რჩება |

ფაზა 5: **RabbitMQ** (BACKLOG B-01). Redis Streams აღარაა default — Redis session/cache-ისთვის რჩება.

### Qdrant

| გამოყენება | სერვისი | პრიორიტეტი |
|------------|---------|------------|
| Embeddings + similarity search | retrieval + ingestion write | **მაღალი** (P4) |

---

## 3. რეკომენდებული განლაგება (repo + სერვერი)

**Repo (კოდი):**

```text
ops/compose/
├── infra/
│   ├── docker-compose.yml      # postgres + redis + qdrant
│   └── README.md
└── stack/
    ├── docker-compose.yml      # აპები (prod/full local docker)
    └── ...
```

**სერვერი (remote, არა artifact):** [SERVER-DEPLOY-LAYOUT.md](SERVER-DEPLOY-LAYOUT.md)

```text
/home/administrator/geostat/     ← გლობალური DEPLOY_PROJECT root
  frontend/ | backend/            ← shared bases
  infra/
    geostat-chat-ai/             ← consumer slug (ერთი stack პროექტზე)
      compose/ + .env.runtime
    <other-slug>/                 ← სხვა პროექტი — საკუთარი DB/Redis/Qdrant
```

**სერვისების სახელები (Docker network სერვერზე):** `postgres`, `redis`, `qdrant`.

**იმიჯები (სტანდარტი):**

- `postgres:16-alpine` (ან `17-alpine`)
- `redis:7-alpine`
- `qdrant/qdrant`

**ვოლიუმები:** `postgres_data`, `redis_data`, `qdrant_storage` — dev-ში `down` **without** `-v` ნაგულისხმევად, რომ მონაცემები არ წაიშალოს.

**პორტები სერვერის host-ზე (hybrid / tunnel):**

| სერვისი | პორტი | შენიშვნა |
|---------|-------|----------|
| Postgres | 5432 | bind `127.0.0.1:5432:5432` სერვერზე — tunnel/LAN |
| Redis | 6379 | იგივე |
| Qdrant | 6333 | HTTP API |

---

## 4. Env (ინფრა + hybrid)

**სერვერზე compose:** `ops/config/infra/.env.dev` (პაროლი, DB name).

**ლოკალური აპები (hybrid):** `INFRA_HOST` — იხ. [HYBRID-DEV-ARCHITECTURE.md](HYBRID-DEV-ARCHITECTURE.md) §4.

```properties
SPRING_DATASOURCE_URL=jdbc:postgresql://${INFRA_HOST}:5432/geostat
SPRING_DATA_REDIS_HOST=${INFRA_HOST}
SPRING_DATA_REDIS_PORT=6379
QDRANT_URL=http://${INFRA_HOST}:6333
```

---

## 5. ფაზური გაშვება

| ეტაპი | Postgres | Redis | RabbitMQ | Qdrant |
|--------|----------|-------|----------|--------|
| **ახლა** P2 (chat→retrieval HTTP) | არა | არა | არა | არა (stub OK) |
| **P3** ingestion pipeline | **კი** — jobs + crawl state | optional | in-process ჯერ | write ბოლოს |
| **P4** retrieval | optional metadata | არა | არა | **კი** |
| **P5** async index | jobs ისტორია Postgres-ში | session/cache | **კი** | კი |
| **P6** full stack prod | compose-ში ყველა | compose-ში | compose-ში | compose-ში |

ანუ: **Postgres — P3**; **RabbitMQ — P5 async**; **Redis — cache/session**; **Qdrant — P4**. ინფრა compose-ში ფაზებად.

---

## 6. Dev vs Prod

| | Dev (hybrid) | Prod |
|---|--------------|------|
| **Compose** | `geostat infra remote up` | იგივე + prod overlay / secrets |
| **წვდომა Windows-დან** | SSH tunnel → `INFRA_HOST=127.0.0.1` | VPN/LAN ან internal only |
| **მიგრაციები** | Flyway/Liquibase per schema | იგივე |
| **Redis persistence** | dev: optional | prod: AOF რეკომენდებული |
| **Postgres backup** | არა აუცილებელი | volume snapshot / `pg_dump` |

---

## 7. გადაწყვეტილების კითხვები (ინფრა)

| ID | კითხვა | რეკომენდაცია (წინასწარი) | სტატუსი |
|----|--------|---------------------------|---------|
| Q-14 | ერთი Postgres cluster vs DB per service | **closed** — ერთი cluster, schema `ingestion` (ingestion-service owner) | closed |
| Q-15 | Redis persistence dev/prod | dev: without OK; prod: AOF | **closed** — `redis.yml` `REDIS_AOF:-yes` |
| Q-16 | Infra compose ცალკე vs მხოლოდ full stack | **ორივე** (D-08): infra + apps `external` network | **closed** — [DOCKER-ECOSYSTEM.md](DOCKER-ECOSYSTEM.md) §3.2 |
| Q-17 | `apps/backend/worker` | **არა** Postgres consumer — ingestion-service = worker (B-05) | **closed** |

დამტკიცებული: [approved/README.md](approved/README.md) — D-06 (stores), D-07…D-09 (network), **D-10** (server tree).

---

## 8. რა **არ** გავაკეთოთ

| არასორსი | რატომ |
|----------|--------|
| Postgres/Redis native Windows install | განსხვავებული prod; hybrid = remote |
| ვექტორები Postgres-ში (pgvector) თუ Qdrant approved | Qdrant dominant სკრინშოტებში |
| ინფრა `apps/backend/docker-compose.dev.yml`-ში | ბინძური ზღვარი |
| საჯარო `0.0.0.0:5432` ინტერნეტზე | tunnel/VPN |

---

## 9. manifest (სამიზნე)

```json
"infra": {
  "role": "infra",
  "type": "compose-stack",
  "path": "ops/compose/infra",
  "secretsModule": "infra",
  "networkName": "geostat-chat-ai-net",
  "remoteOnly": true
}
```

სრული ქსელი / external network: [DOCKER-ECOSYSTEM.md](DOCKER-ECOSYSTEM.md).

CLI: `geostat infra remote up|down|sync`, `geostat infra local up`, `geostat infra tunnel` — [ops/compose/infra/README.md](../../ops/compose/infra/README.md).

---

## 10. PLAN მაპინგი

| PLAN ID | ამოცანა |
|---------|---------|
| P0-infra-01 | `ops/compose/infra/docker-compose.yml` |
| P0-infra-02 | `ops/config/infra/.env.example` |
| P0-infra-03 | `geostat infra remote up` + health |
| P0-infra-04 | `geostat infra tunnel` script |
| P4-01 | Qdrant in infra compose (უკვე proposed) |
| P3-* | Postgres wiring ingestion-ზე (`hybrid` profile) |
| P5-* | RabbitMQ (`hybrid` / server DNS) |
| P6-01 | stack + infra ერთ `geostat-chat-ai-net` (external network) |
| P6-02 | catalog: retrieval + ingestion templates, იგივე network |
