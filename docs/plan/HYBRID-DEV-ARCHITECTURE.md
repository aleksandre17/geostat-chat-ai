# ჰიბრიდული დეველოპმენტი — აპები ლოკალურად, ინფრა remote Linux-ზე

განახლება: **2026-05-21**  
დაკავშირება: [SERVER-DEPLOY-LAYOUT.md](SERVER-DEPLOY-LAYOUT.md) · [DOCKER-ECOSYSTEM.md](DOCKER-ECOSYSTEM.md) · [INFRA-DATA-STORES.md](INFRA-DATA-STORES.md) · [PROJECT-PLAN.md](PROJECT-PLAN.md) · [kits/geostat-kit/docs/DEV-MODES.md](../../kits/geostat-kit/docs/DEV-MODES.md) · [docs/BACKEND-DEV-REMOTE.md](../BACKEND-DEV-REMOTE.md)

**სტატუსი:** დამტკიცებული owner-ის მიერ (2026-05-21) — იმპლემენტაცია pending (`P0-infra-*`, kit `DEV-MODES` §④).

---

## 1. პრობლემა ერთი წინადადებით

| სად | რა |
|-----|-----|
| **Windows (host)** | chat-api, retrieval, ingestion, frontend — `gradlew bootRun`, `npm run dev`, Run and Debug |
| **Linux server (Docker)** | Postgres, Redis, Qdrant — **არა** Windows-ზე |
| **კავშირი** | ლოკალური JVM **არ ხედავს** Docker DNS-ს (`postgres`, `redis`) — მხოლოდ **სერვერის IP/პორტი** ან **SSH tunnel → localhost** |

ეს არის **④ Hybrid** — არა ახალი deploy ტიპი, არამედ **① (ლოკალური apps)** + **③-ის ინფრა ნაწილი** (`DEPLOY_SERVER` იგივე).

---

## 2. სამი ფენა (Clean Architecture / ops)

```text
┌─────────────────────────────────────────────────────────────┐
│  A. Apps (სერვისები) — სად გაშვება არჩევადია          │
│     chat :8090 │ retrieval :8092 │ ingestion :8093 │ fe   │
└───────────────────────────┬─────────────────────────────────┘
                            │ HTTP + JDBC + Redis client
┌───────────────────────────▼─────────────────────────────────┐
│  B. Infra (state) — hybrid-ში: REMOTE Linux Docker ONLY     │
│     postgres:5432 │ redis:6379 │ qdrant:6333                  │
└───────────────────────────┬─────────────────────────────────┘
                            │ SSH / LAN / tunnel
┌───────────────────────────▼─────────────────────────────────┐
│  C. Linux dev server (იგივე რაც be/fe dev watch)            │
│     ops/compose/infra/  +  geostat infra remote up        │
└─────────────────────────────────────────────────────────────┘
```

- **A** — უკვე გაქვთ: `geostat be dev watch` (სერვერზე) **ან** F5 / `bootRun` (ლოკალურად).
- **B** — [INFRA-DATA-STORES.md](INFRA-DATA-STORES.md): `ops/compose/infra/`.
- **C** — `ops/config/deploy.env` (`DEPLOY_SERVER`).

---

## 3. დევ რეჟიმების მატრიცა (DEV-MODES გაფართოება)

არსებული kit გზამკვლევი: [DEV-MODES.md](../../kits/geostat-kit/docs/DEV-MODES.md). დავამატებთ **④ Hybrid**.

| რეჟიმი | Apps | Infra (PG / Redis / Qdrant) | ტიპური ბრძანება |
|--------|------|-----------------------------|------------------|
| **① Local host** | ყველა Windows | არა / mock | F5 Full stack |
| **② Local Docker** | localhost კონტეინერები | optional localhost | `geostat stack up` |
| **③ Remote watch** | სერვერზე Docker | სერვერზე (`postgres` DNS) | `be/fe dev watch` |
| **④ Hybrid** | **Windows host** | **მხოლოდ სერვერზე** | `infra remote up` + tunnel + local `bootRun` |
| **④b Partial hybrid** | ნაწილი local, ნაწილი remote | remote | env `*_BASE_URL` per peer |

**ყოველდღიური სცენარი (owner):** **④ Hybrid** + სურვილისამებრ **④b**.

### ③ vs ④ განსხვავება

| | ③ Remote watch | ④ Hybrid |
|--|----------------|----------|
| კოდის sync | rsync → server workspace | ლოკალური `apps/*` |
| Gradle/Java | კონტეინერში Linux-ზე | Windows host |
| DB hostname | `postgres` (Docker network) | `${INFRA_HOST}` (tunnel/LAN) |
| Breakpoints | არა (CLI) | **კი** (Run and Debug) |

---

## 4. კომუნიკაცია — ოქროს წესი

