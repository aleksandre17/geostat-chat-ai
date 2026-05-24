# Backlog — ჯერ არა დამტკიცებული

იდეები სანამ `PROJECT-PLAN.md`-ში **approved** არ გახდება. დამტკიცებისას: გადატანა PLAN-ში + `CHANGELOG-PLAN.md` + საჭიროებისას ADR.

| ID | იდეა | შენიშვნა |
|----|------|----------|
| B-01 | RabbitMQ ingestion → index events | async; self-host infra compose — [INFRA-DATA-STORES.md](INFRA-DATA-STORES.md) |
| B-02 | LangChain4j vs მხოლოდ Spring AI | **rejected** (2026-05-23) — Q-02 closed: Spring AI primary; [ADR-010](../adr/010-product-stack-benefit-gate.md) |
| B-03 | Playwright dynamic pages | **approved conditional** — P3-03b; მხოლოდ corpus audit trigger (SPA empty-body threshold) |
| B-04 | Ollama local LLM | **→ PLAN P7-01** — local/hybrid profile generation only; prod stays Gemini (Q-01) |
| B-05 | `apps/backend/worker` გაუქმება / გაერთიანება ingestion-თან | **done** (2026-05-22) — `features.worker: false`, manifest `ingestion` |
| B-06 | Rename module `backend` → `chat-api` in manifest | **done** (2026-05-23) |
| B-21 | chat-api package layout | **done** (2026-05-23) |
| B-22 | Shared Qdrant client lib | **done** (2026-05-23) |
| B-07 | Integration test: full RAG question → answer | **done** (2026-05-23) |
| B-08 | `libs/` გადატანა `apps/`-ში | **rejected** — libs root-ზე რჩება (2026-05-21) |
| B-09 | `kits/geostat-kit/docs/DEV-MODES.md` §④ Hybrid | **done** |
| B-10 | `geostat infra` driver | **done** |
| B-11 | Kit: `embeddedWorker` manifest (`features.worker` deprecate) | **done** (2026-05-23) — P0-kit-13 |
| B-12 | Server prod migrate: flat → structured | **done** (2026-05-23) |
| B-13 | retrieval/ingestion `ops.config.sh` dev overrides | **cancelled** (2026-05-23) — files exist; `config-gen` + `ops/config` sufficient |
| B-14 | Manifest → Spring profile YAML | **→ PLAN** P0-kit-09…11 **done** |
| B-15 | Chat: catalog + RAG unified `items` | **done** |
| B-16 | Chat: prod RAG + real embeddings | **done** |
| B-17 | Chat: streaming SSE | **done** (2026-05-23) |
| B-18 | Chat: Redis session history | **done** (2026-05-23) |
| B-19 | Chat: feedback + retrieval telemetry | **done** (2026-05-23) |
| B-20 | Hybrid ④ kit upstream | **done** — P0-kit-12 |
| B-24 | Catalog YAML externalization | **done** — topics, specific-links, loaders |
| B-25 | Zero-gap: StructureLookup removed | **done** — `CorpusContextFormatter`; single seed + link discovery |
| B-26 | Continuous full-site crawl + async index (prod) | **done** — V3 policy, frontier resume, auto-continue, scheduler |
| B-27 | Prompt YAML externalization + Gemini audit fixes | **done** — `resources/prompts/`, JSON schema, session enrichment, stream intro |
| R-01…R-06 | Chat response contract (citations, metadata, no telemetry leak) | **done** — `ChatResponse` v2; server-only hits |
| RAG-L01…L10 | Dual-locale RAG pipeline (complete baseline + ops) | **done** | V4/V5 Flyway, hybrid keyword ON, cache, eval CI, `rag-locale-pipeline.ps1` |
| RAG-L07+ | Semantic cross-encoder rerank | **done** | `SemanticCrossEncoderReranker` |
| RAG-cache-redis | Redis retrieval cache | **done** | optional `RETRIEVAL_CACHE_BACKEND=redis` |
| P3-03b | Playwright audit refetch | **done** | not default crawl; `POST …/playwright-refetch` |
| B-28 | Raw HTML archive (S3/MinIO) | **done** | `S3RawHtmlArchive`, `RawHtmlArchivePort`; MinIO via `INGESTION_RAW_ARCHIVE_*` |
| RAG-freshness | Incremental HTTP re-fetch | **done** | `DocumentFreshnessRefreshService`, `POST …/freshness-refresh` |
| OPS-hybrid-jar | Windows JAR boot (no Gradle lock) | **done** | kit `Invoke-HybridJarBoot.ps1` + manifest `preferJar` + `hybrid-jar-boot.ps1` |
| RAG-Qdrant-locale | Separate ka/en Qdrant collections | **closed** | payload filter + locale field (by design) |
| Q-05-Playwright | Default crawl via Playwright | **closed** | audit trigger only (P3-03b) |
| B-30 | Chat `chat.*` telemetry PG | **done** | profile `telemetry-db` |
| OPS-02 | Full corpus reindex (50-page smoke-ს მიღმა) | **tooling done** | `rag-full-corpus-crawl.ps1` + owner trigger |
| RAG-U-original-U01 | YAML→DB raw migration (`topic`, `portal`, `specific_link`, `news_category`) | **rejected (2026-05-24)** — superseded by derivation (RAG-U01a..h); see [ADR-011](../adr/011-rag-derivation-architecture.md) |
| RAG-U03 | SourceComposer (catalog vs RAG separate orchestrator) | **rejected (2026-05-24)** — merged into RAG-U10 unified hybrid retriever; portals/specific links live in `mv_portal_link`/`mv_specific_link`, not a separate code path |
| RAG-U04 | Hybrid keyword topic classifier in chat-api | **rejected (2026-05-24)** — replaced by RAG-U07c IntentClassifier (intent is broader than topic; topic_cluster_id assigned at enrichment time, not at query time) |
| RAG-U06 | Public catalog REST API | **dropped (2026-05-24)** — no external consumer; derived views accessed via existing retrieval-service |
| RAG-U-py-sidecar | Python sidecar (Stanza/BERTopic/KeyBERT) | **deferred** — Java-native (Gemini few-shot + Smile + YAKE) tried first per ADR-010 benefit gate; reconsider only if eval entity_F1 < 0.75 OR topic_quality_score below threshold |
| RAG-U15 | Knowledge graph (Apache AGE on Postgres) | **deferred (P4+)** — corpus too small (<50K docs); revisit when scale or entity-aware eval gap demands |

