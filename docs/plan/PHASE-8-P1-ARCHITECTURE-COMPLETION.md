# Phase 8 P1 — Architecture completion (Senior bar)

**Status:** P1 **COMPLETE ✅** · code 100% · runtime cutover 100% (G2 pass, YAML deleted)  
**Spec:** [RAG-DERIVATION-ARCHITECTURE.md](RAG-DERIVATION-ARCHITECTURE.md) · **ADR:** [011](../adr/011-rag-derivation-architecture.md)  
**Master plan:** [PHASE-8-ARCHITECTURE-PLAN.md](PHASE-8-ARCHITECTURE-PLAN.md) · **Next phases:** [P2](PHASE-8-P2-ARCHITECTURE-PLAN.md), [P3-P4](PHASE-8-P3-P4-ARCHITECTURE-PLAN.md)

---

## 1. Completion summary

| Dimension | % | Evidence |
|-----------|---|----------|
| **L1 Corpus pipeline** | 100 | crawler4j + Jsoup + PG frontier; B-26 resume |
| **L2 Enrichment (code)** | 100 | U01a–h ports/adapters; V9–V10; orchestrator + backfill API |
| **L3 Catalog views (code)** | 100 | V13–V16; refresh job; `CatalogViewStatusService` |
| **L4 Curation overlay (code)** | 100 | V10 `curation_override`; REST + budget |
| **L5 Chat catalog dual-mode** | 100 | `yaml \| derived`; `DerivedCatalogReader`; JDBC adapter |
| **L6 Query understanding (code)** | 100 | U07 pipeline; **flags OFF** until eval |
| **L7 Eval harness (code)** | 100 | golden 150; `run-eval.py`; gate scripts |
| **Ops / manifest contract** | 100 | `geostat.ops.json` derivation block; CI scripts wired |
| **Runtime cutover** | ~25 | backfill running; MV rows = 0; catalog still `yaml` |
| **YAML deletion (zero-gap exit)** | 0 | **blocked** until eval gate + owner OK |

**P1 „done“ definition:** runtime cutover steps 0–6 complete + eval gate pass. Code/plan architecture is already at 100%.

---

## 2. Layer model (single pipeline)

```text
                    YAML catalog (coexist until eval)
                              │
L1 Corpus ──► L2 Enrichment ──► L3 Aggregation ──► chat-api catalog
 crawl/parse      derivers         MVs + topics          │
 chunk/index      async/batch      refresh               ▼
     │                │                │            retrieval ◄── chat-api
     └────────────────┴────────────────┴── Qdrant ────────┘
                              │
                         L4 Curation overlay (≤50 rows)
                              │
                         L5 Query understanding (chat-api, OFF)
                              │
                         L6 Eval gate (U12) — unlocks P2 flags
```

**Zero-gap law:** chat-api never crawls. Knowledge enters only via ingestion → Qdrant. Catalog rows come from `mv_*` after enrichment, not parallel YAML authoring (YAML = temporary coexist).

---

## 3. Package map & boundaries

### 3.1 Contracts (`libs/platform-contracts`)

Ports live in `com.geostat.platform.enrichment.*` — **agnostic**, no Spring, no consumer brand:

| Port | Adapter (ingestion) | Idempotency key |
|------|---------------------|-----------------|
| `SummaryDeriver` | `GeminiSummaryDeriver` | `modelVersion` on document |
| `KeywordDeriver` | `YakeKeywordDeriver` | `yake-v1` |
| `EntityDeriver` | `GeminiFewShotEntityDeriver` | `entityModelVersion` |
| `LocalePairDeriver` | `UrlPlusEmbeddingLocalePairer` | `localePairModelVersion` |
| `AuthorityDeriver` | `JGraphTAuthorityDeriver` | nightly batch |
| `PageKindClassifier` | `PageKindUrlHeuristic` → `GeminiFewShotPageKindClassifier` | `pageKindModelVersion` |
| `TopicMiner` | `SmileKMeansTopicMiner` | batch |
| `TopicAssigner` | `EmbeddingNearestTopicAssigner` | batch |
| `DocumentVectorWriter` | `QdrantNamedVectorWriter` / `NoOpDocumentVectorWriter` | U08 gate |

