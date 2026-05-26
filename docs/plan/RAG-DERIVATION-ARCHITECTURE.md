# RAG Derivation Architecture — სრული spec

განახლება: **2026-05-26** · სტატუსი: **COMPLETE** · P1 **100% ✅** · G2 pass · YAML deleted · ADR: [011-rag-derivation-architecture](../adr/011-rag-derivation-architecture.md)

ეს დოკუმენტი ერთადერთი **normative spec**-ია RAG-U სერიისთვის (RAG-U01a..h, U02..U15). Phase plans (Senior bar): [PHASE-8-ARCHITECTURE-PLAN.md](PHASE-8-ARCHITECTURE-PLAN.md). იმპლემენტაციის evidence: §20.

**P1 completion (Senior bar):** [PHASE-8-P1-ARCHITECTURE-COMPLETION.md](PHASE-8-P1-ARCHITECTURE-COMPLETION.md)  
**Master plan (P1→P4+, source 100%):** [PHASE-8-ARCHITECTURE-PLAN.md](PHASE-8-ARCHITECTURE-PLAN.md)

---

## 1. ხედვა — რატომ derivation

ცოდნა (თემები, პორტალები, კონკრეტული ბმულები, საკვანძო სიტყვები) **არ იწერება ხელით**. ის **იშვება corpus-იდან** ავტომატური enrichment-ის + aggregation-ის გზით. ადამიანი მხოლოდ მცირე nudge-ებს აკეთებს (boost/demote/exclude/pin).

ცენტრალური პრინციპი: `topics.yaml` (2364 ხაზი) **წაიშლება**. ერთადერთი რაც YAML-ში დარჩება — `topic-style.yaml` (icon, color — presentation only, ~80 ხაზი) და `terminology-overlay.yaml` (აკრონიმი↔სრული ფორმა, 20-40 entry).

### სამი ფენა

```text
┌────────────────────────────────────────────────────────────┐
│ Layer 1 — Corpus (single source of truth)                  │
│   crawler4j + Jsoup → ingestion.document, .chunk           │
│                     → Qdrant body vectors                  │
└─────────────────────────┬──────────────────────────────────┘
                          │ async per-doc enrichment events
                          ▼
┌────────────────────────────────────────────────────────────┐
│ Layer 2 — Enrichment (per-document, derived)               │
│   summary_ka/en, keywords[], entities[],                   │
│   locale_pair, authority_score, page_kind,                 │
│   topic_cluster_id, summary_vector, title_vector           │
└─────────────────────────┬──────────────────────────────────┘
                          │ nightly aggregation jobs
                          ▼
┌────────────────────────────────────────────────────────────┐
│ Layer 3 — Catalog (derived materialized views)             │
│   topic_cluster, mv_portal_link,                           │
│   mv_specific_link, mv_topic_keywords                      │
└─────────────────────────┬──────────────────────────────────┘
                          │ optional override (≤50 rows)
                          ▼
┌────────────────────────────────────────────────────────────┐
│ Layer 4 — Curation overlay (tiny, human nudge)             │
│   curation_override (url_hash, action, reason, expires_at) │
│   action ∈ { boost, demote, exclude, pin_as_portal }       │
└────────────────────────────────────────────────────────────┘
                          │
                          ▼
                    Online query pipeline
                    (Layer 5 below)
```

### Layer 5 — Online query (RAG-U07..U11)

```text
UserMessage
  → SpellFixer → Normalizer → IntentClassifier → EntityExtractor → QueryExpander
  → AnalyzedQuery
  → QueryEmbeddingStrategy (Direct | HyDE | MultiQuery)
  → HybridRetriever (vector + BM25 + KG-future) → RRF fusion
  → CrossEncoderReranker → MMRDiversifier
  → RetrievalConfidence (HIGH/MEDIUM/LOW/NONE)
  → ResponseRouter (answer | answer-with-suggest | clarify | refuse)
```

---

## 2. დიზაინ-პრინციპები

| პრინციპი | რას ნიშნავს |
|---|---|
| **Single source of truth** | corpus = სიმართლე; ყველაფერი დანარჩენი derived |
| **Ports & adapters** | თითო deriver = interface + ცალკე adapter; testable, swappable |
| **Feature flags** | ყველა deriver / retrieval strategy ცალკე ჩაირთვება/გამოირთვება |
| **Idempotent jobs** | one document × one model_version → one enrichment row |
| **Async by default** | per-document enrichment = RabbitMQ event; aggregation = scheduled |
| **Eval gate** | ცვლილება არ მერჯდება სანამ golden hit@5/MRR ≥ baseline |
| **Zero-gap** | ძველი path ცოცხალი რჩება feature-flag-ით; წაიშლება მხოლოდ eval pass-ის შემდეგ |
| **Java-native first** | Python sidecar მხოლოდ თუ Java alternative არ აღწევს ხარისხს |

### Layer 1 prerequisites (assumed inputs to enrichment)

Phase 8 აშენდება **არსებული** ingestion pipeline-ზე. Layer 2 derivers ემოლოდულია, რომ Layer 1-ი უკვე შემდეგ გამოშვებებს ანახორციელებს. **ეს ცხრილი არის contract-ი** — თუ რომელიმე row "✅ done"-ი წარიკითხება და უცებ წყდება, Phase 8-ი გათიშულია.

| Capability | Where | Status | რას უზრუნველყოფს Phase 8-ისთვის |
|---|---|---|---|
| Crawl4j + Jsoup pipeline | `Crawler4jPageFetcher` | ✅ done | `document.canonical_url`, `.title`, `.language`, `.content_text` |
| Content cleaning policy | `HtmlContentCleaner` + `DisplayBoilerplate` | ✅ done (RAG-L11) | clean text, no nav/footer/ads/cookie/accessibility boilerplate |
| `document.content_hash` (SHA-256 of cleaned body) | `CrawlRunStore.fetchAndPersist` | ✅ done | unchanged-body detection |
| Idempotent re-fetch on hash-unchanged | `CrawlRunStore` + `DocumentFreshnessRefreshService` | ✅ done | re-crawl ≠ re-chunk ≠ re-embed when content unchanged — Gemini budget saver |
| Conditional HTTP (ETag / If-Modified-Since) | `ConditionalHttpFetcher` | ✅ done | bandwidth saver during freshness refresh |
| `chunk.text` + `chunk.embedding_model` | `DocumentChunkWriter` | ✅ done | body vector ready in Qdrant |
| `document.section_path` (JSONB array) | `SectionPathExtractor` | ✅ done | breadcrumb context for retrieval |
| `document.display_description` (RAG-L11) | `PageDisplayMetadataExtractor` | ✅ done | citation snippet input |
| URL frontier + link discovery | `LinkDiscoverer` + PG `url_frontier` | ✅ done | self-extending corpus |

> **წესი**: თუ ახალი deriver ახალ Layer 1 capability-ს ითხოვს (მაგ. table extraction), **ჯერ Layer 1-ში დავამატოთ**, მერე deriver. **არ გადააბიჯო** Layer 1-ს.

**არ შედის Phase 8-ში** (ცალკე backlog ID-ები):

- **B-31** — Near-duplicate detector (SimHash/MinHash). Phase 8 derivers მუშაობენ ამის გარეშე; ჩართვისას ერთხელ მოშორდება redundant chunks Qdrant-დან.
- **B-35** — `UrlFrontierPort` abstraction. Phase 8 derivers-ისთვის ცვლილება არ ჭირდება; მომავალში entity-miss → priority enqueue.

---

## 3. Database schema additions

ყველა ცვლილება ახალი Flyway migration-ით. არსებული `ingestion.*` ცხრილები არ ირღვევა — მხოლოდ ახალი სვეტები + ახალი ცხრილები.

### Flyway map (spec ↔ repo)

Spec-ის ძველი „V9–V12“ ლეიბლები გადანაწილდა რეალურ migration-ებში **V9–V16** (Layer 1 უკვე V1–V8):

| Flyway | RAG-U | შინაარსი | ფაილი |
|--------|-------|----------|-------|
| **V9** | U12 | `evaluation_query` metadata columns | `V9__evaluation_query_phase8.sql` |
| **V10** | U01 | `document` enrichment columns | `V10__document_enrichment_columns.sql` |
| **V11** | U01 | `enrichment_run` log | `V11__enrichment_run.sql` |
| **V12** | U01g, U05 | `topic_cluster`, `curation_override`, FKs | `V12__topic_cluster.sql` |
| **V13** | U02 | `mv_portal_link`, `mv_specific_link`, `mv_topic_keywords` | `V13__catalog_materialized_views.sql` |
| **V14** | U12 | golden set seed → 150 queries | `V14__evaluation_query_golden_set.sql` |
| **V15** | U14, U05 | `query_intent_cache`; `mv_portal_link` + `pin_as_portal` | `V15__query_intent_cache_and_portal_pin.sql` |
| **V16** | U02 | `catalog_view_refresh` audit (staleness) | `V16__catalog_view_refresh_audit.sql` |

> **V13 MV-ები** მოითხოვს `topic_cluster.approved=true` join-ს (admin approve gate). **V15** ამატებს pinned portal rows overlay-იდან.

ქვემოთ SQL არის **normative schema** (conceptual); authoritative DDL = migration ფაილები.

### V9 — document enrichment columns

> **Repo:** Flyway **V10** (`V10__document_enrichment_columns.sql`). Spec label „V9“ = conceptual Layer 2 start.