### გადაწყვეტილების კითხვები

სრული აღწერა: [SOURCE-RAG-DESIGN-PROJECTS-FILES.md](SOURCE-RAG-DESIGN-PROJECTS-FILES.md) §9 · [ADR-010](../adr/010-product-stack-benefit-gate.md).

| ID | კითხვა | სტატუსი |
|----|--------|---------|
| Q-01 | Primary LLM: Gemini vs Ollama+Llama | **closed** — hybrid: Gemini prod gen; Ollama local optional (P7-01) |
| Q-02 | Spring AI vs LangChain4j | **closed** — Spring AI primary |
| Q-03 | Embeddings | **closed** — `libs/embedding-adapters` |
| Q-04 | Qdrant vs Weaviate | **closed** — Qdrant |
| Q-05 | Jsoup only vs +Playwright | **closed** — Jsoup default; Playwright = P3-03b trigger |
| Q-06 … Q-12 | იხ. SOURCE დოკი | open (non-blocking) |
| Q-13 | StructureLookup vs RAG | **superseded** — B-25 single pipeline; StructureLookup removed |
| Q-14 | Postgres cluster vs DB per service | **closed** |
| Q-15 | Redis persistence dev/prod | **closed** — prod AOF in infra compose |
| Q-16 | Infra compose vs full stack | **closed** |
| Q-17 | backend/worker vs ingestion | **closed** |

### ოპერაციული (არა კოდი)

| ID | თემა | სტატუსი |
|----|------|---------|
| OPS-01 | **GEMINI_API_KEY rotation** | **owner action** — [OPS-GEMINI-KEY-ROTATION.md](OPS-GEMINI-KEY-ROTATION.md); preflight: `ops/ci/ops-gemini-key-preflight.ps1` |
| OPS-02 | Full corpus reindex (50-page smoke-ს მიღმა) | **tooling done** — `corpus-quality-audit`, `corpora/{name}/reindex`, `rag-locale-pipeline.ps1` |

---

## როგორ დავამატოთ

```markdown
| B-XX | ახალი იდეა | მოკლე ახსნა |
```

დამტკიცება → კოპირება `PROJECT-PLAN.md` + `CHANGELOG-PLAN.md` + ADR თუ არქიტექტურული fork.
