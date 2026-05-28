> **Session 2026-05-27:** CROSS-GAP-01 implemented — `VectorCleanupJob`, `QdrantVectorStore.delete()`, `V25__vector_cleanup_embedding_status.sql`. V20 (Plan 10) partial — `V26__crawl_run_and_document_tracking.sql` (`raw_html_hash`, `last_seen_at`), `DocumentEntity` fields.
> **Session 2026-05-27 (cont.):** AD-03 ✅ — `CrawlCompletionEvent` + `CrawlCompletionListener` implemented. `RunConfig` gained `corpusName`. `CrawlRunner` publishes event after `resolveDocumentLinkTargets()`. Listener runs: enrichment backfill → authority recompute → MV refresh → vector cleanup, all async, each step individually try-caught.
>
### CROSS-GAP-01 — Qdrant vector cleanup after V19 quality filter 🔴

**პრობლემა — root cause:**

```
Before V19: chunk embedded → Qdrant vector exists
After V19:  document.quality_score = 'skip' (portal page)
            MV WHERE quality_score IN ('good','degraded') → doc excluded from MV
            BUT: Qdrant vector still exists!

Result:
  DerivedCatalogReader reads MV → portal page not found
  Qdrant retrieval → portal page vector found → top result
  Chat: returns portal landing page for statistical query
  = phantom vector problem
```

**Resolution steps:**

**ნაბიჯი 1 — `VectorCleanupJob` ingestion-service-ში:**

შექმენი:
`apps/ingestion-service/src/main/java/com/geostat/ingestion/vector/VectorCleanupJob.java`

```java
@Component
public class VectorCleanupJob {

    private final JdbcTemplate       jdbc;
    private final QdrantVectorStore   qdrant;   // existing Spring AI bean

    /**
     * Deletes Qdrant vectors for chunks that:
     *   (a) are embedded (embedding_status='embedded'), BUT
     *   (b) their document is now excluded from MV (skip/rejected quality)
     *
     * Run after: V19 migration, QualityGateRunner, MV REFRESH.
     * Safe to re-run multiple times (idempotent).
     */
    public CleanupResult cleanOrphanVectors(UUID corpusId) {
        // Find chunks: embedded but document excluded from MV
        List<String> orphanChunkIds = jdbc.queryForList("""
            SELECT c.id::text
            FROM ingestion.chunk c
            JOIN ingestion.document d ON d.id = c.document_id
            WHERE c.corpus_id       = ?
              AND c.embedding_status = 'embedded'
              AND (
                d.quality_score NOT IN ('good', 'degraded')
                OR d.page_kind = 'portal'
                OR COALESCE(length(d.content_text), 0) < 100
              )
            """, String.class, corpusId);

        if (orphanChunkIds.isEmpty()) {
            log.info("[vector-cleanup] corpus={} — no orphan vectors", corpusId);
            return CleanupResult.empty(corpusId);
        }

        log.info("[vector-cleanup] corpus={} — deleting {} orphan vectors",
            corpusId, orphanChunkIds.size());

        // Delete from Qdrant by vector ID
        qdrant.delete(orphanChunkIds);

        // Update chunk status — do NOT delete chunk row (keep for audit)
        jdbc.update("""
            UPDATE ingestion.chunk
            SET embedding_status = 'deleted'
            WHERE id = ANY(?)
            """, (Object) orphanChunkIds.stream()
                .map(UUID::fromString)
                .toArray(UUID[]::new));

        log.info("[vector-cleanup] corpus={} — {} vectors deleted from Qdrant",
            corpusId, orphanChunkIds.size());
        return new CleanupResult(corpusId, orphanChunkIds.size());
    }

    public record CleanupResult(UUID corpusId, int deletedCount) {
        public static CleanupResult empty(UUID id) { return new CleanupResult(id, 0); }
    }
}
```

**ნაბიჯი 2 — `CrawlCompletionListener`-ში (AD-03) cleanup call:**