### 4.1 შიდა Docker (მხოლოდ სერვერზე, ყველა კონტეინერი იქ)

```properties
jdbc:postgresql://postgres:5432/geostat
```

### 4.2 ლოკალური Spring Boot Windows-დან

```properties
jdbc:postgresql://${INFRA_HOST}:5432/geostat
```

**არასორ:** `postgres` hostname Windows-ზე.

| მიდგომა | `INFRA_HOST` | უსაფრთხოება |
|---------|--------------|-------------|
| **SSH tunnel (რეკომენდაცია dev-ში)** | `127.0.0.1` | პორტები არ იხსნება ინტერნეტზე |
| **LAN / VPN** | `192.168.x.x` | firewall მხოლოდ dev IP |
| **საჯარო VPS :5432** | IP | **არა** prod/dev თუ შეიძლება |

### 4.3 SSH tunnel (ერთი სესია — სამი პორტი)

```powershell
ssh -N -L 5432:127.0.0.1:5432 -L 6379:127.0.0.1:6379 -L 6333:127.0.0.1:6333 user@DEPLOY_SERVER
```

შემდეგ ყველა ლოკალური სერვისი: `INFRA_HOST=127.0.0.1`.

**სამიზნე CLI:** `geostat infra tunnel` (იგივე პორტები, `deploy.env`-დან `DEPLOY_SERVER`).

**Docker ქსელი vs hybrid host:** სრული ეკოსისტემა, `geostat-chat-ai-net`, შიდა/გარე URL — [DOCKER-ECOSYSTEM.md](DOCKER-ECOSYSTEM.md) §3–§5.

---

## 5. Env / Spring profiles (ერთი წყარო ჭეშმარიტი)

`ops/config/<module>/.env.dev` — არსებული პატერნი. Hybrid-ისთვის profile + ცვლადები (მაგალითი — **არა** რეალური secrets repo-ში):

```properties
# ops/config/backend/.env.dev — hybrid ნაწილი
SPRING_PROFILES_ACTIVE=dev,hybrid
INFRA_HOST=127.0.0.1
SPRING_DATASOURCE_URL=jdbc:postgresql://${INFRA_HOST}:5432/geostat
SPRING_DATA_REDIS_HOST=${INFRA_HOST}
SPRING_DATA_REDIS_PORT=6379
QDRANT_URL=http://${INFRA_HOST}:6333
RETRIEVAL_BASE_URL=http://localhost:8092
```

### 5.1 Peer URL მატრიცა (ცალ-ცალკე / ნაწილობრივი hybrid)

| ცვლადი | ლოკალური chat | ლოკალური retrieval | remote retrieval (④b) |
|--------|---------------|--------------------|------------------------|
| `RETRIEVAL_BASE_URL` | `http://localhost:8092` | — | `http://SERVER:8092` |
| `QDRANT_URL` | — | `http://${INFRA_HOST}:6333` | იგივე |
| DB / Redis | ingestion / chat (როცა P3+) | ingestion | `${INFRA_HOST}` |

**Frontend:** `ops/config/frontend/.env.dev` — `VITE_API_URL=http://localhost:8090`.

**პრინციპი:** ყოველი peer = ერთი env URL; ცალ-ცალკე გაშვება = იგივე env, მხოლოდ არ გაუშვა სერვისი, რომელიც არ გჭირდება.

### 5.2 `docker` vs `hybrid` profile (სრული ცხრილი)

იხ. [DOCKER-ECOSYSTEM.md](DOCKER-ECOSYSTEM.md) §5 — `postgres`/`redis`/`qdrant`/`retrieval` hostname-ები ორივე profile-ში.

### 5.3 ingestion / retrieval (მომავალი)

`ops/config/ingestion/.env.dev`, `ops/config/retrieval/.env.dev` — იგივე `INFRA_HOST` + სერვის-სპეციფიკური პორტები (`8093`, `8092`).

---

## 6. ცალ-ცალკე და ნაწილობრივი გაშვება

```text
ნაბიჯი 0 (დღის დასაწყისში / ერთხელ)
  geostat infra remote up      # SSH → server: postgres + redis + qdrant
  geostat infra tunnel         # ან ხელით ssh -L (ტერმინალი ღია)

ნაბიჯი 1 — მხოლოდ ინფრა
  → tunnel + health (psql / redis-cli / curl qdrant /health)

ნაბიჯი 2 — ერთი აპი
  chat-api:      geostat hybrid boot be       (:8090)
  retrieval:     geostat hybrid boot ret      (:8092)
  ingestion:     geostat hybrid boot ing      (:8093)
  frontend:      geostat hybrid boot fe       (:5173)
                 # alias: geostat fe run | be run | ret run | ing run
                 # legacy wrapper: ops/ci/hybrid-boot-app.ps1 -Service fe

ნაბიჯი 3 — ნაწილობრივი stack
  infra + chat + fe                 # UI, retrieval stub
  infra + retrieval + ingestion     # pipeline, chat გარეშე
  infra + ყველა app + fe            # სრული hybrid
```

