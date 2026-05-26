# Generic RAG stack — რა უკეთესია და რას ვიღებთ

განახლება: **2026-05-24** · წყარო: owner generic blueprint vs [PROJECT-PLAN.md](PROJECT-PLAN.md) + [ADR-010](../adr/010-product-stack-benefit-gate.md) + [ADR-011](../adr/011-rag-derivation-architecture.md)

ეს დოკუმენტი **მხოლოდ** generic გეგმის ის ასპექტებს აჯამებს, სადაც ის ჩვენს დამტკიცებულ გზაზე **უკეთესია ან უფრო ნათელია**. Stack-ის ცვლილებები (LangChain4j, Chroma, Ollama prod primary, Kafka day-1) **არ შედის** — ADR-010 closed.

---

## 1. Executive — generic-ის 8 უპირატესობა

| # | Generic-ში უკეთესი | ჩვენთან ახლა | რა ვიღებთ |
|---|-------------------|-------------|-----------|
| 1 | **MVP mental model** — ერთი pipeline diagram | 3 service + kit + plan spread | 📋 `docs/RAG-MVP-FLOW.md` one-pager |
| 2 | **100% free local dev path** — Ollama/hash, key-ის გარეშე | partial (hash embed, hybrid) | 📋 kit `DEV-MODES.md` § free path |
| 3 | **Hardware honesty** — 8/16/32GB RAM guidance | არ არის დოკumented | 📋 `HYBRID-DEV-ARCHITECTURE.md` § Ollama sizing |
| 4 | **Content cleaning checklist** — explicit REMOVE list | implemented, not canonical doc | 📋 `ingestion-service/README.md` § cleaning |
| 5 | **Duplicate / near-duplicate layer** — named concern | `content_hash` only; no near-dup | 📋 B-31 near-duplicate detector |
| 6 | **Scaling topology** — workers × N → bus → Qdrant cluster | RabbitMQ + single ingestion | 📋 B-32 scale threshold doc |
| 7 | **Multi-site registry** — `websites` domain model | `corpus` row (works, less obvious) | 📋 B-33 multi-corpus ops UI |
| 8 | **Realtime crawl + auto re-index** — explicit product phase | freshness + scheduler (partial) | 📋 B-34 realtime crawl policy |

---

## 2. დეტალური აღწერა — რას ვიღებთ

### G-01 — MVP one-page flow (onboarding)

**Generic უკეთესი:** ერთი ASCII diagram — ახალი dev 5 წუთში ხვდება ჯაჭვს.

**ჩვენთან:** info spread across `ARCHITECTURE-B-SERVICES.md`, `INGESTION-DATA-MODEL.md`, `RAG-DERIVATION-ARCHITECTURE.md`.

**Baseline adoption:**

```text
docs/RAG-MVP-FLOW.md  (~1 page)
  User → chat-api → retrieval → Qdrant
  crawl → ingestion → RabbitMQ → index
  link to Phase 8 derivation as "next"
```

**Plan ID:** `P8-doc-01` · **Priority:** P3 (docs only) · **No code change**

---

### G-02 — Free local dev path (zero Gemini key)

**Generic უკეთესი:** explicit „install Ollama → run llama3 → done" contributor story.

**ჩვენთან:** hash-v1 embeddings + hybrid tunnel exist; Ollama gen = P7-01 backlog; story not unified.

**Baseline adoption:**

```text
kits/geostat-kit/docs/DEV-MODES.md — new § "Free path (no API key)"
  1. geostat infra tunnel
  2. hash-v1 embed profile OR Ollama embed (when P7-01)
  3. smoke: ops/ci/rag-pipeline-smoke with RETRIEVAL_ENABLED=false fallback
```

**Plan ID:** `P7-01` (Ollama adapter) + `P7-doc-01` (free path doc) · **Priority:** P3

---

### G-03 — Hardware / Ollama sizing guide

**Generic უკეთესი:** honest RAM table (8GB min, 16GB good, 32GB+ for large Llama).

**ჩვენთან:** absent — contributors guess.