```java
@Override
public void onApplicationEvent(CrawlCompletionEvent event) {
    enrichmentBackfillJob.run(event.corpusId(), true);
    authorityRecomputeJob.run(event.corpusId());
    mvRefresher.refreshAll();
    vectorCleanupJob.cleanOrphanVectors(event.corpusId()); // ← ADD after MV refresh
}
```

**ნაბიჯი 3 — V20 migration — `chunk.embedding_status` CHECK constraint update:**

```sql
-- V20: add 'deleted' to embedding_status allowed values
ALTER TABLE ingestion.chunk
  DROP CONSTRAINT IF EXISTS chunk_embedding_status_check;
ALTER TABLE ingestion.chunk
  ADD CONSTRAINT chunk_embedding_status_check
    CHECK (embedding_status IN ('pending', 'embedding', 'embedded', 'failed', 'deleted'));
```

**ნაბიჯი 4 — unit tests:**

```java
// VectorCleanupJobTest:
@Test
void cleanOrphanVectors_deletesEmbeddedChunks_forSkipDocuments() {
    // given: 3 chunks embedded, documents: 2 'good', 1 'skip'
    CleanupResult result = job.cleanOrphanVectors(corpusId);
    assertThat(result.deletedCount()).isEqualTo(1);
    verify(qdrant).delete(argThat(ids -> ids.size() == 1));
}

@Test
void cleanOrphanVectors_idempotent_onSecondRun() {
    // after first run: embedding_status='deleted'
    // second run: no vectors found (status != 'embedded')
    job.cleanOrphanVectors(corpusId);
    CleanupResult second = job.cleanOrphanVectors(corpusId);
    assertThat(second.deletedCount()).isEqualTo(0);
}

@Test
void cleanOrphanVectors_doesNotDelete_goodDocChunks() {
    // given: all docs quality_score='good'
    CleanupResult result = job.cleanOrphanVectors(corpusId);
    assertThat(result.deletedCount()).isEqualTo(0);
    verifyNoInteractions(qdrant);
}
```

**ფაილები:**
- New: `apps/ingestion-service/.../vector/VectorCleanupJob.java`
- Update: `apps/ingestion-service/.../crawl/CrawlCompletionListener.java`
- V20 migration — `chunk.embedding_status` constraint

**Acceptance criteria:**
- After V19 + MV rebuild: orphan vectors deleted from Qdrant.
- `embedding_status = 'deleted'` for cleaned chunks — row kept for audit.
- Idempotent: second run = 0 deletions.
- Chat retrieval: portal page chunks no longer returned.

---

### V20 — Flyway migration: crawl_run + raw_html_hash + stale tracking + dates 🟠

**V20 ეს ოთხი DB gap ერთ migration-ში:**

შექმენი:
`apps/ingestion-service/src/main/resources/db/migration/V20__crawl_run_and_document_tracking.sql`

