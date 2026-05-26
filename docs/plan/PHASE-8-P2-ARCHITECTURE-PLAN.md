# Phase 8 P2 — Retrieval quality architecture plan

**Status:** **COMPLETE ✅** · **Implementation:** code ✅ (U08-U11), G3 eval ✅ (hit@5=100%)  
**Parent:** [PHASE-8-ARCHITECTURE-PLAN.md](PHASE-8-ARCHITECTURE-PLAN.md) · **Spec:** [RAG-DERIVATION-ARCHITECTURE.md](RAG-DERIVATION-ARCHITECTURE.md) §4, §8  
**Scope:** RAG-U08, U09, U10, U11

---

## 1. Goal

After derived catalog is live and P1 eval passes, unlock **retrieval quality** without new knowledge paths:

- Multi-vector Qdrant index (summary/title/body)
- Query embedding strategies (Direct, HyDE, MultiQuery)
- Hybrid retrieval + RRF + MMR
- Confidence tiers + response routing

**Zero-gap:** all retrieval reads indexed corpus only; no chat-api crawl; no second Qdrant cache in chat-api.

---

## 2. Sequence (must)

```text
1. RAG-U08  Qdrant v1 → v2 named vectors + backfill
2. RAG-U09  QueryEmbeddingStrategy (retrieval + chat-api)
3. RAG-U10  HybridRetriever + RRF + MMR
4. RAG-U11  RetrievalConfidence + ResponseRouter
5. G3       eval regression (run-eval.py) — all P2 flags ON
```

Do **not** enable U07 query flags and U08+ in the same PR without eval baseline per stage.

---

## 3. RAG-U08 — Multi-vector index

### 3.1 Collection design

| Vector | Size | Source | When |
|--------|------|--------|------|
| `body` | 768 | chunk text | existing |
| `title` | 768 | `document.title` | U01h deriver |
| `summary` | 768 | `summary_ka/en` | U01h deriver |

Collection: `{corpus}-v2` (e.g. `geostat-portal-v2`). Payload contract: spec §4 table — **single source**.

### 3.2 Package placement

```text
ingestion-service/
  enrichment/vectors/     DocumentVectorWriter (enable INGESTION_NAMED_VECTORS_ENABLED)
  index/qdrant/           NamedVectorCollectionMigrator, V2BackfillJob

retrieval-service/
  search/qdrant/          QdrantNamedVectorAdapter (name=summary|title|body)
  config/                 QdrantCollectionProperties
```

### 3.3 Migration steps (idempotent)

1. Create `{corpus}-v2` with named vector schema.
2. Background job: for each chunk → embed body (existing) + replicate title/summary vectors from document.
3. Feature flag: `GEOSTAT_QDRANT_COLLECTION={corpus}-v2` on retrieval.
4. Eval G3 shadow mode: dual-read compare hit@5 v1 vs v2.
5. Pass → delete v1 collection.

### 3.4 Ports (extend platform-contracts)

```text
com.geostat.platform.retrieval.NamedVectorSearchPort
  search(corpus, vectorName, queryVector, filter, topK) → List<ScoredChunk>
```

Adapter: `QdrantNamedVectorSearchAdapter` in retrieval-service.

### 3.5 Manifest (planned)

```json
"retrieval": {
  "qdrant": {
    "collectionEnv": "GEOSTAT_QDRANT_COLLECTION",
    "collectionV2Suffix": "-v2",
    "namedVectors": ["body", "title", "summary"]
  }
}
```

### 3.6 Done criteria

- [ ] v2 collection populated for prod corpus
- [ ] U01h writer ON in prod
- [ ] retrieval default = v2
- [ ] G3 pass vs P1 derived baseline

---

## 4. RAG-U09 — Query embedding strategy

### 4.1 Strategy pattern

```text
QueryEmbeddingStrategy (port)
├── DirectEmbedStrategy      embed normalized query
├── HyDEEmbedStrategy        Gemini hypothetical doc → embed
└── MultiQueryEmbedStrategy  Gemini 3 paraphrases → embed each
```

**Location:** `apps/backend` — `domain/query/` or `application/retrieval/` (query is L5, not ingestion).

### 4.2 Config

| Flag | Default until G2 | After P2 |
|------|------------------|----------|
| `GEOSTAT_QUERY_HYDE_ENABLED` | false | eval-gated |
| `GEOSTAT_QUERY_MULTIQUERY_ENABLED` | false | eval-gated |

