# პროექტის გეგმა — geostat-chat-ai



განახლება: **2026-05-23** · წყარო: [README](README.md) · stack: [ADR-010](../adr/010-product-stack-benefit-gate.md)



ეს არის **ცოცხალი გეგმა**. დამტკიცებული პუნქტები აქ რჩება; ახალი იდეები ჯერ [BACKLOG.md](BACKLOG.md)-ში.



---



## ხედვა (დამტკიცებული)



Geostat ჩატბოტი + **RAG pipeline** (საიტის კონტენტი → ინდექსი → პასუხი), **Architecture B** (მრავალსერვისული განლაგება), **geostat-kit** ops, senior architecture (Clean Architecture, SOLID, manifest-driven).



| დოკი | თემა |

|------|------|

| [SOURCE-RAG-DESIGN-PROJECTS-FILES.md](SOURCE-RAG-DESIGN-PROJECTS-FILES.md) | RAG / crawler სკრინშოტების ამოღება |

| [RAG-DERIVATION-ARCHITECTURE.md](RAG-DERIVATION-ARCHITECTURE.md) | **Phase 8** — derivation architecture (L1..L5), RAG-U სერია, DB schema, pipeline ([ADR-011](../adr/011-rag-derivation-architecture.md)) |

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

| **0d** | Manifest-driven Spring/env config (`config-gen`) | **done** | P0-kit-09…11 — backend (env-profiles), ingestion (postgres), retrieval (simple) |

| **1** | B სქემა — სერვისების **სკელეტონი** | **done** | chat-api, retrieval, ingestion, `libs/platform-contracts` |

| **2** | chat-api → retrieval (HTTP + contracts) | **done** | `HttpRetrievalClient`, `RetrievalContextService` |

| **3** | ingestion pipeline (crawl → chunk → embed) | **approved** | Postgres (remote infra) — P3 + hybrid env |

| **4** | retrieval + Qdrant | **done** | P4-02 Qdrant search |

| **5** | ingestion → index (async events) | **approved** | **RabbitMQ** (self-host infra compose, P5) |

| **6** | Full stack compose (ყველა სერვისი + ინფრა) | **done** | P6-01…03 ✅; P6-migrate server apply ✅ (2026-05-23) |

| **7** | Cursor skills/rules + გეგმის ფოლდერი | **done** | `.cursor/`, `docs/plan/` |
| **7b** | Stack decisions Q-01…Q-05, Q-13, Q-15 + benefit gate | **done** | [ADR-010](../adr/010-product-stack-benefit-gate.md), approved D-18…D-24 |

| **8** | **RAG derivation architecture** — corpus → enrichment → catalog views → curation overlay; RAG-U series | **plan source ✅ 100%** · impl: P1 code ✅, runtime ⏳, P2+ approved | [PHASE-8-ARCHITECTURE-PLAN.md](PHASE-8-ARCHITECTURE-PLAN.md) (master), [P1](PHASE-8-P1-ARCHITECTURE-COMPLETION.md), [P2](PHASE-8-P2-ARCHITECTURE-PLAN.md), [P3-P4](PHASE-8-P3-P4-ARCHITECTURE-PLAN.md), [spec](RAG-DERIVATION-ARCHITECTURE.md), [ADR-011](../adr/011-rag-derivation-architecture.md) |



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

| P0-infra-07 | VS Code compound / preLaunch tunnel | **done** | `vscode_gen`: tunnel background task + hybrid compound |

| P0-infra-08 | Hybrid ④ local app boot — consumer delegate | **done** | `ops/ci/hybrid-boot-app.ps1` → `geostat hybrid boot`; env `ops/config/<module>/.env.dev` |