```sql
-- V20: crawl run tracking + incremental crawl support + stale URL detection + dates

-- 1. crawl_run table — required for CrawlScheduler (AD-03)
--    prevents duplicate concurrent runs; tracks crawl history
CREATE TABLE IF NOT EXISTS ingestion.crawl_run (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    corpus_id     UUID        NOT NULL REFERENCES ingestion.corpus(id),
    started_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at   TIMESTAMPTZ,
    status        VARCHAR(16) NOT NULL DEFAULT 'running'
                  CHECK (status IN ('running', 'completed', 'failed', 'cancelled')),
    pages_crawled INT         NOT NULL DEFAULT 0,
    pages_failed  INT         NOT NULL DEFAULT 0,
    pages_skipped INT         NOT NULL DEFAULT 0,
    trigger       VARCHAR(32) NOT NULL DEFAULT 'scheduled'
                  CHECK (trigger IN ('scheduled', 'manual', 'ci'))
);

COMMENT ON TABLE ingestion.crawl_run IS
    'One row per crawl execution. Used by CrawlScheduler to prevent duplicate runs.
     status=running → block new crawl for same corpus.
     pages_skipped: unchanged pages detected via raw_html_hash comparison.';

CREATE INDEX IF NOT EXISTS idx_crawl_run_corpus_status
    ON ingestion.crawl_run (corpus_id, status);

-- 2. document.raw_html_hash — incremental crawl optimization
--    on re-crawl: hash same → skip extraction entirely (no re-parse, no re-chunk, no re-embed)
ALTER TABLE ingestion.document
    ADD COLUMN IF NOT EXISTS raw_html_hash CHAR(64);

COMMENT ON COLUMN ingestion.document.raw_html_hash IS
    'SHA-256 of raw fetched HTML (before any parsing).
     Used by CrawlPipeline: if hash unchanged on re-crawl → skip extraction.
     Recomputed on every fetch; changes = content changed → full re-pipeline.';

-- 3. document.last_seen_at — stale URL detection
ALTER TABLE ingestion.document
    ADD COLUMN IF NOT EXISTS last_seen_at TIMESTAMPTZ DEFAULT now();

COMMENT ON COLUMN ingestion.document.last_seen_at IS
    'Timestamp of last successful crawl for this URL.
     Updated on every successful HTTP 200 fetch.
     Stale detection: last_seen_at < (current crawl_run.started_at - grace_period)
     → mark fetch_status=stale → exclude from MV.';

UPDATE ingestion.document SET last_seen_at = updated_at WHERE last_seen_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_document_last_seen
    ON ingestion.document (corpus_id, last_seen_at);

-- 4. document.published_at + http_status + original_url (from L-1-12, L-1-14)
ALTER TABLE ingestion.document
    ADD COLUMN IF NOT EXISTS published_at  TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS http_status   SMALLINT,
    ADD COLUMN IF NOT EXISTS original_url  TEXT;

COMMENT ON COLUMN ingestion.document.published_at IS
    'Publication date extracted from JSON-LD datePublished, <time datetime>, or og:article:published_time.
     NULL if not extractable. Populated by GeostatNewsExtractionStrategy.';

COMMENT ON COLUMN ingestion.document.http_status IS
    'HTTP response status code from last fetch: 200, 301, 404, 500, etc.
     Enables distinguishing server errors from content absence.';

COMMENT ON COLUMN ingestion.document.original_url IS
    'Pre-redirect URL when canonical_url was updated after following HTTP redirect.
     NULL if no redirect occurred. Populated by L-1-12 fix.';

CREATE INDEX IF NOT EXISTS idx_document_published_at
    ON ingestion.document (corpus_id, published_at)
    WHERE published_at IS NOT NULL;

-- 5. fetch_status: add 'stale' value
ALTER TABLE ingestion.document
    DROP CONSTRAINT IF EXISTS document_fetch_status_check;
ALTER TABLE ingestion.document
    ADD CONSTRAINT document_fetch_status_check
    CHECK (fetch_status IN ('pending', 'parsing', 'parsed', 'failed', 'stale'));
```

**Java — `CrawlOrchestrator` crawl_run integration:**

```java
// DefaultCrawlOrchestrator.executeSingle():
public void executeSingle(CrawlJob job) {
    // prevent duplicate concurrent crawl
    Optional<UUID> running = crawlRunRepository
        .findRunningByCorpus(job.corpusId());
    if (running.isPresent()) {
        log.warn("[crawl] corpus={} already running, skipping", job.corpusName());
        return;
    }

    UUID runId = crawlRunRepository.startRun(job.corpusId(), "scheduled");
    try {
        crawlRunner.run(job, runId);
        crawlRunRepository.completeRun(runId,
            crawlRunner.pagesCrawled(), crawlRunner.pagesFailed(),
            crawlRunner.pagesSkipped());
    } catch (Exception e) {
        crawlRunRepository.failRun(runId, e.getMessage());
        throw new CrawlExecutionException("Crawl failed for " + job.corpusName(), e);
    }
}
```

**Java — incremental crawl (raw_html_hash check):**

