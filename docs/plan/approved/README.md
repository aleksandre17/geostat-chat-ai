# დამტკიცებული



მოკლე ინდექსი — სრული ცხრილი: [PROJECT-PLAN.md](../PROJECT-PLAN.md).



## არქიტექტურა



- **Architecture B** — ცალკე deployables: chat-api, retrieval, ingestion ([ADR-009](../../adr/009-architecture-b-separate-deployables.md))

- **libs/** root-ზე — `platform-contracts`, არა `apps/libs/` (2026-05-21)

- **Secrets** — `ops/config/<module>/`; compose კოდთან `apps/*`



## Ops



- **geostat-kit** manifest-driven ([ADR-006](../../adr/006-geostat-kit-package.md))

- **4-plane** — `apps/`, `kits/`, `ops/`, `docs/`



## Dev — Hybrid (2026-05-21)



სრული დოკი: [HYBRID-DEV-ARCHITECTURE.md](../HYBRID-DEV-ARCHITECTURE.md) · ინფრა: [INFRA-DATA-STORES.md](../INFRA-DATA-STORES.md)



| ID | გადაწყვეტილება |

|----|----------------|

| **D-01** | რეჟიმი **④ Hybrid**: apps Windows-ზე (bootRun/F5), Postgres/Redis/Qdrant remote Linux Docker |

| **D-02** | Infra = `ops/compose/infra` + `geostat infra remote up/down` + `geostat infra tunnel` |

| **D-03** | კავშირი infra-თან: `INFRA_HOST` + პორტები; dev-ში SSH tunnel → `127.0.0.1` default |

| **D-04** | Peer სერვისები: `RETRIEVAL_BASE_URL`, `VITE_API_URL` და ა.შ. — localhost vs SERVER |

| **D-05** | Spring profile `hybrid` (chat, retrieval, ingestion როცა DB/Redis ჩაერთვება) |

| **D-06** | P3 Postgres + P4 Qdrant + P5 RabbitMQ (+ Redis cache) — remote infra compose | **approved** |

## ბიუჯეტი / stack (2026-05-22, განახლ. 2026-05-23)

| ID | გადაწყვეტილება |
|----|----------------|
| **D-16** | **Paid SaaS = Gemini API only** (generation + prod embeddings default) |
| **D-17** | **Free-first elsewhere** — OSS/self-host/local (Ollama embed, Qdrant, RabbitMQ, Postgres); adapter pattern |
| **D-18** | **Benefit gate** — ახალი lib/service მხოლოდ თუ არქიტექტურა/პროდუქტი/perf/ops-ს აუმჯობესებს ([ADR-010](../../adr/010-product-stack-benefit-gate.md)) |
| **D-19** | **Q-01 closed:** Gemini generation prod; Ollama generation optional local profile (P7-01) |
| **D-20** | **Q-02 closed:** Spring AI primary; LangChain4j rejected |
| **D-21** | **Q-04 closed:** Qdrant; `libs/qdrant-client` shared adapter |
| **D-22** | **Q-05 closed:** crawler4j + Jsoup default; Playwright trigger-only (P3-03b) |
| **D-23** | **Q-13 superseded (B-25):** single pipeline — StructureLookup removed; structure via corpus + RAG |
| **D-24** | **Q-15 closed:** prod Redis AOF (`redis.yml` `REDIS_AOF:-yes`); dev persistence optional |

## RAG derivation architecture (2026-05-24)

სრული spec: [RAG-DERIVATION-ARCHITECTURE.md](../RAG-DERIVATION-ARCHITECTURE.md) · ADR: [011](../../adr/011-rag-derivation-architecture.md)

| ID | გადაწყვეტილება |
|----|----------------|
| **D-25** | **Derivation architecture adopted.** Corpus (`ingestion.document` + Qdrant) = single source of truth. Topics, portals, specific links, keywords — **derived** by per-document enrichment + nightly aggregation, not authored in YAML. Hand-edited `topics.yaml` (2364 lines) deprecated and will be deleted after eval gate passes. Stays: `topic-style.yaml` (~80 lines, presentation only) + `terminology-overlay.yaml` (≤40 entries, synonym graph). |
| **D-26** | **Curation overlay scope.** `ingestion.curation_override` is the only human authoring surface. Budget ≤50 rows. TTL default 90 days. `reason` mandatory. Actions: `boost / demote / exclude / pin_as_portal / rename_topic`. Overlay fixes derivation edge cases — does **not** create content. If overlay row count exceeds 50, signals upstream deriver problem to fix. |
| **RAG-U03** | ~~SourceComposer~~ **superseded** — merged into RAG-U10 unified hybrid retriever |
| **RAG-U04** | ~~Hybrid keyword topic classifier~~ **superseded** — replaced by RAG-U07c IntentClassifier |
| **RAG-U06** | ~~Public catalog API~~ **dropped** — derived materialized views suffice |

## Docker ეკოსისტემა (2026-05-21)

სრული დოკი: [DOCKER-ECOSYSTEM.md](../DOCKER-ECOSYSTEM.md)

| ID | გადაწყვეტილება |
|----|----------------|
| **D-07** | ერთი network ყველა compose-ში: `geostat-chat-ai-net` |
| **D-08** | P6: infra compose ცალკე + apps `external: true` (ვარიანტი B); ერთი stack compose optional (A) |
| **D-09** | Env/Spring profiles `docker` (შიდა DNS) vs `hybrid` (`INFRA_HOST` / localhost peers) |

## სერვერის ხე (2026-05-21)

[SERVER-DEPLOY-LAYOUT.md](../SERVER-DEPLOY-LAYOUT.md)

| ID | გადაწყვეტილება |
|----|----------------|
| **D-10** | Root `/home/.../geostat/`; siblings `frontend/`, `backend/`, `infra/`; **არა** `backend/infra`; infra **არა** JAR artifact |

## geostat-kit N-module ops (2026-05-22)

| ID | გადაწყვეტილება |
|----|----------------|
| **D-12** | Manifest = single source: compose service names, stack deploy steps, CI health URLs — არა hardcoded fe/be/worker |
| **D-13** | `stack.composeModules`: chat-api + retrieval + ingestion + frontend; `features.worker: false` |
| **D-14** | Infra consumer path: `infra/<INFRA_SLUG>/` (არა shared `infra/compose/`) |
| **D-15** | CI `healthModules`: Java services only; UI skip in Docker integration |

## შემდეგი დამტკიცებული ფაზები

- **Stack decisions:** done — [ADR-010](../../adr/010-product-stack-benefit-gate.md) (Q-01…Q-05, Q-13, Q-15)
- **Kit:** ~~P0-kit-13~~ **done** — `compose.embeddedWorker`
- **Product optional:** P3-03b Playwright (trigger), P7-01 Ollama local gen
- **Ops owner:** GEMINI_API_KEY rotation; data-driven corpus reindex