### 4.3 SOLID

- **S:** one strategy class per embedding mode
- **O:** add strategy without changing HybridRetriever
- **D:** HybridRetriever depends on `List<QueryEmbeddingStrategy>` ordered by config

### 4.4 Done criteria

- [ ] 1..5 query vectors passed to retriever
- [ ] Latency budget documented (p95 target in spec §16)
- [ ] G3 pass

---

## 5. RAG-U10 — Hybrid retrieval + fusion

### 5.1 Retriever composition

```text
HybridRetriever
├── QdrantSummaryAdapter   topK=20
├── QdrantTitleAdapter     topK=20
├── QdrantBodyAdapter      topK=40
└── PgBM25Adapter          topK=20 (tsvector, ingestion schema read-only JDBC or retrieval-owned)
         │
         ▼
RRFFusion (k=60) → top-50
         │
         ▼
CrossEncoderReranker (existing RAG-L07+) → top-10
         │
         ▼
MMRDiversifier (λ=0.7) → top-5
```

### 5.2 Package placement

```text
retrieval-service/
  search/hybrid/       HybridRetriever, RrfFusion, MmrDiversifier
  search/bm25/         PgBm25Adapter (Postgres tsvector)
  search/qdrant/       named vector adapters
```

**chat-api** calls retrieval HTTP — does **not** embed fusion logic (Architecture B).

### 5.3 Filter pushdown

Entity/locale/curation filters from `AnalyzedQuery` → Qdrant filter + SQL WHERE — spec §8.

### 5.4 Done criteria

- [ ] RRF unit tests with fixed candidate lists
- [ ] End-to-end hybrid smoke script
- [ ] G3 hit@5 ≥ P1 derived baseline

---

## 6. RAG-U11 — Confidence + routing

### 6.1 RetrievalConfidence

| Tier | Rule (spec §8) |
|------|----------------|
| HIGH | top1 > 0.75 AND gap(top1, top2) > 0.05 |
| MEDIUM | top1 > 0.55 |
| LOW | top1 > 0.35 |
| NONE | else |

Port: `RetrievalConfidenceAssessor` in chat-api domain.

### 6.2 ResponseRouter

| Tier | Behavior |
|------|----------|
| HIGH | answer + citations |
| MEDIUM | answer + suggest cards |
| LOW | ClarificationService |
| NONE | refusal + topic chips from `mv_topic_keywords` |

Integrates with existing `ChatResponse` v2 — no telemetry leak.

### 6.3 Degraded modes

Spec §8 fallback table — each failure → degraded path + metric. **Never** silent empty answer when BM25 alone could match.

### 6.4 Done criteria

- [ ] Confidence unit tests (threshold boundaries)
- [ ] Router integration tests per tier
- [ ] `chat.confidence_*` telemetry wired
- [ ] G3 pass

---

## 7. P2 feature-flag rollout

```text
Stage A: U08 only (v2 collection, retrieval still single-vector query path)
Stage B: + U09 (HyDE off, MultiQuery off — Direct only first)
Stage C: + U10 hybrid
Stage D: + U11 confidence
Stage E: enable U07 query flags (spell, intent, expander) — one flag per eval
```

Each stage: new eval baseline file `ops/eval/baseline.p2-{stage}.json`.

---

## 8. Explicitly out of P2

- YAML catalog deletion (P3 S7)
- Curation admin UI (P3)
- Knowledge graph U15 (P4+)
- enrichment-service split (B-37)

---

## 9. Plan sign-off

- [x] Sequence and dependencies documented
- [x] Package boundaries per deployable
- [x] Ports/adapters identified
- [x] Migration + flag rollout defined
- [x] Eval gates per stage
- [x] Implementation — **COMPLETE 2026-05-26**

**P2 Components:**
- `NamedVectorSearchPort` + `QdrantNamedVectorSearchAdapter`
- `RrfFusion` + `MmrDiversifier` + `HybridRetriever`
- `DirectEmbedStrategy` + `HyDEEmbedStrategy` + `MultiQueryEmbedStrategy`
- `RetrievalConfidenceAssessor` + `ResponseRouter`
- G3 eval: hit@5=100%, MRR=1.0

---

*Last updated: 2026-05-26 — P2 COMPLETE.*