```sql
-- RAG-U01: per-document derived enrichment columns
ALTER TABLE ingestion.document
    ADD COLUMN IF NOT EXISTS summary_ka          TEXT,
    ADD COLUMN IF NOT EXISTS summary_en          TEXT,
    ADD COLUMN IF NOT EXISTS keywords            TEXT[] NOT NULL DEFAULT '{}',
    ADD COLUMN IF NOT EXISTS entities            JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS locale_pair_doc_id  UUID REFERENCES ingestion.document(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS authority_score     DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    ADD COLUMN IF NOT EXISTS page_kind           TEXT NOT NULL DEFAULT 'unknown'
        CHECK (page_kind IN ('portal','dataset','report','news','faq','navigation','unknown')),
    ADD COLUMN IF NOT EXISTS topic_cluster_id    UUID,
    ADD COLUMN IF NOT EXISTS score_boost         DOUBLE PRECISION NOT NULL DEFAULT 1.0
        CHECK (score_boost BETWEEN 0.5 AND 2.0),
    ADD COLUMN IF NOT EXISTS enrichment_version  INT NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_document_keywords_gin ON ingestion.document USING gin (keywords);
CREATE INDEX IF NOT EXISTS idx_document_entities_gin ON ingestion.document USING gin (entities);
CREATE INDEX IF NOT EXISTS idx_document_topic_cluster ON ingestion.document (topic_cluster_id);
CREATE INDEX IF NOT EXISTS idx_document_page_kind ON ingestion.document (page_kind);
CREATE INDEX IF NOT EXISTS idx_document_authority ON ingestion.document (authority_score DESC);
```

**ველების სემანტიკა**:

| ველი | ვინ ავსებს | მოდელი | მაგალითი |
|---|---|---|---|
| `summary_ka`, `summary_en` | SummaryExtractor (U01a) | Gemini batch | "სამომხმარებლო ფასების ინდექსი 2024 წლის I კვარტალი — 2.4% ზრდა..." |
| `keywords[]` | KeyboardExtractor (U01b) | YAKE Java | `{"ფასების ინდექსი","CPI","2024","ინფლაცია"}` |
| `entities[]` | EntityExtractor (U01c) | Gemini few-shot | `[{"type":"INDICATOR","value":"CPI"},{"type":"YEAR","value":2024}]` |
| `locale_pair_doc_id` | LocalePairer (U01d) | URL pattern + cosine | UUID → en/ka counterpart |
| `authority_score` | AuthorityScorer (U01e) | JGraphT PageRank | 0.0 .. 1.0 |
| `page_kind` | PageKindClassifier (U01f) | Gemini few-shot | `portal` / `dataset` / `report` / `news` / `faq` / `navigation` |
| `topic_cluster_id` | TopicMiner (U01g) | Smile k-means + Gemini label | UUID → topic_cluster |
| `score_boost` | feedback loop (U13) | computed | default 1.0; ±5% per thumbs |
| `enrichment_version` | enrichment runner | counter | bump → re-enrich on schema change |

### V10 — topic_cluster + curation_override + enrichment_run

> **Repo:** split — **V11** `enrichment_run`, **V12** `topic_cluster` + `curation_override`.

```sql
-- RAG-U01g: derived topic clusters
CREATE TABLE ingestion.topic_cluster (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    corpus_id         UUID NOT NULL REFERENCES ingestion.corpus(id) ON DELETE CASCADE,
    label_ka          TEXT NOT NULL,
    label_en          TEXT NOT NULL,
    keywords          TEXT[] NOT NULL DEFAULT '{}',
    document_count    INT NOT NULL DEFAULT 0,
    centroid_summary  TEXT,                -- few-sentence cluster centroid summary
    approved          BOOLEAN NOT NULL DEFAULT false,
    approved_by       TEXT,
    approved_at       TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_topic_cluster_corpus_label_ka UNIQUE (corpus_id, label_ka)
);

ALTER TABLE ingestion.document
    ADD CONSTRAINT fk_document_topic_cluster
    FOREIGN KEY (topic_cluster_id) REFERENCES ingestion.topic_cluster(id) ON DELETE SET NULL;

-- Curation overlay (Layer 4) — tiny human nudge, NOT bulk content
CREATE TABLE ingestion.curation_override (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    url_hash    TEXT NOT NULL,
    action      TEXT NOT NULL
        CHECK (action IN ('boost','demote','exclude','pin_as_portal','rename_topic')),
    target      TEXT,                       -- e.g. topic_cluster.id when action=pin_as_portal
    payload     JSONB NOT NULL DEFAULT '{}'::jsonb,
    reason      TEXT NOT NULL,
    created_by  TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ,                -- NULL = permanent; recommend 90 days default
    CONSTRAINT uq_override_url_action UNIQUE (url_hash, action)
);

CREATE INDEX idx_curation_override_url_hash ON ingestion.curation_override (url_hash);
CREATE INDEX idx_curation_override_action_active
    ON ingestion.curation_override (action)
    WHERE expires_at IS NULL OR expires_at > now();

-- Enrichment run log — observability, idempotency, failure replay
CREATE TABLE ingestion.enrichment_run (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id     UUID NOT NULL REFERENCES ingestion.document(id) ON DELETE CASCADE,
    deriver_kind    TEXT NOT NULL
        CHECK (deriver_kind IN ('summary','keywords','entities','locale_pair',
                                'authority','page_kind','topic_assign','title_vector','summary_vector')),
    status          TEXT NOT NULL DEFAULT 'pending'
        CHECK (status IN ('pending','running','completed','failed','skipped')),
    model_version   TEXT NOT NULL,           -- e.g. 'gemini-2.5-flash-lite@2026-05-25'
    started_at      TIMESTAMPTZ,
    finished_at     TIMESTAMPTZ,
    duration_ms     INT,
    cost_units      INT,                     -- token estimate, 0 for pure-Java
    error           TEXT,
    payload         JSONB NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT uq_enrichment_doc_kind_version UNIQUE (document_id, deriver_kind, model_version)
);

CREATE INDEX idx_enrichment_run_doc ON ingestion.enrichment_run (document_id);
CREATE INDEX idx_enrichment_run_status ON ingestion.enrichment_run (status, deriver_kind);
```

### V11 — derived materialized views (Layer 3 catalog)

> **Repo:** Flyway **V13** (`V13__catalog_materialized_views.sql`).

```sql
-- mv_portal_link: top-authority page per (topic_cluster, locale)
CREATE MATERIALIZED VIEW ingestion.mv_portal_link AS
SELECT DISTINCT ON (d.topic_cluster_id, d.language)
    d.topic_cluster_id,
    d.language,
    d.id              AS document_id,
    d.canonical_url,
    d.title,
    COALESCE(d.summary_ka, d.summary_en) AS summary,
    d.authority_score
FROM ingestion.document d
WHERE d.fetch_status = 'parsed'
  AND d.topic_cluster_id IS NOT NULL
  AND d.page_kind = 'portal'
  AND NOT EXISTS (
      SELECT 1 FROM ingestion.curation_override co
      WHERE co.url_hash = d.url_hash AND co.action = 'exclude'
        AND (co.expires_at IS NULL OR co.expires_at > now())
  )
ORDER BY d.topic_cluster_id, d.language, d.authority_score DESC, d.fetched_at DESC;

CREATE UNIQUE INDEX idx_mv_portal_link_unique
    ON ingestion.mv_portal_link (topic_cluster_id, language);

-- mv_specific_link: top-N per (topic_cluster, language, page_kind)
CREATE MATERIALIZED VIEW ingestion.mv_specific_link AS
SELECT
    d.topic_cluster_id,
    d.language,
    d.page_kind,
    d.id              AS document_id,
    d.canonical_url,
    d.title,
    COALESCE(d.summary_ka, d.summary_en) AS summary,
    d.authority_score,
    ROW_NUMBER() OVER (
        PARTITION BY d.topic_cluster_id, d.language, d.page_kind
        ORDER BY d.authority_score DESC, d.fetched_at DESC
    ) AS rank_in_kind
FROM ingestion.document d
WHERE d.fetch_status = 'parsed'
  AND d.topic_cluster_id IS NOT NULL
  AND d.page_kind IN ('dataset','report','news','faq')
  AND NOT EXISTS (
      SELECT 1 FROM ingestion.curation_override co
      WHERE co.url_hash = d.url_hash AND co.action = 'exclude'
        AND (co.expires_at IS NULL OR co.expires_at > now())
  );

CREATE INDEX idx_mv_specific_link_lookup
    ON ingestion.mv_specific_link (topic_cluster_id, language, page_kind, rank_in_kind);

-- mv_topic_keywords: aggregated TF-IDF per cluster (top-30 per cluster)
CREATE MATERIALIZED VIEW ingestion.mv_topic_keywords AS
WITH unnested AS (
    SELECT
        d.topic_cluster_id,
        d.language,
        unnest(d.keywords) AS kw
    FROM ingestion.document d
    WHERE d.topic_cluster_id IS NOT NULL
)
SELECT
    topic_cluster_id,
    language,
    kw          AS keyword,
    COUNT(*)    AS doc_frequency,
    ROW_NUMBER() OVER (
        PARTITION BY topic_cluster_id, language
        ORDER BY COUNT(*) DESC
    ) AS rank
FROM unnested
GROUP BY topic_cluster_id, language, kw;

CREATE INDEX idx_mv_topic_keywords_lookup
    ON ingestion.mv_topic_keywords (topic_cluster_id, language, rank);

-- Refresh schedule: nightly via Spring @Scheduled or geostat-kit task
-- REFRESH MATERIALIZED VIEW CONCURRENTLY ingestion.mv_portal_link;
-- REFRESH MATERIALIZED VIEW CONCURRENTLY ingestion.mv_specific_link;
-- REFRESH MATERIALIZED VIEW CONCURRENTLY ingestion.mv_topic_keywords;
```

