# პროექტის გეგმა — geostat-chat-ai



განახლება: **2026-05-22** · წყარო: [README](README.md)



ეს არის **ცოცხალი გეგმა**. დამტკიცებული პუნქტები აქ რჩება; ახალი იდეები ჯერ [BACKLOG.md](BACKLOG.md)-ში.



---



## ხედვა (დამტკიცებული)



Geostat ჩატბოტი + **RAG pipeline** (საიტის კონტენტი → ინდექსი → პასუხი), **Architecture B** (მრავალსერვისული განლაგება), **geostat-kit** ops, senior architecture (Clean Architecture, SOLID, manifest-driven).



| დოკი | თემა |

|------|------|

| [SOURCE-RAG-DESIGN-PROJECTS-FILES.md](SOURCE-RAG-DESIGN-PROJECTS-FILES.md) | RAG / crawler სკრინშოტების ამოღება |

| [INFRA-DATA-STORES.md](INFRA-DATA-STORES.md) | Postgres, Redis, Qdrant |

| [HYBRID-DEV-ARCHITECTURE.md](HYBRID-DEV-ARCHITECTURE.md) | Dev: apps local, infra remote Linux |
| [DOCKER-ECOSYSTEM.md](DOCKER-ECOSYSTEM.md) | ერთი Docker network, კომუნიკაცია, compose A/B |
| [SERVER-DEPLOY-LAYOUT.md](SERVER-DEPLOY-LAYOUT.md) | `geostat/{frontend,backend,infra}`, artifact-ები |



---



## ფაზები



| ფაზა | სათაური | სტატუსი | შენიშვნა |

|------|--------|---------|----------|

| **0** | Ops + monorepo v2 (`apps/`, `kits/`, `ops/`, `geostat.ops.json`) | **done** | geostat-kit v1.0.0, manifest, CI smoke |

| **0b** | Hybrid infra — remote Postgres/Redis/Qdrant + tunnel | **done** | slug path `infra/geostat-chat-ai/` — [SERVER-DEPLOY-LAYOUT.md](SERVER-DEPLOY-LAYOUT.md) |