**Run and Debug (სამიზნე):** compound — `Hybrid: infra tunnel + chat + fe` (tunnel = `preLaunchTask`).

**არ აურიოთ:** `apps/backend/worker` (legacy compose placeholder) — RAG worker = `ingestion-service` (B-05).

**geostat CLI (არსებული + სამიზნე):**

| მიზანი | ბრძანება | სტატუსი |
|--------|----------|---------|
| მხოლოდ chat | `geostat hybrid boot be` ან `geostat be run` | ✅ |
| მხოლოდ retrieval | `geostat hybrid boot ret` ან `geostat ret run` | ✅ |
| მხოლოდ ingestion | `geostat hybrid boot ing` ან `geostat ing run` | ✅ |
| UI (hybrid ④) | `geostat hybrid boot fe` ან `geostat fe run` | ✅ |
| ინფრა remote | `geostat infra remote up` | ✅ |
| tunnel | `geostat infra tunnel` | ✅ |
| apps სერვერზე (არა hybrid) | `geostat be dev watch` / `geostat fe dev watch` | ✅ |

---

## 7. სერვერზე infra compose

ფაილი (სამიზნე): `ops/compose/infra/docker-compose.yml`

- სერვისები: `postgres`, `redis`, `qdrant`
- `ports`: bind **`127.0.0.1:5432:5432`** (და 6379, 6333) — tunnel/LAN, არა `0.0.0.0` მთელ ინტერნეტზე
- named volumes + healthcheck
- network: `geostat-infra` (სახელი manifest-იდან)
- secrets: `ops/config/infra/.env.dev` (server compose)

**Remote up (კონცეპტი):**

```powershell
.\tools\geostat.ps1 infra remote up      # rsync ops/compose/infra + ssh compose up -d
.\tools\geostat.ps1 infra remote down
.\tools\geostat.ps1 infra tunnel       # ssh -L ...
```

### 7.1 manifest (სამიზნე `geostat.ops.json`)

```json
"infra": {
  "role": "infra",
  "type": "compose-stack",
  "path": "ops/compose/infra",
  "secretsModule": "infra",
  "remoteOnly": true
}
```

---

## 8. სქემა — სრული hybrid

```text
  Windows (dev laptop)
  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌─────────┐
  │ chat-api     │  │ retrieval    │  │ ingestion    │  │ Vite FE │
  │ :8090        │──│ :8092        │  │ :8093        │  │ :5173   │
  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘  └────┬────┘
         │                 │                  │               │
         │    localhost HTTP (peer სერვისები)               │
         └─────────────────┴──────────────────┘               │
                               │                                │
         INFRA_HOST:5432 / 6379 / 6333 (tunnel ან LAN)         │
                               │                                │
         ══════════════════════╪════════════════════════════════╪══  SSH -L
                               ▼
  Linux server (DEPLOY_SERVER)
  ┌────────────────────────────────────────────────────────────┐
  │  docker compose — ops/compose/infra                        │
  │   postgres:5432   redis:6379   qdrant:6333                 │
  └────────────────────────────────────────────────────────────┘
```

### 8.1 Partial hybrid (④b)

- chat **ლოკალური**, retrieval **სერვერზე** (`be dev watch` retrieval):  
  `RETRIEVAL_BASE_URL=http://SERVER:8092`, ingestion/chat კვლავ `INFRA_HOST` tunnel-ზე.
- ყველა app სერვერზე, მხოლოდ FE ლოკალური: `VITE_API_URL=http://SERVER:8090` — არსებული პატერნი DEV-MODES ①.

### 8.2 სერვერზე ყველაფერი (③) + ინფრა იგივე host-ზე

`be dev watch` + `infra remote up` **იგივე Linux-ზე** → apps იყენებენ `postgres` DNS (არა tunnel). Windows-დან მხოლოდ SSH/ბრაუზერი.

---

## 9. არსებული DEV-MODES სქემა (რეფერენსი)

```text
                    ┌─────────────────────────────────────┐
                    │  შენი ლეპტოპი (Windows / Mac)        │
                    └─────────────────────────────────────┘
         │                              │
         │ ① ლოკალური, NO Docker         │ ② ლოკალური Docker
         │    Run and Debug             │    localhost
         │ ④ Hybrid: ① apps + remote B  │
         │                              │
         └──────────────┬───────────────┘
                        │ SSH + rsync / tunnel
                        ▼
                    ┌─────────────────────────────────────┐
                    │  Linux სერვერი                        │
                    │  ③ fe|be dev watch                    │
                    │  B: infra compose (postgres/redis/…)  │
                    └─────────────────────────────────────┘
```

---