| P0-kit-01 | Infra tunnel — manifest + `infra-catalog.json` | **done** | `lib/infra_tunnel.py`, `Invoke-Infra.ps1` |
| P0-kit-02 | Stack URL hints — manifest roles, არა hardcoded fe/be | **done** | `lib/stack_endpoints.py`, `stack-catalog.json` |
| P0-kit-03 | compose-gen N modules (retrieval, ingestion, ui) | **done** | `manifest_compose.py`, `ops/compose/catalog.json` |
| P0-kit-04 | `COMPOSE_*` → `compose_identity.py` | **done** | service names manifest-იდან |
| P0-kit-05 | `stackDeploy.steps` auto from `stack.composeModules` | **done** | `lib/stack_deploy.py` |
| P0-kit-06 | Schema + scoped `be manage all nuke` | **done** | არა global `docker image prune` |
| P0-kit-07 | CI health matrix + secrets scaffold | **done** | `lib/ci_health.py`, `ci.healthModules`, 224 pytest |
| P0-kit-08 | `features.worker: false` — worker = manifest `ingestion` | **done** | `ops/compose/catalog.json` |

| P0-kit-09 | `geostat config-gen` — Spring `application*.yml` manifest-იდან | **done** | `lib/config_gen.py`; backend + ingestion + retrieval golden ✅ |
| P0-kit-10 | Generated vs custom split — `application-custom.yml` ხელით | **done** | backend + ingestion + retrieval `application-custom.yml` ✅ |
| P0-kit-11 | `geostat validate` — config drift (manifest vs YAML) | **done** | `validate_manifest` + `config-gen --check`; 3 java-boot modules ✅ |

| P0-kit-12 | Hybrid ④ **`geostat hybrid boot`** + module **`run`** (kit upstream) | **done** | `toolkit/hybrid/Invoke-HybridRun.ps1`; `java-boot`/`node-vite` `run`; `geostat hybrid boot fe\|be\|ing\|ret` |
| P0-kit-13 | Deprecate `features.worker`; manifest `compose.embeddedWorker` | **done** | `lib/compose_identity.py`; consumer `chat-api.compose.embeddedWorker: false` |
| P3-03b | Playwright SPA fetch (corpus audit trigger) | **approved** | B-03; ingestion infra adapter — not default |
| P7-01 | Ollama chat generation (local/hybrid profile) | **approved** | B-04; prod stays Gemini (D-16) |
| OPS-02-tool | Corpus quality audit API + CI | **done** | `GET …/corpora/{name}/quality`, `corpus-quality-audit.sh` |

| P2-01 | `RetrievalClient` HTTP adapter in chat-api | **done** | `infrastructure/retrieval/HttpRetrievalClient` |

| P2-02 | env: `RETRIEVAL_BASE_URL`, `INFRA_HOST` | **done** | `ops/config/backend/.env.prod` docker DNS + `RETRIEVAL_ENABLED=true` |

| P3-01 | crawler4j + Jsoup + Postgres URL frontier | **done** | `Crawler4jPageFetcher`, PG queue, robots + policy |

| P3-02 | Jsoup parse + content cleaner | **done** | `parse/HtmlContentCleaner` |

| P3-03 | chunking strategy | **done** | `chunk/strategy/FixedSizeChunker`, `chunk/DocumentChunkWriter` |

| P3-04 | embeddings + Qdrant write | **done** | `embed/`, `index/qdrant/`, `ChunkVectorIndexer` |

| P3-05 | Postgres pipeline schema (Flyway `ingestion.*`) | **done** | all 6 tables JPA + jobs API |

| P4-01 | Qdrant in infra compose (remote) | **approved** | ops/compose/infra |

| P4-02 | `RetrievalPort` იმპლემენტაცია | **done** | `search/QdrantRetrievalService`, Qdrant search |

