# Phase 8 P3 / P4+ — Operations, exit, deferred

**Status:** **COMPLETE ✅** · **Implementation:** P3 code ✅ (U13, U14, U05 API), G4 eval ✅  
**Parent:** [PHASE-8-ARCHITECTURE-PLAN.md](PHASE-8-ARCHITECTURE-PLAN.md)  
**Spec:** [RAG-DERIVATION-ARCHITECTURE.md](RAG-DERIVATION-ARCHITECTURE.md) §7, §10–§11, §17

---

## 1. P3 scope

| ID | Item | Layer | Plan | Code |
|----|------|-------|------|------|
| RAG-U13 | Feedback → `score_boost` | L2 | ✅ | ✅ |
| RAG-U14 | Intent + retrieval cache tier | L5/L7 | ✅ | ✅ |
| RAG-U05 | Curation overlay **UI** | L4 | ✅ | ✅ (API + React UI) |
| **S7** | YAML catalog **deletion** | zero-gap exit | ✅ | ✅ (done in P1) |

**Gate:** P2 eval G3 pass before P3 prod rollout.

---

## 2. RAG-U13 — Feedback-driven score boost

### 2.1 Data flow

```text
chat.feedback_* (PG, B-30 telemetry)
    → nightly FeedbackScoreAggregator (ingestion or chat-api batch)
    → UPDATE ingestion.document.score_boost [0.5 .. 2.0]
    → Qdrant payload republish (chunk points)
    → catalog MV refresh (authority/rank may incorporate boost)
```

### 2.2 Architecture

- **Port:** `ScoreBoostPolicy` — maps feedback signals → boost delta
- **Single writer:** ingestion owns `document.score_boost` (corpus truth)
- **Idempotent:** aggregator version column; reruns safe

### 2.3 Done criteria

- [ ] Aggregator job + Flyway audit table optional
- [ ] Boost reflected in Qdrant payload within 24h
- [ ] Eval shows no regression on portal link ranking smoke

---

## 3. RAG-U14 — Caching tier

### 3.1 Layers

| Cache | Store | TTL | Key |
|-------|-------|-----|-----|
| Intent | PG `query_intent_cache` (V15) | 24h | hash(normalized query) |
| Retrieval multi-vector | Redis | 1h | corpus + vector names + query hash |
| Response (optional) | Redis | 5m | session + query hash |

### 3.2 Rules

- Cache **invalidates** on corpus reindex or MV stale > 25h
- Redis optional — degrade to no-cache (existing pattern `RETRIEVAL_CACHE_BACKEND`)
- No second corpus in chat-api memory

### 3.3 Done criteria

- [ ] Intent cache wired when U07c ON
- [ ] Hit rate telemetry (`cache.hit_ratio`)
- [ ] Document invalidation playbook in ingestion README

---

## 4. RAG-U05 — Curation overlay UI (admin)

### 4.1 Scope

Frontend admin tab — CRUD on `ingestion.curation_override`:

- Actions: boost, demote, exclude, pin_as_portal, rename_topic
- Budget gauge: ≤50 active rows (D-26)
- Mandatory `reason` + TTL default 90d

### 4.2 Architecture

```text
frontend/admin/curation/     thin UI
    → chat-api BFF or direct ingestion REST (prefer ingestion API — single owner)
ingestion/api/CurationController   existing P1 REST
```

**No business logic in frontend** — validation server-side.

### 4.3 Done criteria

- [ ] Admin route + auth gate (existing admin pattern)
- [ ] Budget enforced UI-side display + server 409 on exceed
- [ ] E2E smoke: create override → visible in chat link ranking

---

## 5. S7 — Zero-gap YAML exit (normative)

### 5.1 Preconditions (all required)

1. G2 P1 eval pass (derived catalog)
2. G3 P2 eval pass (if P2 shipped)
3. Owner explicit OK
4. Grep shows no production path loading `topics.yaml`