**Baseline adoption:**

| Profile | RAM | Model | Use |
|---------|-----|-------|-----|
| minimal | 8GB | hash-v1 embed only | CI / no LLM |
| hybrid | 16GB | Ollama llama3.2:3b or gemma2:2b | local chat dev |
| quality local | 32GB+ | llama3.1:8b | optional P7-01 |

Add to [HYBRID-DEV-ARCHITECTURE.md](HYBRID-DEV-ARCHITECTURE.md) § new.

**Plan ID:** `P7-doc-02` · **Priority:** P3 · **No code**

---

### G-04 — Content cleaning canonical checklist

**Generic უკეთესი:** explicit REMOVE list (navbar, footer, ads, scripts, cookie popups, duplicate text).

**ჩვენთან:** `HtmlContentCleaner`, `DisplayBoilerplate` — **implemented** but not documented as single checklist operators can extend.

**Baseline adoption:**

```markdown
# ingestion-service/README.md § Content cleaning policy
REMOVE (always):
- nav, header menus, footer, breadcrumb-only blocks
- script/style, cookie/consent banners, accessibility boilerplate
- duplicate locale switcher text, social share widgets
KEEP:
- main/article content, tables with data, definition lists
Reference: DisplayBoilerplate markers (sync when adding markers)
```

**Plan ID:** `P3-doc-01` · **Priority:** P2 (low effort, helps crawl quality) · **No ADR**

---

### G-05 — Near-duplicate detection (beyond content_hash)

**Generic უკეთესი:** named „Duplicate Detector — Hash / Similarity Check" layer before chunking.

**ჩვენთან:** `content_hash` on exact body match only. Same page re-published with tiny edits → duplicate chunks in Qdrant.

**Baseline adoption:**

```text
Port: NearDuplicateDetector
  - exact: content_hash (existing)
  - near: SimHash or MinHash on normalized text; threshold 0.92
  - action: skip re-chunk OR mark supersedes_document_id (existing column)
Placement: ingestion parse pipeline, before DocumentChunkWriter
Test: two HTML fixtures 95% similar → one indexed
```

**Plan ID:** `B-31` · **Priority:** P2 · **Benefit:** fewer redundant vectors, better retrieval diversity

---

### G-06 — Horizontal scaling topology (when needed)

**Generic უკეთესი:** clear scale story — Crawler Workers ×10 → Kafka → Processing ×20 → Embedding ×5 → Qdrant cluster.

**ჩვენთან:** single ingestion-service + RabbitMQ; sufficient for geostat.ge corpus (~5-10K pages). No documented threshold when to split.

**Baseline adoption (doc + gate, not implementation now):**

```text
docs/plan/SCALE-THRESHOLDS.md
  < 10K pages: current Architecture B (RabbitMQ) — sufficient
  10K–100K: horizontal ingestion replicas + shared Postgres frontier
  > 100K OR >500 crawl pages/hour: evaluate Kafka + dedicated workers (benefit gate)
  Qdrant: single node → cluster when collection > 2M vectors
```

**Plan ID:** `B-32` · **Priority:** P4+ · **Trigger:** corpus quality audit shows ingest lag

---

### G-07 — Multi-site / multi-corpus clarity

**Generic უკეთესი:** `websites` table with domain, status, last_crawled — obvious multi-tenant model.

**ჩვენთან:** `ingestion.corpus` JSONB policy — **correct and more flexible**, but less obvious for „add second site".

**Baseline adoption:**

```text
Document in INGESTION-DATA-MODEL.md:
  corpus.name = logical site (geostat-portal, future: geostat-open-data)
  seed_urls + policy JSON = generic's websites row
  ops: POST /corpora to add new site (API exists)
Optional P4: admin UI corpus list (status, last_crawl, page count)
```

**Plan ID:** `B-33` · **Priority:** P4 · **No schema change** — documentation + optional UI

---

### G-08 — Realtime crawl + auto re-index (product feature)

**Generic უკეთესი:** lists „realtime crawling, auto re-indexing" as explicit next phase.