### V12 (spec) — query intent cache → **Flyway V15**

`query_intent_cache` DDL იხ. `V15__query_intent_cache_and_portal_pin.sql`. Spec-ის ქვემოთ მოცემული SQL **reference**-ია:

```sql
-- RAG-U14: intent classification cache (24h TTL) — implemented V15
CREATE TABLE ingestion.query_intent_cache (
    query_hash    TEXT PRIMARY KEY,        -- sha256(normalized_query + locale)
    locale        TEXT NOT NULL,
    intent        TEXT NOT NULL
        CHECK (intent IN ('factual','lookup','compare','definition','latest','navigation','smalltalk')),
    entities      JSONB NOT NULL DEFAULT '[]'::jsonb,
    expansions    JSONB NOT NULL DEFAULT '[]'::jsonb,
    cached_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at    TIMESTAMPTZ NOT NULL DEFAULT (now() + interval '24 hours')
);

CREATE INDEX idx_query_intent_cache_expires ON ingestion.query_intent_cache (expires_at);
```

> Retrieval result cache (1h) და response cache (5m) **არ ინახება Postgres-ში** — Redis-ში არსებული `RETRIEVAL_CACHE_BACKEND=redis` infrastructure-ით (RAG-L-cache).

### V13 — catalog materialized views (implemented)

Authoritative: `V13__catalog_materialized_views.sql`. MV definitions ემთხვევა §6 ცხრილს + `approved=true` filter on `topic_cluster`.

### V14 — golden set expansion (implemented)

Authoritative: `V14__evaluation_query_golden_set.sql`. Idempotent INSERT → **150 active** curated queries (`geostat-portal`).

### V15 — intent cache + portal pin (implemented)

Authoritative: `V15__query_intent_cache_and_portal_pin.sql`.

- `query_intent_cache` — U14 schema
- `mv_portal_link` rebuilt: `pinned` CTE (`pin_as_portal` overrides) **UNION** `derived` rank-by-authority

### V16 — catalog refresh audit (implemented)

Authoritative: `V16__catalog_view_refresh_audit.sql`.

```sql
CREATE TABLE ingestion.catalog_view_refresh (
    view_name    TEXT PRIMARY KEY,
    refreshed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

`GET /api/v1/ingestion/catalog/status` — `anyStale` if any view older than threshold (default 25h). Spec §8 degraded mode „mv stale“.

---

## 4. Qdrant collection design (named vectors)

ერთი collection per corpus, **სამი named vector**:

```yaml
collection: geostat-portal
vectors:
  body:
    size: 768         # current Gemini text-embedding-004
    distance: Cosine
  title:
    size: 768
    distance: Cosine
  summary:
    size: 768
    distance: Cosine
```

**Point structure** (one point per chunk):

```json
{
  "id": "<chunk_id>",
  "vectors": {
    "body":    [0.12, ...],    // chunk text embedding (existing)
    "title":   [0.34, ...],    // document title embedding (replicated per chunk)
    "summary": [0.56, ...]     // document summary embedding (replicated per chunk)
  },
  "payload": {
    "documentId":      "<uuid>",
    "corpusId":        "<uuid>",
    "canonicalUrl":    "...",
    "language":        "ka",
    "pageTitle":       "...",
    "pageDescription": "...",
    "sectionPath":     "...",
    "pageKind":        "portal",
    "topicClusterId":  "<uuid>",
    "authorityScore":  0.42,
    "scoreBoost":      1.0,
    "keywords":        ["..."],
    "entities":        [...]
  }
}
```

**მიგრაცია ერთი vector-დან named vectors-ზე** (RAG-U08):

1. `geostat-portal-v2` ახალი collection შექმნა named vectors სქემით.
2. Background job — ყოველი არსებული chunk-ისთვის გაუშვი 3 embedding (body / title / summary).
3. ჩაწერე ახალ collection-ში payload-ის ჩათვლით.
4. retrieval-service feature flag `geostat.qdrant.collection=geostat-portal-v2`.
5. eval gate (ხუთ ცხრილს ქვემოთ) → თუ pass, ძველი წავშალოთ.

**Retrieval cascade** (RAG-U10):

```text
Query → Q.embed (Direct + HyDE + MultiQuery → 1..3 vectors)
  ├─ search via name='summary'   top-K=20  (precision-first)
  ├─ search via name='title'     top-K=20  (semantic title match)
  ├─ search via name='body'      top-K=40  (recall)
  └─ BM25 (Postgres tsvector)    top-K=20
RRF fusion (k=60) → top-50
→ CrossEncoderReranker → top-10
→ MMRDiversifier (lambda=0.7) → top-5
```

### Qdrant payload contract (complete)

ყოველი point-ის payload ცხრილი — ვინ წერს, როდის, ვინ კითხულობს. **ეს ცხრილი არის ერთადერთი წყარო** — ნებისმიერი ახალი payload field ჯერ აქ უნდა დაემატოს.

| Field | Type | Source (PG) | When set | Used by (retrieval) |
|---|---|---|---|---|
| `documentId` | uuid | `document.id` | index | dedup, link to PG |
| `chunkId` | uuid | `chunk.id` | index | rerank traceability |
| `corpusId` | uuid | `document.corpus_id` | index | per-corpus filter |
| `canonicalUrl` | string | `document.canonical_url` | index | citation card url |
| `language` | string `ka`/`en` | `document.language` | index | locale filter (mandatory) |
| `pageTitle` | string | `document.title` | index | citation title, title vector source |
| `pageDescription` | string | `document.display_description` | index (RAG-L11) | snippet (CatalogRagLinkMerger) |
| `sectionPath` | string | `document.section_path` | index | breadcrumb fallback snippet |
| `pageKind` | string (enum) | `document.page_kind` | enrichment (U01f) | retrieval pre-filter (e.g. exclude `navigation`) |
| `topicClusterId` | uuid \| null | `document.topic_cluster_id` | enrichment (U01g) | topic-aware filter |
| `authorityScore` | float `[0..1]` | `document.authority_score` | enrichment (U01e) | rerank weight |
| `scoreBoost` | float `[0.5..2.0]` | `document.score_boost` | feedback loop (U13) | rerank multiplier (default 1.0) |
| `keywords` | array<string> | `document.keywords[]` | enrichment (U01b) | sparse hint, faceted filter |
| `entities` | array<object> | `document.entities` | enrichment (U01c) | entity filter pushdown |
| `localePairDocId` | uuid \| null | `document.locale_pair_doc_id` | enrichment (U01d) | citation card cross-locale switch |
| `enrichmentVersion` | int | `document.enrichment_version` | enrichment runner | stale-payload detection |

**წესი**: payload-ში ყველაფერი **denormalized** ასლია PG-დან. PG = source of truth; Qdrant = read-optimized index. Enrichment update → republish point (idempotent overwrite by `chunkId`).

**არ შედის payload-ში**:

- raw `chunk.text` body (already in vector + index — duplication waste)
- `summary_ka/en` (replicated chunk-ში მძიმეა; მხოლოდ summary vector ინახება Qdrant-ში)
- secrets, personal data — corpus-ი public

---

## 5. Layer 2 — Per-document enrichment (8 derivers)

ყოველი deriver = port + adapter. ყველა port `libs/platform-contracts/src/main/java/com/geostat/platform/enrichment/`-ში. Adapter-ები — `apps/ingestion-service/src/main/java/com/geostat/ingestion/enrichment/<kind>/`.

### Service boundary — no split (D-27)

Phase 8 **არ ქმნის** ახალ deployable-ებს. Derivers, aggregation jobs, curation API — ყველა `ingestion-service`-ში რჩება. Query understanding (RAG-U07) — `chat-api`-ში. Eval harness (RAG-U12) — `ops/ci/run-eval.py` + cron, არა ცალკე JVM.

**Rejected candidates (now)**:

| Candidate | Why not now |
|---|---|
| `enrichment-service` | `ingestion.*` write owner უნდა დარჩეს ერთი; RabbitMQ უკვე async decouples Gemini work crawl-ისგან |
| `query-understanding-service` | hot path +1 hop; 1 consumer; workload მცირეა |
| `eval-service` | scheduled script საკმარისია |
| `aggregation-service` | nightly idempotent job — ცალკე JVM ფასადია |

**Future extraction trigger** (observed only, not planned): **B-37** — `enrichment-service` მხოლოდ თუ **სამივე** ერთდროულად: (1) Gemini rate-limit chat-api budget-ზე enrichment-ის გამო, (2) enrichment p95 > 30 min / 1K docs, (3) crawl throughput −30% enrichment-ის გამო. იხ. § 13.5.

**Planning review** (scheduled, not implementation): **P8-plan-01** — Phase 8 P2 cutover + 30d telemetry-ის შემდეგ owner review: stay 4 services vs plan extraction. იხ. § 13.6.

### Ingestion-service package layout (Phase 8)

Strangler Fig წინსაფარი — ჯერ მოდულარიზაცია, extraction მხოლოდ B-37 trigger-ზე:

```text
apps/ingestion-service/src/main/java/com/geostat/ingestion/
  crawl/ parse/ chunk/ index/     # Layer 1 (existing)
  enrichment/                     # Layer 2 — adapters (ports in platform-contracts)
    summary/ keyword/ entity/ locale/ authority/ pagekind/ topic/ vectors/
    runner/                       DocumentEnrichmentOrchestrator, EnrichmentBackfillService
    config/                       EnrichmentAiConfiguration, EnrichmentProperties
  catalog/                        # Layer 3
    refresh/ readiness/ topic/
  curation/                       # Layer 4
  events/                         DocumentEnrichmentListener (RabbitMQ, when enabled)
  api/                            CorpusController, CatalogController, CurationController, …
