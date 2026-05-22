# geostat-chat-ai

**Version:** [1.0.0](VERSION) · Ops package: [geostat-kit](https://github.com/aleksandre17/geostat-kit) @ v1.1.0 (submodule)

**Geostat AI chatbot with RAG pipeline** — React UI, Spring Boot multi-service stack (chat, retrieval, ingestion), Qdrant vectors, manifest-driven ops via [geostat-kit](https://github.com/aleksandre17/geostat-kit).

ქართული სრული overview: **[docs/PROJECT-OVERVIEW.md](docs/PROJECT-OVERVIEW.md)**

---

## რას ვაკეთებთ

| | |
|--|--|
| **პროდუქტი** | AI ჩატი Geostat portal-ის კონტent-ზე — crawl → index → semantic search → პასუხი |
| **არქიტექტურა** | Architecture B — 4 deployable + shared contracts; Clean Architecture, SOLID |
| **Ops** | `geostat.ops.json` + [geostat-kit](https://github.com/aleksandre17/geostat-kit) submodule (compose, deploy, infra tunnel) |

---

## Pipeline (მოკლედ)

```text
ingestion (:8093)  crawl → chunk → embed → Qdrant + Postgres
retrieval (:8092)  query embed → Qdrant search → chunks
chat-api (:8090)   RAG context + Gemini → პასუხი
frontend (:5177)   React chat widget
```

→ სრული დიაგრამა: [docs/PROJECT-OVERVIEW.md#5-სრული-rag-pipeline](docs/PROJECT-OVERVIEW.md)

---

## Repo სტრუქტურა

```text
apps/frontend · apps/backend · apps/retrieval-service · apps/ingestion-service
libs/platform-contracts · libs/embedding-adapters
kits/geostat-kit/          ← git submodule (ცალკე GitHub repo)
ops/config/                ← secrets (gitignored)
ops/compose/               ← catalog + generated stack
geostat.ops.json           ← manifest
tools/geostat.ps1          ← CLI
```

---

## სტატუსი (2026-05-22)

| Done | Next |
|------|------|
| Monorepo v2, hybrid infra tunnel | P5 — RabbitMQ async index |
| Ingestion pipeline (crawler4j, Jsoup, Qdrant) | P6-migrate — server structured deploy |
| chat-api → retrieval wired | `geostat config-gen` (manifest → Spring YAML) |
| geostat-kit v1.1.0 submodule | CI full RAG E2E test |

→ [docs/plan/PROJECT-PLAN.md](docs/plan/PROJECT-PLAN.md)

---

## Quick start

```powershell
git clone --recurse-submodules https://github.com/aleksandre17/geostat-chat-ai.git
cd geostat-chat-ai

# secrets — copy examples from ops/config/*.example (არ იწერება git-ში)
.\tools\geostat.ps1 init
.\tools\geostat.ps1 compose-gen
.\tools\geostat.ps1 infra tunnel   # hybrid: remote Postgres/Qdrant → localhost
```

| Service | URL |
|---------|-----|
| UI | http://localhost:5177 |
| Chat API | http://localhost:8090 |
| Retrieval | http://localhost:8092 |
| Ingestion | http://localhost:8093 |

Setup: [docs/GEOSTAT-INIT.md](docs/GEOSTAT-INIT.md) · Config: [docs/CONFIG.md](docs/CONFIG.md)

---

## geostat-kit (გარე პაკეტი)

| | geostat-chat-ai | geostat-kit |
|--|-----------------|-------------|
| **GitHub** | ეს repo | [github.com/aleksandre17/geostat-kit](https://github.com/aleksandre17/geostat-kit) |
| **როლი** | პროდუქტი | ops framework (CLI, compose-gen, deploy) |
| **path** | `apps/`, `ops/`, `docs/` | `kits/geostat-kit/` (submodule @ v1.1.0) |

→ [kits/README.md](kits/README.md)

---

## Ops CLI

```powershell
.\tools\geostat.ps1 help
.\tools\geostat.ps1 validate
.\tools\geostat.ps1 compose-gen
.\tools\geostat.ps1 stack up -d --build
.\tools\geostat.ps1 stack-deploy --prod
.\tools\geostat.ps1 fe deploy dist
.\tools\geostat.ps1 be deploy api --prod
```

---

## Documentation

| Doc | Topic |
|-----|--------|
| **[docs/PROJECT-OVERVIEW.md](docs/PROJECT-OVERVIEW.md)** | **სრული overview** — vision, pipeline, status, structure |
| [docs/plan/PROJECT-PLAN.md](docs/plan/PROJECT-PLAN.md) | Living plan — phases, tasks |
| [docs/ARCHITECTURE-B-SERVICES.md](docs/ARCHITECTURE-B-SERVICES.md) | Services map (Architecture B) |
| [docs/CONFIG.md](docs/CONFIG.md) | Configuration map |
| [docs/](docs/README.md) | Full doc index |
| [docs/adr/](docs/adr/) | Architecture decisions |

---

## Related repositories

- **geostat-kit** — reusable ops package: https://github.com/aleksandre17/geostat-kit