| P5-01 | RabbitMQ ingestion → index events | **done** | B-01; async publisher + listener; CI infra+stack smoke |
| P5-02 | crawl smoke → index → retrieval | **done** | `ops/ci/rag-pipeline-smoke.sh`; hybrid smoke verified |
| P5-03 | chat catalog+RAG unified items + citations | **done** | B-15; `CatalogRagLinkMerger`, `chat-catalog-rag-smoke.ps1` |
| P5-ux | chat UX: SSE, Redis sessions, feedback telemetry | **done** | B-17…B-19; `verify-services-prod.sh` |
| B-07 | integration test: RAG question → answer | **done** | `ops/ci/chat-rag-e2e-smoke.sh`; `ci.chatRagE2eSmoke` |
| B-24 | Catalog YAML externalization (topics, links) | **done** | `resources/catalog/*.yaml`, `YamlTopicCatalog` |
| B-25 | Zero-gap: StructureLookup removed | **done** | `CorpusContextFormatter`; single seed + link discovery |
| B-26 | Continuous full-site crawl + frontier resume | **done** | V3 policy, `FrontierResumeService`, `CorpusCrawlScheduler`, auto-continue |
| B-27 | Prompt YAML + Gemini quality (JSON schema, history, stream) | **done** | `resources/prompts/chat-prompts.yaml`, `AiChatOptionsFactory`, `ops/ci/chat-prompt-smoke.ps1` |

| RAG-L01…L10 | Dual-locale RAG pipeline (complete baseline + ops) | **done** | V4/V5 Flyway, hybrid keyword ON, cache, eval CI, `rag-locale-pipeline.ps1` |
| RAG-L11 | Display metadata — locale citation descriptions | **done** | V8 Flyway, `PageDisplayMetadataExtractor`, Qdrant `pageDescription`, `CatalogRagLinkMerger` |
| RAG-L07+ | Semantic cross-encoder rerank (embedding bi-encoder) | **done** | `SemanticCrossEncoderReranker` |
| RAG-L-cache | Redis retrieval cache backend | **done** | `RETRIEVAL_CACHE_BACKEND=redis` |