```

**წესი**: ყოველი deriver = `com.geostat.platform.enrichment.*` port + 1 ingestion adapter. B-37 trigger-ზე მთელი `enrichment/` package extract — `crawl/` და `catalog/` არ ეხება.

### საერთო trigger model

```text
document.fetch_status = 'parsed' AND content_text != ''
  → optional RabbitMQ 'document.parsed' (INGESTION_EVENTS_ENABLED)
  → DocumentEnrichmentOrchestrator.enrichDocument(id)  — sync path + backfill batch
  → per deriver: summary → locale_pair → keywords → entities → page_kind → vectors
  → authority: batch POST …/authority:recompute (U01e)
  → topics: POST …/topics:remine + admin approve (U01g)
  → catalog: POST …/catalog:refresh (U02)
```

**Production default:** enrichment ON + aggregation ON; async events when RabbitMQ in stack (`max-capability-collaboration.mdc`).

Legacy diagram (event fan-out, when events ON):

```text
  → EnrichmentRouter consumers per deriver kind (idempotent enrichment_run)
  → eventually 'document.enriched' → TopicAssigner
```

თითოეული deriver:

- კითხულობს `document` row-ს;
- წერს ერთ enrichment field + `enrichment_run` row;
- idempotent — `(document_id, deriver_kind, model_version)` unique.

### U01a — SummaryDeriver

**მიზანი**: Gemini-ით 2-3 წინადადებიანი summary ka და en. ეს არის **ყველაზე დიდი ხარისხის ნახტომი** retrieval-ისთვის (Perplexity insight).

**Port** (`libs/platform-contracts`):

```java
package com.geostat.platform.enrichment;

public interface SummaryDeriver {
    SummaryResult derive(DocumentContext doc);
}

public record DocumentContext(UUID documentId, String url, String title,
                              String contentText, String language, String sectionPath) {}

public record SummaryResult(String summaryKa, String summaryEn, String modelVersion) {}
```

**Adapter**: `GeminiSummaryDeriver` (Spring AI `ChatClient`, batch via `BatchPredictionService` future). Cheap model (`gemini-2.5-flash-lite`), max 256 tokens output, temperature 0.2.

**Prompt template** (`apps/ingestion-service/src/main/resources/prompts/enrichment/summary.yaml`):

```yaml
system: |
  შენ ხარ Geostat-ის სტატისტიკური დოკუმენტების summarizer.
  მომხმარებელი მოგცემს გვერდის სათაურს და ტექსტს.
  დააბრუნე JSON: {"summary_ka":"<2-3 sentences ka>","summary_en":"<2-3 sentences en>"}
  წესები:
    - არ დაამატო ცრუ ფაქტი — მხოლოდ ის რაც ტექსტშია
    - არ ჩართო navigation / banner / boilerplate
    - მაქს. 320 char თითო ენაზე
user: |
  Title: {{title}}
  Section: {{sectionPath}}
  Text: {{contentText|truncate:8000}}
```

**Cost**: ~50K tokens per 1K docs (one-time + on freshness refresh) ≈ $0.02 / 1K docs flash-lite.

**Failure**: retry 2x with exponential backoff; persist error in `enrichment_run.error`; no fallback (next nightly retry).

### U01b — KeywordDeriver

**მიზანი**: top-15 keywords per document, ენის-მცოდნე.

**Port**:

```java
public interface KeywordDeriver {
    List<String> deriveKeywords(DocumentContext doc, int topN);
}
```

**Adapter**: `YakeKeywordDeriver` — YAKE Java port (e.g. `com.crawljax.yake:yake-core` or vendored). `topN=15`, ngram 1-3, deduplication.

**Locale**: ცალკე გამოძახება ka და en ტექსტებზე (locale_pair-ს თუ აქვს).

**Cost**: pure CPU; ~50ms/doc.

**Idempotency**: `(document_id, 'keywords', 'yake-v1')`.

### U01c — EntityDeriver

**მიზანი**: დომენის entity-ები — `INDICATOR` (CPI, GDP, …), `YEAR` (1990-2030), `REGION` (Tbilisi, Imereti, …), `ORGANIZATION` (Geostat, NBG, …), `INDEX_CODE` (ECOICOP, …).

**Port**:

```java
public interface EntityDeriver {
    List<Entity> deriveEntities(DocumentContext doc);
}

public record Entity(String type, String value, String normalizedForm, double confidence) {}
```

**Adapter**: `GeminiFewShotEntityDeriver` — Gemini few-shot prompt with 10-15 hand-picked examples per entity type. Returns JSON array, validated against schema.

**Alternative considered**: Stanza Georgian Python sidecar → **rejected** for P1 (Java-native first; Gemini quality sufficient on small entity set).

**Cost**: ~$0.03 / 1K docs flash-lite.

### U01d — LocalePairDeriver

**მიზანი**: ka↔en ერთი და იმავე გვერდის pair-ი. შემავსებს არსებულ `ingestion.document_locale_pair` ცხრილს.

**Algorithm**:

1. URL pattern თუ ემთხვევა (`/ka/...` ↔ `/en/...`) — ცდით.
2. სხვა შემთხვევაში — title embedding cosine similarity > 0.92 ზე.
3. დაწერე `document.locale_pair_doc_id` ორმხრივად + `document_locale_pair` row.

**Port**:

```java
public interface LocalePairDeriver {
    Optional<UUID> findPair(DocumentContext doc);
}
```

**Adapter**: `UrlPlusEmbeddingLocalePairer`.

**Cost**: 1 vector compare per doc ≈ <10ms.

### U01e — AuthorityDeriver

**მიზანი**: page-rank-style authority score [0..1], in-link გრაფზე + recency bonus.

**Algorithm** (run periodically, not per-doc):

1. Build `JGraphT` directed graph: nodes = documents, edges = href-ები რომლებიც corpus-შია.
2. PageRank damping = 0.85, iterations = 30.
3. Normalize [0..1] per corpus.
4. Recency bonus: `final = 0.7 * pagerank + 0.3 * freshness_decay(fetched_at)`.
5. Update `document.authority_score`.

**Port**:

```java
public interface AuthorityDeriver {
    void recomputeForCorpus(UUID corpusId);
}
```

**Adapter**: `JGraphTPageRankAuthorityDeriver`.

**Trigger**: nightly schedule, NOT per-document event.

**Cost**: O(N + E) memory + ~30 iterations; manageable up to ~100K nodes.

### U01f — PageKindClassifier

**მიზანი**: closed-set classifier `portal/dataset/report/news/faq/navigation/unknown`.

**Port**:

```java
public interface PageKindClassifier {
    PageKindResult classify(DocumentContext doc);
}

public record PageKindResult(String kind, double confidence, String modelVersion) {}
```

**Adapter**: `GeminiFewShotPageKindClassifier` — closed-set few-shot. 5-7 examples per kind in prompt.

**Heuristic pre-filter** (Java): URL pattern (`/news/` → news; trailing `.xls/.csv` reference → dataset; etc.) — saves Gemini calls for clear cases.

**Cost**: ~$0.01 / 1K docs flash-lite (after heuristic filter).

### U01g — TopicMiner + TopicAssigner

**მიზანი**: corpus-მასშტაბის topic-ების discovery. ეს არის **batch job** (არა per-doc).

**Algorithm**:

1. ყოველი document-ის `summary_vector` ავიღოთ Qdrant-დან.
2. `Smile k-means` — k = sqrt(N/10) ერევრისტიკით (e.g. 1000 docs → ~10 clusters); ან HDBSCAN-ის Java equivalent.
3. ყოველ cluster-ის centroid-ისთვის — closest 5 docs → Gemini "გენერირე label_ka და label_en".
4. INSERT/UPDATE `topic_cluster` rows; `approved=false` მანამ admin დაადასტურებს.
5. Per-document: `document.topic_cluster_id = nearest centroid`.

**Port**:

```java
public interface TopicMiner {
    List<TopicCluster> mineForCorpus(UUID corpusId);
}