**Pattern:** Port (interface) → Adapter (infra) → Service (application) → Orchestrator (`DocumentEnrichmentOrchestrator`).

### 3.2 Ingestion (`apps/ingestion-service`)

```text
api/              REST — CorpusController, CatalogController, CurationController
catalog/          L3 — refresh, readiness, topic admin
  readiness/      DerivationReadinessService — single gate view (spec §14)
curation/         L4 — overlay CRUD + budget
enrichment/       L2 — deriver adapters + runner + prompt YAML
  runner/         EnrichmentProperties, Orchestrator, BackfillService
events/           async enrichment/index triggers (RabbitMQ)
persistence/      Flyway V1–V16, JPA entities
crawl|parse|chunk|index/   L1 — unchanged single pipeline
quality/          audit, lifecycle sync, eval queries
```

**Flyway (derivation):** V9 document columns · V10 topic_cluster + curation · V11–V13 MVs · V14 evaluation_query extensions · V15 intent cache + pin · V16 refresh audit.

### 3.3 Chat-api (`apps/backend`)

```text
domain/catalog/
  TopicCatalog          ← yaml mode (legacy coexist)
  DerivedCatalogReader  ← jdbc mode (mv_portal_link, mv_specific_link)
  CatalogResponseAssembler, CatalogTopicLabelResolver
  PresentationStyleCatalog ← topic-style.yaml ONLY (presentation)
infrastructure/config/  CatalogProperties — source flag
application/chat/       PromptBuilder, retrieval client — no crawl
```

**Strategy pattern:** `CatalogProperties.source` → `YamlTopicCatalog` | `DerivedCatalogReader`.

---

## 4. SOLID & design patterns

| Principle | P1 implementation |
|-----------|-------------------|
| **S** | One deriver per service; readiness separate from enrichment run |
| **O** | New deriver = new adapter implementing platform port; orchestrator unchanged |
| **L** | `NoOpDocumentVectorWriter` substitutable until U08 |
| **I** | Small ports (`SummaryDeriver`, not god `Enricher`) |
| **D** | Services depend on ports + properties, not Gemini/Qdrant types |

| Pattern | Where |
|---------|--------|
| **Ports & adapters** | `platform-contracts` ↔ ingestion adapters |
| **Strategy** | catalog `yaml \| derived`; query understanding stages |
| **Template method** | enrichment orchestrator pipeline order |
| **Factory / conditional beans** | `@ConditionalOnProperty` enrichment, aggregation |
| **Materialized view** | L3 catalog — refresh, not live join on hot path |
| **Overlay** | L4 curation — small mutable layer on immutable derivation |

---

## 5. Manifest-driven ops (`geostat.ops.json`)

Consumer declares **what**, kit/scripts declare **how**:

| Key | Purpose |
|-----|---------|
| `modules.ingestion.derivation` | P1 corpus, flyway range, env var names, cutover script refs |
| `modules.chat-api.catalog` | source flip env, presentation YAML allowlist |
| `ci.ragP1Cutover` | master orchestrator path |
| `ci.ragDerivationCutoverPrep` | prep API sequence |
| `ci.ragEvalGate` / `ragEvalFreezeYamlBaseline` | eval gate |
| `ci.chatDerivedCatalogSmoke` | post-flip smoke |

Secrets stay in `ops/config/*` (gitignored). Manifest never holds API keys.

---

## 6. Config contract (env)

### Ingestion (`ops/config/ingestion/.env.*`)

| Variable | Layer | P1 prod/hybrid |
|----------|-------|----------------|
| `INGESTION_ENRICHMENT_ENABLED` | L2 | `true` |
| `INGESTION_ENRICHMENT_CHAT_MODEL` | L2 | `gemini-2.5-flash-lite` |
| `INGESTION_AGGREGATION_ENABLED` | L3 | `true` |
| `GEMINI_API_KEY` | L2 | required when enrichment ON |

### Chat-api (`ops/config/backend/.env.*`)

