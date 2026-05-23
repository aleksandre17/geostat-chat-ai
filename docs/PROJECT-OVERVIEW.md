# geostat-chat-ai — პროექტის მიმოხილვა

> სრული სურათი: რას ვაკეთებთ, სად, რა გვინდა, რა დარჩა, pipeline და ops.  
> GitHub: [aleksandre17/geostat-chat-ai](https://github.com/aleksandre17/geostat-chat-ai) · **ვერსია:** [VERSION](../VERSION) · Ops პაკეტი: [aleksandre17/geostat-kit](https://github.com/aleksandre17/geostat-kit) (submodule @ `bdbd183`)

---

## 1. რა არის ეს პროექტი

**geostat-chat-ai** — Geostat-ის AI ჩატბოტი **RAG pipeline**-ით: საიტის კონტent-ის crawl → ინდექსი → semantic search → პასუხი მომხმარებლის კითხვაზე.

| | |
|--|--|
| **UI** | React (Vite) — embeddable widget |
| **Chat API** | Spring Boot — Gemini, topics, speech |
| **Retrieval** | Spring Boot — Qdrant search |
| **Ingestion** | Spring Boot — crawl, chunk, embed, index |
| **Ops** | [geostat-kit](https://github.com/aleksandre17/geostat-kit) — manifest-driven CLI, compose, deploy |

**არქიტექტურა:** Clean Architecture, SOLID, **Architecture B** (ცალკე deployable სერვისები), manifest = `geostat.ops.json`.

---

## 2. რა გვინდა (მიზანი)

1. **ჭკვიანი ჩატი** — Geostat portal-ის კონტენტზე დაფუძნებული პასუხები (არა მხოლოდ static links).
2. **სრული RAG pipeline** — crawl → Postgres state → chunk → embed → Qdrant → retrieval → chat prompt.
3. **Production-ready ops** — ერთი manifest, compose-gen, hybrid dev (apps ლოკალურად, infra remote), structured server deploy.
4. **გამოყოფილი პაკეტი** — `geostat-kit` სხვა პროექტებშიც გამოყენებადი (არა chat-ში ჩაშენებული ops).
5. **Senior-level სტრუქტურა** — `apps/` (პროდუქტი), `kits/` (ops), `ops/config/` (secrets), `docs/plan/` (ცოცხალი გეგმა).

---

## 3. სად რა არის (repo სტრუქტურა)

```text
geostat-chat-ai/
├── geostat.ops.json          # კონტრაქტი: modules, infra, compose, CI
├── apps/
│   ├── frontend/             # UI (Vite/React)           :5177
│   ├── backend/              # chat-api (Spring Boot)    :8090
│   ├── retrieval-service/    # RAG search                :8092
│   └── ingestion-service/    # crawl / index pipeline    :8093
├── libs/
│   ├── platform-contracts/   # DTO + ports (სერვისებს შორის)
│   └── embedding-adapters/   # hash-v1 / Gemini / Ollama embed
├── kits/
│   └── geostat-kit/          # git submodule → GitHub (ops framework)
├── ops/
│   ├── config/               # secrets (.env*, SSH) — gitignored
│   ├── compose/              # catalog + generated stack + infra
│   └── ci/                   # integration smoke, E2E scripts
├── tools/geostat.ps1         # thin CLI → kit
└── docs/                     # ADR, plan, runbooks
```

**საზღვრები:**

| ფოლდერი | რაა | რა არაა |
|---------|-----|---------|
| `apps/*` | ბიზნეს ლოგიკა, API, UI | deploy scripts, prod secrets |
| `kits/geostat-kit/` | CLI, compose-gen, drivers | consumer brand, API keys |
| `ops/config/` | env, credentials, SSH | application source |
| `libs/` | shared contracts, embedding | Spring apps |

→ [ROOT-LAYOUT.md](ROOT-LAYOUT.md) · [kits/README.md](../kits/README.md)

---

## 4. სერვისები და პორტები

| სერვისი | Path | Manifest id | Port | როლი |
|---------|------|-------------|------|------|
| **UI** | `apps/frontend` | `frontend` | 5177 | React chat widget |
| **chat-api** | `apps/backend` | `backend` | 8090 | Chat, Gemini, TTS/STT, retrieval client |
| **retrieval** | `apps/retrieval-service` | `retrieval` | 8092 | Qdrant vector search |
| **ingestion** | `apps/ingestion-service` | `ingestion` | 8093 | Crawl, chunk, embed, index |

**ქსელი (Docker):** `geostat-chat-ai-net` (`geostat.ops.json` → `stack.networkName`)

**ინფრა** (`ops/compose/infra/`): Postgres, Redis, Qdrant, RabbitMQ — manifest-ით ირჩევა `stack.infra.services`.

---

## 5. სრული RAG pipeline

```text
                    ┌─────────────────────────────────────────┐
                    │           INGESTION (:8093)              │
  geostat.ge crawl  │  crawler4j → Jsoup → chunk → embed     │
        ──────────► │  Postgres (ingestion.*) + Qdrant write  │
                    └──────────────────┬──────────────────────┘
                                       │
                    ┌──────────────────▼──────────────────────┐
                    │         QDRANT (collection per corpus)   │
                    └──────────────────┬──────────────────────┘
                                       │
  user question     ┌──────────────────▼──────────────────────┐
        ──────────► │         RETRIEVAL (:8092)                  │
                    │  embed query → Qdrant search → chunks      │
                    └──────────────────┬──────────────────────┘
                                       │ HTTP (sync)
                    ┌──────────────────▼──────────────────────┐
  UI (:5177) ──────►│         CHAT-API (:8090)                   │
                    │  topics + links + RAG context → Gemini     │
                    └───────────────────────────────────────────┘
```

### Ingestion ნაბიჯები (დეტალი)

| ნაბიჯი | ტექნოლოგია | სად |
|--------|-----------|-----|
| Job API | REST `POST /api/v1/ingestion/jobs` | `ingestion-service` |
| URL queue | Postgres `url_frontier` | ჩვენი domain model |
| Fetch + robots | **crawler4j** adapter | `crawl/fetch/` |
| Parse/clean | **Jsoup** | `parse/` |
| Chunk | FixedSizeChunker | `chunk/` |
| Embed | `embedding-adapters` (hash-v1 dev, Gemini prod) | `libs/embedding-adapters` |
| Vector store | **Qdrant** | `index/qdrant/` |
| Schema | **Flyway** `ingestion.*` (6 tables) | `db/migration/` |

### Chat path

1. მომხმარებელი წერს UI-ში → `GET/POST /api/chat`
2. chat-api → `RetrievalContextService` → HTTP → retrieval `:8092`
3. retrieval → Qdrant search → chunks
4. chat-api → `PromptBuilder` + Gemini → structured JSON პასუხი

→ [INGESTION-DATA-MODEL.md](plan/INGESTION-DATA-MODEL.md) · [SOURCE-RAG-DESIGN-PROJECTS-FILES.md](plan/SOURCE-RAG-DESIGN-PROJECTS-FILES.md)

---

## 6. Ops — geostat-kit + manifest

**პრინციპი:** `geostat.ops.json` = ერთი წყარო paths, modules, infra, CI-სთვის.

```powershell
.\tools\geostat.ps1 validate      # manifest შემოწმება
.\tools\geostat.ps1 compose-gen   # compose ფაილების გენერაცია
.\tools\geostat.ps1 infra tunnel  # hybrid: remote PG/Redis/Qdrant → localhost
.\tools\geostat.ps1 stack up -d --build
.\tools\geostat.ps1 stack-deploy --prod
```

**Dev რეჟიმები** (kit `DEV-MODES.md`):

| # | რეჟიმი | როცა |
|---|--------|------|
| ① | Local host | F5 / Gradle + npm ლოკალურად |
| ② | Local Docker | `geostat stack` |
| ③ | Remote SSH | `fe/be dev watch` სერვერზე |
| ④ | Hybrid | apps local + `infra tunnel` (დამტკიცებული, plan-ში done) |

→ [CONFIG.md](CONFIG.md) · [GEOSTAT-INIT.md](GEOSTAT-INIT.md) · [KITS-PACKAGE.md](KITS-PACKAGE.md)

---

## 7. ფაზები — რა გაკეთდა / რა დარჩა

**ცოცხალი გეგმა:** [plan/PROJECT-PLAN.md](plan/PROJECT-PLAN.md) (2026-05-22)

### ✅ Done (მთავარი)

| ფაზა | რა |
|------|-----|
| **0** | Monorepo v2, manifest, geostat-kit submodule |
| **0b–0c** | Hybrid infra tunnel, N-module compose-gen, CI health |
| **1** | Architecture B skeleton — 4 სერვისი + `platform-contracts` |
| **2** | chat-api → retrieval HTTP (`HttpRetrievalClient`) |
| **3** | Ingestion pipeline: crawl4j, Jsoup, chunk, Flyway schema |
| **4** | Qdrant search in retrieval |
| **7** | Cursor skills/rules, `docs/plan/` |

### 🔄 In progress / approved (შემდეგი)

| ID | ამოცანა | სად |
|----|---------|-----|
| **P5-01** | RabbitMQ async ingestion → index events | **done** — `RabbitDocumentIndexPublisher`, listener, infra rabbitmq, CI stack smoke |
| **P6-migrate** | Server prod: flat → structured deploy (`runtime/`, `static/`) | სერვერი |
| **P0-kit-09…11** | `geostat config-gen` — Spring YAML manifest-იდან | kit + apps |
| **P0-infra-07** | VS Code compound: tunnel preLaunch + hybrid F5 | `.vscode` |
| **B-07** | CI: full RAG question → answer integration test | `ops/ci/` |
| **B-09** | DEV-MODES §④ Hybrid kit docs-ში | kit docs |

### 📋 Backlog (ჯერ არა approved)

LangChain4j vs Spring AI, Playwright SPA, Ollama primary LLM — [plan/BACKLOG.md](plan/BACKLOG.md)

---

## 8. Clone და პირველი გაშვება

```powershell
git clone --recurse-submodules https://github.com/aleksandre17/geostat-chat-ai.git
cd geostat-chat-ai

# secrets (არ იწერება git-ში)
copy ops\config\deploy.env.example ops\config\deploy.env
# შეავსე ops\config\backend\.env.dev, google-credentials.json, და ა.შ.

.\tools\geostat.ps1 init
.\tools\geostat.ps1 compose-gen
.\tools\geostat.ps1 infra tunnel    # hybrid dev
# სერვისები: ingestion :8093, retrieval :8092, backend :8090, frontend :5177
```

**Hybrid E2E smoke:** `ops/ci/hybrid-e2e-smoke.ps1` (tunnel + crawl + Qdrant + retrieval + chat)

---

## 9. დოკუმენტაციის რუკა

| საჭიროება | ფაილი |
|-----------|-------|
| **ეს დოკი** — სრული overview | `docs/PROJECT-OVERVIEW.md` |
| ცოცხალი გეგმა / სტატუსები | [plan/PROJECT-PLAN.md](plan/PROJECT-PLAN.md) |
| Architecture B | [ARCHITECTURE-B-SERVICES.md](ARCHITECTURE-B-SERVICES.md) |
| კონფიგის რუკა | [CONFIG.md](CONFIG.md) |
| Ops + deploy | [GEOSTAT-KIT-SETUP.md](GEOSTAT-KIT-SETUP.md) |
| Kit adoption | [kits/geostat-kit/docs/ADOPTION-LINE.md](../kits/geostat-kit/docs/ADOPTION-LINE.md) |
| ADR-ები | [adr/README.md](adr/README.md) |
| ინდექსი | [docs/README.md](README.md) |

---

## 10. ორი GitHub repo

```text
geostat-kit (GitHub)              geostat-chat-ai (GitHub)
     │                                    │
     │  git submodule @ bdbd183            │
     └──────────► kits/geostat-kit ◄──────┘
                         │
                   geostat.ops.json
                   apps/  ops/  docs/
```

Kit განახლება consumer-ში: `cd kits/geostat-kit && git fetch && git checkout main`

**ამ repo ვერსია:** root [VERSION](../VERSION) (semver; git tag `v1.0.0` push-ის შემდეგ).