| RAG-U01a | **SummaryDeriver** — Gemini 2-3 sentence summary ka/en | **done (P1 code)** | `gemini-2.5-flash-lite`; runtime backfill ⏳ |
| RAG-U01b | **KeywordDeriver** — YAKE Java port top-15 per locale | **done (P1 code)** | spec §5.U01b |
| RAG-U01c | **EntityDeriver** — Gemini few-shot INDICATOR/YEAR/REGION/ORG/INDEX_CODE | **done (P1 code)** | spec §5.U01c |
| RAG-U01d | **LocalePairDeriver** — URL pattern + embedding cosine | **done (P1 code)** | spec §5.U01d |
| RAG-U01e | **AuthorityDeriver** — JGraphT PageRank + recency, nightly batch | **done (P1 code)** | spec §5.U01e |
| RAG-U01f | **PageKindClassifier** — closed-set portal/dataset/report/news/faq/navigation/unknown | **done (P1 code)** | spec §5.U01f |
| RAG-U01g | **TopicMiner + TopicAssigner** — Smile k-means + Gemini cluster label (admin-approved) | **done (P1 code)** | spec §5.U01g |
| RAG-U01h | **Title/SummaryVectorDeriver** — Qdrant named vectors `title`, `summary` | **done (P1 code)** | `INGESTION_NAMED_VECTORS_ENABLED=false` until U08 |
| RAG-U02 | **Catalog views** — `mv_portal_link`, `mv_specific_link`, `mv_topic_keywords` + refresh + status | **done (P1 code)** | V13–V16; chat `source=yaml|derived` |
| RAG-U03 | ~~SourceComposer (catalog vs RAG separate path)~~ | **superseded by U10** | merged into unified hybrid retriever |
| RAG-U04 | ~~Hybrid keyword topic classifier~~ | **superseded by U07c** | replaced by IntentClassifier |
| RAG-U05 | **Curation overlay UI** — admin tab for boost/demote/exclude/pin_as_portal/rename_topic | **done (API, P1)** / UI **P3** | REST API + budget; admin tab pending |
| RAG-U06 | ~~Public catalog API~~ | **dropped** | derived views suffice; no external consumer |
| RAG-U07 | **Query understanding pipeline** — SpellFixer (SymSpell) + Normalizer + IntentClassifier (Gemini) + EntityExtractor + QueryExpander (terminology-overlay.yaml + LLM) | **done (P1 code)** | flags default OFF until eval |
| RAG-U08 | **Multi-vector index** — Qdrant migration v1 → v2 (named vectors `body`, `title`, `summary`); backfill | **approved (P2)** | spec §4 |
| RAG-U09 | **HyDE + multi-query** — `QueryEmbeddingStrategy` (Direct \| HyDE \| MultiQuery) | **approved (P2)** | spec §8 stage 6 |
| RAG-U10 | **Hybrid retrieval + RRF fusion + MMR** — Qdrant(summary,title,body) + Postgres tsvector → RRF k=60 → CrossEncoder → MMR λ=0.7 | **approved (P2)** | spec §8 stage 7-9 |
| RAG-U11 | **Confidence + smart fallback** — `RetrievalConfidence` HIGH/MEDIUM/LOW/NONE; `ResponseRouter` answer/suggest/clarify/refuse | **approved (P2)** | spec §8 stages 10-11 |
| RAG-U12 | **Eval harness + CI gate** — extend `evaluation_query`, golden 150–300, `ops/ci/run-eval.py` (hit@1/5, MRR, NDCG@10, intent_acc, entity_F1), 5% regression block | **done (P1 code)** / **runtime gate ⏳** | `rag-p1-cutover.ps1`, dual baseline |
| RAG-U13 | **Feedback-driven score boost** — `document.score_boost` from `chat.feedback_*` aggregator | **approved (P3)** | spec §10 |
| RAG-U14 | **Caching tier** — `query_intent_cache` (24h, PG), retrieval cache (1h, Redis), response cache (5m, Redis, optional) | **approved (P3)** | spec §11 |
| RAG-U15 | **Knowledge graph** — Apache AGE on Postgres; entity+relation extraction | **deferred (P4+)** | trigger when corpus > 50K docs OR entity-aware eval gap |
| **P8-plan-01** | **Service/package split planning review** — telemetry audit + candidate split map (`enrichment-service`, `query-understanding-service`, …); ADR draft **only if** review recommends extraction | **approved (gate)** | after Phase 8 P2 cutover (YAML deleted) **+ 30d prod telemetry** OR early if scale signals approach (spec § 13.6); **planning only** — code split = B-37 triggers |
| P3-03b | Playwright SPA refetch (audit trigger) | **done** | `PlaywrightPageFetcher`, `POST …/playwright-refetch` |
| B-28 | Raw HTML archive port + schema hook | **baseline** | `RawHtmlArchivePort`, V6 `raw_archive_key`; S3/MinIO adapter backlog |
| B-30 | Chat telemetry JDBC (`chat.*`) | **done** | Flyway V1, profile `telemetry-db`, `JdbcChatTurnWriter` |
| OPS-02 | Full corpus crawl (prod policy) | **tooling done** | `rag-full-corpus-crawl.ps1`, `rag-full-corpus-policy.py` |

| P6-01 | Stack + infra — `geostat-chat-ai-net` | **done** | `ops/compose/stack/docker-compose.yml` (manifestStack) |
| P6-02 | catalog templates: retrieval, ingestion, stack N-module | **done** | `manifestModule` + `manifestStack` targets |
| P6-03 | CI integration — full stack health (backend+retrieval+ingestion) | **done** | `ops/ci/integration-stack.sh`, `wait-stack-health.sh` |
| P6-04 | სერვერის prod deploy — structured `runtime/`/`static/` | **done** | structured paths live; legacy container names coexist |
| P6-migrate | `geostat-chat-api`/`app` → structured + ახალი stack slug names | **done** | migrate + prod redeploy (api/ret/ing); `verify-services-prod.sh` ✅ (2026-05-23) |



---



## არქიტექტურის გადაწყვეტილებები (ADR + plan)



| წყარო | თემა | სტატუსი |

|-------|------|--------|

| [009](../adr/009-architecture-b-separate-deployables.md) | Architecture B | **Accepted** |

| [006](../adr/006-geostat-kit-package.md) | geostat-kit | Accepted |

| [008](../adr/008-root-layout-consolidation.md) | Root 4-plane | Proposed |