| **0c** | geostat-kit N-module ops (manifest audit #1–#7) | **done** | compose-gen, stack-deploy, CI health matrix — ქვედა P0-kit-* |

| **0d** | Manifest-driven Spring/env config (`config-gen`) | **approved** | P0-kit-09…11 — ქვედა; ingestion YAML = reference hand-written |

| **1** | B სქემა — სერვისების **სკელეტონი** | **done** | chat-api, retrieval, ingestion, `libs/platform-contracts` |

| **2** | chat-api → retrieval (HTTP + contracts) | **done** | `HttpRetrievalClient`, `RetrievalContextService` |

| **3** | ingestion pipeline (crawl → chunk → embed) | **approved** | Postgres (remote infra) — P3 + hybrid env |

| **4** | retrieval + Qdrant | **done** | P4-02 Qdrant search |

| **5** | ingestion → index (async events) | **approved** | **RabbitMQ** (self-host infra compose, P5) |

| **6** | Full stack compose (ყველა სერვისი + ინფრა) | **in_progress** | compose-gen + stack ✅; prod deploy structured path — pending |

| **7** | Cursor skills/rules + გეგმის ფოლდერი | **done** | `.cursor/`, `docs/plan/` |



---



## დეტალური ცხრილი (დამტკიცებული + მიმდინარე)



| ID | ამოცანა | სტატუსი | სერვისი / ადგილი |

|----|---------|---------|------------------|

| P1-01 | `retrieval-service` skeleton (health, search stub) | **done** | `apps/retrieval-service` |

| P1-02 | `ingestion-service` skeleton (health, jobs stub) | **done** | `apps/ingestion-service` |

| P1-03 | `platform-contracts` (DTO + ports) | **done** | `libs/platform-contracts` |

| P1-04 | `geostat.ops.json` modules: retrieval, ingestion | **done** | root manifest |

| P1-05 | `docs/ARCHITECTURE-B-SERVICES.md` | **done** | docs |

| P0-infra-01 | `ops/compose/infra/docker-compose.yml` | **done** | postgres, redis, qdrant |

| P0-infra-02 | `ops/config/infra/.env.example` + deploy | **done** | ops/config/infra |

| P0-infra-03 | `geostat infra remote up` + sync | **done** | `kits/geostat-kit/toolkit/infra/Invoke-Infra.ps1` |
| P0-infra-03b | სერვერზე slug path + ძველი `infra/compose/` cleanup | **done** | `/home/.../geostat/infra/geostat-chat-ai/` (2026-05-22) |

| P0-infra-04 | `geostat infra tunnel` (ssh -L) | **done** | `geostat infra tunnel` |

| P0-infra-05 | manifest `stack.networkName` + `infraComposeDir` | **done** | `geostat.ops.json` |

| P0-infra-06 | env hints `hybrid` / `INFRA_HOST` in module `.env.example` | **done** | backend, retrieval, ingestion |

| P0-infra-07 | VS Code compound / preLaunch tunnel | **proposed** | `.vscode` |

| P0-kit-01 | Infra tunnel — manifest + `infra-catalog.json` | **done** | `lib/infra_tunnel.py`, `Invoke-Infra.ps1` |
| P0-kit-02 | Stack URL hints — manifest roles, არა hardcoded fe/be | **done** | `lib/stack_endpoints.py`, `stack-catalog.json` |
| P0-kit-03 | compose-gen N modules (retrieval, ingestion, ui) | **done** | `manifest_compose.py`, `ops/compose/catalog.json` |
| P0-kit-04 | `COMPOSE_*` → `compose_identity.py` | **done** | service names manifest-იდან |
| P0-kit-05 | `stackDeploy.steps` auto from `stack.composeModules` | **done** | `lib/stack_deploy.py` |
| P0-kit-06 | Schema + scoped `be manage all nuke` | **done** | არა global `docker image prune` |
| P0-kit-07 | CI health matrix + secrets scaffold | **done** | `lib/ci_health.py`, `ci.healthModules`, 224 pytest |
| P0-kit-08 | `features.worker: false` — worker = manifest `ingestion` | **done** | `ops/compose/catalog.json` |

| P0-kit-09 | `geostat config-gen` — Spring `application*.yml` manifest-იდან | **approved** | `modules.*.port`, `datastores.postgres/redis/qdrant`; templates kit-ში |
| P0-kit-10 | Generated vs custom split — `application-custom.yml` ხელით | **approved** | generator არ overwrite-ს custom; header `# generated by config-gen` |
| P0-kit-11 | `geostat validate` — config drift (manifest vs YAML / `.env.example`) | **approved** | pytest snapshot; ingestion-service = golden reference |

| P2-01 | `RetrievalClient` HTTP adapter in chat-api | **done** | `Chatbot/retrieval/HttpRetrievalClient` |

| P2-02 | env: `RETRIEVAL_BASE_URL`, `INFRA_HOST` | **approved** | `ops/config/backend` |

| P3-01 | crawler4j + Jsoup + Postgres URL frontier | **done** | `Crawler4jPageFetcher`, PG queue, robots + policy |

| P3-02 | Jsoup parse + content cleaner | **done** | `parse/HtmlContentCleaner` |

| P3-03 | chunking strategy | **done** | `chunk/strategy/FixedSizeChunker`, `chunk/DocumentChunkWriter` |

| P3-04 | embeddings + Qdrant write | **done** | `embed/`, `index/qdrant/`, `ChunkVectorIndexer` |

| P3-05 | Postgres pipeline schema (Flyway `ingestion.*`) | **done** | all 6 tables JPA + jobs API |

| P4-01 | Qdrant in infra compose (remote) | **approved** | ops/compose/infra |

| P4-02 | `RetrievalPort` იმპლემენტაცია | **done** | `search/QdrantRetrievalService`, Qdrant search |

| P5-01 | RabbitMQ ingestion → index events | **approved** | B-01; OSS self-host, not paid SaaS |

| P6-01 | Stack + infra — `geostat-chat-ai-net` | **done** | `ops/compose/stack/docker-compose.yml` (manifestStack) |
| P6-02 | catalog templates: retrieval, ingestion, stack N-module | **done** | `manifestModule` + `manifestStack` targets |
| P6-03 | CI integration — full stack health (backend+retrieval+ingestion) | **done** | `ops/ci/integration-stack.sh`, `wait-stack-health.sh` |
| P6-04 | სერვერის prod deploy — structured `runtime/`/`static/` | **approved** | ძველი flat paths კიდევ live — ქვედა P6-migrate |
| P6-migrate | `geostat-chat-api`/`app` → structured + ახალი stack slug names | **approved** | server: flat `backend/geostat-chat-api`, `frontend/geostat-chat-app` |



---



## არქიტექტურის გადაწყვეტილებები (ADR + plan)



| წყარო | თემა | სტატუსი |

|-------|------|--------|

| [009](../adr/009-architecture-b-separate-deployables.md) | Architecture B | **Accepted** |

| [006](../adr/006-geostat-kit-package.md) | geostat-kit | Accepted |

| [008](../adr/008-root-layout-consolidation.md) | Root 4-plane | Proposed |

| D-01…D-06 | Hybrid dev + remote infra | **approved** — [HYBRID-DEV-ARCHITECTURE.md](HYBRID-DEV-ARCHITECTURE.md) §11 |
| D-07…D-09 | Docker ecosystem (network, compose B, profiles) | **approved** — [DOCKER-ECOSYSTEM.md](DOCKER-ECOSYSTEM.md) §8 |

| D-11 | Manifest-driven app config (Spring profiles + env) | **approved** — ფაზა 0d, P0-kit-09…11; ახლა ingestion YAML ხელით |



სრული ADR: [docs/adr/README.md](../adr/README.md)



---



## რა **არ** შედის ამ ეტაპის გეგმაში



- consumer monorepo სრული git push (მხოლოდ kit repo გამოქვეყნებულია)

- `libs/` → `apps/` (დამტკიცებული: **libs root-ზე**)

- Postgres/Redis **native Windows** — მხოლოდ Docker remote ([HYBRID-DEV-ARCHITECTURE.md](HYBRID-DEV-ARCHITECTURE.md) §10)



---



## შემდეგი ნაბიჯი (რიგითობა)



### A. პროდუქტი (RAG pipeline — ფაზები 2–5)



1. **P2-01** — chat-api → retrieval HTTP (`RetrievalClient`, contracts)  
2. **P3-01…P3-05** — ingestion pipeline + Postgres (hybrid/tunnel უკვე მზადაა)  
3. **P4-01…P4-02** — Qdrant client + retrieval search (stub → real)  
4. **P5-01** — RabbitMQ ingestion → index events (self-host)  

### B. Ops / deploy (ფაზა 6 დასრულება)



5. **P6-migrate** — სერვერზე chat-api + frontend structured deploy (`runtime/`, `static/`) + ახალი compose service names  
6. **P0-infra-07** — VS Code compound: tunnel preLaunch + hybrid F5  
7. **Kit future** — `embeddedWorker` manifest-ში; deprecate `features.worker` ([PACKAGE-PRINCIPLES.md](../../kits/geostat-kit/docs/PACKAGE-PRINCIPLES.md))  
8. **P0-kit-09…11 (ფაზა 0d)** — manifest → Spring/env config generation (იხ. ქვემოთ)

### D. Kit — manifest-driven app config (ფაზა 0d, **approved**)

**პრობლემა:** `apps/*/src/main/resources/application*.yml` და `ops/config/*/.env.example` ხელით იწერება; ingestion-ზე 5 profile ფაილი გამეორებადი boilerplate-ია (hybrid/docker/nodb/db).

**მიზანი:** ისევე როგორც `compose-gen` / `vscode-gen` — **`geostat.ops.json` = წყარო**, generator = artifact.

| Command | რას გამოიტანს |
|---------|----------------|
| `geostat config-gen [module]` | `application.yml`, `application-hybrid-env.yml`, `application-docker-env.yml`, `application-nodb.yml`; თუ `datastores.postgres` → `application-db.yml` |
| `geostat config-gen --all` | ყველა `modules.*` java-boot |
| `geostat validate` (extend) | manifest vs generated drift; `--fix` optional regenerate |

**Manifest გაფართოება** (`modules.<id>`):

```json
"datastores": {
  "postgres": { "schema": "ingestion", "flyway": true },
  "redis": { "optional": true },
  "qdrant": { "optional": true }
}
```

**არქიტექტურა (generated + custom):**

```text
src/main/resources/
  application.yml                 ← generated
  application-db.yml              ← generated (თუ postgres declared)
  application-hybrid-env.yml      ← generated
  application-docker-env.yml      ← generated
  application-nodb.yml            ← generated (SB4 autoconfigure excludes — kit template)
  application-custom.yml          ← ხელით; generator არ ეხება
ops/config/<module>/.env.example  ← generated hints (secrets არა)
```

**რეფერენსი:** `apps/ingestion-service` არსებული YAML → პირველი `config-gen` golden output + pytest.

**არ შედის:** runtime-ზე YAML-ის „თვითონ შევსება“; secrets `.env.dev`-ში რჩება.

### C. გადაწყვეტილებები (გახსნა)



- **Q-01…Q-05** — LLM, embeddings, Qdrant, crawler stack ([SOURCE-RAG-DESIGN-PROJECTS-FILES.md](SOURCE-RAG-DESIGN-PROJECTS-FILES.md))  
- **Q-17** — **closed** — worker = `ingestion`, არა `apps/backend/worker`  

### Hybrid dev ახლა (მზადაა)



```powershell
.\tools\geostat.ps1 infra remote status   # postgres, redis, qdrant
.\tools\geostat.ps1 infra tunnel          # 5432, 6379, 6333 → localhost
# შემდეგ: bootRun / F5 chat-api, retrieval, ingestion
```



განახლების შემდეგ ჩაწერე [CHANGELOG-PLAN.md](CHANGELOG-PLAN.md).