```java
// DocumentIngestionPipeline — before extraction:
String fetchedHtml = fetchedPage.html();
String newHash = Hashing.sha256()
    .hashString(fetchedHtml, StandardCharsets.UTF_8)
    .toString();

Optional<String> existingHash = documentRepository
    .findRawHtmlHash(fetchedPage.canonicalUrl(), job.corpusId());

if (existingHash.isPresent() && existingHash.get().equals(newHash)) {
    // page unchanged — update last_seen_at only, skip full pipeline
    documentRepository.updateLastSeenAt(fetchedPage.canonicalUrl(), job.corpusId());
    metrics.increment("documents.skipped_unchanged");
    return;
}
// page changed or new — run full extraction pipeline
```

**Stale URL cleanup job:**

```java
// StaleDocumentCleanupJob.java
@Component
public class StaleDocumentCleanupJob {

    /**
     * After crawl completes: mark as 'stale' any documents not seen in current run.
     * Grace period: 3 days (allows for temporary crawl interruptions).
     */
    public int markStaleDocuments(UUID corpusId, Instant crawlStartedAt) {
        return jdbc.update("""
            UPDATE ingestion.document
            SET fetch_status = 'stale'
            WHERE corpus_id = ?
              AND fetch_status = 'parsed'
              AND last_seen_at < ? - INTERVAL '3 days'
            """, corpusId, crawlStartedAt);
    }
}
```

**Acceptance criteria (V20):**
- `crawl_run` table created; `CrawlOrchestrator` inserts row on start, updates on finish.
- Duplicate crawl prevention: second `executeSingle()` call = skipped with WARN log.
- `raw_html_hash` populated on first crawl; unchanged page = skip extraction (log `documents.skipped_unchanged`).
- `last_seen_at` updated on every successful fetch.
- `published_at` populated for news docs with structured date signals (L-1-14).
- `http_status` populated for every fetched document.
- Stale documents: `fetch_status='stale'` after 3-day grace period.
- MV: add `AND fetch_status != 'stale'` to existing quality filter.

---

### Backlog — Crawler + Parser (owner approval before implementation)

> ეს items ახლა blocking არ არის. Phase 1–7 შემდეგ owner-ს შეუთანხმე.

#### BACKLOG-C01 — sitemap.xml discovery

```yaml
# geostat-portal-policy.yaml (future):
sitemapDiscovery:
  enabled: true
  sitemapUrls:
    - https://www.geostat.ge/sitemap.xml
```

```java
// SitemapUrlDiscoverer.java
// parse sitemap.xml → extract all <loc> URLs
// add to crawl frontier at startup
// benefit: complete URL coverage without deep link-follow (4 levels)
```

#### BACKLOG-C02 — per-host rate limiting

```yaml
# geostat-portal-policy.yaml (future):
limits:
  rateLimitMs: 500           # global default
  perHostRateLimitMs:
    "www.geostat.ge": 1000   # main domain — stricter
    "cpi.geostat.ge": 2000   # external portal — conservative
```

#### BACKLOG-P01 — structured table extraction

```java
// ExtractedTable record:
public record ExtractedTable(
    String caption,
    List<String> headers,
    List<List<String>> rows,
    String unit,    // "მლნ. ლარი", "%", "ათასი კაცი"
    String period   // "2024 Q3", "2025"
) {}
// CleanedDocument: add List<ExtractedTable> tables field
// GeostatDatasetExtractionStrategy: aggressive table extraction
// benefit: Chat can answer "GDP 2024 Q3" with exact figure
```

#### BACKLOG-DB01 — Hot reload without restart

```java
// Future: @RefreshScope + FileWatcher on ops/config/corpus/
// CorpusConfigurationLoader @RefreshScope
// → YAML change deploys without service restart
// → zero downtime config updates
// implement only if deployment cycle > 5 min becomes a problem
```

---

*Audit: 2026-05-26 | Report: `ops/eval/reports/2026-05-26-db-data-quality-audit.txt` | Senior: bottom-up review*