### 5.2 Delete list (spec §17)

```text
DELETE:
  apps/backend/src/main/resources/catalog/topics.yaml
  apps/backend/.../YamlTopicCatalog.java
  apps/backend/.../TopicCatalogLoader.java
  apps/backend/.../SpecificLinkLoader.java
  apps/backend/.../NewsCategoryLoader.java

SIMPLIFY:
  CatalogRagLinkMerger → mv_portal_link + mv_specific_link only

KEEP (presentation):
  topic-style.yaml
  terminology-overlay.yaml
```

### 5.3 Config after S7

| Setting | Value |
|---------|-------|
| `GEOSTAT_CHAT_CATALOG_SOURCE` | `derived` (only mode) |
| Remove yaml branch | delete `YamlTopicCatalog` bean wiring |
| `CatalogProperties.source` | default `derived`; remove yaml enum after deprecation period |

### 5.4 Verification (mandatory)

```powershell
# No legacy loaders
rg "YamlTopicCatalog|topics\\.yaml" apps/backend --glob "*.java"
# Default derived
rg "GEOSTAT_CHAT_CATALOG_SOURCE" ops/config
# Eval still passes
.\ops\ci\rag-eval-gate.ps1
```

Update CHANGELOG + mark Phase 8 **done** in PROJECT-PLAN when S7 complete.

---

## 6. P4+ — Deferred

### 6.1 RAG-U15 — Knowledge graph (Apache AGE)

**Triggers (any):**

- Corpus > 50K documents
- entity_F1 < 0.75 after P2
- Owner requests entity-aware navigation product

**Plan-only:** new ADR required before code. Graph is **overlay on PG**, not second crawl path.

### 6.2 P8-plan-01 — Service split planning review

**When:** P3 complete (YAML deleted) + **30 days** prod telemetry  
**Output:** telemetry report + split map + stay/extract decision  
**Not implementation** unless B-37 three triggers fire simultaneously:

1. Gemini rate limit affects chat-api from shared enrichment key
2. enrichment_run p95 > 30 min per 1K batch
3. crawl pages/hour −30% with enrichment queue depth > 0

### 6.3 BACKLOG cross-refs

| ID | Relation to Phase 8 |
|----|---------------------|
| B-37 | enrichment-service extraction trigger |
| P0-config-enrichment | manifest codegen for enrichment env |
| B-31…B-36 | generic RAG improvements, not Phase 8 blockers |

---

## 7. Document lifecycle (maintenance)

Spec §17 state machine — **single mental model** for ops:

`discovered → … → enriched → indexed → live`

chat-api cites only `live` | `partial_enriched`. Document in ingestion README + INGESTION-DATA-MODEL cross-link.

---

## 8. Phase 8 closure criteria (project level)

Phase 8 marked **done** in PROJECT-PLAN when:

- [x] Architectural source plan 100% (master + P1/P2/P3-P4 docs)
- [ ] P1 runtime S0–S6 + G2
- [ ] P2 U08–U11 + G3 (if approved for prod)
- [ ] P3 U13/U14/UI as approved
- [ ] S7 YAML deleted + grep clean
- [ ] CHANGELOG final row

---

## 9. Plan sign-off

- [x] P3 items scoped with packages and gates
- [x] S7 exit checklist normative — **done in P1**
- [x] P4+ triggers documented
- [x] P8-plan-01 scheduling clear
- [x] Implementation — **COMPLETE 2026-05-26**

**P3 Components:**
- `ScoreBoostPolicy` port + `DefaultScoreBoostPolicy` + `FeedbackScoreAggregator`
- `CachingIntentClassifier` decorator (Caffeine, 24h TTL)
- Curation Admin UI: `CurationAdmin.jsx` (React), budget gauge, CRUD
- G4 eval: hit@5=100%, MRR=1.0, mean_ms=31.9ms

---

*Last updated: 2026-05-26 — P3 COMPLETE. Zero-gap exit done in P1.*