| [010](../adr/010-product-stack-benefit-gate.md) | Product stack — benefit gate | Accepted |

| [011](../adr/011-rag-derivation-architecture.md) | **RAG derivation architecture** — corpus = single source of truth; YAML catalog deprecated; ფაზა 8 | **Accepted** |

| D-01…D-06 | Hybrid dev + remote infra | **approved** — [HYBRID-DEV-ARCHITECTURE.md](HYBRID-DEV-ARCHITECTURE.md) §11 |
| D-07…D-09 | Docker ecosystem (network, compose B, profiles) | **approved** — [DOCKER-ECOSYSTEM.md](DOCKER-ECOSYSTEM.md) §8 |

| D-11 | Manifest-driven app config (Spring profiles + env) | **approved** — ფაზა 0d, P0-kit-09…11; ახლა ingestion YAML ხელით |

| D-25 | **Derivation architecture** — corpus = single source of truth; YAML catalog deprecated; topics/portals/specific links/keywords derived | **approved** — [ADR-011](../adr/011-rag-derivation-architecture.md) |
| D-26 | **Curation overlay** — `ingestion.curation_override` budget ≤50 rows, TTL 90d default, reason mandatory; actions: boost/demote/exclude/pin_as_portal/rename_topic | **approved** — [ADR-011](../adr/011-rag-derivation-architecture.md) §7 |



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
4. **P5-01** — RabbitMQ ingestion → index events — **done**  
5. **P5-02** — crawl smoke → index → retrieval (`ops/ci/rag-pipeline-smoke.sh`) — **done**  
6. **P5-03** — chat catalog+RAG unified response + citations (B-15) — **done**  
7. **B-25** — zero-gap: StructureLookup removed; clarification via corpus (Q-13 superseded) — **done**
8. **P5-ux** — streaming / Redis sessions / feedback (B-17…B-19) — **done** (2026-05-23)

### A2. RAG ხარისხი — ფაზა 8 derivation architecture

**არქიტექტურული source plan: ✅ 100%** — [PHASE-8-ARCHITECTURE-PLAN.md](PHASE-8-ARCHITECTURE-PLAN.md) (master) · P1 [completion](PHASE-8-P1-ARCHITECTURE-COMPLETION.md) · P2 [plan](PHASE-8-P2-ARCHITECTURE-PLAN.md) · P3/P4 [plan](PHASE-8-P3-P4-ARCHITECTURE-PLAN.md)  
**Normative spec:** [RAG-DERIVATION-ARCHITECTURE.md](RAG-DERIVATION-ARCHITECTURE.md) · ADR: [011](../adr/011-rag-derivation-architecture.md) · Flyway **V9–V16**  
**Implementation:** P1 code ✅ · runtime cutover ⏳ · P2+ approved, not started

**Orchestrator (owner):** `.\ops\ci\rag-p1-cutover.ps1` — `-Step status|freeze|prep|gate|run` (manifest `ci.ragP1Cutover`).

**წესი (ZERO-GAP)**: RAG-U-ის ნებისმიერი feature flag prod-ში მხოლოდ მაშინ ჩაირთვება, როცა golden eval (RAG-U12) **≥ baseline**. ძველი YAML catalog რჩება ცოცხალი feature-flag-ით, წაიშლება მხოლოდ eval pass-ის შემდეგ.

**P1 — Foundation (sequence)**:

1. **RAG-U12** — eval harness + golden set 150–300 (გაკეთდეს **პირველი** რომ ყველაფერი დანარჩენი იზომებოდეს)
2. **V9 migration** — `document` enrichment columns (summary, keywords, entities, locale_pair, authority, page_kind, topic_cluster, score_boost, enrichment_version)
3. **V10 migration** — `topic_cluster`, `curation_override`, `enrichment_run`
4. **RAG-U01a** — SummaryDeriver (Gemini batch; ყველაზე დიდი ხარისხის წილი)
5. **RAG-U01b** — KeywordDeriver (YAKE Java port)
6. **RAG-U01c** — EntityDeriver (Gemini few-shot)
7. **RAG-U01d** — LocalePairDeriver (URL pattern + cosine)
8. **RAG-U01f** — PageKindClassifier (Gemini few-shot + URL heuristic pre-filter)
9. **RAG-U01h** — Title/SummaryVectorDeriver (Qdrant named vectors prep)
10. **RAG-U01e** — AuthorityDeriver (JGraphT PageRank, nightly)
11. **RAG-U01g** — TopicMiner + TopicAssigner (Smile k-means + Gemini cluster label)
12. **V11 migration** — `mv_portal_link`, `mv_specific_link`, `mv_topic_keywords` + `CatalogViewRefreshJob`
13. **RAG-U02** — Layer 3 catalog views online; admin approves topic_cluster labels
14. **RAG-U07** — Query understanding pipeline (SpellFix → Normalize → Intent → Entity → Expander)
15. **Eval gate (U12)** — hit@5 with derivation ≥ hit@5 with YAML catalog → unlock P2

**P2 — Retrieval quality (after eval pass)**:

16. **RAG-U08** — Multi-vector Qdrant migration v1 → v2; backfill body+title+summary vectors
17. **RAG-U09** — HyDE + multi-query QueryEmbeddingStrategy
18. **RAG-U10** — Hybrid retriever + RRF fusion + MMR diversifier
19. **RAG-U11** — Confidence tier + ResponseRouter
20. **Eval gate (U12)** — pass → flip default `geostat.chat.catalog.source = derived`

**P3 — Operations & polish**:

21. **RAG-U13** — Feedback-driven score_boost
22. **RAG-U14** — Caching tier (intent cache + retrieval multi-vector cache)
23. **RAG-U05** — Curation overlay UI (admin tab; ≤50 rows budget)
24. **YAML catalog deletion** — `topics.yaml`, loaders, `YamlTopicCatalog`, `NewsCategoryLoader`, `SpecificLinkLoader` წაიშლება (`zero-gap-architecture.mdc`)

**P4+ — Deferred**:

25. **RAG-U15** — Knowledge graph (Apache AGE) — only when corpus > 50K docs OR entity-aware eval gap

**P4+ — Planning gate (not implementation)**:

26. **P8-plan-01** — **Service/package split planning review** — სწორ დროს დავგეგმოთ დამატებით deployable-ებად დაჭრა (spec § 13.6). **არა Phase 8 build task** — owner review meeting + short doc/ADR draft. Implementation მხოლოდ B-37 observed triggers-ზე.

**P1 → 100% — არქიტექტურული maturity (Senior bar)**

> სრული evidence, package map, SOLID, cutover FSM: **[PHASE-8-P1-ARCHITECTURE-COMPLETION.md](PHASE-8-P1-ARCHITECTURE-COMPLETION.md)**  
> **განსხვავება:** *architecture/plan* = 100% (კოდი + კონტრაქტები + manifest + ops); *runtime P1 done* = cutover S0–S6 + eval gate.

| ზოლი | Arch % | Runtime | Done criteria |
|------|--------|---------|---------------|
| **L1 Corpus** | 100 | ✅ | crawler4j+Jsoup → `ingestion.document`; resume; single seed |
| **L2 Enrichment** | 100 | ⏳ | ports/adapters U01a–h; backfill API; ≥95% summary/page_kind |
| **L3 Aggregation** | 100 | ⏳ | remine → approve → `catalog:refresh` → `mv_*` rows > 0 |
| **L4 Catalog (chat-api)** | 100 | ⏳ | dual-mode code ✅; flip `derived` + smoke after S3 |
| **L5 Curation** | 100 | ✅ | overlay REST + budget; UI = P3 |
| **L6 Query understanding** | 100 | ✅ | U07 pipeline; flags OFF until eval |
| **Eval gate (U12)** | 100 | ⏳ | frozen YAML baseline ✅; derived hit@5 ≥ YAML −5% |
| **Ops/manifest** | 100 | ⏳ | `modules.*.derivation`; prod enrichment ON; cutover scripts |
| **Zero-gap** | 100 plan | ⏳ | YAML delete **only** S7 after eval + owner OK |
| **SOLID / layers** | 100 | ✅ | `platform-contracts` ports; readiness single view |
| **Agnostic growth** | 100 | ✅ | flags; manifest; presentation YAML only |