public interface TopicAssigner {
    UUID assignNearest(UUID documentId);
}
```

**Adapters**: `SmileKMeansTopicMiner`, `EmbeddingNearestTopicAssigner`.

**Trigger**: nightly + manual `POST /api/v1/ingestion/corpora/{id}/topics:remine`.

**Approval flow**: admin reviews `topic_cluster` rows where `approved=false`, edits label, approves → published to `mv_portal_link` etc.

### U01h — TitleVectorDeriver + SummaryVectorDeriver

**მიზანი**: Qdrant-ში `title` და `summary` named vectors.

**Port**:

```java
public interface DocumentVectorWriter {
    void writeNamedVector(UUID documentId, String vectorName, float[] embedding);
}
```

**Adapter**: `QdrantNamedVectorWriter` (uses `libs/qdrant-client`).

**Trigger**: 
- TitleVector — on document `parsed`;
- SummaryVector — on `enrichment_run.deriver_kind='summary' status='completed'`.

**Cost**: 2 embeddings per doc ≈ $0.0001 (Gemini text-embedding-004 per 1K docs).

---

## 6. Layer 3 — Aggregation jobs (catalog views)

ყველა view automatically refresh-დება nightly + manual trigger.

| View | რას აგროვებს | Refresh trigger |
|---|---|---|
| `mv_portal_link` | top-authority page per (cluster, locale) where `page_kind='portal'` | nightly 03:00 + after AuthorityDeriver |
| `mv_specific_link` | top-N per (cluster, locale, page_kind) for dataset/report/news/faq | nightly 03:00 |
| `mv_topic_keywords` | TF-IDF aggregated keywords per (cluster, locale) | nightly 03:00 |

**Refresh job** (`apps/ingestion-service`):

```java
@Component
@ConditionalOnProperty("geostat.ingestion.aggregation.enabled")
public class CatalogViewRefreshJob {
    @Scheduled(cron = "0 0 3 * * *")
    public void refreshAll() {
        jdbc.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY ingestion.mv_portal_link");
        jdbc.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY ingestion.mv_specific_link");
        jdbc.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY ingestion.mv_topic_keywords");
    }
}
```

**Manual API**: `POST /api/v1/ingestion/catalog:refresh` (idempotent).

### Per-corpus refresh policy

Phase 8 spec assumes single corpus (`geostat-portal`). მომავალში მრავალი corpus-ი იქნება (იხ. § 13.5 scale thresholds). რეფრეშის რიტმი **per corpus** დეცკლარირდება არა გლობალურად:

| Field | Where | Default | მაგალითი |
|---|---|---|---|
| `corpus.refresh_cron` | `ingestion.corpus` table column (V11+ extension) | `0 0 3 * * *` (nightly 03:00) | high-traffic news → `0 0 */6 * * *` |
| `corpus.realtime_enabled` | same | `false` | true-სი მხოლოდ თუ news/bulletin source |
| `corpus.full_recrawl_cron` | same | weekly Sunday 04:00 | მსხვილი sites → monthly |

**Realtime path** (when `realtime_enabled = true`):

```text
RSS / sitemap-changed webhook
   → enqueue with priority=HIGH on url_frontier
   → fetch + chunk + enrich (single-doc pipeline)
   → emit doc.indexed event
   → catalog mv refresh on demand (only affected cluster row)
```

**Ops command** (geostat-kit, planned):

```text
geostat ingestion refresh --corpus <slug> [--full] [--since=<iso8601>]
```

**არ ვაკეთებთ Phase 8-ში**: per-page TTL, websocket-based realtime — Phase 8 spec-ის გარეთ (B-34 backlog).

---

## 7. Layer 4 — Curation overlay

**წესები**:

| წესი | რას ნიშნავს |
|---|---|
| **Tiny** | ≤50 row max მთლიანი corpus-ისთვის |
| **Override only** | მხოლოდ derived data-ს ცვლის; **არ ქმნის** content-ს |
| **TTL default 90d** | `expires_at` defaults `now() + 90 days` (auto-expire stale overrides) |
| **Reason mandatory** | ყოველი override-ს უნდა ჰქონდეს `reason` ტექსტი (audit trail) |
| **Owner-approved actions only** | actions: `boost / demote / exclude / pin_as_portal / rename_topic` |

**API** (`apps/ingestion-service`):

```text
GET    /api/v1/ingestion/curation/overrides
POST   /api/v1/ingestion/curation/overrides   { url, action, target?, reason, expiresAt? }
DELETE /api/v1/ingestion/curation/overrides/{id}
```

**Effect on retrieval**:

- `exclude` → document filtered out entirely (Qdrant payload filter + materialized view WHERE).
- `boost` → multiply `score_boost` by `payload.factor` (default 1.2).
- `demote` → multiply `score_boost` by `payload.factor` (default 0.8).
- `pin_as_portal` → force `mv_portal_link` to use this URL for `target` (topic_cluster).
- `rename_topic` → override `topic_cluster.label_*` for given topic_cluster id.

**Expected steady-state**: corpus 5-10K docs → ≤10 overrides ever; if more, signals deriver problem to fix upstream.

---

## 8. Layer 5 — Online query pipeline

ყველა stage = ცალკე port + adapter; ყველა feature-flag-ით.

### Stages (sequential)

```text
1. SpellFixer            (RAG-U07a) — SymSpell ka/en dictionaries
2. Normalizer            (RAG-U07b) — NFKC, lowercase, expand abbreviations
3. IntentClassifier      (RAG-U07c) — Gemini cheap call → factual / lookup / compare / definition / latest / navigation / smalltalk
4. EntityExtractor       (RAG-U07d) — same as enrichment U01c (reused port)
5. QueryExpander         (RAG-U07e) — synonym overlay (terminology-overlay.yaml) + LLM expand
   → AnalyzedQuery       (record passed to retrieval)

6. QueryEmbeddingStrategy (RAG-U09)
     - DirectEmbed       — embed query as-is
     - HyDEEmbed         — Gemini hypothetical doc → embed
     - MultiQueryEmbed   — Gemini 3 paraphrases → embed each
     → 1..5 vectors

7. HybridRetriever       (RAG-U10)
     - QdrantSummaryAdapter (named='summary')
     - QdrantTitleAdapter   (named='title')
     - QdrantBodyAdapter    (named='body')
     - PgBM25Adapter        (tsvector)
     → RRF fusion (k=60) → top-50

8. CrossEncoderReranker  (existing RAG-L07+) → top-10
9. MMRDiversifier        (RAG-U10b, lambda=0.7) → top-5

10. RetrievalConfidence  (RAG-U11)
     score(top1)         > 0.75 AND gap(top1, top2) > 0.05  → HIGH
     score(top1)         > 0.55                              → MEDIUM
     score(top1)         > 0.35                              → LOW
     else                                                    → NONE

11. ResponseRouter       (RAG-U11b)
     HIGH    → answer with citations
     MEDIUM  → answer + suggest "see also" cards
     LOW     → ClarificationService (existing)
     NONE    → "ვერ ვიპოვე ჩვენს კატალოგში — სცადე..." + topic chips from mv_topic_keywords
```

### AnalyzedQuery contract

```java
public record AnalyzedQuery(
    String original,
    String normalized,
    String corrected,        // after spell-fix
    String locale,
    String intent,
    List<Entity> entities,
    List<String> expansions, // synonyms + paraphrases
    Map<String, Object> filters // year=2024, region=Tbilisi, etc.
) {}
```

### Filter pushdown

Entity-ები filter-ად გადადის Qdrant-ში:

```java
// example: query "CPI 2024 Tbilisi"
qdrant.search(collection, vectorName="summary", queryVector,
    filter = match("language", locale)
        .and(matchAny("entities[].value", entities.values()))
        .and(should(notExclude(curationOverrides))));
```

### Pipeline fallback chain (degraded modes)

ყოველი stage-ი **ცალკე feature flag-ით** ირთვება/იხსნება. რეცეპტი — **არასოდეს დახშო ბოლო ცოცხალი path**, ყოველთვის ჩაიგდე degraded mode.

| Failure | Trigger | Fallback | User-visible | Telemetry |
|---|---|---|---|---|
| Gemini timeout / 5xx (intent classifier) | 1× retry → still fail | rule-based intent (regex on entities + heuristics from § 8) | `intent=heuristic` flag in trace | `chat.intent_fallback{reason}` |
| Cross-encoder service down | health check fail | bi-encoder-only ranking (Qdrant raw cosine) | none | `chat.rerank_fallback` |
| Qdrant timeout > 1s | per-query budget | BM25-only search (Postgres tsvector) | "ვერ მოიძებნა highly-relevant; sparse match" suffix | `chat.qdrant_fallback` |
| Catalog mv stale (`last_refreshed > 25h`) | scheduled check | serve from mv anyway + warn admin via `geostat ingestion catalog status` | none | `ingestion.mv_stale{view,age_h}` |
| Confidence = NONE (§ 8 last branch) | top score < threshold | suggested topics (mv_topic_keywords) + clarifying chips | "ვერ ვიპოვე ჩვენს კატალოგში — სცადე..." | `chat.confidence_none` |
| All retrievers fail | all 3 vectors + BM25 fail | safe refusal copy (no hallucination) | "სამწუხაროდ, ამ მომენტში ვერ ვპასუხობ. სცადეთ ცოტა ხანში." | `chat.fallback_total_failure` |

**წესი**: მონიტორინგი მუდმივად საზომავს fallback rate-ს. **`fallback_rate > 5% per day`** → on-call ალერტი (Risk Register-ში RR-FB-01).

---

## 9. Eval harness — golden set + nightly regression (RAG-U12)

### Golden set storage

არსებული `ingestion.evaluation_query` ცხრილი გაფართოვდება:

```sql
ALTER TABLE ingestion.evaluation_query
    ADD COLUMN IF NOT EXISTS expected_intent   TEXT,
    ADD COLUMN IF NOT EXISTS expected_entities JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS expected_topic    UUID REFERENCES ingestion.topic_cluster(id),
    ADD COLUMN IF NOT EXISTS difficulty        TEXT
        CHECK (difficulty IN ('easy','medium','hard')) DEFAULT 'medium',
    ADD COLUMN IF NOT EXISTS source            TEXT;     -- 'curated' | 'user_log' | 'feedback'
