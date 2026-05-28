# DB Table Ownership & Coupling Architecture Fix

> **Senior directive — read every line before writing any code.**
> This document is the result of a two-pass analysis:
> - Pass 1: full data-flow audit of who reads and writes which tables
> - Pass 2: self-critique of Pass 1 — revised priority order, corrected phasing
>
> Both passes are merged here. Where Pass 1 was wrong, it is **explicitly marked as corrected**.
> Follow the phasing exactly. Do not swap phases. Do not skip steps.

> **Session 2026-05-27:** Phase A implemented (@Modifying targeted UPDATEs, enrichment services, SmileKMeansTopicMiner, JGraphTPageRankAuthorityDeriver).
> **Session 2026-05-27 (continued):** Phases B, C, D implemented.
> Remaining: Phase E (ArchUnit + port interfaces — future, after quality pipeline is stable).

---

## Table of Contents

1. [Problem: What Was Found](#1-problem-what-was-found)
2. [Full Read/Write Map](#2-full-readwrite-map)
3. [Concrete Violations Found in Code](#3-concrete-violations-found-in-code)
4. [Priority Assessment — Revised Phasing](#4-priority-assessment--revised-phasing)
5. [PHASE A — Column Ownership via @Modifying (immediate)](#5-phase-a--column-ownership-via-modifying-immediate)
6. [PHASE B — document_link Table (urgent — before FrontierCleanupJob)](#6-phase-b--document_link-table-urgent--before-frontiercleanupjob)
7. [PHASE C — Minor Structural Cleanup](#7-phase-c--minor-structural-cleanup)
8. [PHASE D — FeedbackScoreAggregator Rerouting](#8-phase-d--feedbackscoreaggregator-rerouting)
9. [PHASE E — Port Interfaces + ArchUnit (last)](#9-phase-e--port-interfaces--archunit-last)
10. [Execution Order and Dependencies](#10-execution-order-and-dependencies)
11. [Acceptance Criteria](#11-acceptance-criteria)

---

## 1. Problem: What Was Found

The `ingestion.document` table is accessed by **5+ unrelated lifecycle phases**, each calling
`documentRepository.save(entity)` after loading a full entity. This creates a **silent data overwrite risk**:

```
Thread A: EnrichmentRunExecutor loads doc D1 (t=0)
Thread B: SmileKMeansTopicMiner loads doc D1 (t=1)
Thread A: saves D1 with summary="..." (t=10) ← enrichment data written
Thread B: saves D1 with topicClusterId=X  (t=11) ← OVERWRITES summary="" 
                                                     because Thread B loaded at t=1
```

This is not theoretical. The enrichment backfill and topic mining **can run concurrently** during backfill.
`summary_ka`, `keywords`, `page_kind` can be overwritten silently. **This is a data quality bug.**

Additionally, some services read/write tables that do not belong to their architectural layer — this
creates tight coupling that breaks the Clean Architecture boundaries defined in `libs/platform-contracts`.

---

## 2. Full Read/Write Map

Legend: `W` = writes, `R` = reads, `⚠` = violation

```
Service / Class                        | Tables accessed
---------------------------------------|--------------------------------------------------
CrawlRunStore                          | W: document, url_frontier, crawl_run
                                       | R: url_frontier (dedup check)
LinkDiscoverer                         | R: url_frontier (hash dedup)  ← PERF-10
DocumentChunkWriter                    | W: chunk, chunk_group
ChunkVectorIndexer                     | W: vector_index
                                       | W: chunk.embedding_model     ← ⚠ PATTERN 3
EnrichmentRunExecutor                  | W: enrichment_run
                                       | W: document (via entity.save) ← ⚠ PATTERN 1
SmileKMeansTopicMiner                  | R: chunk (for vectors)
                                       | W: topic_cluster
                                       | W: document.topic_cluster_id  ← ⚠ PATTERN 1
JGraphTPageRankAuthorityDeriver        | R: url_frontier (link graph)  ← ⚠ PATTERN 2
                                       | W: document.authority_score
FeedbackScoreAggregator                | R: chat.retrieval_hit         ← ⚠ PATTERN 4
                                       | R: chat.feedback
                                       | R: chat.turn
                                       | W: ingestion.document.score_boost
PlaywrightRefetchService               | R/W: document
QueryIntentClassifier (?)              | query_intent_cache            ← ⚠ ORPHAN (unused)
VectorIndexRepository / QdrantStore    | R/W: vector_index
DerivedCatalogReader                   | R: topic_cluster, document (via MV)
CatalogMetaLoader                      | R: corpus
```

---

## 3. Concrete Violations Found in Code

### Violation 1 — SmileKMeansTopicMiner overwrites enrichment data

**Why this is a bug, not just a smell:**

`SmileKMeansTopicMiner.assignTopics()` pattern (current):
```java
List<DocumentEntity> docs = documentRepository.findByCorpus(corpusId); // load ALL
for (DocumentEntity doc : docs) {
    UUID clusterId = assignCluster(doc);
    doc.setTopicClusterId(clusterId);               // modifies loaded entity
}
documentRepository.saveAll(docs);  // saves ALL 80+ columns per document
```

If enrichment has written `summaryKa`, `keywords`, `pageKind` between the `findByCorpus()` and
`saveAll()` calls, those values **are overwritten with their state at time of load** (which may
be null or stale).

**Required fix — targeted UPDATE (PATTERN 1):**
```java
// In DocumentRepository:
@Modifying
@Transactional
@Query("UPDATE DocumentEntity d SET d.topicClusterId = :clusterId WHERE d.id = :id")
void updateTopicCluster(@Param("id") UUID id, @Param("clusterId") UUID clusterId);

// In SmileKMeansTopicMiner:
for (Map.Entry<UUID, UUID> entry : assignments.entrySet()) {
    documentRepository.updateTopicCluster(entry.getKey(), entry.getValue());
}
// No entity load. No overwrite risk. O(n) single-column updates.
```

For bulk performance, use a batch update approach (see Phase A implementation below).

---

### Violation 2 — EnrichmentRunExecutor overwrites with entity.save()

**Current pattern in `EnrichmentRunExecutor`:**
```java
DocumentEntity doc = documentRepository.findById(id).orElseThrow();
EnrichmentResult result = service.enrich(doc);            // Gemini call — seconds pass
doc.setSummaryKa(result.summaryKa());
doc.setKeywords(result.keywords());
doc.setPageKind(result.pageKind());
documentRepository.save(doc);  // saves ALL columns — overwrites whatever changed during Gemini call
```

During that Gemini call (which takes 2–8 seconds), another enrichment service or SmileKMeansTopicMiner
may have written to the same entity. `save(doc)` with a stale entity state overwrites it.

**Required fix — targeted UPDATE per enrichment type:**

```java
// In DocumentRepository — one method per enrichment service group:

@Modifying
@Transactional
@Query("""
    UPDATE DocumentEntity d
    SET d.summaryKa = :summaryKa,
        d.summaryEn = :summaryEn,
        d.updatedAt = CURRENT_TIMESTAMP
    WHERE d.id = :id
""")
void updateSummary(@Param("id") UUID id,
                   @Param("summaryKa") String summaryKa,
                   @Param("summaryEn") String summaryEn);

@Modifying
@Transactional
@Query("""
    UPDATE DocumentEntity d
    SET d.keywords = :keywords,
        d.updatedAt = CURRENT_TIMESTAMP
    WHERE d.id = :id
""")
void updateKeywords(@Param("id") UUID id, @Param("keywords") List<String> keywords);

@Modifying
@Transactional
@Query("""
    UPDATE DocumentEntity d
    SET d.pageKind = :pageKind,
        d.updatedAt = CURRENT_TIMESTAMP
    WHERE d.id = :id
""")
void updatePageKind(@Param("id") UUID id, @Param("pageKind") String pageKind);

@Modifying
@Transactional
@Query("""
    UPDATE DocumentEntity d
    SET d.authorityScore = :score,
        d.updatedAt = CURRENT_TIMESTAMP
    WHERE d.id = :id
""")
void updateAuthorityScore(@Param("id") UUID id, @Param("score") double score);
```

Each enrichment service calls **only its own** `updateXxx()` method. Never `save(entity)`.

---

### Violation 3 — JGraphTPageRankAuthorityDeriver reads url_frontier (boundary violation)

**Current code (approximate):**
```java
// JGraphTPageRankAuthorityDeriver:
List<UrlFrontierEntity> frontier = urlFrontierRepository.findByCrawlRun(runId);
for (UrlFrontierEntity uf : frontier) {
    if (uf.getParentUrl() != null) {
        graph.addEdge(uf.getParentUrl(), uf.getUrl());
    }
}
```

**Why this is a violation:**
- `url_frontier` is the crawl layer's internal queue table
- `JGraphTPageRankAuthorityDeriver` is in the enrichment layer
- Enrichment must not import from crawl internals
- `FrontierCleanupJob` (QDRANT-03) **deletes old frontier rows** — when it runs, the link graph disappears
  → PageRank has 0 edges → all `authority_score = 0` → MV sorts randomly

**This violation causes data quality degradation the moment FrontierCleanupJob is enabled.**

Required fix: PHASE B below — create `document_link` table.

---

### Violation 4 — ChunkVectorIndexer writes chunk.embedding_model (wrong owner)

**Current:**
```java
// ChunkVectorIndexer:
chunk.setEmbeddingModel(embedding.modelId()); // ← indexer writes to chunk entity
chunkRepository.save(chunk);
```

The `chunk` table is owned by the parse/chunking layer (`DocumentChunkWriter`).
The vector index layer (`ChunkVectorIndexer`) writing to `chunk.embedding_model` crosses this boundary.

**Required fix:** move `embedding_model` to `vector_index` — PHASE C below.

---

### Violation 5 — FeedbackScoreAggregator reads chat schema, writes ingestion schema

```java
// FeedbackScoreAggregator (currently disabled — matchIfMissing = false):
// Reads:  chat.retrieval_hit, chat.feedback, chat.turn
// Writes: ingestion.document.score_boost

jdbc.update("UPDATE ingestion.document SET score_boost = ? WHERE id = ?", delta, documentId);
```

The ingestion-service directly queries the `chat` schema and writes back to `ingestion.document`.
This cross-schema dependency means ingestion-service is tightly coupled to chat schema structure.

**Current status: DISABLED.** Do not enable before Phase D fix is in place.

---

### Violation 6 — query_intent_cache table: orphan

```sql
-- Table exists in schema: ingestion.query_intent_cache
-- No Java entity class found
-- No repository found
-- Not referenced in any Java file
-- Conclusion: dead table, safe to drop in V21
```

---

## 4. Priority Assessment — Revised Phasing

> **⚠ IMPORTANT: Pass 1 (original proposal) had wrong phasing.**
> Pass 2 corrects it. Follow Pass 2 phasing below.

| Phase | What | Why this order | Schema change? |
|-------|------|----------------|----------------|
| **A** | `@Modifying` targeted UPDATEs | Prevents active data overwrite. No schema changes. Immediate. | No |
| **B** | `document_link` table | MUST happen before `FrontierCleanupJob` runs. If cleanup deletes frontier rows first, PageRank loses its graph permanently. | Yes — V21 |
| **C** | `embedding_model` move + `query_intent_cache` drop | Low risk, clean boundary fix. After A and B. | Yes — V21 |
| **D** | FeedbackScoreAggregator rerouting | Feature is disabled. Fix before enabling. | Yes — V22 |
| **E** | Port interfaces in platform-contracts + ArchUnit | After quality pipeline is stable and running. Over-engineering to do earlier. | No |

**❌ Pass 1 mistake:** Port interfaces (Phase E) were listed as Phase A. **Do not add `DocumentCrawlPort` etc. to `platform-contracts` until Phase E.**

**❌ Pass 1 mistake:** `document_link` was listed as "Phase B — later". **It must be Phase B — urgent**, because `FrontierCleanupJob` activation (planned in QDRANT-03 / 10-cross-gap-backlog.md) will destroy the link graph if document_link is not seeded first.

---

## 5. PHASE A — Column Ownership via @Modifying (immediate)

### Step A-1: Add targeted UPDATE methods to DocumentRepository

File: `apps/ingestion-service/src/main/java/com/geostat/ingestion/persistence/repository/DocumentRepository.java`

Add these methods (do not remove any existing methods):

```java
// ─── Topic assignment (SmileKMeansTopicMiner) ──────────────────────────────

@Modifying
@Transactional
@Query("UPDATE DocumentEntity d SET d.topicClusterId = :clusterId, d.updatedAt = CURRENT_TIMESTAMP WHERE d.id = :id")
void updateTopicCluster(@Param("id") UUID id, @Param("clusterId") UUID clusterId);

/**
 * Bulk topic assignment — avoids N individual UPDATE calls.
 * topicMap: key = documentId, value = topicClusterId
 * Uses native SQL CASE for single-statement bulk update.
 */
@Modifying
@Transactional
@Query(value = """
    UPDATE ingestion.document
    SET topic_cluster_id = CASE id
        <foreach collection="entries" item="e" separator=" ">
          WHEN :#{#e.key} THEN :#{#e.value}
        </foreach>
        END,
        updated_at = NOW()
    WHERE id IN (:ids)
    """, nativeQuery = true)
void bulkUpdateTopicClusters(@Param("ids") List<UUID> ids,
                              @Param("entries") Set<Map.Entry<UUID, UUID>> entries);
```

> **Note on bulkUpdateTopicClusters:** If the JPQL CASE approach is complex with your JPA version,
> use `JdbcTemplate` in `SmileKMeansTopicMiner` directly:
>
> ```java
> // Simpler — JdbcTemplate bulk update in batches of 500:
> List<Object[]> params = assignments.entrySet().stream()
>     .map(e -> new Object[]{e.getValue(), e.getKey()})
>     .toList();
> jdbcTemplate.batchUpdate(
>     "UPDATE ingestion.document SET topic_cluster_id = ?, updated_at = NOW() WHERE id = ?",
>     params
> );
> ```
> Use `jdbcTemplate.batchUpdate` — this is the safe fallback.

```java
// ─── Enrichment fields (EnrichmentRunExecutor / individual services) ────────

@Modifying
@Transactional
@Query("""
    UPDATE DocumentEntity d
    SET d.summaryKa = :summaryKa, d.summaryEn = :summaryEn,
        d.updatedAt = CURRENT_TIMESTAMP
    WHERE d.id = :id
""")
void updateSummary(@Param("id") UUID id,
                   @Param("summaryKa") String summaryKa,
                   @Param("summaryEn") String summaryEn);

@Modifying
@Transactional
@Query("""
    UPDATE DocumentEntity d
    SET d.keywords = :keywords, d.updatedAt = CURRENT_TIMESTAMP
    WHERE d.id = :id
""")
void updateKeywords(@Param("id") UUID id, @Param("keywords") List<String> keywords);

@Modifying
@Transactional
@Query("""
    UPDATE DocumentEntity d
    SET d.pageKind = :pageKind, d.updatedAt = CURRENT_TIMESTAMP
    WHERE d.id = :id
""")
void updatePageKind(@Param("id") UUID id, @Param("pageKind") String pageKind);

// ─── Authority score (JGraphTPageRankAuthorityDeriver) ──────────────────────

@Modifying
@Transactional
@Query("""
    UPDATE DocumentEntity d
    SET d.authorityScore = :score, d.updatedAt = CURRENT_TIMESTAMP
    WHERE d.id = :id
""")
void updateAuthorityScore(@Param("id") UUID id, @Param("score") double score);

// ─── Crawl fetch result (CrawlRunStore) ─────────────────────────────────────

@Modifying
@Transactional
@Query("""
    UPDATE DocumentEntity d
    SET d.fetchStatus = :status,
        d.qualityScore = :quality,
        d.rawHtmlHash  = :rawHtmlHash,
        d.updatedAt    = CURRENT_TIMESTAMP
    WHERE d.id = :id
""")
void updateFetchResult(@Param("id") UUID id,
                       @Param("status") String status,
                       @Param("quality") String quality,
                       @Param("rawHtmlHash") String rawHtmlHash);
```

---

### Step A-2: Update SmileKMeansTopicMiner to use updateTopicCluster

File: `apps/ingestion-service/src/main/java/com/geostat/ingestion/enrichment/topic/SmileKMeansTopicMiner.java`

**Before (DO NOT keep this pattern):**
```java
List<DocumentEntity> docs = documentRepository.findByCorpusId(corpusId);
// ... compute assignments ...
for (DocumentEntity doc : docs) {
    doc.setTopicClusterId(assignedCluster.getId());
}
documentRepository.saveAll(docs);  // ❌ overwrites all columns
```

**After:**
```java
// Step 1: load only IDs + chunk content (not full entities)
List<UUID> docIds = documentRepository.findIdsByCorpusIdAndFetchStatus(
    corpusId, FetchStatus.SUCCESS);

// Step 2: load chunk vectors for clustering (chunks only, not documents)
List<ChunkProjection> chunks = chunkRepository.findVectorsByDocumentIds(docIds);

// Step 3: run KMeans clustering
Map<UUID, UUID> docToCluster = runClustering(chunks);

// Step 4: persist — targeted update only
List<Object[]> params = docToCluster.entrySet().stream()
    .map(e -> new Object[]{e.getValue(), e.getKey()})
    .toList();
jdbcTemplate.batchUpdate(
    "UPDATE ingestion.document SET topic_cluster_id = ?, updated_at = NOW() WHERE id = ?",
    params
);
```

---

### Step A-3: Update EnrichmentRunExecutor to use targeted updates

File: `apps/ingestion-service/src/main/java/com/geostat/ingestion/enrichment/runner/EnrichmentRunExecutor.java`

Each enrichment service must call its own targeted update. The pattern:

**SummaryEnrichmentService:**
```java
// After: EnrichmentResult result = deriveWithGemini(document);
documentRepository.updateSummary(document.getId(), result.summaryKa(), result.summaryEn());
```

**KeywordEnrichmentService:**
```java
documentRepository.updateKeywords(document.getId(), result.keywords());
```

**PageKindEnrichmentService:**
```java
documentRepository.updatePageKind(document.getId(), result.pageKind().name());
```

**Rule:** Each enrichment service calls exactly ONE `updateXxx()` method.
No service calls `documentRepository.save(entity)`. Remove all `save()` calls from enrichment services.

---

### Step A-4: Update JGraphTPageRankAuthorityDeriver authority write

This service currently calls `documentRepository.save(entity)` after computing PageRank scores.
Change to:

```java
for (Map.Entry<UUID, Double> entry : pageRankScores.entrySet()) {
    documentRepository.updateAuthorityScore(entry.getKey(), entry.getValue());
}
```

Note: do NOT yet change where this service reads its graph data — that is Phase B.

---

### Phase A acceptance criteria

- [ ] `SmileKMeansTopicMiner` does not call `documentRepository.save()` or `saveAll()`
- [ ] No enrichment service calls `documentRepository.save(entity)`
- [ ] `JGraphTPageRankAuthorityDeriver` calls `updateAuthorityScore()` not `save()`
- [ ] Run full enrichment backfill + topic mining concurrently — no column overwrite observed in DB
- [ ] All existing unit tests pass

---

## 6. PHASE B — document_link Table (urgent — before FrontierCleanupJob)

> **⚠ DO NOT enable FrontierCleanupJob until all steps in Phase B are complete and verified.**
>
> If FrontierCleanupJob runs before document_link is seeded, all link graph data is lost.
> `JGraphTPageRankAuthorityDeriver` will compute PageRank with 0 edges → all authority_score = 0.
> The retrieval MV sorts by authority → random ordering. Chat quality degrades silently.

---

### Step B-1: Create V21 migration — document_link table

File: `apps/ingestion-service/src/main/resources/db/migration/V21__document_link.sql`

```sql
-- Document link graph: stores parent→child URL relationships discovered during crawl.
-- Decouples PageRank computation (enrichment layer) from url_frontier (crawl layer).
-- Populated by CrawlRunStore. Read by JGraphTPageRankAuthorityDeriver.

CREATE TABLE IF NOT EXISTS ingestion.document_link (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    corpus_id       UUID         NOT NULL REFERENCES ingestion.corpus(id)   ON DELETE CASCADE,
    source_doc_id   UUID         NOT NULL REFERENCES ingestion.document(id)  ON DELETE CASCADE,
    target_url      TEXT         NOT NULL,
    target_doc_id   UUID                  REFERENCES ingestion.document(id)  ON DELETE SET NULL,
    crawl_run_id    UUID                  REFERENCES ingestion.crawl_run(id) ON DELETE SET NULL,
    discovered_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_document_link PRIMARY KEY (id)
);

-- Fast lookup: all outbound links for a document
CREATE INDEX idx_document_link_source ON ingestion.document_link(source_doc_id);

-- Fast lookup: all inbound links to a target (backlinks)
CREATE INDEX idx_document_link_target ON ingestion.document_link(target_doc_id)
    WHERE target_doc_id IS NOT NULL;

-- Dedup: prevent duplicate edges per crawl run
CREATE UNIQUE INDEX idx_document_link_dedup
    ON ingestion.document_link(source_doc_id, target_url);
```

---

### Step B-2: Create DocumentLinkEntity

File: `apps/ingestion-service/src/main/java/com/geostat/ingestion/persistence/entity/DocumentLinkEntity.java`

```java
package com.geostat.ingestion.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "ingestion", name = "document_link")
public class DocumentLinkEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "corpus_id", nullable = false)
    private UUID corpusId;

    @Column(name = "source_doc_id", nullable = false)
    private UUID sourceDocId;

    @Column(name = "target_url", nullable = false)
    private String targetUrl;

    @Column(name = "target_doc_id")
    private UUID targetDocId;   // null until target URL is crawled

    @Column(name = "crawl_run_id")
    private UUID crawlRunId;

    @Column(name = "discovered_at", nullable = false)
    private Instant discoveredAt = Instant.now();

    // getters / setters / builder omitted — generate with Lombok @Data or manually
}
```

---

### Step B-3: Create DocumentLinkRepository

File: `apps/ingestion-service/src/main/java/com/geostat/ingestion/persistence/repository/DocumentLinkRepository.java`

```java
package com.geostat.ingestion.persistence.repository;

import com.geostat.ingestion.persistence.entity.DocumentLinkEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentLinkRepository extends JpaRepository<DocumentLinkEntity, UUID> {

    /**
     * Returns all (sourceDocId, targetDocId) pairs for a corpus.
     * Used by JGraphTPageRankAuthorityDeriver to build the link graph.
     * Only returns resolved edges (where target_doc_id is not null).
     */
    @Query("""
        SELECT dl FROM DocumentLinkEntity dl
        WHERE dl.corpusId = :corpusId
          AND dl.targetDocId IS NOT NULL
    """)
    List<DocumentLinkEntity> findResolvedEdgesByCorpus(@Param("corpusId") UUID corpusId);

    /**
     * Used by CrawlRunStore to check for duplicate edges before insert.
     */
    boolean existsBySourceDocIdAndTargetUrl(UUID sourceDocId, String targetUrl);
}
```

---

### Step B-4: Populate document_link in CrawlRunStore

File: `apps/ingestion-service/src/main/java/com/geostat/ingestion/crawl/store/CrawlRunStore.java`

When a page is fetched and its document is persisted, record the outbound links:

```java
// After saving the DocumentEntity for a page, save its outbound links:

private final DocumentLinkRepository documentLinkRepository;

public void recordOutboundLinks(UUID sourceDocId, UUID corpusId, UUID crawlRunId,
                                 List<String> discoveredUrls) {
    List<DocumentLinkEntity> links = discoveredUrls.stream()
        .filter(url -> !documentLinkRepository.existsBySourceDocIdAndTargetUrl(sourceDocId, url))
        .map(url -> {
            DocumentLinkEntity link = new DocumentLinkEntity();
            link.setSourceDocId(sourceDocId);
            link.setCorpusId(corpusId);
            link.setCrawlRunId(crawlRunId);
            link.setTargetUrl(url);
            // targetDocId is null here — resolved in Step B-5
            return link;
        })
        .toList();

    if (!links.isEmpty()) {
        documentLinkRepository.saveAll(links);
    }
}
```

Call this method inside `CrawlRunStore.storePage(...)` after document persistence, passing the list of
outbound URLs discovered by `LinkDiscoverer` for this page.

---

### Step B-5: Resolve target_doc_id after crawl completion

After a full crawl run completes, resolve `target_url → target_doc_id` using a native SQL UPDATE:

File: `apps/ingestion-service/src/main/java/com/geostat/ingestion/crawl/store/CrawlRunStore.java`

```java
/**
 * After crawl run completes: resolve target_doc_id for all document_link rows
 * where target_url matches a known document URL.
 * Run once per crawl run in CrawlOrchestrator.afterCrawl().
 */
public void resolveDocumentLinkTargets(UUID corpusId) {
    jdbcTemplate.update("""
        UPDATE ingestion.document_link dl
        SET target_doc_id = d.id
        FROM ingestion.document d
        WHERE d.corpus_id = dl.corpus_id
          AND d.url = dl.target_url
          AND dl.corpus_id = ?
          AND dl.target_doc_id IS NULL
        """, corpusId);
    log.info("Resolved document_link targets for corpus {}", corpusId);
}
```

Call `resolveDocumentLinkTargets(corpusId)` in `CrawlOrchestrator` after each corpus crawl finishes.

---

### Step B-6: Update JGraphTPageRankAuthorityDeriver to use document_link

File: `apps/ingestion-service/src/main/java/com/geostat/ingestion/enrichment/authority/JGraphTPageRankAuthorityDeriver.java`

**Before (DO NOT keep this — uses url_frontier):**
```java
// ❌ Boundary violation — enrichment layer reads crawl layer internals
List<UrlFrontierEntity> frontier = urlFrontierRepository.findByCrawlRun(runId);
for (UrlFrontierEntity uf : frontier) {
    if (uf.getParentUrl() != null) {
        graph.addEdge(uf.getParentUrl(), uf.getUrl());
    }
}
```

**After:**
```java
// ✅ Uses document_link — enrichment layer reads its own port
private final DocumentLinkRepository documentLinkRepository;

// Remove: private final UrlFrontierRepository urlFrontierRepository;

private Graph<UUID, DefaultEdge> buildLinkGraph(UUID corpusId) {
    Graph<UUID, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);

    List<DocumentLinkEntity> edges = documentLinkRepository.findResolvedEdgesByCorpus(corpusId);
    for (DocumentLinkEntity link : edges) {
        graph.addVertex(link.getSourceDocId());
        graph.addVertex(link.getTargetDocId());
        graph.addEdge(link.getSourceDocId(), link.getTargetDocId());
    }
    return graph;
}
```

Remove `UrlFrontierRepository` from this class's constructor entirely.

---

### Step B-7: Seed document_link from existing url_frontier (one-time migration)

Before FrontierCleanupJob is enabled, seed `document_link` from existing `url_frontier` data:

Add to V21 migration after the CREATE TABLE statements:

```sql
-- One-time seed: populate document_link from existing url_frontier data
-- Only inserts rows where both source and target documents are known
INSERT INTO ingestion.document_link (corpus_id, source_doc_id, target_url, target_doc_id, crawl_run_id)
SELECT DISTINCT
    d_source.corpus_id,
    d_source.id            AS source_doc_id,
    uf.url                 AS target_url,
    d_target.id            AS target_doc_id,
    uf.crawl_run_id
FROM ingestion.url_frontier uf
JOIN ingestion.document d_source
    ON d_source.url = uf.parent_url
    AND d_source.corpus_id = (SELECT corpus_id FROM ingestion.crawl_run WHERE id = uf.crawl_run_id)
LEFT JOIN ingestion.document d_target
    ON d_target.url = uf.url
    AND d_target.corpus_id = d_source.corpus_id
WHERE uf.parent_url IS NOT NULL
ON CONFLICT (source_doc_id, target_url) DO NOTHING;
```

After running V21, verify seeding:
```sql
SELECT COUNT(*) FROM ingestion.document_link;
-- Should be > 0 if url_frontier has parent_url data
```

---

### Phase B acceptance criteria

- [ ] V21 migration runs without errors
- [ ] `document_link` table exists and is seeded (COUNT > 0)
- [ ] `JGraphTPageRankAuthorityDeriver` has NO import of `UrlFrontierRepository`
- [ ] PageRank computation uses `document_link` and produces non-zero `authority_score` values
- [ ] FrontierCleanupJob can now be enabled safely (QDRANT-03) — link graph is preserved
- [ ] `CrawlRunStore` populates `document_link` during new crawls (confirmed via logs)

---

## 7. PHASE C — Minor Structural Cleanup

### Step C-1: Move embedding_model from chunk to vector_index

**Why:** `ChunkVectorIndexer` (vector index layer) writes `chunk.embedding_model` (chunk layer).
Column ownership mismatch. `embedding_model` belongs next to the vector in `vector_index`.

Add to V21 migration:

```sql
-- Move embedding_model ownership to vector_index
ALTER TABLE ingestion.vector_index
    ADD COLUMN IF NOT EXISTS embedding_model TEXT;

-- Backfill from chunk table
UPDATE ingestion.vector_index vi
SET embedding_model = c.embedding_model
FROM ingestion.chunk c
WHERE c.id = vi.chunk_id
  AND c.embedding_model IS NOT NULL;

-- Do NOT drop chunk.embedding_model yet — drop in V22 after verification
```

Update `VectorIndexEntity.java`:
```java
@Column(name = "embedding_model")
private String embeddingModel;
```

Update `ChunkVectorIndexer.java`:
```java
// Before: chunk.setEmbeddingModel(embedding.modelId());
// After:
VectorIndexEntity index = new VectorIndexEntity();
index.setChunkId(chunk.getId());
index.setVector(vectors[i]);
index.setEmbeddingModel(embedding.modelId());  // ← write to vector_index, not chunk
vectorIndexRepository.save(index);
```

Remove `chunk.setEmbeddingModel(...)` from `ChunkVectorIndexer`. The field still exists in
`ChunkEntity` for now (will be dropped in V22 after verifying `vector_index.embedding_model`).

---

### Step C-2: Drop orphan query_intent_cache table

Add to V21 migration:

```sql
-- Drop unused orphan table (no Java entity or repository references this table)
-- Confirmed by: grep -r "query_intent_cache" apps/ → 0 results
DROP TABLE IF EXISTS ingestion.query_intent_cache;
```

Before adding this to V21, verify with:
```powershell
# In project root:
rg "query_intent_cache" apps/ --type java
# Expected: 0 matches
```

If any match is found, do NOT drop — investigate first.

---

### Phase C acceptance criteria

- [ ] `vector_index.embedding_model` column exists and is backfilled
- [ ] `ChunkVectorIndexer` writes `embedding_model` to `vector_index`, not `chunk`
- [ ] `query_intent_cache` table dropped (after confirming zero Java references)
- [ ] `ModelMigrationJob` (ARCH-09) reads from `vector_index.embedding_model` (not `chunk`)

---

## 8. PHASE D — FeedbackScoreAggregator Rerouting

> **Status: feature is currently DISABLED** (`matchIfMissing = false`).
> Do NOT enable it before completing this phase.

### Current violation

`FeedbackScoreAggregator` (in `ingestion-service`) reads `chat.retrieval_hit`, `chat.feedback`, `chat.turn`
and writes `ingestion.document.score_boost`.

This means ingestion-service knows the chat DB schema structure, creating tight coupling.

### Required architecture change

**Option A (preferred — event-based):**

1. `chat-service` publishes a nightly `FeedbackScoreEvent` (via Spring `ApplicationEventPublisher`
   if monolith, or via message queue if services are separate).
2. `ingestion-service` listens for `FeedbackScoreEvent` and writes to `curation_override`.
3. `curation_override` influences scoring via MV, not `document.score_boost` directly.

**Option B (pragmatic for now):**

If both services share the same DB instance (current state), keep the `JdbcTemplate` cross-schema query
but wrap it in a clearly named adapter class and document the coupling explicitly:

```java
// File: apps/ingestion-service/.../feedback/ChatFeedbackReader.java
// Purpose: reads chat schema data — TEMPORARY. Remove when chat service owns this query.
// TODO PHASE-D: replace with FeedbackScoreEvent listener when chat service is decoupled.
@Component
public class ChatFeedbackReader {
    private final JdbcTemplate jdbc;
    List<FeedbackAggregateRow> fetchAggregates() { ... }
}
```

And add V22 migration to drop `ingestion.document.score_boost`:

```sql
-- V22: after FeedbackScoreAggregator rerouted to curation_override
ALTER TABLE ingestion.document DROP COLUMN IF EXISTS score_boost;
```

**For Phase D, implement Option B first (mark as TODO), then Option A in a follow-up.**

---

## 9. PHASE E — Port Interfaces + ArchUnit (last)

> **Do NOT implement Phase E until:**
> - Quality pipeline is stable and producing clean data
> - Phases A, B, C are verified in production
> - The team has time for a non-urgent refactor
>
> **⚠ Pass 1 proposed Port interfaces as Phase A. This was wrong.**
> Port interfaces add no data quality benefit. They are a DDD refinement for a later stage.

### What Phase E adds

**Port interfaces in `libs/platform-contracts`:**

```
libs/platform-contracts/src/main/java/com/geostat/platform/document/
├── DocumentCrawlPort.java         ← updateFetchResult, updateQuality
├── DocumentEnrichmentPort.java    ← updateSummary, updateKeywords, updatePageKind
├── DocumentAuthorityPort.java     ← updateAuthorityScore
└── DocumentTopicPort.java         ← updateTopicCluster
```

Each port is an interface. `DocumentRepository` implements all four. Each service only injects
the port it needs — it cannot call methods outside its scope.

**ArchUnit test:**

Add dependency to `apps/ingestion-service/build.gradle.kts`:
```kotlin
testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
```

Test:
```java
@AnalyzeClasses(packages = "com.geostat.ingestion")
class ArchitectureBoundaryTest {

    @ArchTest
    static ArchRule enrichmentMustNotAccessUrlFrontier =
        noClasses()
            .that().resideInAPackage("..enrichment..")
            .should().accessClassesThat().resideInAPackage("..crawl.frontier..")
            .because("Enrichment layer must not depend on crawl internals. Use document_link.");

    @ArchTest
    static ArchRule topicMinerMustNotSaveDocument =
        noClasses()
            .that().haveSimpleName("SmileKMeansTopicMiner")
            .should().callMethod(DocumentRepository.class, "save", Object.class)
            .because("SmileKMeansTopicMiner must use updateTopicCluster(), not save().");
}
```

---

## 10. Execution Order and Dependencies

```
CRITICAL ORDERING — do not deviate:

Phase A (no schema change):
  A-1: Add @Modifying methods to DocumentRepository
  A-2: Update SmileKMeansTopicMiner
  A-3: Update each enrichment service's persist call
  A-4: Update JGraphTPageRankAuthorityDeriver authority write
  ↓ test: run enrichment backfill + topic mining concurrently, verify no overwrites

Phase B (V21 migration — run BEFORE FrontierCleanupJob):
  B-1: Write V21__document_link.sql (CREATE TABLE + seed from url_frontier)
  B-2: Create DocumentLinkEntity + DocumentLinkRepository
  B-3: Run V21 migration, verify seed count > 0
  B-4: Update CrawlRunStore to populate document_link
  B-5: Add resolveDocumentLinkTargets() call to CrawlOrchestrator
  B-6: Update JGraphTPageRankAuthorityDeriver to use DocumentLinkRepository
  B-7: Remove UrlFrontierRepository from JGraphTPageRankAuthorityDeriver
  ↓ test: PageRank produces non-zero authority scores
  ↓ NOW it is safe to enable FrontierCleanupJob (QDRANT-03)

Phase C (V21 migration — same migration file as B or V22):
  C-1: ALTER vector_index ADD embedding_model + backfill UPDATE
  C-2: Update ChunkVectorIndexer
  C-3: DROP query_intent_cache (after confirming zero Java refs)
  ↓ test: new embeddings go to vector_index.embedding_model

Phase D (only when feedback feature is being enabled):
  D-1: Create ChatFeedbackReader adapter with TODO comment
  D-2: Confirm FeedbackScoreAggregator uses adapter
  D-3: Write V22 migration stub for score_boost column drop

Phase E (future — after quality pipeline is stable):
  E-1: Add archunit-junit5 dependency
  E-2: Create port interfaces in libs/platform-contracts
  E-3: Write ArchUnit boundary tests
  E-4: Update services to inject ports instead of repository
```

---

## 11. Acceptance Criteria

### Phase A complete when:
- Zero calls to `documentRepository.save(entity)` in enrichment services (grep confirms)
- Zero calls to `documentRepository.saveAll(entities)` in `SmileKMeansTopicMiner`
- Each `update*()` method in `DocumentRepository` has a corresponding unit test
- Concurrent enrichment + topic mining produces no silent column overwrites

### Phase B complete when:
- `SELECT COUNT(*) FROM ingestion.document_link` > 0 after V21
- `JGraphTPageRankAuthorityDeriver` has zero imports from `..crawl.frontier..`
- PageRank scores are non-zero for at least 50% of documents with inbound links
- FrontierCleanupJob can run without degrading PageRank quality

### Phase C complete when:
- `SELECT COUNT(*) FROM ingestion.vector_index WHERE embedding_model IS NOT NULL` matches `SELECT COUNT(*) FROM ingestion.chunk WHERE embedding_model IS NOT NULL`
- `\d ingestion.query_intent_cache` → "relation does not exist"

### Phase D complete when:
- `FeedbackScoreAggregator` has a `// TODO PHASE-D` comment and uses `ChatFeedbackReader`
- No direct `UPDATE ingestion.document SET score_boost` in ingestion service

### Phase E complete when:
- All ArchUnit tests pass in CI
- No service injects `DocumentRepository` directly (all use typed ports)

---

*Senior directive. Combined from two-pass analysis (original proposal + self-critique).*
*Do not swap phases. Do not skip steps. Phasing order is not cosmetic — it is correctness.*

---

## 12. EXACT CODE PATCHES — Read this before touching any file

> **This section contains exact corrections based on reading the actual source files.**
> Where Sections 5–6 use generic/approximate code, **this section overrides them.**
> These are the only instructions the junior needs for Phase A and Phase B.

---

### PATCH A-1 — SmileKMeansTopicMiner (exact lines to change)

File: `apps/ingestion-service/src/main/java/com/geostat/ingestion/enrichment/topic/SmileKMeansTopicMiner.java`

Inject `JdbcTemplate` into constructor:

```java
// Add to constructor parameters:
private final JdbcTemplate jdbcTemplate;

public SmileKMeansTopicMiner(
        DocumentRepository documentRepository,
        TopicClusterRepository topicClusterRepository,
        CorpusRepository corpusRepository,
        SummaryEmbeddingSource embeddingSource,
        GeminiTopicClusterLabeler clusterLabeler,
        EnrichmentProperties properties,
        JdbcTemplate jdbcTemplate) {       // ← add this
    ...
    this.jdbcTemplate = jdbcTemplate;      // ← add this
}
```

Replace lines 133–141 (the `for` loop + `documentRepository.saveAll`) **exactly**:

```java
// BEFORE (lines 133–141 — remove this entire block):
int assigned = 0;
for (DocumentEntity document : candidates) {
    UUID clusterId = documentToCluster.get(document.getId());
    if (clusterId != null) {
        document.setTopicClusterId(clusterId);
        assigned++;
    }
}
documentRepository.saveAll(candidates);

// AFTER (replace with):
int assigned = 0;
if (!documentToCluster.isEmpty()) {
    List<Object[]> params = documentToCluster.entrySet().stream()
        .map(e -> new Object[]{e.getValue(), e.getKey()})
        .toList();
    jdbcTemplate.batchUpdate(
        "UPDATE ingestion.document SET topic_cluster_id = ?, updated_at = NOW() WHERE id = ?",
        params
    );
    assigned = params.size();
}
```

**Why:** `documentRepository.saveAll(candidates)` saves ALL 30+ columns including `summary_ka`, `keywords`,
`authority_score`, etc. back to their state at load time. `jdbcTemplate.batchUpdate` writes ONLY
`topic_cluster_id` — no other column is touched.

**Verify fix:** after the change, grep the file:
```powershell
rg "saveAll" apps/ingestion-service/src/main/java/com/geostat/ingestion/enrichment/topic/SmileKMeansTopicMiner.java
# Expected: 0 matches
```

---

### PATCH A-2 — JGraphTPageRankAuthorityDeriver — Phase A (authority_score write only)

File: `apps/ingestion-service/src/main/java/com/geostat/ingestion/enrichment/authority/JGraphTPageRankAuthorityDeriver.java`

Inject `JdbcTemplate`:

```java
// Add to constructor parameters:
private final JdbcTemplate jdbcTemplate;

public JGraphTPageRankAuthorityDeriver(
        DocumentRepository documentRepository,
        UrlFrontierRepository urlFrontierRepository,
        EnrichmentProperties properties,
        JdbcTemplate jdbcTemplate) {    // ← add
    ...
    this.jdbcTemplate = jdbcTemplate;  // ← add
}
```

Replace lines 90–99 (the `for` loop over documents + `documentRepository.saveAll`) **exactly**:

```java
// BEFORE (lines 90–99 — remove this block):
for (DocumentEntity document : documents) {
    if (PageKindValues.NAVIGATION.equals(document.getPageKind())) {
        document.setAuthorityScore(0.0);
        continue;
    }
    double pageRank = normalized.getOrDefault(document.getId(), 0.0);
    double freshness = FreshnessDecay.score(document.getFetchedAt(), now);
    document.setAuthorityScore(AuthorityScoreComposer.compose(pageRank, freshness));
}
documentRepository.saveAll(documents);

// AFTER (replace with — compute scores into a map, then batch UPDATE):
List<Object[]> params = new ArrayList<>();
for (DocumentEntity document : documents) {
    double score;
    if (PageKindValues.NAVIGATION.equals(document.getPageKind())) {
        score = 0.0;
    } else {
        double pageRank = normalized.getOrDefault(document.getId(), 0.0);
        double freshness = FreshnessDecay.score(document.getFetchedAt(), now);
        score = AuthorityScoreComposer.compose(pageRank, freshness);
    }
    params.add(new Object[]{score, document.getId()});
}
jdbcTemplate.batchUpdate(
    "UPDATE ingestion.document SET authority_score = ?, updated_at = NOW() WHERE id = ?",
    params
);
```

Add import at top of file:
```java
import java.util.ArrayList;
import org.springframework.jdbc.core.JdbcTemplate;
```

> **Note:** do NOT remove `UrlFrontierRepository` dependency yet — that is Phase B.
> Phase A only changes HOW scores are saved (saveAll → batchUpdate). Phase B changes
> WHERE the graph data is read from (url_frontier → document_link).

---

### PATCH A-3 — EnrichmentRunExecutor + each enrichment service (keywords, summary, pageKind)

**Root cause:** `EnrichmentRunExecutor.run()` calls `persist.accept(document, result)` which
mutates the entity, then `documentRepository.save(document)` which saves ALL columns. The
`persist` lambda is defined by each calling service.

**Strategy:** 
1. Remove `documentRepository.save(document)` from `EnrichmentRunExecutor` (line 71)
2. Each enrichment service injects `DocumentRepository` and its `persistXxx` lambda calls `documentRepository.updateXxx()` directly

---

**Step 1 — EnrichmentRunExecutor.java line 71:**

```java
// File: apps/ingestion-service/src/main/java/com/geostat/ingestion/enrichment/runner/EnrichmentRunExecutor.java

// BEFORE (lines 69–71):
T result = deriveWithRetry(document, maxRetries, derive);
persist.accept(document, result);
documentRepository.save(document);   // ← REMOVE this line

// AFTER:
T result = deriveWithRetry(document, maxRetries, derive);
persist.accept(document, result);
// documentRepository.save() intentionally removed.
// Each persist lambda is responsible for its own targeted UPDATE.
```

---

**Step 2 — KeywordEnrichmentService.java:**

Add `DocumentRepository` to constructor and change `persistKeywords`:

```java
// Add import:
import com.geostat.ingestion.persistence.repository.DocumentRepository;
import org.springframework.jdbc.core.JdbcTemplate;

// Constructor — add DocumentRepository and JdbcTemplate:
private final DocumentRepository documentRepository;
private final JdbcTemplate jdbcTemplate;

public KeywordEnrichmentService(
        EnrichmentRunExecutor enrichmentRunExecutor,
        KeywordDeriver keywordDeriver,
        EnrichmentProperties properties,
        DocumentRepository documentRepository,  // ← add
        JdbcTemplate jdbcTemplate) {            // ← add
    ...
    this.documentRepository = documentRepository;
    this.jdbcTemplate = jdbcTemplate;
}

// Replace persistKeywords method:
// BEFORE:
private void persistKeywords(DocumentEntity document, List<String> keywords) {
    document.setKeywords(keywords.toArray(String[]::new));
}

// AFTER — uses JdbcTemplate because keywords is text[] in PostgreSQL (Spring @Modifying
// does not handle String[] → text[] type conversion reliably with @JdbcTypeCode):
private void persistKeywords(DocumentEntity document, List<String> keywords) {
    String[] arr = keywords.toArray(String[]::new);
    jdbcTemplate.update(
        con -> {
            var ps = con.prepareStatement(
                "UPDATE ingestion.document SET keywords = ?, updated_at = NOW() WHERE id = ?");
            ps.setArray(1, con.createArrayOf("text", arr));
            ps.setObject(2, document.getId());
            return ps;
        }
    );
}
```

---

**Step 3 — SummaryEnrichmentService.java** (find by looking for the class that calls `EnrichmentDeriverKind.summary`):

Find the service — search:
```powershell
rg "EnrichmentDeriverKind.summary" apps/ingestion-service/src/ --type java -l
```

In that service, find the `persistSummary` / `persist` lambda. It will do:
```java
// CURRENT pattern (approximate):
(document, result) -> {
    document.setSummaryKa(result.summaryKa());
    document.setSummaryEn(result.summaryEn());
}
```

Replace with:
```java
// Add DocumentRepository to constructor of that service (same pattern as KeywordEnrichmentService)

// AFTER — targeted JPQL update (summaryKa/summaryEn are plain TEXT, so @Modifying JPQL works):
(document, result) -> {
    documentRepository.updateSummary(
        document.getId(), result.summaryKa(), result.summaryEn());
}
```

Add to `DocumentRepository`:
```java
@Modifying
@Transactional
@Query("""
    UPDATE DocumentEntity d
    SET d.summaryKa = :summaryKa,
        d.summaryEn = :summaryEn,
        d.updatedAt = CURRENT_TIMESTAMP
    WHERE d.id = :id
""")
void updateSummary(@Param("id") UUID id,
                   @Param("summaryKa") String summaryKa,
                   @Param("summaryEn") String summaryEn);
```

---

**Step 4 — PageKindEnrichmentService.java:**

Find the persist lambda — it will do `document.setPageKind(...)`. Replace with:

```java
// DocumentRepository method:
@Modifying
@Transactional
@Query("UPDATE DocumentEntity d SET d.pageKind = :pageKind, d.updatedAt = CURRENT_TIMESTAMP WHERE d.id = :id")
void updatePageKind(@Param("id") UUID id, @Param("pageKind") String pageKind);

// In PageKindEnrichmentService persist lambda:
(document, result) -> {
    documentRepository.updatePageKind(document.getId(), result.name());
}
```

---

**Step 5 — Find all remaining services that call enrichmentRunExecutor.run():**

```powershell
rg "enrichmentRunExecutor.run" apps/ingestion-service/src/ --type java -l
```

For EACH file returned:
1. Find its `persist*` lambda / method
2. Change it to call `documentRepository.updateXxx()` instead of `document.setXxx()`
3. Add `DocumentRepository` + `JdbcTemplate` to constructor if not already there

---

**Step 6 — Verify Phase A complete:**

After all services are updated:
```powershell
# These should return 0 matches:
rg "documentRepository\.save\(document\)" apps/ingestion-service/src/main/java/com/geostat/ingestion/enrichment/ --type java
rg "documentRepository\.saveAll\(candidates\)" apps/ingestion-service/src/main/java/com/geostat/ingestion/enrichment/ --type java
rg "documentRepository\.saveAll\(documents\)" apps/ingestion-service/src/main/java/com/geostat/ingestion/enrichment/ --type java
```

---

### PATCH B-1 — JGraphTPageRankAuthorityDeriver Phase B (graph source change)

> **Do this only after Phase B migration (V21) is applied and document_link is seeded.**

File: `apps/ingestion-service/src/main/java/com/geostat/ingestion/enrichment/authority/JGraphTPageRankAuthorityDeriver.java`

**Remove `UrlFrontierRepository` from constructor. Add `DocumentLinkRepository`:**

```java
// BEFORE constructor:
public JGraphTPageRankAuthorityDeriver(
        DocumentRepository documentRepository,
        UrlFrontierRepository urlFrontierRepository,
        EnrichmentProperties properties,
        JdbcTemplate jdbcTemplate) { ... }

// AFTER constructor (remove urlFrontierRepository, add documentLinkRepository):
public JGraphTPageRankAuthorityDeriver(
        DocumentRepository documentRepository,
        DocumentLinkRepository documentLinkRepository,  // ← replace
        EnrichmentProperties properties,
        JdbcTemplate jdbcTemplate) { ... }
```

**Replace lines 72–84 (the url_frontier loop):**

```java
// BEFORE (lines 72–84):
int edgeCount = 0;
for (Object[] link : urlFrontierRepository.findParentChildUrlsByCorpusId(corpusId)) {
    String childUrl = (String) link[0];
    String parentUrl = (String) link[1];
    UUID sourceId = urlHashToDocumentId.get(UrlHasher.hash(parentUrl));
    UUID targetId = urlHashToDocumentId.get(UrlHasher.hash(childUrl));
    if (sourceId == null || targetId == null || !eligible.contains(sourceId) || !eligible.contains(targetId)) {
        continue;
    }
    graph.addVertex(sourceId);
    graph.addVertex(targetId);
    graph.addEdge(sourceId, targetId);
    edgeCount++;
}

// AFTER (uses document_link — no URL hashing needed, direct UUID edges):
int edgeCount = 0;
List<DocumentLinkEntity> edges = documentLinkRepository.findResolvedEdgesByCorpus(corpusId);
for (DocumentLinkEntity link : edges) {
    UUID sourceId = link.getSourceDocId();
    UUID targetId = link.getTargetDocId();
    if (!eligible.contains(sourceId) || !eligible.contains(targetId)) {
        continue;
    }
    graph.addVertex(sourceId);
    graph.addVertex(targetId);
    if (!graph.containsEdge(sourceId, targetId)) {
        graph.addEdge(sourceId, targetId);
        edgeCount++;
    }
}
```

Remove unused import:
```java
// Remove these imports (no longer needed after Phase B):
import com.geostat.ingestion.crawl.frontier.UrlHasher;
import com.geostat.ingestion.persistence.repository.UrlFrontierRepository;

// Add:
import com.geostat.ingestion.persistence.entity.DocumentLinkEntity;
import com.geostat.ingestion.persistence.repository.DocumentLinkRepository;
```

---

### PATCH B-2 — V21 migration file name

**Check current highest migration number first:**

```powershell
ls apps/ingestion-service/src/main/resources/db/migration/ | Sort-Object Name
```

Use the next available number. If V20 exists, use V21. If V20 does not exist yet (check), use V20.
The migration SQL content is exactly as defined in Section 6 Step B-1 and Step B-7.

---

### Summary — what junior must do, in order

```
1. Read this entire file (all 12 sections)
2. Phase A:
   a. Add @Modifying methods to DocumentRepository (Section 5 Step A-1)
   b. Apply PATCH A-1 to SmileKMeansTopicMiner
   c. Apply PATCH A-2 to JGraphTPageRankAuthorityDeriver (authority write only)
   d. Apply PATCH A-3 Steps 1–6 to EnrichmentRunExecutor + all enrichment services
   e. Run verification greps to confirm 0 save() calls remain in enrichment package
3. Phase B:
   a. Check migration file numbering (PATCH B-2)
   b. Create V21__document_link.sql (Section 6 Step B-1 + Step B-7)
   c. Create DocumentLinkEntity (Section 6 Step B-2)
   d. Create DocumentLinkRepository (Section 6 Step B-3)
   e. Apply V21 migration, verify COUNT > 0
   f. Add recordOutboundLinks to CrawlRunStore (Section 6 Step B-4)
   g. Add resolveDocumentLinkTargets to CrawlRunStore (Section 6 Step B-5)
   h. Apply PATCH B-1 to JGraphTPageRankAuthorityDeriver
   i. Only now: enable FrontierCleanupJob (Section 10, QDRANT-03)
4. Phase C: Section 7 Steps C-1 and C-2
5. Phase D: Section 8 only when feedback feature is being enabled
6. Phase E: Section 9 only after quality pipeline is stable
```