**Runtime sequence (S0–S7)** — იხ. completion doc §7; backfill **background** (`p1-prep-background.log`).

**Tests/quality (P1 შემდეგ, არა ადრე)**:

- Integration: enrichment backfill + readiness gates (`EnrichmentBackfillService`, `DerivationReadinessService`)
- **Unit ✅ started:** `EnrichmentBackfillServiceTest` (queue, progress, failure tolerance, catalog refresh)
- Eval CI: `run-eval.py` regression vs `ops/eval/baseline.yaml-frozen.json`
- Module tests: deriver unit + catalog MV refresh; chat derived catalog smoke in hybrid matrix

**Automation gate (BACKLOG, არა block P1 runtime)**:

- **P0-config-enrichment** — `geostat config-gen` / manifest declares enrichment model + flags (duplicate `.env` literals today)

**Phase 8 plan tree (source 100%):**

```text
docs/plan/PHASE-8-ARCHITECTURE-PLAN.md      ← master
├── PHASE-8-P1-ARCHITECTURE-COMPLETION.md   ← P1 Senior bar + cutover FSM
├── PHASE-8-P2-ARCHITECTURE-PLAN.md         ← U08–U11 (post eval)
├── PHASE-8-P3-P4-ARCHITECTURE-PLAN.md      ← U13/U14/UI, S7 YAML exit, P4+
└── RAG-DERIVATION-ARCHITECTURE.md          ← normative spec §1–19
```

### B. Ops / deploy (ფაზა 6 დასრულება)



5. **P6-migrate** — სერვერზე chat-api + frontend structured deploy — **done** (2026-05-23)  
6. **P0-infra-07** — VS Code compound: tunnel preLaunch + hybrid F5 — **done**  
7. **P0-infra-08** — consumer delegate `hybrid-boot-app.ps1` → `geostat hybrid boot` — **done**  
8. **P0-kit-12** — kit upstream: `geostat hybrid boot` + `fe`/`be`/`ing`/`ret` **`run`** — **done**  
9. **P0-kit-09…11 (ფაზა 0d)** — manifest → Spring/env config generation — **done** (backend env-profiles + ingestion + retrieval)

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

### C. გადაწყვეტილებები — **closed** (2026-05-23)

[ADR-010](../adr/010-product-stack-benefit-gate.md) · [approved](approved/README.md) D-18…D-24

| ID | გადაწყვეტილება |
|----|----------------|
| Q-01 | Gemini prod generation; Ollama optional local (P7-01) |
| Q-02 | Spring AI primary; LangChain4j rejected |
| Q-03 | embedding-adapters (done) |
| Q-04 | Qdrant |
| Q-05 | Jsoup + crawler4j; Playwright trigger (P3-03b) |
| Q-13 | Single pipeline — corpus + RAG (B-25) |
| Q-15 | prod Redis AOF (infra compose) |
| Q-17 | worker = ingestion (closed) |

**Benefit gate (D-18):** ახალი integration მხოლოდ სარგებლისთვის — არქიტექტურა, პროდუქტი, perf, ops.

**Ops (owner):** GEMINI_API_KEY rotation (OPS-01); full corpus reindex data-driven (OPS-02).  

### Hybrid dev ახლა (მზადაა)



```powershell
.\tools\geostat.ps1 infra remote status   # postgres, redis, qdrant
.\tools\geostat.ps1 infra tunnel          # 5432, 6379, 6333 → localhost
# შემდეგ: bootRun / F5 chat-api, retrieval, ingestion
```



განახლების შემდეგ ჩაწერე [CHANGELOG-PLAN.md](CHANGELOG-PLAN.md).