```

**Target size**: 150–300 questions, balanced:

| Bucket | Min |
|---|---|
| Locale: ka | 100 |
| Locale: en | 50 |
| Intent: factual | 80 |
| Intent: lookup | 60 |
| Intent: compare | 30 |
| Intent: definition | 30 |
| Intent: latest | 20 |
| Intent: navigation | 20 |
| Difficulty: easy | 60 |
| Difficulty: medium | 120 |
| Difficulty: hard | 60 |

### Eval runner

`ops/ci/run-eval.py` (already-existing pattern from `rag-locale-pipeline.ps1`):

**Metrics computed**:

| Metric | Target |
|---|---|
| `hit@1` | ≥ 60% |
| `hit@5` | ≥ 85% |
| `MRR` | ≥ 0.65 |
| `NDCG@10` | ≥ 0.70 |
| `intent_accuracy` | ≥ 90% |
| `entity_F1` | ≥ 0.80 |
| `mean_response_ms` | ≤ 3000 |
| `confidence_calibration` | HIGH-tier hit@1 ≥ 90%; NONE-tier hit@5 ≤ 20% |

**Output**: `ops/eval/reports/<YYYY-MM-DD>.json` + diff vs baseline.

**P1 baselines (implemented)**:

| File | When |
|------|------|
| `ops/eval/baseline.yaml-frozen.json` | G0 — YAML-era frozen (Step 0 cutover) ✅ |
| `ops/eval/baseline.derived.json` | G2 — after derived catalog eval pass (written by gate) |
| `ops/eval/p1-prep-background.log` | prep script tee (background cutover) |

Orchestrator: `ops/ci/rag-p1-cutover.ps1` · Gate: `ops/ci/rag-eval-gate.ps1`

### CI gate

Nightly cron + on PR:

```yaml
# .github/workflows/eval.yml (P1+)
- name: RAG eval
  run: ops/ci/run-eval.py --baseline ops/eval/baseline.json --max-regression 0.05
  # exits non-zero if any metric drops > 5% vs baseline
```

**Owner playbook** when regression:

1. Compare per-bucket metrics — which intent/difficulty regressed?
2. Sample 5 failed queries per bucket → manual review.
3. Decide: rollback feature flag OR adjust prompt OR mark expected (update baseline).

---

## 10. Feedback-driven score boost (RAG-U13)

**Source**: existing `ChatFeedbackController` + `chat.*` telemetry tables (B-30).

**Job**: nightly aggregator

```java
@Scheduled(cron = "0 30 3 * * *")
public void recomputeScoreBoosts() {
    // SELECT document_id,
    //        AVG(CASE WHEN rating='up' THEN 1 WHEN rating='down' THEN -1 ELSE 0 END) AS net,
    //        COUNT(*) AS n
    // FROM chat.feedback_citation
    // WHERE created_at > now() - interval '30 days'
    // GROUP BY document_id HAVING COUNT(*) >= 3
    // → score_boost = clamp(1.0 + 0.05 * net * log(n), 0.5, 2.0)
}
```

**Effect**: applied at rerank stage `final_score = ce_score * document.score_boost`.

**Stop conditions**:

- ≤3 feedback events → no change (statistical noise).
- Per-month update; not real-time.

---

## 11. Caching tier (RAG-U14)

| Cache | Backend | TTL | Key | Purpose |
|---|---|---|---|---|
| Embedding | Redis | infinite | `sha256(text)` | already done; reused |
| Intent classification | Postgres `query_intent_cache` | 24h | `sha256(normalized + locale)` | cuts Gemini cost |
| Retrieval result | Redis | 1h | `sha256(analyzed_query)` | already done backend; extend to multi-vector |
| Final response | Redis | 5min | `sha256(query + history_hash)` | optional, off by default |

`spring.cache` abstraction — already wired via `RETRIEVAL_CACHE_BACKEND`.

---

## 12. Free packages — adopted vs rejected

### Adopted (P1+P2)

| Package | Use | Source |
|---|---|---|
| **JGraphT** `org.jgrapht:jgrapht-core` | PageRank-style authority graph | Apache 2.0 |
| **Smile** `com.github.haifengl:smile-core` | k-means topic clustering | Apache 2.0 |
| **YAKE Java port** | per-doc keyword extraction | open source |
| **SymSpell Java** | typo correction (ka/en dictionaries) | MIT |
| **Postgres tsvector + GIN** | BM25 full-text retrieval | already there |
| **Qdrant named vectors** | multi-vector index | already there |
| **Spring Cache + Redis** | caching tier | already there |
| **Spring AI ChatClient** | Gemini calls (summary, NER, classification) | already there |

**Total new deps**: 4 jars (~5MB).

### Rejected for P1 / P2 (BACKLOG)

| Package | რატომ უარი | Future trigger |
|---|---|---|
| **Stanza Georgian** (Python) | Java NER quality sufficient via Gemini few-shot | If entity_F1 < 0.75 in eval |
| **BERTopic** (Python) | Smile k-means + LLM label sufficient for ≤10K docs | If topic_quality_score < threshold (TBD) |
| **KeyBERT** (Python) | YAKE Java port lighter and Java-native | If keyword precision benchmark < YAKE |
| **LangChain4j** | Q-02 closed (Spring AI primary) | — |
| **Apache AGE** (Postgres KG) | overkill for current corpus size | RAG-U15 P4+ when corpus > 50K docs |
| **Neo4j** | adds infra service; AGE preferred when needed | — |

---

## 13. Phase rollout — RAG-U series (PROJECT-PLAN.md)

### Naming convention

- `RAG-U01a..h` — Layer 2 derivers (per-doc enrichment)
- `RAG-U02` — Layer 3 catalog views + curation overlay schema
- `RAG-U03` — DEPRECATED (was SourceComposer; merged into U10)
- `RAG-U04` — DEPRECATED (was hybrid topic classifier; replaced by U07c IntentClassifier)
- `RAG-U05` — Layer 4 curation overlay UI (minimal)
- `RAG-U06` — DEPRECATED (was public catalog API; not needed — derived views suffice)
- `RAG-U07` — Query understanding pipeline (a..e)
- `RAG-U08` — Multi-vector index (Qdrant named vectors) + summary/title vectors
- `RAG-U09` — HyDE + multi-query expansion
- `RAG-U10` — Hybrid retrieval + RRF fusion + MMR
- `RAG-U11` — Confidence + smart fallback
- `RAG-U12` — Eval harness + CI gate
- `RAG-U13` — Feedback-driven score boost
- `RAG-U14` — Caching tier
- `RAG-U15` — Knowledge graph (deferred to P4+)

### Sequence (must)

```text
P1 — Foundation
  RAG-U01a   SummaryDeriver        (largest single quality win)
  RAG-U01b   KeywordDeriver
  RAG-U01c   EntityDeriver
  RAG-U01d   LocalePairDeriver
  RAG-U01e   AuthorityDeriver
  RAG-U01f   PageKindClassifier
  RAG-U01g   TopicMiner + TopicAssigner
  RAG-U01h   Title/SummaryVectorDeriver
  RAG-U02    Catalog views (mv_portal_link, mv_specific_link, mv_topic_keywords)
  RAG-U07    Query understanding pipeline (a..e)
  RAG-U12    Eval harness + golden set 150–300

P2 — Retrieval quality
  RAG-U08    Multi-vector index (Qdrant migration v1 → v2)
  RAG-U09    HyDE + multi-query
  RAG-U10    Hybrid retrieval + RRF + MMR
  RAG-U11    Confidence + smart fallback

P3 — Operations & polish
  RAG-U13    Feedback-driven score boost
  RAG-U14    Caching tier (intent + multi-vector retrieval)
  RAG-U05    Curation overlay UI (admin tab)

P4 — Advanced (defer)
  RAG-U15    Knowledge graph (Apache AGE) — only if eval shows entity-aware gap
```

### Migration order (data-side)

```text
1. V9 migration (document enrichment columns)
2. Backfill existing docs: enrichment_run rows pending
3. Run derivers per-doc (async via existing RabbitMQ)
4. V10 migration (topic_cluster, curation_override, enrichment_run)
5. Run TopicMiner once corpus has ≥1K enriched docs
6. V11 migration (mv_portal_link, mv_specific_link, mv_topic_keywords)
7. First REFRESH MATERIALIZED VIEW
8. Switch chat-api citation source: catalog YAML → mv_portal_link / mv_specific_link
   (feature flag: geostat.chat.catalog.source = yaml | derived)