## 10. რა **არ** გავაკეთოთ

| არასორსი | რატომ |
|----------|--------|
| Postgres/Redis native Windows install | owner შეზღუდვა; prod parity |
| ლოკალურ `.env`-ში `postgres` hostname | Windows-ზე არ resolve-დება |
| ინფრა `apps/backend/docker-compose.dev.yml`-ში | ზღვარი; მხოლოდ `ops/compose/infra` |
| ყოველ save-ზე infra redeploy | infra ცალკე ციკლი; apps — bootRun/watch |
| Hybrid = „ნახევრად remote deploy“ | ორი დამოუკიდებელი ღვედი: apps + infra |
| API keys committed `.env.dev` | მხოლოდ `.example` repo-ში |

---

## 11. დამტკიცებული გადაწყვეტილებები (D-01 … D-06)

| ID | გადაწყვეტილება | სტატუსი |
|----|----------------|---------|
| **D-01** | Dev რეჟიმი **④ Hybrid**: apps local (Windows), infra remote Linux Docker | **approved** 2026-05-21 |
| **D-02** | Infra = `ops/compose/infra` + `geostat infra remote` + `geostat infra tunnel` | **approved** |
| **D-03** | ყველა კავშირი infra-თან = `INFRA_HOST` + პორტები; tunnel default dev-ში | **approved** |
| **D-04** | Peer სერვისები = `*_BASE_URL` env (localhost vs SERVER per service) | **approved** |
| **D-05** | Spring profile `hybrid` — chat / retrieval / ingestion (როცა DB/Redis ჩაერთვება) | **approved** |
| **D-06** | P3 Postgres + P4 Qdrant + P5 Redis — იგივე remote infra compose | **approved** |

ინფრა დეტალი: [INFRA-DATA-STORES.md](INFRA-DATA-STORES.md).  
Kit დოკის განახლება pending: `DEV-MODES.md` §④, `LOCAL-DEBUG.md` hybrid compound.

---

## 12. PLAN მაპინგი

| ID | ამოცანა | სტატუსი |
|----|---------|---------|
| P0-infra-01 | `ops/compose/infra/docker-compose.yml` | **approved** |
| P0-infra-02 | `ops/config/infra/.env.example` | **approved** |
| P0-infra-03 | `geostat infra remote up` + health | **approved** |
| P0-infra-04 | `geostat infra tunnel` script | **approved** |
| P0-infra-05 | `geostat.ops.json` module `infra` | **approved** |
| P0-infra-06 | Spring `hybrid` profile + env docs per module | **approved** |
| P0-infra-07 | VS Code compound / preLaunch tunnel task | **done** | `vscode_gen` — `Hybrid: infra tunnel + API + UI` |
| P0-infra-08 | Consumer delegate `ops/ci/hybrid-boot-app.ps1` → `geostat hybrid boot` | **done** |
| P0-kit-12 | Kit: `geostat hybrid boot` + `fe run` / java-boot `run` for hybrid ④ | **done** — `toolkit/hybrid/Invoke-HybridRun.ps1` |
| P2-02+ | `RETRIEVAL_BASE_URL`, `INFRA_HOST` in ops/config | **approved** (გაფართოება hybrid-ით) |

**ფაზა 0b (ახალი):** Hybrid infra + tunnel — იმპლემენტაცია P2-მდე ან პარალელურად.

**ფაზა 6 (prod):** სრული stack — infra+apps **ყველა სერვერზე** (არა hybrid).

---

## 13. პრაქტიკული დღის workflow

1. **დილით:** `geostat infra remote up` (სერვერზე PG/Redis/Qdrant).
2. **`geostat infra tunnel`** — ცალკე ტერმინალი, ღია დატოვე.
3. **Cursor / CLI:** `geostat hybrid boot be` (+ ret, ing, fe) — ან `geostat be run`; wrapper `hybrid-boot-app.ps1` delegate.
4. ~~**`npm run dev`** frontend~~ → **`hybrid-boot-app.ps1 -Service fe`** (ან მომავალში `geostat hybrid boot fe`).
5. **საღამოს:** `infra remote down` ან დატოვე running dev server-ზე.

**როცა მხოლოდ Java Linux-ზე გინდა:** `be dev watch` + infra იგივე host-ზე → `postgres` DNS, tunnel არა საჭირო სერვერზე.

---

## 14. შეჯამება

**ჰიბრიდი** = ორი დამოუკიდებელი ღვედი: **(A)** აპლიკაციის სერვისები — DEV-MODES ①; **(B)** state — infra compose DEV-MODES ③ მხოლოდ `ops/compose/infra`. კომუნიკაცია — **env URL მატრიცა**, არა ერთი shared Docker network Windows-ზე. ცალ-ცალკე გაშვება: infra ჩართული რჩება, apps ემატება env-ის მიხედვით.