**ჩვენთან:** `DocumentFreshnessRefreshService`, scheduler, RabbitMQ index — **partially there**, not named as product capability.

**Baseline adoption:**

```text
Corpus policy extension (ingestion.corpus.policy JSON):
  freshnessIntervalHours: 168        # weekly default
  autoReindexOnChange: true         # already via content_hash + events
  priorityUrls: []                  # curation pin list → faster refresh
Manifest flag: corpus.autoRefreshEnabled
Ops: geostat ing corpus refresh --name geostat-portal
```

**Plan ID:** `B-34` · **Priority:** P3 · **Builds on:** RAG-freshness (done), B-26 scheduler

---

### G-09 — Chunk size guidance (500–1000 tokens)

**Generic უკეთესი:** explicit token target in architecture doc.

**ჩვენთან:** `FixedSizeChunker` exists; target not in README.

**Baseline adoption:** document current chunk params in `ingestion-service/README.md`; tune if eval shows context truncation issues.

**Plan ID:** `P3-doc-02` · **Priority:** P3 · **Check:** read FixedSizeChunker defaults and document

---

## 3. Generic-იდან **არ** ვიღებთ (ADR closed)

| Generic idea | Why not |
|---|---|
| LangChain4j | Q-02 rejected — Spring AI primary |
| ChromaDB | Q-04 closed — Qdrant operational |
| Ollama primary prod LLM | D-16 — Gemini prod quality |
| Kafka day-1 | RabbitMQ sufficient; benefit gate for scale |
| 10 microservices MVP | Architecture B — 3 deployables |
| Hand `pages` catalog without derivation | ADR-011 — corpus-derived catalog |
| api-gateway / auth-service separate | no multi-tenant auth requirement yet |

---

## 4. Adoption roadmap (priority)

```text
P2 (quick wins, docs + small feature)
  P3-doc-01  G-04 content cleaning checklist
  B-31       G-05 near-duplicate detector

P3 (docs + polish)
  P8-doc-01  G-01 MVP one-page flow
  P7-doc-01  G-02 free local dev path
  P7-doc-02  G-03 Ollama hardware guide
  P3-doc-02  G-09 chunk size documented
  B-34       G-08 realtime refresh policy explicit

P4+ (when measured need)
  B-32       G-06 scale topology / Kafka gate
  B-33       G-07 multi-corpus admin UI
  P7-01      Ollama chat gen adapter (already approved)
```

---

## 5. Summary table — generic wins only

| ID | Generic win | Action | Effort |
|----|-------------|--------|--------|
| G-01 | Simple pipeline diagram | `docs/RAG-MVP-FLOW.md` | 1h doc |
| G-02 | Free dev without API key | DEV-MODES § | 2h doc |
| G-03 | RAM/hardware honesty | HYBRID-DEV § | 1h doc |
| G-04 | Cleaning REMOVE checklist | ingestion README | 1h doc |
| G-05 | Near-duplicate layer | B-31 code | 1-2 days |
| G-06 | Scale worker topology | B-32 doc + gate | doc now, code later |
| G-07 | Multi-site clarity | B-33 doc + UI later | doc 1h |
| G-08 | Realtime re-index named | B-34 policy + ops | 1 day |
| G-09 | Chunk token target | P3-doc-02 | 30min doc |

**Total quick wins (P2-P3 docs):** ~1 day · **One code feature worth doing first:** B-31 near-duplicate.

---

## References

- Generic blueprint (owner paste, 2026-05-24)
- [ADR-010 Product stack benefit gate](../adr/010-product-stack-benefit-gate.md)
- [ADR-011 RAG derivation architecture](../adr/011-rag-derivation-architecture.md)
- [RAG-DERIVATION-ARCHITECTURE.md](RAG-DERIVATION-ARCHITECTURE.md)
- [HYBRID-DEV-ARCHITECTURE.md](HYBRID-DEV-ARCHITECTURE.md)
- [INGESTION-DATA-MODEL.md](INGESTION-DATA-MODEL.md)