9. Eval gate — if hit@5 ≥ baseline, deprecate topics.yaml
10. Delete topics.yaml; keep topic-style.yaml + terminology-overlay.yaml
```

### MVP cutover set — minimum derivers to switch off YAML

გარდა derivers-ისა, ვერ გავთიშავთ YAML-ს. შემდეგი 4 deriver არის **ყველაზე მცირე საკმარისი ნაკრები** იმისათვის, რომ `mv_portal_link` + `mv_specific_link` შეცვალოს `topics.yaml`. დანარჩენი 4 deriver-ი (P2) ხარისხს აუმჯობესებს, მაგრამ cutover-ის ბლოკერი არ არის.

| # | Deriver | რატომ MVP | რა მოხდება ამის გარეშე |
|---|---|---|---|
| 1 | **U01a SummaryDeriver** | summary vector + snippet წყარო | retrieval ხარისხი = baseline; snippets = lead-text fallback |
| 2 | **U01e AuthorityDeriver** | `mv_portal_link.top_authority` rank-ისთვის | portal links = arbitrary order |
| 3 | **U01f PageKindClassifier** | `WHERE page_kind='portal'` ფილტრი | mv-ში დატა + nav + dataset რევარდად |
| 4 | **U01g TopicMiner + Assigner** | `(cluster, locale)` group-ისთვის | mv-ს არ აქვს row-ები (no clusters = no aggregates) |

**Cutover gate**: ამ ოთხის გაშვება + eval `hit@5 ≥ baseline_yaml` → `geostat.chat.catalog.source = derived` → topics.yaml-ის წაშლა.

**Deferred to P2** (quality additions, not cutover blockers): U01b Keywords, U01c Entities, U01d LocalePair, U01h Title/Summary vectors.

### Scale thresholds (when current architecture stops being enough)

ეს Phase 8 spec არის **5K–50K docs / 1 corpus** target-ისთვის. **მისი ცვლილება არ არის Phase 8 part-ი**, მაგრამ უნდა გვქონდეს გასაღებები (signals) რომ ვიცოდეთ როდის გავაფართოოთ.

| Threshold | Signal (telemetry metric) | Triggered work | Plan ID |
|---|---|---|---|
| Enrichment JVM pressure | **all three simultaneously**: (1) Gemini rate-limit on shared key affects chat-api; (2) `enrichment_run.duration_ms` p95 > 30 min per 1K-doc batch; (3) crawl pages/hour −30% vs baseline while enrichment queue depth > 0 | extract `enrichment/` package → `enrichment-service` deployable; keep `ingestion.*` write via single owner contract | **B-37** (observed trigger only) |
| Single corpus growth | `ingestion.documents_total > 100K` | Kafka instead of RabbitMQ for index events; Qdrant sharding | **B-32** (P4+) |
| Multi-corpus (≥2 sites) | second corpus added to manifest | per-corpus refresh policies (§ 6.5), corpus admin UI | **B-33** (P4) |
| Cross-corpus knowledge | repeated entity-aware questions failing | knowledge graph layer (Apache AGE) | **RAG-U15** (P4+) |
| Embedding model upgrade | new model superior on eval set | dual-write embeddings, A/B compare, atomic flip | **B-36** (P3+) |

> **Default**: არ ვცვლი არქიტექტურას, თუ არც ერთი threshold არ არის გადაჭარბებული — ეს განზრახ "boring" საფეხურია.

### Planned service-split review (P8-plan-01)

Phase 8 **implementation-ის დროს არ ვჭრით** ახალ deployable-ებს (D-27). მაგრამ **სწორ დროს დავგეგმავთ** — owner + agent review, რომ решить: რჩება 4 service თუ plan-ში ჩანს extraction.

**როდის schedule-დება review** (რომელიმე პირობა საკმარისია):

| Gate | როდის | რატომ |
|---|---|---|
| **Primary** | Phase 8 P2 cutover done (YAML deleted, `geostat.chat.catalog.source=derived`) **+ 30 დღე** prod telemetry | პირველი რეალური enrichment load + derived catalog steady-state |
| **Early** | `documents_total > 80K` OR enrichment queue depth p95 > 100 (7 consecutive days) OR Gemini rate-limit incident on shared key (≥2×/week) | threshold-ებამდე არ ველოდებით |

**Review output** (planning only — არა code):

1. Telemetry snapshot: `enrichment_run.duration_ms`, crawl pages/hour, chat-api latency p95, Gemini quota usage
2. Candidate split map — რომელი `ingestion-service` package-ები extraction candidate-ია (`enrichment/`, `catalog/`, …)
3. Decision: **stay 4 services** OR **plan extraction** → BACKLOG row update + ADR draft (ADR-012 only if extraction approved)
4. თუ extraction approved → B-37 implementation triggers რჩება observed gate; review ≠ automatic split

**არ შედის review-ში**: hardware sizing, Gemini cost tables, kit extraction to `kits/` (separate gate per `plan-automation-gate.mdc`).

---

## 14. Quality gates

| Gate | Criterion | Action if fail |
|---|---|---|
| **Per-deriver smoke** | 100 sampled docs derive successfully without error | block deriver enable in prod |
| **Enrichment coverage** | ≥95% docs have summary, keywords, page_kind populated | continue backfill nightly |
| **Topic clustering** | ≥80% docs assigned to a non-`unknown` cluster | tune k-means k or HDBSCAN params |
| **Eval baseline** | hit@5 with derivation ≥ hit@5 with YAML catalog | rollback feature flag |
| **Eval regression** | each PR ≤5% drop on any metric | block merge |
| **Confidence calibration** | HIGH-tier hit@1 ≥90% on golden set | tune thresholds |
| **Curation budget** | overrides count ≤50 | if more, fix derivers upstream |
| **Cost ceiling** | enrichment cost ≤$5/month per 10K docs (Gemini flash-lite) | switch to Java-only derivers |

---

## 15. Risk register

| Risk | Probability | Impact | Mitigation |
|---|---|---|---|
| Topic clustering yields noisy clusters | medium | medium | manual approval gate; tune k; LLM label review |
| Gemini summarizer hallucinates | low | high | grounding enforcer (existing); strict prompt; eval set |
| Authority graph dominated by nav links | medium | medium | exclude `page_kind=navigation` from PageRank |
| Cold start: empty corpus → empty catalog | high | high | small bootstrap seed (5-10 known portal URLs in `curation_override` action `pin_as_portal`) |
| Migration breaks existing chat | high | high | feature flag both paths until eval pass |
| Cost spikes from re-enrichment | low | medium | `enrichment_run` idempotent; only on `model_version` bump |
| Curation overlay creep | medium | low | TTL 90d; budget alarm at 50 rows |
| YAKE Georgian quality unknown | medium | medium | fallback to ts_stat; eval keyword_precision metric |
| Stale materialized views | low | low | nightly + manual refresh API |

---

## 16. Telemetry (must instrument)

| Metric | Source | Dashboard |
|---|---|---|
| `enrichment_run.duration_ms` per kind | DB | per-deriver p50/p95/p99 |
| `enrichment_run.error_rate` per kind | DB | alarm when > 5% |
| `query_intent_cache.hit_ratio` | computed | should converge ≥80% steady state |
| `retrieval.confidence_distribution` | telemetry | watch NONE/LOW share — too high → eval problem |
| `feedback.thumbs_per_topic_cluster` | chat.* | data for U13 score boost |
| `mv_portal_link.staleness_hours` | DB query | should be ≤24h |
| `topic_cluster.unapproved_count` | DB query | admin queue size |
| `curation_override.active_count` | DB query | budget gauge |

---

## 17. What gets removed (zero-gap)

Migration succeeds → delete:

- `apps/backend/src/main/resources/catalog/topics.yaml` (2364 lines)
- `apps/backend/src/main/java/com/geostat/chat/infrastructure/catalog/YamlTopicCatalog.java`
- `apps/backend/src/main/java/com/geostat/chat/infrastructure/catalog/TopicCatalogLoader.java`
- `apps/backend/src/main/java/com/geostat/chat/infrastructure/catalog/SpecificLinkLoader.java`
- `apps/backend/src/main/java/com/geostat/chat/infrastructure/catalog/NewsCategoryLoader.java`
- `CatalogRagLinkMerger` simplified to read `mv_portal_link` + `mv_specific_link` instead of YAML topics

What stays (presentation only):

- `apps/backend/src/main/resources/catalog/topic-style.yaml` (icons, colors per page_kind — ≤80 lines)
- `apps/backend/src/main/resources/catalog/terminology-overlay.yaml` (synonym graph, ≤40 entries)

### Document lifecycle state machine

ერთი document-ის ცხოვრების ყველა state-ი — ეს ცხრილი არის **single source of truth** და eval debugging-ისთვის ერთიანი მენტალური მოდელი.

```text
discovered ─▶ fetching ─▶ fetched ─▶ chunked ─▶ embedded ─▶ enriching ─▶ enriched ─▶ indexed ─▶ live
   │             │           │           │           │             │
   ▼             ▼           ▼           ▼           ▼             ▼
 dropped     fetch_fail   parse_fail  empty_chunks  embed_fail  enrich_fail
                                                                     │
                                                                     ▼
                                                              partial_enriched
                                                                     │
                                                                     ▼
                                                                  retried