| Variable | Layer | Until eval |
|----------|-------|------------|
| `GEOSTAT_CHAT_CATALOG_SOURCE` | L5 | `yaml` |
| `GEOSTAT_CHAT_CATALOG_JDBC_URL` | L5 | set before flip; default in Spring |
| `GEOSTAT_CHAT_CATALOG_JDBC_USER/PASSWORD` | L5 | PG read ingestion schema |

---

## 7. Cutover state machine (✅ COMPLETE 2026-05-26)

```text
S0  freeze YAML baseline          ✅ ops/eval/baseline.yaml-frozen.json (2026-05-25)
S1  enrichment backfill ≥95%      ✅ summary=100%, page_kind=99.2%
S2  authority + topics:remine     ✅ 5 approved clusters
S3  approve clusters + MV refresh ✅ 10 portal links in MV
S4  lifecycle:sync-qdrant         ✅ 237 docs synced
S5  catalog source = derived      ✅ chat-api live on derived
S6  eval gate pass                ✅ hit@5=100%, MRR=1.0, intent=97.7%
S7  owner OK → delete YAML        ✅ topics.yaml + 4 loaders deleted
```

**G2 Eval Results:**
- hit@1: 100%, hit@5: 100%, MRR: 1.0
- intent_accuracy: 97.7%
- mean_response_ms: 55.7ms
- baseline written: `ops/eval/baseline.json`

---

## 8. Quality gates (spec §14)

| Check ID | Threshold | Blocks eval |
|----------|-----------|-------------|
| `golden_set` | ≥150 queries | yes |
| `parsed_corpus` | ≥1 doc | yes |
| `vector_coverage` | ≥90% | yes |
| `summary_coverage` | ≥95% | yes |
| `page_kind_coverage` | ≥95% | yes |
| `topic_assignment` | ≥80% | yes |
| `topic_clusters_approved` | ≥1 | yes |
| `mv_portal_links` | ≥1 row | yes |
| `catalog_mv_fresh` | within 25h | yes |
| `curation_budget` | ≤50 overrides | no |

Implementation: `DerivationReadinessService` + `DerivationReadinessReader` (JDBC metrics, no duplicate truth).

---

## 9. Explicitly out of P1 (growth path)

| ID | Phase | Reason |
|----|-------|--------|
| RAG-U08–U11 | P2 | eval gate must pass first |
| RAG-U05 UI | P3 | API done |
| RAG-U13–U14 | P3 | feedback + cache tier |
| RAG-U15 | P4+ | corpus scale |
| YAML deletion | S7 | zero-gap exit, not coexist forever |
| P0-config-enrichment | BACKLOG | manifest codegen for env literals |
| P8-plan-01 | gate | service split planning only |

---

## 10. Agnostic & extensible

- **Multi-corpus:** `corpus` table + `{name}` in all APIs; default `geostat-portal` in manifest only.
- **Feature flags:** every U-stage toggled via properties/env — prod rollout = eval-gated.
- **Presentation vs truth:** `topic-style.yaml`, `terminology-overlay.yaml` stay after YAML delete.
- **No kit consumer brand** in platform ports or geostat-kit runtime.
- **New store/driver:** extend manifest `datastores`, not hardcode in derivers.

---

## 11. Verification checklist (architecture sign-off) — ✅ ALL COMPLETE

- [x] Single crawl/index pipeline (no chat-api fetch)
- [x] All U01 ports in `platform-contracts` with ingestion adapters
- [x] Flyway V9–V16 applied
- [x] Dual catalog mode in chat-api
- [x] Curation overlay REST + budget
- [x] Readiness single view
- [x] Cutover scripts + frozen baseline
- [x] Manifest derivation contract
- [x] Runtime S1–S6 — **G2 pass: hit@5=100%, MRR=1.0**
- [x] Eval regression vs frozen baseline — **no regression**
- [x] YAML removal (S7) — **topics.yaml + 4 loaders deleted**

---

*Last updated: 2026-05-26 — **P1 COMPLETE**. G2 eval pass, YAML deleted, derived catalog live.*