```

| State | Where stored | Trigger to next |
|---|---|---|
| `discovered` | `url_frontier.status='queued'` | crawler picks up |
| `fetching` | `url_frontier.status='in_progress'` | HTTP 2xx |
| `fetched` | `document` row created, `content_hash` set | Layer 1 cleaner runs |
| `chunked` | `chunk` rows present | embedder consumes |
| `embedded` | Qdrant point exists with `body` vector | enrichment dispatcher emits N events |
| `enriching` | ≥1 `enrichment_run` row `status=running` | all derivers either `completed` or `failed` |
| `enriched` | all 8 derivers `completed` (or marked optional-fail) | `enrichment_version=N`; index update |
| `indexed` | Qdrant payload reflects enrichment fields | published to retrieval |
| `live` | catalog mv-ში appears; chat-api ციტირდება | — |
| `partial_enriched` | ≥1 deriver `failed` after retries, but mandatory subset (summary, page_kind, topic) succeeded | served with degraded payload + `enrichment_warning` flag |
| `retried` | failed derivers re-queued with backoff | back to `enriching` |
| `dropped` | curation override `action=exclude` OR robots disallow OR fetch fail × max_retries | not served |

**წესი**: chat-api **მხოლოდ `live` ან `partial_enriched`** state-ის documents-ს ასრულებს ციტირებას. ამის გარდა — invisible to retrieval.

---

## 18. Acceptance checklist (agent runbook)

### Phase A — P1 code (✅ done 2026-05-25)

1. ☑ Flyway V9–V16 applied (`ingestion` schema)
2. ☑ Ports in `libs/platform-contracts` + ingestion adapters U01a–h
3. ☑ `DocumentEnrichmentOrchestrator` + `EnrichmentBackfillService` + readiness API
4. ☑ Catalog MVs + refresh + `catalog/status` (V16 audit)
5. ☑ Curation REST + budget (D-26)
6. ☑ Chat-api dual catalog (`yaml` \| `derived`) + `DerivedCatalogReader`
7. ☑ U07 pipeline code (flags OFF until eval)
8. ☑ Eval harness: 150 golden queries + `run-eval.py` + cutover scripts
9. ☑ Unit tests per deriver + `EnrichmentBackfillServiceTest`
10. ☑ Manifest `modules.ingestion.derivation` + plan docs ([PHASE-8-ARCHITECTURE-PLAN.md](PHASE-8-ARCHITECTURE-PLAN.md))

### Phase B — P1 runtime cutover (✅ COMPLETE 2026-05-26)

1. ☑ G0 frozen baseline captured (`baseline.yaml-frozen.json`) — **done 2026-05-25**
2. ☑ Enrichment backfill ≥95% summary + page_kind — **summary=100%, page_kind=99.2%**
3. ☑ `topics:remine` → approve clusters → `catalog:refresh` — **5 approved clusters, 10 portal links**
4. ☑ `lifecycle:sync-qdrant` — **237 docs synced**
5. ☑ `GEOSTAT_CHAT_CATALOG_SOURCE=derived` — **chat-api live on derived**
6. ☑ G2 eval gate pass — **hit@5=100%, MRR=1.0, intent=97.7%**
7. ☑ S7 YAML delete — **topics.yaml + 4 loaders deleted**

### Phase C — P2+ (unlocked after G2)

1. ☐ U08 Qdrant v2 named vectors — [P2 plan](PHASE-8-P2-ARCHITECTURE-PLAN.md)
2. ☐ U09–U11 query/retrieval flags ON per staged eval
3. ☐ P3 U13/U14/UI — [P3-P4 plan](PHASE-8-P3-P4-ARCHITECTURE-PLAN.md)

> Failure: stop, log `CHANGELOG-PLAN.md`, ask owner. **Do not skip eval gates.**

---

## 19. Implementation status (P1 code map)

| RAG-U | Component | Location | Status |
|-------|-----------|----------|--------|
| U01a | `GeminiSummaryDeriver` | `enrichment/summary/` | ✅ |
| U01b | `YakeKeywordDeriver` | `enrichment/keyword/` | ✅ |
| U01c | `GeminiFewShotEntityDeriver` | `enrichment/entity/` | ✅ |
| U01d | `UrlPlusEmbeddingLocalePairer` | `enrichment/locale/` | ✅ |
| U01e | `JGraphTAuthorityDeriver` | `enrichment/authority/` | ✅ |
| U01f | `GeminiFewShotPageKindClassifier` | `enrichment/pagekind/` | ✅ |
| U01g | `TopicRemineService`, cluster admin | `enrichment/topic/`, `catalog/topic/` | ✅ |
| U01h | `QdrantNamedVectorWriter` / NoOp | `enrichment/vectors/` | ✅ (OFF until U08) |
| U02 | MV refresh, `CatalogViewStatusService` | `catalog/refresh/` | ✅ |
| U05 | `CurationController` | `curation/`, `api/CurationController` | ✅ API |
| U07 | Spell/Intent/Expand pipeline | chat-api `query/` | ✅ code, flags OFF |
| U12 | `DerivationReadinessService`, eval queries | `catalog/readiness/`, V14 seed | ✅ |
| — | Cutover prep | `ops/ci/rag-derivation-cutover-prep.ps1` | ✅ |
| — | Master cutover | `ops/ci/rag-p1-cutover.ps1` | ✅ |

**Gemini model (runtime):** `gemini-2.5-flash-lite` — `gemini-2.0-flash-lite` returns API 404 (2026-05-25).

---

## 20. Ops REST API catalog (ingestion, db profile)

Base: `http://<host>:8093/api/v1/ingestion` · Corpus default: `geostat-portal`

| Method | Path | RAG-U | Purpose |
|--------|------|-------|---------|
| GET | `/corpora/{name}/derivation-readiness` | U12 | Cutover + eval gate checks (§14) |
| POST | `/corpora/{name}/enrichment:backfill` | U01 | Async backfill parsed docs |
| GET | `/corpora/{name}/enrichment/status` | U01 | Backfill progress |
| POST | `/corpora/{name}/authority:recompute` | U01e | PageRank batch |
| POST | `/corpora/{name}/topics:remine` | U01g | Topic mining |
| GET | `/corpora/{name}/topic-clusters` | U01g | List / pending approve |
| POST | `/topic-clusters/{id}:approve` | U01g | Admin approve label |
| POST | `/catalog:refresh` | U02 | REFRESH MATERIALIZED VIEW |
| GET | `/catalog/status` | U02 | MV freshness (V16) |
| POST | `/corpora/{name}/lifecycle:sync-qdrant` | L1 | Payload metadata sync |
| GET | `/corpora/{name}/evaluation-queries` | U12 | Golden set export |
| GET/POST/DELETE | `/curation/overrides` | U05 | Overlay CRUD |

**Chat-api (catalog flip):**

| Env | Purpose |
|-----|---------|
| `GEOSTAT_CHAT_CATALOG_SOURCE=yaml\|derived` | Strategy selector |
| `GEOSTAT_CHAT_CATALOG_JDBC_URL` | Derived mode PG read |
| GET `/api/v1/chat/catalog/status` | Smoke: `source`, `derivedReaderActive` |

Scripts: `geostat.ops.json` → `ci.ragP1Cutover`, `ci.ragDerivationCutoverPrep`, `ci.ragEvalGate`, `ci.chatDerivedCatalogSmoke`.

---

## 21. Configuration & feature flags

### Ingestion (`ops/config/ingestion/.env.*`)

| Variable | Default (dev) | Layer |
|----------|---------------|-------|
| `INGESTION_ENRICHMENT_ENABLED` | `true` (prod/hybrid cutover) | L2 |
| `INGESTION_ENRICHMENT_CHAT_MODEL` | `gemini-2.5-flash-lite` | L2 |
| `INGESTION_ENRICHMENT_MODEL_VERSION` | `gemini-2.5-flash-lite@2026-05-25` | L2 idempotency |
| `INGESTION_AGGREGATION_ENABLED` | `true` | L3 |
| `INGESTION_NAMED_VECTORS_ENABLED` | `false` | U01h until U08 |
| `INGESTION_EVENTS_ENABLED` | stack-dependent | async index/enrich |
| `GEMINI_API_KEY` | secret | L2 |

Manifest mirror: `geostat.ops.json` → `modules.ingestion.derivation`.

### Chat-api (`ops/config/backend/.env.*`)

| Variable | Until G2 | After cutover |
|----------|----------|---------------|
| `GEOSTAT_CHAT_CATALOG_SOURCE` | `yaml` | `derived` |
| `GEOSTAT_CHAT_CATALOG_JDBC_*` | set for flip readiness | required |
| U07 flags (`GEOSTAT_QUERY_*`) | `false` | eval-gated ON (P2) |

Presentation YAML (always kept): `topic-style.yaml`, `terminology-overlay.yaml` — not env toggles.

---

## 22. References

- [PHASE-8-ARCHITECTURE-PLAN.md](PHASE-8-ARCHITECTURE-PLAN.md) — **master source plan** (P1→P4+)
- [PHASE-8-P1-ARCHITECTURE-COMPLETION.md](PHASE-8-P1-ARCHITECTURE-COMPLETION.md)
- [PHASE-8-P2-ARCHITECTURE-PLAN.md](PHASE-8-P2-ARCHITECTURE-PLAN.md)
- [PHASE-8-P3-P4-ARCHITECTURE-PLAN.md](PHASE-8-P3-P4-ARCHITECTURE-PLAN.md)
- [ADR-011 RAG derivation architecture](../adr/011-rag-derivation-architecture.md)
- [ADR-010 Product stack benefit gate](../adr/010-product-stack-benefit-gate.md)
- [INGESTION-DATA-MODEL.md](INGESTION-DATA-MODEL.md)
- [INFRA-DATA-STORES.md](INFRA-DATA-STORES.md)
- [PROJECT-PLAN.md](PROJECT-PLAN.md) RAG-U rows
- [BACKLOG.md](BACKLOG.md) — Phase 8-ის გარეთ ცალკე IDs
- `.cursor/rules/zero-gap-architecture.mdc`
- `.cursor/rules/max-capability-collaboration.mdc`

### Backlog IDs referenced in this spec

| ID | Title | Where mentioned |
|---|---|---|
| **B-31** | Near-duplicate detector (SimHash/MinHash) | § 3 prerequisites, post-Phase-8 |
| **B-32** | Kafka switch (>100K docs / corpus) | § 13.5 scale thresholds |
| **B-33** | Multi-corpus admin UI | § 13.5 scale thresholds |
| **B-34** | Realtime refresh policy + ops command | § 6.5 per-corpus refresh |
| **B-35** | `UrlFrontierPort` abstraction | § 3 prerequisites |
| **B-36** | Embedding model migration playbook | § 13.5 scale thresholds |
| **B-37** | Enrichment-service extraction (deferred) | § 5 service boundary, § 13.5 — 3-condition observed trigger only |
| **P0-config-enrichment** | Manifest → enrichment env codegen | BACKLOG — duplicate `.env` literals |
| **P8-plan-01** | Service/package split planning review | § 13.6 — scheduled gate after P2 cutover + 30d telemetry |

---

*Spec completeness: §1–22. **P1 COMPLETE** (2026-05-26). G2 pass, YAML deleted, derived catalog live. Plan tree: [PHASE-8-ARCHITECTURE-PLAN.md](PHASE-8-ARCHITECTURE-PLAN.md).*
