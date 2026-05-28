> **Session 2026-05-27:** L-1-23 implemented — `StatisticalContentGuard` in platform-contracts; guard wired in `MarkerBoilerplateStripper.isBoilerplateParagraph()` and `HtmlContentCleaner.isLegacyBoilerplate()`. L-1-24 implemented — `V22__chunk_nav_breadcrumb.sql`, `ChunkEntity.navBreadcrumb`, `DocumentChunkWriter` INSERT propagation + metadata. L-1-25 implemented — atomic document+chunks TX in `CrawlRunStore` (`parsing`→`parsed` status flow), `EmbeddingQueue`, `EmbeddingRetryJob`, `StuckDocumentRecoveryJob`, `V23` migration (`embedding_status`, `parsing` fetch_status). L-1-26 implemented — `UrlNormalizer`, wired in `PolicyUrlFilter`/`LinkDiscoverer`/`CrawlRunStore`, `idx_document_canonical_corpus_unique` in V23. L-1-27 implemented — `FetchOptions` (`ifModifiedSince`/`ifNoneMatch`), `FetchedPage.lastModifiedHttp`/`etagHttp`/`notModified()`, `ConditionalHttpFetcher` 304 handling, `V24__document_http_cache_fields.sql`, `DocumentEntity` cache columns, conditional re-crawl in `CrawlRunStore` + `DocumentFreshnessRefreshService` (304 → update `fetched_at` only, skip parse/chunk/embed).

### L-1-23 — `StatisticalContentGuard`: numeric/% blocks protected from boilerplate stripping 🔴

**Root cause — `containsEn` / `containsKa` false positive on statistical sentences:**

```yaml
# geostat-portal-parse.yaml line 99–109:
containsKa:
  - "საქსტატის ოფიციალური ვებგვერდი"   # contains-anywhere check

containsEn:
  - "official statistics of georgia"     # contains-anywhere check
```

```java
// MarkerBoilerplateStripper.isBoilerplateParagraph():
for (String marker : containsMarkers(markers)) {
    if (normalized.contains(normalize(marker))) {
        return true;   // ← strips ANY paragraph containing this phrase
    }
}
```

**Concrete false positive — statistical sentence destroyed:**

```
Input block:
  "According to the official statistics of Georgia,
   GDP grew by 8.7% in 2024 — highest rate since 2019."

Processing:
  containsEn: "official statistics of georgia"
  → normalized.contains("official statistics of georgia") = true
  → isBoilerplateParagraph() = true
  → block DROPPED ❌

Result in DB: "8.7%" — MISSING. Chat cannot answer "GDP 2024".
```

```
Input block:
  "საქსტატის ოფიციალური ვებგვერდის მიხედვით, 2024 წელს
   საქართველოს მთლიანი შიდა პროდუქტი 8.7%-ით გაიზარდა."

Processing:
  containsKa: "საქსტატის ოფიციალური ვებგვერდი"
  → match → DROPPED ❌
```

**Why this matters specifically for geostat.ge:**
Many news articles and publication pages cite the source as "სტატისტიკის ეროვნული სამსახური" or include the phrase "official statistics of Georgia" in the lead paragraph — the **exact same paragraph** that contains the key statistical figure. `contains`-anywhere stripping silently destroys these blocks.

---

**Resolution — `StatisticalContentGuard`: numeric-bearing blocks are immune to boilerplate stripping:**

**Step 1 — New class `StatisticalContentGuard`:**

შექმენი:
`libs/platform-contracts/src/main/java/com/geostat/platform/parse/StatisticalContentGuard.java`

```java
package com.geostat.platform.parse;

import java.util.regex.Pattern;

/**
 * Guards statistical content blocks from boilerplate stripping.
 *
 * Design contract:
 *   A text block containing statistical data signals (numbers, percentages,
 *   units, years) MUST NOT be stripped by boilerplate markers, regardless of
 *   whether it also contains a boilerplate phrase.
 *
 * Rationale:
 *   "official statistics of georgia" is a boilerplate footer phrase AND
 *   legitimately appears in news article lead paragraphs that contain key
 *   statistical figures. The containsEn marker cannot distinguish these.
 *   The guard resolves the ambiguity: if numbers are present → keep.
 *
 * This is intentionally a final utility class (no inheritance, no state).
 * All logic is in one place for easy audit.
 */
public final class StatisticalContentGuard {

    private StatisticalContentGuard() {}

    /**
     * Primary statistical signal patterns.
     *
     * Matched against the raw (non-lowercased) text to preserve Georgian script.
     * Uses Unicode-aware character classes.
     *
     * Patterns:
     *   1. Percentage:        "8.7%",  "12%",  "0.5%"
     *   2. Georgian units:    "47 მლნ.", "200 ათასი", "3 მილიარდი", "500 ლარი"
     *   3. Year + Georgian:  "2024 წელს", "2025 წელ"
     *   4. Decimal number:   "1.2",  "3,450"  (locale-aware separators)
     *   5. Standalone int:   any 2+ digit number — "15", "200", "1 200"
     *   6. English units:    "million", "billion", "thousand", "GEL", "USD"
     *   7. Ordinal/rank:     "#1", "1st", "2nd"
     */
    private static final Pattern STAT_PATTERN = Pattern.compile(
        // 1. percentage
        "\\d+[.,]?\\d*\\s*%" +
        // 2. Georgian numeric units
        "|\\d+[.,]?\\d*\\s*(მლნ|ათასი|მილ|ლარი|ტ\\.?\\s*ც|ათ\\.\\s*ადამ)" +
        // 3. year + Georgian word
        "|\\d{4}\\s*წელ" +
        // 4. decimal number (both . and , as separator)
        "|\\d{1,3}([.,]\\d{3})+" +   // thousand-grouped: 1,234 or 1.234
        "|\\d+[.,]\\d+" +             // simple decimal: 8.7 or 8,7
        // 5. standalone integer 2+ digits
        "|\\b\\d{2,}\\b" +
        // 6. English units after number
        "|\\d+[.,]?\\d*\\s*(million|billion|thousand|GEL|USD|EUR)" +
        // 7. ordinal / rank
        "|#\\d+|\\d+(st|nd|rd|th)\\b",
        Pattern.UNICODE_CHARACTER_CLASS
    );

    /**
     * Returns true if the text block contains statistical data signals.
     *
     * When true: {@link com.geostat.platform.parse.BoilerplateStripper}
     * implementations MUST return {@code false} from {@code isBoilerplateParagraph()}
     * for this block, regardless of marker matches.
     *
     * @param text raw extracted text block (after TextSanitizer, before normalization)
     */
    public static boolean isStatisticalContent(String text) {
        if (text == null || text.isBlank()) return false;
        return STAT_PATTERN.matcher(text).find();
    }
}
```

**Step 2 — `MarkerBoilerplateStripper.isBoilerplateParagraph()`: guard as first check:**

```java
// MarkerBoilerplateStripper.java:

@Override
public boolean isBoilerplateParagraph(String paragraph, ParseProfile profile) {
    if (paragraph == null || paragraph.isBlank()) return true;

    // ── GUARD: statistical content is NEVER boilerplate ──────────────────────
    // Must run BEFORE any marker check.
    // A block with "official statistics of georgia" AND "8.7%" is real content.
    // A block with only "official statistics of georgia" (no numbers) = footer.
    if (StatisticalContentGuard.isStatisticalContent(paragraph)) {
        return false;
    }
    // ─────────────────────────────────────────────────────────────────────────

    String normalized = normalize(paragraph);
    if (normalized.startsWith("×")) return true;

    BoilerplateMarkers markers = profile.boilerplateMarkers();

    for (String marker : startsWithMarkers(markers)) {
        if (marker.isBlank()) continue;
        if (normalized.startsWith(normalize(marker))) return true;
    }
    for (String marker : containsMarkers(markers)) {
        if (marker.isBlank()) continue;
        if (normalized.contains(normalize(marker))) return true;
    }
    return false;
}
```

**Step 3 — same guard in `legacyClean` hardcoded boilerplate check (L-1-22):**

```java
// HtmlContentCleaner.isLegacyBoilerplate():
private static boolean isLegacyBoilerplate(String text) {
    // statistical content is immune — same rule as profile-driven path
    if (StatisticalContentGuard.isStatisticalContent(text)) return false;

    String lower = text.toLowerCase();
    for (String s : LEGACY_BOILERPLATE_STARTS)   if (lower.startsWith(s)) return true;
    for (String s : LEGACY_BOILERPLATE_CONTAINS) if (lower.contains(s))   return true;
    return false;
}
```

---

**Unit tests:**

```java
// StatisticalContentGuardTest:

@Test
void isStatisticalContent_true_forPercentage() {
    assertThat(StatisticalContentGuard.isStatisticalContent("GDP grew by 8.7% in 2024")).isTrue();
    assertThat(StatisticalContentGuard.isStatisticalContent("0.5% ზრდა")).isTrue();
    assertThat(StatisticalContentGuard.isStatisticalContent("100%")).isTrue();
}

@Test
void isStatisticalContent_true_forGeorgianUnits() {
    assertThat(StatisticalContentGuard.isStatisticalContent("47 მლნ. ლარი")).isTrue();
    assertThat(StatisticalContentGuard.isStatisticalContent("200 ათასი კაცი")).isTrue();
    assertThat(StatisticalContentGuard.isStatisticalContent("3 მილიარდი")).isTrue();
}

@Test
void isStatisticalContent_true_forYearWithGeorgianWord() {
    assertThat(StatisticalContentGuard.isStatisticalContent("2024 წელს გაიზარდა")).isTrue();
    assertThat(StatisticalContentGuard.isStatisticalContent("2019 წლის მაჩვენებელი")).isTrue();
}

@Test
void isStatisticalContent_true_forDecimalNumber() {
    assertThat(StatisticalContentGuard.isStatisticalContent("მაჩვენებელი: 1.2")).isTrue();
    assertThat(StatisticalContentGuard.isStatisticalContent("3,450 ათასი")).isTrue();
}

@Test
void isStatisticalContent_true_forStandaloneInteger() {
    assertThat(StatisticalContentGuard.isStatisticalContent("სულ 15 ქვეყანა")).isTrue();
    assertThat(StatisticalContentGuard.isStatisticalContent("200 document ingested")).isTrue();
}

@Test
void isStatisticalContent_false_forPureBoilerplateFooter() {
    // no numbers → guard does NOT protect → boilerplate markers can strip it
    assertThat(StatisticalContentGuard.isStatisticalContent(
        "official statistics of georgia")).isFalse();
    assertThat(StatisticalContentGuard.isStatisticalContent(
        "საქსტატის ოფიციალური ვებგვერდი")).isFalse();
    assertThat(StatisticalContentGuard.isStatisticalContent(
        "Crafted by Agency")).isFalse();
    assertThat(StatisticalContentGuard.isStatisticalContent(
        "უკან დაბრუნება")).isFalse();
}

@Test
void isStatisticalContent_false_forNullAndBlank() {
    assertThat(StatisticalContentGuard.isStatisticalContent(null)).isFalse();
    assertThat(StatisticalContentGuard.isStatisticalContent("")).isFalse();
    assertThat(StatisticalContentGuard.isStatisticalContent("   ")).isFalse();
}

// MarkerBoilerplateStripperTest — integration:

@Test
void isBoilerplateParagraph_false_when_statisticalSentenceContainsBoilerplatePhrase() {
    // The critical false-positive scenario
    String block = "According to the official statistics of Georgia, " +
                   "GDP grew by 8.7% in 2024.";
    // without guard: containsEn match → would return true (wrongly stripped)
    // with guard: 8.7% detected → returns false (correctly kept)
    assertThat(stripper.isBoilerplateParagraph(block, enProfile)).isFalse();
}

@Test
void isBoilerplateParagraph_false_for_georgianStatSentenceWithOfficialPhrase() {
    String block = "საქსტატის ოფიციალური ვებგვერდის მიხედვით, " +
                   "2024 წელს მთლიანი შიდა პროდუქტი 8.7%-ით გაიზარდა.";
    assertThat(stripper.isBoilerplateParagraph(block, kaProfile)).isFalse();
}

@Test
void isBoilerplateParagraph_true_for_pureboilerplateFooterWithNoNumbers() {
    // footer: no numbers → guard does NOT activate → marker strips it
    assertThat(stripper.isBoilerplateParagraph(
        "official statistics of georgia", enProfile)).isTrue();
    assertThat(stripper.isBoilerplateParagraph(
        "საქსტატის ოფიციალური ვებგვერდი", kaProfile)).isTrue();
}

@Test
void isBoilerplateParagraph_true_for_uiLabels() {
    // classic UI boilerplate — no numbers, no stats
    assertThat(stripper.isBoilerplateParagraph("სრულად ნახვა", kaProfile)).isTrue();
    assertThat(stripper.isBoilerplateParagraph("უკან დაბრუნება", kaProfile)).isTrue();
    assertThat(stripper.isBoilerplateParagraph("Read more", enProfile)).isTrue();
}

@Test
void stripFromBody_keepsBlock_containingOfficialPhraseAndPercentage() {
    String text = "სრულად ნახვა\n\n" +
        "საქსტატის ოფიციალური ვებგვერდის მიხედვით, GDP 8.7%-ით გაიზარდა.\n\n" +
        "უკან დაბრუნება";
    String result = stripper.stripFromBody(text, kaProfile);
    // leading + trailing boilerplate stripped; middle statistical block kept
    assertThat(result).doesNotContain("სრულად ნახვა");
    assertThat(result).doesNotContain("უკან დაბრუნება");
    assertThat(result).contains("8.7%-ით");
    assertThat(result).contains("GDP");
}
```

**ფაილები:**
- New: `libs/platform-contracts/src/main/java/com/geostat/platform/parse/StatisticalContentGuard.java`
- Update: `apps/ingestion-service/.../parse/profile/MarkerBoilerplateStripper.java` — guard as first check in `isBoilerplateParagraph()`
- Update: `apps/ingestion-service/.../parse/HtmlContentCleaner.java` — same guard in `isLegacyBoilerplate()`

**Acceptance criteria:**
- Block containing `%` AND boilerplate phrase → KEPT (never stripped).
- Block containing Georgian unit (მლნ., ათასი, ლარი) → KEPT.
- Block containing year `20XX წელ` → KEPT.
- Pure boilerplate footer (no numbers) → still stripped correctly.
- Short UI labels (სრულად, უკან, Read more) → no numbers → stripped correctly.
- `StatisticalContentGuard.isStatisticalContent(null)` → `false` (no NPE).
- Regression: all existing `MarkerBoilerplateStripperTest` tests pass (guard only broadens protection, never tightens).

---

---

### L-1-24 — `chunk.nav_breadcrumb`: propagate from document at insert time 🔴

**Root cause — missing propagation:**

```
L-1-17 adds: document.nav_breadcrumb = "სტატისტიკა > მოსახლეობა > ბუნებრივი მოძრაობა"
L-1-09 adds: chunk INSERT per document

BUT: chunk table has no nav_breadcrumb column.
     chunk INSERT does not copy it from document.

RAG retrieval path:
  Qdrant: returns chunk_id (vector search)
  Backend: loads chunk row → metadata for context
  chunk.nav_breadcrumb = NULL → DerivedCatalogReader cannot filter by topic path
  Chat: "მოსახლეობის" query → correct document found → wrong topic cluster assigned
```

**Impact:**
- Topic cluster assignment in `DerivedCatalogResponseAssembler` relies on nav_breadcrumb path to match against `geostat-portal-topic-catalog.yaml` slugs.
- Without `chunk.nav_breadcrumb`: all chunks = "unknown" topic → poor catalog navigation in chat response.
- Qdrant metadata filter by topic (`where: {"nav_breadcrumb": {"$like": "%მოსახლეობა%"}}`) = impossible.

**Resolution:**

**Step 1 — V20 migration: `chunk.nav_breadcrumb` column:**

```sql
-- V20 (add to existing V20 migration file):
ALTER TABLE ingestion.chunk
  ADD COLUMN IF NOT EXISTS nav_breadcrumb TEXT;

COMMENT ON COLUMN ingestion.chunk.nav_breadcrumb IS
  'Navigation breadcrumb copied from parent document at chunk insert time.
   Example: "სტატისტიკა > მოსახლეობა > ბუნებრივი მოძრაობა"
   Propagated by ChunkingService.createChunks() — not re-read from HTML.
   Used by: DerivedCatalogResponseAssembler, Qdrant metadata filter.';

-- Backfill for existing chunks from parent document:
UPDATE ingestion.chunk c
SET nav_breadcrumb = d.nav_breadcrumb
FROM ingestion.document d
WHERE c.document_id = d.id
  AND c.nav_breadcrumb IS NULL
  AND d.nav_breadcrumb IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_chunk_nav_breadcrumb
  ON ingestion.chunk (corpus_id, nav_breadcrumb)
  WHERE nav_breadcrumb IS NOT NULL;
```

**Step 2 — `ChunkingService.createChunks()`: copy nav_breadcrumb from document:**

`apps/ingestion-service/src/main/java/com/geostat/ingestion/chunk/ChunkingService.java`

```java
public List<Chunk> createChunks(Document document, CleanedDocument cleaned) {
    List<String> textBlocks = splitter.split(cleaned.bodyText(), chunkingProps);
    List<Chunk> chunks = new ArrayList<>();

    for (int i = 0; i < textBlocks.size(); i++) {
        String block = textBlocks.get(i);
        Chunk chunk = Chunk.builder()
            .id(UUID.randomUUID())
            .documentId(document.getId())
            .corpusId(document.getCorpusId())
            .chunkIndex(i)
            .content(block)
            .contentHash(ChunkHasher.sha256(block))
            .sectionPath(document.getSectionPath())   // existing
            .navBreadcrumb(document.getNavBreadcrumb()) // ← NEW: copy at creation time
            .language(document.getLanguage())
            .pageKind(document.getPageKind())
            .embeddingStatus(EmbeddingStatus.PENDING)
            .build();
        chunks.add(chunk);
    }
    return chunks;
}
```

**Step 3 — Qdrant payload: include `nav_breadcrumb` in vector metadata:**

```java
// QdrantEmbeddingService.embed():
Map<String, Object> payload = new HashMap<>();
payload.put("chunk_id",       chunk.getId().toString());
payload.put("document_id",    chunk.getDocumentId().toString());
payload.put("corpus_id",      chunk.getCorpusId().toString());
payload.put("language",       chunk.getLanguage());
payload.put("page_kind",      chunk.getPageKind());
payload.put("nav_breadcrumb", chunk.getNavBreadcrumb()); // ← NEW
payload.put("section_path",   String.join(" > ", chunk.getSectionPath()));
```

**Unit tests:**

```java
// ChunkingServiceTest:

@Test
void createChunks_propagatesNavBreadcrumb_fromDocument() {
    Document document = documentWithNavBreadcrumb(
        "სტატისტიკა > მოსახლეობა > ბუნებრივი მოძრაობა");
    CleanedDocument cleaned = cleanedDocumentWith("ბუნებრივი მოძრაობა 2024 წელს...");

    List<Chunk> chunks = service.createChunks(document, cleaned);

    assertThat(chunks).isNotEmpty();
    assertThat(chunks).allMatch(c ->
        "სტატისტიკა > მოსახლეობა > ბუნებრივი მოძრაობა".equals(c.getNavBreadcrumb()));
}

@Test
void createChunks_navBreadcrumb_isNull_whenDocumentHasNone() {
    Document document = documentWithNavBreadcrumb(null);
    List<Chunk> chunks = service.createChunks(document, anyCleanedDoc());
    assertThat(chunks).allMatch(c -> c.getNavBreadcrumb() == null);
}
```

**ფაილები:**
- V20 migration — `chunk.nav_breadcrumb` column + backfill + index
- Update: `apps/ingestion-service/.../chunk/ChunkingService.java` — copy `navBreadcrumb`
- Update: `apps/ingestion-service/.../vector/QdrantEmbeddingService.java` — add to Qdrant payload

**Acceptance criteria:**
- All new chunks: `nav_breadcrumb` = parent document's `nav_breadcrumb`.
- Existing chunks: backfilled by V20 migration.
- Qdrant payload includes `nav_breadcrumb` field.
- `DerivedCatalogResponseAssembler` can filter chunks by `nav_breadcrumb LIKE '%მოსახლეობა%'`.
- `nav_breadcrumb = NULL` chunks: no error — null-safe in all downstream logic.

---

### L-1-25 — Parse → chunk → embed: transaction boundary + retry guarantee 🔴

**Root cause — three separate operations, no atomicity:**

```
DocumentIngestionPipeline current flow:

  STEP 1: documentRepository.save(document)   ← TX-1 committed
  STEP 2: chunkRepository.saveAll(chunks)      ← TX-2 committed separately
  STEP 3: qdrantService.embed(chunks)          ← external HTTP call, no TX

Failure A: TX-1 ok, TX-2 fails
  → document row: fetch_status='parsed', chunks=0
  → on re-run: duplicate document check by canonical_url → skip
  → chunks NEVER created → document never embedded → PERMANENT DATA LOSS

Failure B: TX-1+TX-2 ok, Qdrant HTTP fails
  → DB: chunks with embedding_status='pending'
  → Qdrant: nothing
  → re-run: same URL → "already parsed" → skip
  → chunks stuck in 'pending' forever

Failure C: service restart mid-pipeline
  → document: fetch_status='parsing' (intermediate state never cleaned)
  → stuck documents — never retried
```

**Resolution — three-step fix:**

**Step 1 — `document + chunks` in ONE transaction:**

```java
// DocumentIngestionPipeline.java:

@Transactional  // document + chunks committed atomically
public IngestionOutcome ingestDocument(FetchedPage page, CrawlJob job) {
    // dedup check inside transaction
    Optional<Document> existing = documentRepository
        .findByCanonicalUrlAndCorpusId(page.canonicalUrl(), job.corpusId());

    // if same raw_html_hash → update last_seen_at, return SKIPPED
    if (existing.isPresent()) {
        String existingHash = existing.get().getRawHtmlHash();
        String newHash      = HashUtils.sha256(page.html());
        if (newHash.equals(existingHash)) {
            documentRepository.updateLastSeenAt(existing.get().getId());
            return IngestionOutcome.skipped(existing.get().getId());
        }
    }

    // fetch_status = 'parsing' — marks in-progress, cleaned up by recovery job
    Document document = buildDocument(page, job)
        .withFetchStatus(FetchStatus.PARSING);
    documentRepository.save(document);

    // parse + chunk (both in same TX)
    CleanedDocument cleaned = contentExtractor.extract(
        new HtmlPageInput(page.html(), page.canonicalUrl()),
        profileLoader.profileFor(job.corpusName()));

    ValidationOutcome validation = validationPipeline.validate(cleaned);
    document = document
        .withCleanedContent(cleaned)
        .withQualityScore(validation.quality())
        .withValidationViolations(validation.violations())
        .withFetchStatus(FetchStatus.PARSED);  // ← set PARSED only after chunks ready
    documentRepository.save(document);  // update in same TX

    List<Chunk> chunks = chunkingService.createChunks(document, cleaned);
    chunkRepository.saveAll(chunks);   // same TX
    // TX committed here — both document(PARSED) and chunks exist or neither does

    // embedding is OUTSIDE transaction — async, retriable
    embeddingQueue.enqueue(chunks.stream().map(Chunk::getId).toList());

    return IngestionOutcome.ingested(document.getId(), chunks.size());
}
```

**Step 2 — `EmbeddingRetryJob`: cleans up stuck `pending` chunks:**

```java
// EmbeddingRetryJob.java:
@Component
public class EmbeddingRetryJob {

    /**
     * Finds chunks that have been in embedding_status='pending' for > 10 minutes
     * (Qdrant call must have failed or was never attempted).
     * Re-enqueues them for embedding.
     *
     * Scheduled: every 15 minutes.
     * Safe to re-run: QdrantEmbeddingService uses upsert (not insert).
     */
    @Scheduled(fixedDelayString = "${ingestion.embedding.retry-interval-ms:900000}")
    public void retryStuckChunks() {
        List<UUID> stuckIds = jdbc.queryForList("""
            SELECT id FROM ingestion.chunk
            WHERE embedding_status = 'pending'
              AND created_at < now() - INTERVAL '10 minutes'
            LIMIT 500
            """, UUID.class);

        if (stuckIds.isEmpty()) return;

        log.warn("[embedding-retry] {} chunks stuck in 'pending' — re-enqueueing",
            stuckIds.size());
        embeddingQueue.enqueueAll(stuckIds);
    }
}
```

**Step 3 — `StuckDocumentRecoveryJob`: cleans up `fetch_status='parsing'` orphans:**

```java
// StuckDocumentRecoveryJob.java:
@Component
public class StuckDocumentRecoveryJob {

    /**
     * Documents stuck in fetch_status='parsing' for > 30 minutes
     * = pipeline crashed mid-flight. Reset to 'pending' for re-crawl.
     *
     * Scheduled: every 30 minutes.
     */
    @Scheduled(fixedDelayString = "${ingestion.recovery.interval-ms:1800000}")
    public void recoverStuckDocuments() {
        int count = jdbc.update("""
            UPDATE ingestion.document
            SET fetch_status = 'pending',
                updated_at   = now()
            WHERE fetch_status = 'parsing'
              AND updated_at < now() - INTERVAL '30 minutes'
            """);
        if (count > 0) {
            log.warn("[stuck-recovery] Reset {} documents from 'parsing' to 'pending'",
                count);
        }
    }
}
```

**Unit tests:**

```java
// DocumentIngestionPipelineTest:

@Test
void ingest_documentAndChunks_committedAtomically() {
    // given: Qdrant embed throws → but document+chunks already committed
    doThrow(new RuntimeException("Qdrant down"))
        .when(embeddingQueue).enqueue(any());

    // when
    IngestionOutcome outcome = pipeline.ingestDocument(fetchedPage, job);

    // then: document + chunks in DB despite embedding failure
    assertThat(documentRepository.findById(outcome.documentId())).isPresent();
    assertThat(chunkRepository.findByDocumentId(outcome.documentId())).isNotEmpty();
    // embedding retry job will pick these up
}

@Test
void ingest_rollsBack_document_and_chunks_onChunkSaveFailure() {
    doThrow(new DataIntegrityViolationException("chunk constraint"))
        .when(chunkRepository).saveAll(any());

    assertThatThrownBy(() -> pipeline.ingestDocument(fetchedPage, job))
        .isInstanceOf(DataIntegrityViolationException.class);
    // document must NOT exist (rolled back atomically)
    assertThat(documentRepository.findByCanonicalUrlAndCorpusId(
        fetchedPage.canonicalUrl(), job.corpusId())).isEmpty();
}

// EmbeddingRetryJobTest:
@Test
void retryJob_reenqueues_stuckPendingChunks() {
    // given: 3 chunks pending for > 10 min
    // when
    retryJob.retryStuckChunks();
    // then: enqueue called with those 3 IDs
    verify(embeddingQueue).enqueueAll(argThat(ids -> ids.size() == 3));
}
```

**ფაილები:**
- Update: `apps/ingestion-service/.../DocumentIngestionPipeline.java` — `@Transactional`, atomic doc+chunk
- New: `apps/ingestion-service/.../embed/EmbeddingRetryJob.java`
- New: `apps/ingestion-service/.../recovery/StuckDocumentRecoveryJob.java`

**Acceptance criteria:**
- `document` + `chunks` always exist together or neither does (atomic TX).
- Qdrant failure → no data loss in DB; retry job picks up within 15 min.
- `fetch_status='parsing'` cleaned up within 30 min by recovery job.
- Re-crawl same URL + same hash → `last_seen_at` updated, no re-parse.
- Re-crawl same URL + changed hash → full pipeline runs, old chunks replaced.

---

### L-1-26 — URL normalization: `utm_*`, `fbclid`, tracking params → dedup 🟠

**Root cause:**

```
Same article — 3 different URLs enqueued by crawler:
  https://www.geostat.ge/ka/single-news/1234
  https://www.geostat.ge/ka/single-news/1234?utm_source=facebook&utm_medium=post
  https://www.geostat.ge/ka/single-news/1234?fbclid=IwAR3xKjq...

PolicyUrlFilter.shouldEnqueue() deduplicates by raw URL → all 3 pass
→ 3 document rows, 3×chunks, 3×vectors
→ Chat: same article appears 3 times as "different" sources
```

**Resolution — `UrlNormalizer` utility:**

**Step 1 — New `UrlNormalizer` class:**

`apps/ingestion-service/src/main/java/com/geostat/ingestion/crawl/UrlNormalizer.java`

```java
package com.geostat.ingestion.crawl;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Normalizes crawled URLs to a canonical form before deduplication.
 *
 * Removes tracking, session, and UI-only query parameters that do not
 * affect content identity. Applied in two places:
 *   1. PolicyUrlFilter.shouldEnqueue()  — before adding to frontier
 *   2. DocumentIngestionPipeline        — before canonical_url storage
 *
 * This ensures:
 *   - Same article shared via Facebook / Google Ads → 1 document
 *   - Language toggle links (?lang=en) do not create duplicates
 *     when URL segment (/ka/, /en/) already encodes language
 */
public final class UrlNormalizer {

    private UrlNormalizer() {}

    // Parameters that never affect page content identity
    private static final Set<String> STRIP_PARAMS = Set.of(
        // UTM tracking
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
        // Social media tracking
        "fbclid", "gclid", "msclkid", "twclid", "igshid",
        // Referrer / share
        "ref", "referrer", "share",
        // UI / display toggles (content determined by URL path, not param)
        "lang",
        // Analytics / AB test IDs
        "mc_cid", "mc_eid", "_ga", "wickedsource"
    );

    /**
     * Returns the canonical form of the URL:
     *   - Strips tracking query parameters
     *   - Removes fragment (#anchor) — always UI-only
     *   - Removes trailing slash from path (except root "/")
     *   - Lowercases scheme and host
     *
     * Returns the original URL string if normalization fails (parse error).
     */
    public static String normalize(String url) {
        if (url == null || url.isBlank()) return url;
        try {
            URI uri = new URI(url).normalize();
            String query = buildCleanQuery(uri.getQuery());
            URI normalized = new URI(
                uri.getScheme().toLowerCase(),
                null,
                uri.getHost().toLowerCase(),
                uri.getPort(),
                normalizePath(uri.getPath()),
                query,
                null  // strip fragment
            );
            return normalized.toString();
        } catch (URISyntaxException e) {
            return url; // safe fallback — unparseable URL unchanged
        }
    }

    private static String buildCleanQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) return null;
        String[] pairs = rawQuery.split("&");
        StringBuilder clean = new StringBuilder();
        for (String pair : pairs) {
            String key = pair.split("=", 2)[0].toLowerCase();
            if (!STRIP_PARAMS.contains(key)) {
                if (clean.length() > 0) clean.append('&');
                clean.append(pair);
            }
        }
        return clean.isEmpty() ? null : clean.toString();
    }

    private static String normalizePath(String path) {
        if (path == null || path.equals("/")) return "/";
        return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }
}
```

**Step 2 — `PolicyUrlFilter.shouldEnqueue()`: normalize before dedup:**

```java
@Override
public boolean shouldEnqueue(String url, String referrer) {
    String normalized = UrlNormalizer.normalize(url);
    if (visitedUrls.contains(normalized)) return false;
    // ... other policy checks ...
    visitedUrls.add(normalized);
    return true;
}
```

**Step 3 — `DocumentIngestionPipeline`: store normalized URL as canonical:**

```java
String canonicalUrl = UrlNormalizer.normalize(fetchedPage.canonicalUrl());
Document document = buildDocument(fetchedPage, job)
    .withCanonicalUrl(canonicalUrl);
```

**Step 4 — V20 migration: UNIQUE constraint on normalized canonical_url per corpus:**

```sql
-- V20: prevent duplicate documents from tracking-param URL variants
-- First: normalize existing canonical_urls (one-time backfill via Java job, not SQL)
-- Then: add unique constraint

CREATE UNIQUE INDEX IF NOT EXISTS idx_document_canonical_corpus_unique
  ON ingestion.document (corpus_id, canonical_url)
  WHERE fetch_status != 'stale';
-- WHERE clause: stale docs excluded so historical data doesn't block new inserts
```

**Unit tests:**

```java
// UrlNormalizerTest:

@Test
void normalize_stripsUtmParams() {
    String url = "https://www.geostat.ge/ka/news/1234?utm_source=facebook&utm_medium=post";
    assertThat(UrlNormalizer.normalize(url))
        .isEqualTo("https://www.geostat.ge/ka/news/1234");
}

@Test
void normalize_stripsFbclid() {
    String url = "https://www.geostat.ge/ka/news/1234?fbclid=IwAR3xKjq...";
    assertThat(UrlNormalizer.normalize(url))
        .isEqualTo("https://www.geostat.ge/ka/news/1234");
}

@Test
void normalize_preservesContentParams() {
    // page= is content-affecting — must NOT be stripped
    String url = "https://www.geostat.ge/ka/news?page=2&category=population";
    assertThat(UrlNormalizer.normalize(url))
        .isEqualTo("https://www.geostat.ge/ka/news?page=2&category=population");
}

@Test
void normalize_stripsFragment() {
    assertThat(UrlNormalizer.normalize("https://www.geostat.ge/ka/page#section2"))
        .isEqualTo("https://www.geostat.ge/ka/page");
}

@Test
void normalize_removesTrailingSlash() {
    assertThat(UrlNormalizer.normalize("https://www.geostat.ge/ka/page/"))
        .isEqualTo("https://www.geostat.ge/ka/page");
}

@Test
void normalize_preservesRoot() {
    assertThat(UrlNormalizer.normalize("https://www.geostat.ge/"))
        .isEqualTo("https://www.geostat.ge/");
}

@Test
void normalize_handlesNullAndBlank() {
    assertThat(UrlNormalizer.normalize(null)).isNull();
    assertThat(UrlNormalizer.normalize("")).isBlank();
}
```

**ფაილები:**
- New: `apps/ingestion-service/.../crawl/UrlNormalizer.java`
- Update: `apps/ingestion-service/.../parse/profile/PolicyUrlFilter.java` — normalize before dedup
- Update: `apps/ingestion-service/.../DocumentIngestionPipeline.java` — normalize canonical_url
- V20 migration — `UNIQUE INDEX` on `(corpus_id, canonical_url)`

**Acceptance criteria:**
- Same article URL with `?utm_source=X` and without → 1 document row (UNIQUE conflict → skip second).
- `?fbclid=` stripped before frontier dedup → never crawled twice.
- `?page=2` preserved (content-affecting param).
- Fragment `#section` stripped from canonical_url.
- Existing canonical_urls with tracking params: backfill job normalizes before UNIQUE index.

---

### L-1-27 — `If-Modified-Since` / `ETag`: skip unchanged pages at HTTP level 🟠

**Root cause — inefficient full re-download on every crawl:**

```
Current incremental crawl flow:
  1. Fetch full HTML (200 response + body)  ← network download always
  2. Compute SHA-256(html)
  3. Compare with document.raw_html_hash
  4. If equal → skip extraction           ← too late: HTML already downloaded

Optimal flow:
  1. Send If-Modified-Since: <document.last_modified_http>
  2. Server returns 304 Not Modified (0 bytes body)  ← 60-90% of re-crawled pages
  3. Update last_seen_at only
  → Bandwidth saved, crawl faster, server load reduced
```

**Resolution:**

**Step 1 — V20 migration: `document.last_modified_http` column:**

```sql
-- V20:
ALTER TABLE ingestion.document
  ADD COLUMN IF NOT EXISTS last_modified_http TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS etag_http          TEXT;

COMMENT ON COLUMN ingestion.document.last_modified_http IS
  'Value of HTTP Last-Modified header from last successful fetch.
   Used as If-Modified-Since on next crawl.';

COMMENT ON COLUMN ingestion.document.etag_http IS
  'Value of HTTP ETag header from last successful fetch.
   Used as If-None-Match on next crawl. Preferred over Last-Modified.';
```

**Step 2 — `FetchedPage` record: add HTTP cache fields:**

```java
// FetchedPage.java (ARCH-01):
public record FetchedPage(
    String   url,
    String   finalUrl,
    String   html,
    int      httpStatus,         // 200 | 304 | 301 | 404 etc.
    String   contentType,
    RenderMode renderMode,
    String   lastModifiedHttp,   // ← NEW: value of Last-Modified response header
    String   etagHttp            // ← NEW: value of ETag response header
) {
    /** True if server confirmed content is unchanged (304 Not Modified). */
    public boolean notModified() { return httpStatus == 304; }

    public String canonicalUrl() {
        return finalUrl != null && !finalUrl.equals(url) ? finalUrl : url;
    }
}
```

**Step 3 — `FetchOptions`: send conditional headers on re-crawl:**

```java
// FetchOptions.java:
public record FetchOptions(
    RenderMode    renderMode,
    int           timeoutMs,
    String        userAgent,
    NetworkPolicy networkPolicy,
    String        ifModifiedSince,  // ← NEW: from document.last_modified_http
    String        ifNoneMatch       // ← NEW: from document.etag_http
) {}
```

**Step 4 — `Crawler4jStaticPageFetcher`: set conditional headers, handle 304:**

```java
@Override
public FetchedPage fetch(String url, FetchOptions options) throws PageFetchException {
    HttpGet request = new HttpGet(url);

    // Set conditional headers if available (re-crawl optimization)
    if (options.ifNoneMatch() != null) {
        request.setHeader("If-None-Match", options.ifNoneMatch());
    } else if (options.ifModifiedSince() != null) {
        request.setHeader("If-Modified-Since", options.ifModifiedSince());
    }

    HttpResponse response = httpClient.execute(request);
    int status = response.getStatusLine().getStatusCode();

    // 304: content unchanged — return minimal FetchedPage, no body
    if (status == 304) {
        return new FetchedPage(url, url, null, 304, null, RenderMode.STATIC, null, null);
    }

    String html         = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
    String lastModified = headerValue(response, "Last-Modified");
    String etag         = headerValue(response, "ETag");

    return new FetchedPage(url, finalUrl, html, status,
        contentType, RenderMode.STATIC, lastModified, etag);
}
```

**Step 5 — `DocumentIngestionPipeline`: handle 304:**

```java
// Before extraction:
if (fetchedPage.notModified()) {
    documentRepository.updateLastSeenAt(
        UrlNormalizer.normalize(fetchedPage.url()), job.corpusId());
    metrics.increment("documents.not_modified");
    return IngestionOutcome.notModified();
}
// ... normal pipeline ...
// After successful parse, save last_modified_http + etag_http:
document = document
    .withLastModifiedHttp(fetchedPage.lastModifiedHttp())
    .withEtagHttp(fetchedPage.etagHttp());
```

**Step 6 — `CrawlPipeline`: pass stored values into FetchOptions for re-crawl:**

```java
// When re-crawling a known URL:
Optional<Document> existing = documentRepository.findByCanonicalUrl(url, corpusId);
FetchOptions opts = new FetchOptions(
    profile.renderMode(), 30_000, "GeostatBot/1.0",
    networkPolicy,
    existing.map(Document::getLastModifiedHttp).orElse(null),  // If-Modified-Since
    existing.map(Document::getEtagHttp).orElse(null)           // If-None-Match
);
```

**Unit tests:**

```java
// Crawler4jStaticPageFetcherTest:

@Test
void fetch_sends_ifNoneMatch_header_whenEtagPresent() {
    FetchOptions opts = optsWith(null, "\"abc123\"");
    fetcher.fetch("https://x.ge/page", opts);
    verify(httpClient).execute(argThat(req ->
        "\"abc123\"".equals(req.getFirstHeader("If-None-Match").getValue())));
}

@Test
void fetch_returns_notModified_on_304() {
    mockHttpResponse(304, null);
    FetchedPage result = fetcher.fetch("https://x.ge/page", anyOpts());
    assertThat(result.notModified()).isTrue();
    assertThat(result.html()).isNull();
}

// DocumentIngestionPipelineTest:
@Test
void ingest_skips_pipeline_on_304() {
    FetchedPage page = notModifiedPage();
    IngestionOutcome outcome = pipeline.ingestDocument(page, job);
    assertThat(outcome.type()).isEqualTo(OutcomeType.NOT_MODIFIED);
    verify(contentExtractor, never()).extract(any(), any());
    verify(documentRepository).updateLastSeenAt(any(), any());
}
```

**ფაილები:**
- Update: `libs/platform-contracts/.../crawl/FetchedPage.java` — `lastModifiedHttp`, `etagHttp`, `notModified()`
- Update: `libs/platform-contracts/.../crawl/FetchOptions.java` — `ifModifiedSince`, `ifNoneMatch`
- Update: `apps/ingestion-service/.../crawl/fetch/Crawler4jStaticPageFetcher.java` — conditional headers + 304 handling
- Update: `apps/ingestion-service/.../DocumentIngestionPipeline.java` — 304 early exit + save HTTP cache headers
- V20 migration — `document.last_modified_http`, `document.etag_http`

**Acceptance criteria:**
- Re-crawl of unchanged page: server returns 304 → pipeline exits early, `documents.not_modified` counter incremented.
- `ETag` preferred over `Last-Modified` when both present.
- 304 response: `last_seen_at` updated, `last_modified_http` / `etag_http` unchanged.
- 200 response: `last_modified_http` / `etag_http` updated from response headers.
- First crawl (no stored ETag): normal 200 flow, no conditional header sent.

---

### L-1-28 — `<meta name="robots" content="noindex">` → `REJECT` quality 🟠

**Root cause:**

```
robots.txt: site-level crawl rules → crawler4j respects ✅
<meta name="robots" content="noindex">: page-level directive → NOT checked ❌

geostat.ge potential noindex pages:
  - Draft publications not yet officially released
  - Internal admin preview pages
  - Pagination variants (?page=2 alternate)
  - Staging preview links shared externally

These pages crawled + parsed + embedded → appear in chat results
= serving unpublished/draft statistical data to users
```

**Resolution:**

**Step 1 — `NoindexDetector` in parse layer:**

`apps/ingestion-service/src/main/java/com/geostat/ingestion/parse/NoindexDetector.java`

```java
public final class NoindexDetector {

    private NoindexDetector() {}

    /**
     * Returns true if the page has a robots noindex directive.
     *
     * Checks (in priority order):
     *   1. <meta name="robots" content="noindex">
     *   2. <meta name="robots" content="noindex,nofollow">
     *   3. X-Robots-Tag HTTP header (passed via FetchedPage metadata)
     *
     * Case-insensitive. Checks both "robots" and "googlebot" named meta tags.
     */
    public static boolean isNoindex(Document html) {
        for (Element meta : html.select("meta[name~=(?i)robots], meta[name~=(?i)googlebot]")) {
            String content = meta.attr("content").toLowerCase();
            if (content.contains("noindex")) {
                return true;
            }
        }
        return false;
    }
}
```

**Step 2 — `JsoupContentExtractor.extract()`: check before extraction:**

```java
@Override
public CleanedDocument extract(HtmlPageInput page, ParseProfile profile) {
    Document html = Jsoup.parse(page.html(), page.canonicalUrl());

    // noindex check — before any extraction work (fast-exit)
    if (NoindexDetector.isNoindex(html)) {
        log.debug("[noindex] page skipped: {}", page.canonicalUrl());
        return CleanedDocument.noindexMarker(page.canonicalUrl());
        // noindexMarker: minimal record with pageKind="noindex", body=""
    }

    // ... normal extraction flow ...
}
```

**Step 3 — `CleanedDocument`: static factory for noindex case:**

```java
// CleanedDocument.java:
public static CleanedDocument noindexMarker(String url) {
    return new CleanedDocument(
        "", "", "ka", List.of(), null, null, null,
        0, 0, null, null, "noindex"  // pageKind = "noindex"
    );
}

public boolean isNoindex() {
    return "noindex".equals(pageKind());
}
```

**Step 4 — `DocumentValidationPipeline`: noindex → `REJECT`:**

```java
// MinContentLengthValidator (or dedicated NoindexValidator):
@Override
public ValidationResult validate(CleanedDocument doc) {
    if (doc.isNoindex()) {
        return ValidationResult.rejected("noindex_directive");
    }
    // ... normal length check ...
}
```

**Unit tests:**

```java
// NoindexDetectorTest:

@Test
void isNoindex_true_forRobotsNoindex() {
    String html = "<html><head>" +
        "<meta name='robots' content='noindex,nofollow'>" +
        "</head><body><p>text</p></body></html>";
    assertThat(NoindexDetector.isNoindex(Jsoup.parse(html))).isTrue();
}

@Test
void isNoindex_true_caseInsensitive() {
    String html = "<html><head>" +
        "<meta name='ROBOTS' content='NOINDEX'>" +
        "</head><body></body></html>";
    assertThat(NoindexDetector.isNoindex(Jsoup.parse(html))).isTrue();
}

@Test
void isNoindex_false_whenNoRobotsMeta() {
    String html = "<html><body><p>normal page</p></body></html>";
    assertThat(NoindexDetector.isNoindex(Jsoup.parse(html))).isFalse();
}

@Test
void isNoindex_false_whenRobotsIndex() {
    String html = "<html><head>" +
        "<meta name='robots' content='index,follow'>" +
        "</head><body></body></html>";
    assertThat(NoindexDetector.isNoindex(Jsoup.parse(html))).isFalse();
}
```

**ფაილები:**
- New: `apps/ingestion-service/.../parse/NoindexDetector.java`
- Update: `apps/ingestion-service/.../parse/profile/JsoupContentExtractor.java` — early-exit on noindex
- Update: `libs/platform-contracts/.../parse/CleanedDocument.java` — `noindexMarker()` factory
- Update: `apps/ingestion-service/.../validation/MinContentLengthValidator.java` — noindex → REJECT

**Acceptance criteria:**
- Page with `<meta name="robots" content="noindex">` → `CleanedDocument.isNoindex()` = true → `quality_score = REJECT`.
- Not embedded in Qdrant (`VectorCleanupJob` CROSS-GAP-01 handles existing vectors).
- `index,follow` pages → normal extraction flow unchanged.
- Log `DEBUG` for each skipped noindex page.

---

### L-1-29 — `<template>` HTML5 element: add to `removeSelectors` 🟡

**Root cause:**

```java
// JsoupContentExtractor — removeSelectors:
// "script" removes all <script> tags (regardless of type) ✅
// BUT: <template> is a separate HTML5 element — NOT removed by "script"

// <template> in Jsoup:
// Jsoup 1.15+: parses <template> content as a document fragment
// template.text() = all text from template children
// → Handlebars/Mustache/Angular template strings appear in body

Example:
  <template id="news-card">
    <li class="news-item">
      <a href="{{url}}">{{title}}</a>
      <span class="date">{{date}}</span>
    </li>
  </template>

→ extractBody: li.text() = "{{title}}"  and  span-like content = "{{date}}"
→ body contains "{{title}} {{date}}" — Mustache template syntax in DB
→ embedding: "{{title}}" tokenized → garbage vector dimensions
```

**Resolution — one-line YAML fix:**

```yaml
# geostat-portal-parse.yaml — add to removeSelectors:
removeSelectors:
  # ... existing selectors ...
  - "template"        # HTML5 <template> — JS template fragments, never real content
  - "[x-template]"    # Vue.js / Alpine.js template attribute pattern
```

**Unit test:**

```java
// JsoupContentExtractorTest:

@Test
void extract_removesTemplateElement() {
    String html = """
        <html><body><main>
          <p>სტატია GDP-ის შესახებ 8.7%.</p>
          <template id="card-tpl">
            <li><a href="{{url}}">{{title}}</a></li>
          </template>
        </main></body></html>""";
    CleanedDocument doc = extractor.extract(
        new HtmlPageInput(html, "https://x.ge"), profileWithTemplateRemoval);
    assertThat(doc.bodyText()).doesNotContain("{{title}}");
    assertThat(doc.bodyText()).doesNotContain("{{url}}");
    assertThat(doc.bodyText()).contains("8.7%");
}
```

**ფაილები:**
- Update: `ops/config/corpus/geostat-portal-parse.yaml` — `"template"` to `removeSelectors`

**Acceptance criteria:**
- `<template>` content never appears in `body`.
- Real content alongside `<template>` unchanged.
- YAML change only — no Java changes required.

---

### L-1-30 — Character encoding: detect mismatch, WARN, never mis-parse Georgian 🟡

**Root cause:**

```
HTTP flow:
  Server: Content-Type: text/html; charset=UTF-8
  Actual bytes: Windows-1252 encoded (old server, misconfiguration)

crawler4j / HttpClient: reads bytes, applies Content-Type charset (UTF-8)
  → Georgian chars (U+10D0–U+10FF, 3-byte UTF-8) decoded as Windows-1252
  → mojibake: "?????? ????????" in html string

Jsoup.parse(html, url) receives already-corrupted string
  → body text = garbage
  → LanguageConsistencyValidator → REJECT (correct outcome)
  → BUT: no alert, no metric, operator doesn't know which page failed and why
```

**Resolution:**

**Step 1 — `EncodingMismatchDetector`:**

`apps/ingestion-service/src/main/java/com/geostat/ingestion/crawl/EncodingMismatchDetector.java`

```java
/**
 * Detects potential character encoding mismatch after fetch.
 *
 * Strategy: after Jsoup parses the HTML, check for Georgian character presence.
 * geostat.ge is a Georgian government site — every /ka/ page MUST contain Georgian.
 * If /ka/ page has zero Georgian characters → encoding mismatch.
 *
 * Does NOT re-encode. Logs WARN. Sets document.encoding_issue = true.
 * Re-encoding with correct charset is left to future BACKLOG item.
 */
public final class EncodingMismatchDetector {

    // Georgian Unicode block: U+10A0–U+10FF (script) + U+2D00–U+2D2F (supplement)
    private static final Pattern GEORGIAN_CHARS =
        Pattern.compile("[\u10A0-\u10FF\u2D00-\u2D2F]");

    /**
     * True if page appears to be a Georgian-language page but contains
     * no Georgian characters — strong signal of encoding corruption.
     */
    public static boolean looksCorrupted(String url, String htmlText) {
        if (htmlText == null || htmlText.isBlank()) return false;
        boolean isKaUrl = url != null && url.contains("/ka/");
        if (!isKaUrl) return false; // only check /ka/ pages
        return !GEORGIAN_CHARS.matcher(htmlText).find();
    }
}
```

**Step 2 — `Crawler4jStaticPageFetcher`: detect and log WARN:**

```java
@Override
public FetchedPage fetch(String url, FetchOptions options) throws PageFetchException {
    // ... existing fetch ...
    if (EncodingMismatchDetector.looksCorrupted(url, html)) {
        log.warn("[encoding] Possible encoding mismatch: /ka/ page with no Georgian chars. " +
            "url={} Content-Type={}", url, contentType);
        // Document will be REJECTED by LanguageConsistencyValidator — that's correct.
        // This WARN allows operators to investigate the server config.
    }
    // ... return FetchedPage ...
}
```

**Step 3 — V20 migration: `document.encoding_issue` flag:**

```sql
-- V20:
ALTER TABLE ingestion.document
  ADD COLUMN IF NOT EXISTS encoding_issue BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN ingestion.document.encoding_issue IS
  'True if EncodingMismatchDetector identified likely charset corruption.
   Document will be REJECTED by validation pipeline.
   Operator action: check server Content-Type headers for this URL.';
```

**Step 4 — Quality gate: `encoding_issue_rate` metric:**

```yaml
# corpus-quality-gate.yaml:
metrics:
  - id: encoding_issue_rate
    description: "Share of parsed docs with encoding corruption flag"
    warnThreshold: 0.01     # 1% — any encoding issue is a server config problem
    failThreshold: 0.05     # 5% — critical
```

**Unit tests:**

```java
// EncodingMismatchDetectorTest:

@Test
void looksCorrupted_true_forKaUrl_withNoGeorgianChars() {
    assertThat(EncodingMismatchDetector.looksCorrupted(
        "https://www.geostat.ge/ka/news/123",
        "Some text with no georgian letters at all"))
        .isTrue();
}

@Test
void looksCorrupted_false_forKaUrl_withGeorgianChars() {
    assertThat(EncodingMismatchDetector.looksCorrupted(
        "https://www.geostat.ge/ka/news/123",
        "ბუნებრივი მოძრაობა 2024 წელს"))
        .isFalse();
}

@Test
void looksCorrupted_false_forEnUrl() {
    // /en/ pages have no Georgian — not a corruption signal
    assertThat(EncodingMismatchDetector.looksCorrupted(
        "https://www.geostat.ge/en/news/123",
        "Some English text"))
        .isFalse();
}
```

**ფაილები:**
- New: `apps/ingestion-service/.../crawl/EncodingMismatchDetector.java`
- Update: `apps/ingestion-service/.../crawl/fetch/Crawler4jStaticPageFetcher.java` — detect + WARN
- V20 migration — `document.encoding_issue` + quality gate metric

**Acceptance criteria:**
- `/ka/` page with no Georgian characters → WARN logged with URL + Content-Type.
- `encoding_issue = true` set on document row.
- `LanguageConsistencyValidator` still REJECTs correctly (existing L-1-08 path).
- `/en/` pages → no false positive (detector only checks `/ka/`).
- `encoding_issue_rate` metric surfaced in quality gate report.

---

### CFG-01 — HikariCP pool sizing for parallel `CrawlOrchestrator` 🟡

**Root cause — default pool exhaustion under parallel crawl:**

```
CrawlOrchestrator (ARCH-04): 2 corpora in parallel
Each corpus: 10 crawler4j worker threads
Each worker: DB insert per page (document + chunks)
+ EmbeddingRetryJob: SELECT queries
+ CorpusReparseWorker: batch SELECT + UPDATE
+ Spring Boot app: normal HTTP request connections
+ MV refresh: long-running connection

Default HikariCP: maximum-pool-size = 10

Under full load: 2×10 crawl workers = 20 DB connections needed for inserts alone
→ pool exhausted → ConnectionTimeoutException after 30s
→ crawl worker thread hangs → corpus crawl stalls silently
```

**Resolution — `application-custom.yml` HikariCP config:**

```yaml
# apps/ingestion-service/src/main/resources/application-custom.yml

spring:
  datasource:
    hikari:
      pool-name: GeostatIngestionPool

      # Sizing rationale:
      # 2 corpora × 10 crawl threads = 20 (document inserts)
      # + 5 embedding/retry workers
      # + 3 background jobs (reparse, recovery, mv-refresh)
      # + 2 Spring Boot API threads overhead
      # = ~30 peak. 40 gives 25% headroom.
      maximum-pool-size: 40
      minimum-idle: 5

      # Fail fast on pool exhaustion — don't silently hang crawl workers
      connection-timeout: 10000   # 10s: ConnectionTimeoutException if no connection available

      # Connection lifecycle
      idle-timeout: 300000        # 5min: reclaim idle connections quickly
      max-lifetime: 1800000       # 30min: rotate to avoid stale connections
      keepalive-time: 60000       # 1min: keep idle connections alive against firewall timeout

      # Validation
      connection-test-query: "SELECT 1"
      validation-timeout: 5000
```

**Pool size formula (document for junior):**
```
max_pool = (num_corpora × crawl_workers_per_corpus)
         + embedding_workers
         + background_jobs
         + api_connections
         + 10% headroom

= (2 × 10) + 5 + 3 + 2 + headroom(4) = 30 → round to 40
```

**Monitor via Spring Boot Actuator + existing metrics:**

```yaml
# application-custom.yml:
management:
  metrics:
    enable:
      hikaricp: true    # exposes hikaricp.connections.active, .pending, .timeout
```

**Alert condition:** `hikaricp.connections.pending > 5` → scale `maximum-pool-size` or reduce `crawl_workers_per_corpus`.

**ფაილები:**
- Update: `apps/ingestion-service/src/main/resources/application-custom.yml` — HikariCP block

**Acceptance criteria:**
- 2-corpus parallel crawl completes without `ConnectionTimeoutException`.
- `hikaricp.connections.pending` = 0 under normal load.
- `hikaricp.connections.active` < 35 under full parallel crawl.
- Pool name `GeostatIngestionPool` visible in Actuator metrics for identification.

---

---

### PERF-01 — `CrawlRunner` single-threaded loop → bounded parallel worker pool 🔴

**Root cause — კოდიდან პირდაპირ (`CrawlRunner.java` line 46–72):**

```java
while (pagesFetched < config.maxPages()) {
    List<UUID> frontierIds = crawlRunStore.nextQueuedFrontierIds(runId);  // batch of 50
    for (UUID frontierId : frontierIds) {
        // ONE page processed at a time — blocking, sequential
        Optional<PersistedPage> page =
            crawlRunStore.processFrontier(frontierId, runId, config);
        // ...
        Thread.sleep(config.rateLimitMs());  // ← blocking sleep per page
    }
}
```

**Performance impact:**
```
geostat.ge: ~15,000 pages total
Avg fetch + parse + save per page: ~2.5s
rateLimitMs: 500ms
Sequential: 15,000 × 3.0s = 12.5 hours per corpus

With 8 parallel workers + rate limit per worker:
  Effective time: 12.5h / 8 = ~1.6 hours
  With batch frontier fetch: ~45 minutes
```

**Resolution — `CrawlRunner.runCrawl()`: replace loop with `ExecutorService`:**

`apps/ingestion-service/src/main/java/com/geostat/ingestion/crawl/runner/CrawlRunner.java`

```java
void runCrawl(UUID runId) throws InterruptedException {
    RunConfig config = crawlRunStore.loadRunConfig(runId);
    crawlRunStore.markRunning(runId);

    // Bounded thread pool — worker count from config, default 5
    // Each worker independently fetches + persists one page at a time
    int workers = config.workerThreads();  // ← new field in RunConfig (see below)
    ExecutorService pool = Executors.newFixedThreadPool(workers,
        r -> new Thread(r, "crawl-worker-" + runId.toString().substring(0, 8)));

    AtomicInteger pagesFetched    = new AtomicInteger(0);
    AtomicInteger linksDiscovered = new AtomicInteger(0);
    AtomicInteger failures        = new AtomicInteger(0);

    try {
        while (pagesFetched.get() < config.maxPages()) {
            List<UUID> batch = crawlRunStore.nextQueuedFrontierIds(runId);
            if (batch.isEmpty()) break;

            // Submit entire batch concurrently
            List<CompletableFuture<Void>> futures = batch.stream()
                .filter(_ -> pagesFetched.get() < config.maxPages())
                .map(frontierId -> CompletableFuture.runAsync(() -> {
                    try {
                        Optional<CrawlRunStore.PersistedPage> page =
                            crawlRunStore.processFrontier(frontierId, runId, config);
                        if (page.isPresent()) {
                            crawlRunStore.indexPersistedPage(page.get());
                            linksDiscovered.addAndGet(page.get().linksDiscovered());
                            pagesFetched.incrementAndGet();
                        }
                        // Rate limit per worker — not per pool
                        if (config.rateLimitMs() > 0) {
                            Thread.sleep(config.rateLimitMs());
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        log.warn("fetch failed frontier={}: {}", frontierId, e.getMessage());
                        crawlRunStore.markFrontierFailed(frontierId, e.getMessage());
                        failures.incrementAndGet();
                    }
                }, pool))
                .toList();

            // Wait for batch to complete before fetching next batch of frontier IDs
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }
    } finally {
        pool.shutdown();
        pool.awaitTermination(60, TimeUnit.SECONDS);
    }

    long queuedRemaining = crawlRunStore.countQueuedFrontier(runId);
    crawlRunStore.markCompleted(runId, pagesFetched.get(),
        linksDiscovered.get(), failures.get(), queuedRemaining);
    crawlContinuationService.scheduleContinuationIfNeeded(runId, queuedRemaining);
}
```

**`RunConfig` — add `workerThreads` field:**

```java
// RunConfig.java:
public record RunConfig(
    UUID          runId,
    UUID          corpusId,
    int           maxPages,
    int           maxDepth,
    int           rateLimitMs,
    List<String>  allowedHosts,
    int           workerThreads   // ← NEW: default 5, max 20
) {}
```

**`CorpusPolicy` — read `workerThreads` from policy YAML:**

```yaml
# geostat-portal-policy.yaml:
crawl:
  maxPagesPerRun: 10000
  maxDepth: 5
  rateLimitMs: 500
  workerThreads: 8    # ← NEW: 8 concurrent workers for geostat.ge
```

```java
// CorpusPolicy:
public static int workerThreads(CorpusEntity corpus) {
    return Optional.ofNullable(corpus.getPolicyConfig())
        .map(c -> (Integer) c.getOrDefault("crawl.workerThreads", 5))
        .orElse(5);   // safe default
}
```

**Unit tests:**

```java
// CrawlRunnerTest:

@Test
void runCrawl_processes_pages_concurrently() throws Exception {
    // given: 10 frontier entries, workerThreads=4
    // each page takes 100ms to process (mocked)
    // sequential: 10 × 100ms = 1000ms
    // parallel (4 workers): ~300ms
    long start = System.currentTimeMillis();
    runner.runCrawl(runId);
    long elapsed = System.currentTimeMillis() - start;
    assertThat(elapsed).isLessThan(500); // parallel — not sequential
    verify(crawlRunStore, times(10)).processFrontier(any(), any(), any());
}

@Test
void runCrawl_respects_maxPages_under_concurrency() throws Exception {
    // given: 100 frontier entries, maxPages=15
    runner.runCrawl(runId);
    // pagesFetched must not exceed maxPages
    verify(crawlRunStore, atMost(15)).indexPersistedPage(any());
}
```

**ფაილები:**
- Update: `apps/ingestion-service/.../crawl/runner/CrawlRunner.java` — `ExecutorService` parallel loop
- Update: `apps/ingestion-service/.../crawl/runner/RunConfig.java` — `workerThreads` field
- Update: `ops/config/corpus/geostat-portal-policy.yaml` — `crawl.workerThreads: 8`

**Acceptance criteria:**
- `runCrawl()` processes pages concurrently (N workers in parallel).
- `maxPages` never exceeded despite concurrent workers (`AtomicInteger` counter).
- `rateLimitMs` applied per-worker (not per-pool) — politeness maintained.
- `workerThreads` configurable per-corpus via policy YAML.
- Existing single-page unit tests pass (mock processFrontier returns immediately).

---

### PERF-02 — HTTP fetch inside `@Transactional`: split fetch phase from persist phase 🔴

**Root cause — `CrawlRunStore.processFrontier()` line 123–145:**

```java
@Transactional                         // ← DB transaction OPEN
public Optional<PersistedPage> processFrontier(...) {
    // DB: frontier status update → READ from pool
    frontier.setStatus(FrontierStatus.fetching);
    urlFrontierRepository.save(frontier);

    // persistPage() → fetchAndPersist() → pageFetcher.fetch()
    // ↓
    // HTTP network call — 1–5 seconds latency
    // DB connection HELD OPEN during entire HTTP round-trip
    PersistedPage page = persistPage(run, corpus, frontier, config.maxDepth());
    // ...
}                                      // ← DB transaction CLOSE (after HTTP done)
```

**Impact with parallel workers (PERF-01):**
```
8 workers × 1 DB connection held per HTTP fetch × avg 3s fetch time
= 8 connections busy with network wait
HikariCP pool: 10 connections → 8 used for network wait → only 2 left for other operations
= pool near exhaustion under parallel crawl
```

**Resolution — split into two methods: `fetchHtml()` (no TX) + `persistFetched()` (TX):**

```java
// CrawlRunStore.java:

// STEP 1: Fetch HTML — NO transaction, no DB connection held during HTTP
public Optional<FetchedPage> fetchHtml(UUID frontierId, UUID runId, RunConfig config)
        throws IOException, InterruptedException, RobotsBlockedException, PolicyBlockedException {
    // read-only: get URL from frontier — quick TX, released immediately
    String url    = frontierUrlReadOnly(frontierId);
    String etag   = frontierEtagReadOnly(frontierId);
    Instant lastM = frontierLastModifiedReadOnly(frontierId);

    // HTTP fetch — NO active transaction, no held connection
    try {
        FetchedPage page = (etag != null || lastM != null)
            ? pageFetcher.fetchConditional(url, corpusOf(config), etag, lastM)
            : pageFetcher.fetch(url, corpusOf(config));
        return Optional.of(page);
    } catch (PageNotModifiedException e) {
        markFrontierNotModified(frontierId);
        return Optional.empty();
    }
}

// STEP 2: Persist — inside @Transactional, HTML already in memory
@Transactional
public PersistedPage persistFetched(UUID frontierId, UUID runId, FetchedPage page, RunConfig config)
        throws IOException {
    // DB operations only — no network calls inside transaction
    UrlFrontierEntity frontier = urlFrontierRepository.findById(frontierId).orElseThrow();
    CrawlRunEntity    run      = crawlRunRepository.findById(runId).orElseThrow();
    CorpusEntity      corpus   = run.getCorpus();

    int links = persistPage(run, corpus, frontier, page, config.maxDepth());
    frontier.setStatus(FrontierStatus.done);
    urlFrontierRepository.save(frontier);
    return new PersistedPage(documentIdFor(frontier, corpus), corpus.getId(), links);
}
```

**Updated `CrawlRunner` worker loop:**

```java
// In CompletableFuture worker (PERF-01):
CompletableFuture.runAsync(() -> {
    try {
        // Phase 1: HTTP fetch — no TX, no DB connection held
        Optional<FetchedPage> htmlPage =
            crawlRunStore.fetchHtml(frontierId, runId, config);

        if (htmlPage.isEmpty()) return; // 304 Not Modified

        // Phase 2: Parse + persist — TX, fast DB operations
        CrawlRunStore.PersistedPage persisted =
            crawlRunStore.persistFetched(frontierId, runId, htmlPage.get(), config);

        crawlRunStore.indexPersistedPage(persisted);
        // ...
    }
}, pool)
```

**Unit tests:**

```java
// CrawlRunStoreTest:

@Test
void fetchHtml_doesNotHoldTransaction_duringHttpCall() {
    // verify: no active transaction when pageFetcher.fetch() is called
    // use TransactionSynchronizationManager.isActualTransactionActive()
    doAnswer(inv -> {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive())
            .isFalse();  // ← no TX during HTTP fetch
        return mockFetchedPage();
    }).when(pageFetcher).fetch(any(), any());

    store.fetchHtml(frontierId, runId, config);
}

@Test
void persistFetched_isTransactional() {
    // verify: transaction IS active during persistFetched
    doAnswer(inv -> {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive())
            .isTrue();  // ← TX active during persist
        return null;
    }).when(chunkRepository).saveAll(any());

    store.persistFetched(frontierId, runId, mockPage(), config);
}
```

**ფაილები:**
- Update: `apps/ingestion-service/.../crawl/runner/CrawlRunStore.java` — split `processFrontier()` into `fetchHtml()` + `persistFetched()`
- Update: `apps/ingestion-service/.../crawl/runner/CrawlRunner.java` — two-phase call (PERF-01)

**Acceptance criteria:**
- No DB connection held during HTTP fetch.
- `pageFetcher.fetchConditional()` called when `etag` or `lastModified` present (wires up existing `ConditionalHttpFetcher`).
- `persistFetched()` is `@Transactional` — atomicity of document + chunks preserved.
- Connection pool usage under 8-worker parallel crawl: peak < 12 connections.

---

### PERF-03 — `DocumentChunkWriter.replaceChunks()`: `save()` per chunk → `saveAll()` 🔴

**Root cause — `DocumentChunkWriter.java` line 40–64:**

```java
for (TextChunk chunk : chunks) {
    ChunkEntity entity = new ChunkEntity();
    // ... set fields ...
    chunkRepository.save(entity);  // ← ONE INSERT per chunk
}
// 1 document → ~15 chunks → 15 separate INSERT round-trips
// 10,000 documents → 150,000 individual INSERT statements
```

**Resolution — batch with `saveAll()`:**

```java
@Transactional
public int replaceChunks(
        DocumentEntity document, CorpusEntity corpus,
        String cleanedText, List<String> sectionPath, String language) {

    chunkRepository.deleteByDocument_Id(document.getId());
    List<TextChunk> chunks = chunker.chunk(cleanedText);
    if (chunks.isEmpty()) return 0;

    String sectionJoined = SectionPathExtractor.joinPath(sectionPath);
    String navBreadcrumb = document.getNavBreadcrumb();  // L-1-24

    List<ChunkEntity> entities = new ArrayList<>(chunks.size());
    for (TextChunk chunk : chunks) {
        ChunkEntity entity = new ChunkEntity();
        entity.setDocument(document);
        entity.setCorpus(corpus);
        entity.setSequenceNo(chunk.sequenceNo());
        entity.setText(chunk.text());
        entity.setTextHash(UrlHasher.hash(chunk.text()));
        entity.setTokenCount(estimateTokens(chunk.text()));
        entity.setChunkStrategy(FixedSizeChunker.STRATEGY_ID);
        entity.setNavBreadcrumb(navBreadcrumb);  // L-1-24
        Map<String, Object> meta = new HashMap<>();
        if (language        != null) meta.put("language",        language);
        if (!sectionJoined.isBlank()) meta.put("sectionPath",    sectionJoined);
        if (navBreadcrumb   != null) meta.put("navBreadcrumb",   navBreadcrumb);  // Qdrant payload
        if (document.getTitle() != null) meta.put("pageTitle",   document.getTitle());
        entity.setMetadata(meta);
        entities.add(entity);
    }

    chunkRepository.saveAll(entities);  // ← ONE batch INSERT for all chunks
    return entities.size();
}
```

**Enable JDBC batch in `application-custom.yml`:**

```yaml
spring:
  jpa:
    properties:
      hibernate:
        jdbc:
          batch_size: 50        # batch up to 50 INSERTs per statement
          order_inserts: true   # group INSERTs by table for batching
          order_updates: true
```

**Unit tests:**

```java
// DocumentChunkWriterTest:

@Test
void replaceChunks_callsSaveAll_notSavePerChunk() {
    // given: 15 chunks from text
    writer.replaceChunks(document, corpus, longText, List.of(), "ka");
    // then: saveAll called ONCE (not 15 times)
    verify(chunkRepository, times(1)).saveAll(argThat(
        list -> ((List<?>) list).size() == 15));
    verify(chunkRepository, never()).save(any(ChunkEntity.class));
}

@Test
void replaceChunks_propagatesNavBreadcrumb_toAllChunks() {
    document.setNavBreadcrumb("სტატისტიკა > მოსახლეობა");
    writer.replaceChunks(document, corpus, longText, List.of(), "ka");
    verify(chunkRepository).saveAll(argThat(entities ->
        ((List<ChunkEntity>) entities).stream()
            .allMatch(e -> "სტატისტიკა > მოსახლეობა".equals(e.getNavBreadcrumb()))));
}
```

**ფაილები:**
- Update: `apps/ingestion-service/.../chunk/DocumentChunkWriter.java` — `saveAll()` + `navBreadcrumb`
- Update: `apps/ingestion-service/src/main/resources/application-custom.yml` — hibernate batch config

**Acceptance criteria:**
- `chunkRepository.saveAll()` called once per document (not N times).
- `hibernate.jdbc.batch_size=50` enables JDBC batch at driver level.
- Chunk insert time for 15 chunks: < 5ms (vs ~75ms with individual saves).
- `nav_breadcrumb` propagated to all chunks in same call (L-1-24 wired here).

---

### PERF-04 — `urlFrontierRepository.save()` per discovered link → `saveAll()` 🟠

**Root cause — `CrawlRunStore.fetchAndPersist()` line 274–280:**

```java
List<UrlFrontierEntity> discovered =
    linkDiscoverer.discover(run.getId(), corpus, frontier, page.html(), maxDepth);
for (UrlFrontierEntity next : discovered) {
    next.setCrawlRun(run);
    urlFrontierRepository.save(next);   // ← one INSERT per discovered link
}
// 1 page → avg 20 links → 20 INSERT round-trips
// 10,000 pages → 200,000 individual frontier INSERTs
```

**Resolution:**

```java
// CrawlRunStore.fetchAndPersist() — replace loop:
List<UrlFrontierEntity> discovered =
    linkDiscoverer.discover(run.getId(), corpus, frontier, page.html(), maxDepth);
discovered.forEach(next -> next.setCrawlRun(run));
urlFrontierRepository.saveAll(discovered);  // ← ONE batch INSERT for all links
```

**Same pattern in the `contentUnchanged` branch (line 237–241) and `gateDecision` branch (line 258–265) — fix all three occurrences.**

**Unit tests:**

```java
// CrawlRunStoreTest:

@Test
void persistFetched_callsSaveAll_for_discoveredLinks() {
    when(linkDiscoverer.discover(any(), any(), any(), any(), anyInt()))
        .thenReturn(List.of(link1, link2, link3, link4, link5));

    store.persistFetched(frontierId, runId, mockPage(), config);

    verify(urlFrontierRepository, times(1))
        .saveAll(argThat(list -> ((List<?>) list).size() == 5));
    verify(urlFrontierRepository, never()).save(any(UrlFrontierEntity.class));
}
```

**ფაილები:**
- Update: `apps/ingestion-service/.../crawl/runner/CrawlRunStore.java` — 3 occurrences of link save loop → `saveAll()`

**Acceptance criteria:**
- `urlFrontierRepository.save()` never called for link discovery (only for frontier status updates).
- `saveAll(discovered)` called once per page (all 3 branches).
- Frontier INSERT time per page: < 5ms for 20 links.

---

### BUG-CRAWL-01 — `fetchConditional()` exists but NOT wired in main crawl loop 🟠

**Root cause — `CrawlRunStore.fetchAndPersist()` line 213:**

```java
FetchedPage page = pageFetcher.fetch(frontier.getUrl(), corpus);
//                             ↑ ALWAYS unconditional full fetch

// BUT: pageFetcher.fetchConditional() EXISTS (Crawler4jPageFetcher.java line 34)
// AND: document.httpEtag + document.lastModified are STORED (lines 228-229)
// AND: ConditionalHttpFetcher properly implemented

// The infrastructure for If-Modified-Since is complete — it's just never called.
```

**What `fetchConditional()` does (already implemented correctly):**
```java
// Crawler4jPageFetcher.fetchConditional() sends:
//   If-None-Match: "etag-value"         (preferred)
//   If-Modified-Since: Mon, 26 May 2025... (fallback)
// On 304: throws PageNotModifiedException
// On 200: returns FetchedPage with new etag + lastModified
```

**Resolution — wire `fetchConditional()` in `fetchAndPersist()`:**

```java
// CrawlRunStore.fetchAndPersist() — replace line 213:

// Check if this URL was previously fetched and has HTTP cache validators
DocumentEntity existing = documentRepository
    .findByCorpusIdAndUrlHash(corpus.getId(), frontier.getUrlHash())
    .orElse(null);

FetchedPage page;
try {
    if (existing != null
            && (existing.getHttpEtag() != null || existing.getLastModified() != null)) {
        // Re-crawl with conditional GET — skip download if unchanged
        page = pageFetcher.fetchConditional(
            frontier.getUrl(), corpus,
            existing.getHttpEtag(),
            existing.getLastModified());
        log.debug("[conditional-fetch] 200 content changed: {}", frontier.getUrl());
    } else {
        // First crawl or no cache validators — unconditional GET
        page = pageFetcher.fetch(frontier.getUrl(), corpus);
    }
} catch (PageNotModifiedException e) {
    // 304: content unchanged — update last_seen_at, skip re-parse
    log.debug("[conditional-fetch] 304 not modified: {}", frontier.getUrl());
    if (existing != null) {
        existing.setLastModified(existing.getLastModified()); // touch timestamp
        documentRepository.save(existing);
    }
    return 0; // 0 links (skip link discovery for unchanged pages)
}
```

**Unit tests:**

```java
// CrawlRunStoreTest:

@Test
void fetchAndPersist_usesFetchConditional_whenEtagStored() {
    // given: document with stored ETag
    existingDocument.setHttpEtag("\"abc123\"");
    existingDocument.setLastModified(null);

    store.persistFetched(frontierId, runId, /* fetched via conditional */, config);

    verify(pageFetcher).fetchConditional(
        eq(url), eq(corpus), eq("\"abc123\""), isNull());
    verify(pageFetcher, never()).fetch(eq(url), any());
}

@Test
void fetchAndPersist_skips_reparse_on_304() {
    when(pageFetcher.fetchConditional(any(), any(), any(), any()))
        .thenThrow(new PageNotModifiedException(url));

    // when
    store.fetchHtml(frontierId, runId, config);

    // then: no content cleaning, no chunk replacement
    verify(contentCleaner, never()).clean(any(), any(), any());
    verify(documentChunkWriter, never()).replaceChunks(any(), any(), any(), any(), any());
}
```

**ფაილები:**
- Update: `apps/ingestion-service/.../crawl/runner/CrawlRunStore.java` — wire `fetchConditional()` (replaces PERF-02's `fetchHtml()` method)

**Acceptance criteria:**
- Re-crawl of page with stored ETag: `If-None-Match` header sent.
- 304 response: no re-parse, `documents.not_modified` metric incremented.
- 200 response: full pipeline runs, new ETag stored.
- First crawl (no stored validators): unconditional GET (existing behavior preserved).

---

### BUG-DB-01 — `canonical_url` stored without normalization (`utm_*` duplicates) 🟠

**Root cause — `CrawlRunStore.fetchAndPersist()` line 225:**

```java
document.setCanonicalUrl(frontier.getUrl());
// ↑ raw URL — no normalization
// "https://www.geostat.ge/ka/news/1234?utm_source=facebook" stored as-is
// Different from: "https://www.geostat.ge/ka/news/1234"
// = two document rows for same article
```

**Resolution — apply `UrlNormalizer` at `setCanonicalUrl` time:**

```java
// CrawlRunStore.fetchAndPersist() line 225:
document.setCanonicalUrl(UrlNormalizer.normalize(frontier.getUrl())); // ← normalize
document.setUrlHash(UrlHasher.hash(
    UrlNormalizer.normalize(frontier.getUrl())));  // ← hash of normalized URL
```

**Also normalize in `PolicyUrlFilter.shouldEnqueue()` for frontier dedup:**

```java
// PolicyUrlFilter:
@Override
public boolean shouldEnqueue(String url, CorpusEntity corpus) {
    String normalized = UrlNormalizer.normalize(url);
    if (visitedUrls.contains(normalized)) return false;
    // ... policy checks ...
    visitedUrls.add(normalized);
    return true;
}
```

**Unit tests:**

```java
// UrlNormalizerTest (see L-1-26 for full test suite)

// CrawlRunStoreTest (integration):
@Test
void persistFetched_stores_normalizedCanonicalUrl() {
    String rawUrl  = "https://www.geostat.ge/ka/news/1234?utm_source=fb&fbclid=X";
    String cleanUrl = "https://www.geostat.ge/ka/news/1234";
    frontier.setUrl(rawUrl);

    store.persistFetched(frontierId, runId, fetchedPage(rawUrl), config);

    DocumentEntity saved = documentRepository.findByCorpusIdAndUrlHash(
        corpusId, UrlHasher.hash(cleanUrl)).orElseThrow();
    assertThat(saved.getCanonicalUrl()).isEqualTo(cleanUrl);
}
```

**ფაილები:**
- Update: `apps/ingestion-service/.../crawl/runner/CrawlRunStore.java` — `UrlNormalizer.normalize()` at line 225
- Update: `apps/ingestion-service/.../parse/profile/PolicyUrlFilter.java` — normalize before dedup
- New: `apps/ingestion-service/.../crawl/UrlNormalizer.java` (see L-1-26 for full class)

**Acceptance criteria:**
- `canonical_url` in DB never contains `utm_*`, `fbclid`, `gclid`.
- Same article URL with and without tracking params → 1 document row (dedup by normalized hash).
- Fragment `#section` stripped from canonical_url.

---

### DB-ARCH-01 — `document.content_text` + `chunk.text`: storage duality decision 🟡

**Current state (from code):**

```java
// CrawlRunStore.fetchAndPersist() line 247:
document.setContentText(cleaned.text());  // FULL body stored in document table

// DocumentChunkWriter.replaceChunks() line 43:
entity.setText(chunk.text());              // EACH chunk stores its portion
```

**Storage math for geostat.ge:**
```
15,000 documents × avg 3KB body = 45MB in document.content_text
15,000 documents × 15 chunks × avg 300 chars = 67MB in chunk.text
Total: 112MB — chunk content is a SUBSET of document content

document.content_text is NOT used by:
  - RAG retrieval (uses chunk.text via Qdrant)
  - Chat response (uses chunk metadata)
  - Quality gate (uses CleanedDocument in memory, not DB column)

document.content_text IS used by:
  - CorpusReparseWorker (re-parsing: reads text → re-chunks without re-fetching)
  - Quality audit queries (direct SQL content inspection)
  - EncodingMismatchDetector (checks for Georgian chars)
```

**Senior architectural decision:**

`document.content_text` is **justified** for:
1. `CorpusReparseWorker` — re-chunking without re-crawling (critical for efficiency)
2. Debugging/audit — direct SQL content inspection
3. Future: full-text search index on `content_text` (`pg_trgm`, `tsvector`)

**Action:** Keep `document.content_text` — document the decision. Add GIN FTS index:

```sql
-- V20 (add to migration):
-- Full-text search on content_text (for quality audit queries)
CREATE INDEX IF NOT EXISTS idx_document_content_fts
  ON ingestion.document
  USING gin(to_tsvector('simple', COALESCE(content_text, '')))
  WHERE fetch_status = 'parsed';

COMMENT ON COLUMN ingestion.document.content_text IS
  'Full cleaned body text. Retained for: (1) CorpusReparseWorker re-chunking,
   (2) quality audit SQL queries, (3) FTS index.
   NOT used for RAG — use chunk.text via Qdrant instead.
   Decision: storage cost (~45MB) justified by reparse benefit.';
```

**ფაილები:**
- V20 migration — FTS index on `content_text`

**Acceptance criteria:**
- `document.content_text` retention documented with justification comment in migration.
- GIN FTS index created on `(corpus_id, content_text)` for audit queries.
- Quality audit query `WHERE to_tsvector('simple', content_text) @@ plainto_tsquery('simple', 'GDP')` uses index.

---

### DB-ARCH-02 — `ON DELETE CASCADE`: document → chunk orphan prevention 🟡

**Current state (inferred — no explicit cascade in reviewed code):**

```sql
-- Current FK (likely):
FOREIGN KEY (document_id) REFERENCES ingestion.document(id)
-- No ON DELETE behavior → PostgreSQL default = RESTRICT
-- delete document → ERROR: FK constraint violation (chunk rows block deletion)
-- OR: delete without cascade → chunk rows become orphans (if FK is DEFERRABLE)
```

**Operational scenarios requiring document deletion:**
- `StaleDocumentCleanupJob` deletes very old stale documents
- Corpus reset / re-crawl from scratch
- GDPR/content removal request

**Resolution — V20 migration: explicit FK cascade:**

```sql
-- V20 (add to migration):

-- 1. chunk → document: CASCADE (delete chunks when document deleted)
ALTER TABLE ingestion.chunk
  DROP CONSTRAINT IF EXISTS chunk_document_id_fkey;
ALTER TABLE ingestion.chunk
  ADD CONSTRAINT chunk_document_id_fkey
  FOREIGN KEY (document_id)
  REFERENCES ingestion.document(id)
  ON DELETE CASCADE;

COMMENT ON CONSTRAINT chunk_document_id_fkey ON ingestion.chunk IS
  'CASCADE: deleting a document removes all its chunks.
   Qdrant vectors must be deleted first (VectorCleanupJob) to avoid orphan vectors.';

-- 2. document → corpus: RESTRICT (do not delete corpus with documents)
ALTER TABLE ingestion.document
  DROP CONSTRAINT IF EXISTS document_corpus_id_fkey;
ALTER TABLE ingestion.document
  ADD CONSTRAINT document_corpus_id_fkey
  FOREIGN KEY (corpus_id)
  REFERENCES ingestion.corpus(id)
  ON DELETE RESTRICT;  -- explicit: must delete documents before corpus

-- 3. url_frontier → crawl_run: CASCADE (delete frontier when run deleted)
ALTER TABLE ingestion.url_frontier
  DROP CONSTRAINT IF EXISTS url_frontier_crawl_run_id_fkey;
ALTER TABLE ingestion.url_frontier
  ADD CONSTRAINT url_frontier_crawl_run_id_fkey
  FOREIGN KEY (crawl_run_id)
  REFERENCES ingestion.crawl_run(id)
  ON DELETE CASCADE;
```

**`CorpusResetService` — correct deletion order (important):**

```java
@Service
public class CorpusResetService {

    /**
     * Full corpus reset: delete all documents + chunks for a corpus.
     * ORDER MATTERS:
     *   1. Delete Qdrant vectors first (no FK, must be manual)
     *   2. Delete documents (cascade → chunks auto-deleted)
     *   3. Delete crawl_run rows (cascade → url_frontier auto-deleted)
     *
     * Never: delete corpus entity while documents exist (RESTRICT will block).
     */
    @Transactional
    public void resetCorpus(UUID corpusId) {
        // Step 1: Clean Qdrant vectors (external — no TX, must precede DB delete)
        vectorCleanupJob.deleteAllForCorpus(corpusId);

        // Step 2: Delete documents (cascade removes chunks automatically)
        documentRepository.deleteAllByCorpusId(corpusId);

        // Step 3: Delete crawl runs (cascade removes url_frontier)
        crawlRunRepository.deleteAllByCorpusId(corpusId);

        log.info("[corpus-reset] corpus={} fully reset", corpusId);
    }
}
```

**ფაილები:**
- V20 migration — FK cascade definitions for `chunk`, `url_frontier`
- New: `apps/ingestion-service/.../corpus/CorpusResetService.java` — safe deletion order

**Acceptance criteria:**
- `document` deletion → `chunk` rows automatically deleted (no manual cleanup needed).
- `url_frontier` deletion → `crawl_run` deletion cascades.
- `corpus` deletion blocked if documents exist (RESTRICT — data safety).
- `CorpusResetService.resetCorpus()` completes without FK violations.
- Qdrant vectors deleted before DB rows (no phantom vectors).

---

---

### PERF-05 — `FixedSizeChunker`: `\n\n` paragraph boundary ignored as split point 🔴

**Root cause — `FixedSizeChunker.java` line 36:**

```java
// chunk() line 36:
int wordBreak = normalized.lastIndexOf(' ', end);
// ↑ searches for SPACE char only
// \n\n paragraph boundary = two newlines — NOT found by lastIndexOf(' ')
// Result: chunk boundary falls at arbitrary word within a paragraph,
//         NOT at the semantically meaningful paragraph break
```

**After L-1-20's fix**, `joinedText()` produces `"\n\n"`-separated text:

```
"ბუნებრივი მოძრაობა 2024 წელს.\n\nGDP გაიზარდა 8.7%-ით.\n\nმოსახლეობა 3.7 მლნ."
```

But `FixedSizeChunker` ignores this structure:

```
maxChars=800 window ends mid-second paragraph:
  Chunk 1: "ბუნებრივი მოძრაობა 2024 წელს.\n\nGDP გაიზარდა 8.7%"   ← split mid-sentence
  Chunk 2: "ით.\n\nმოსახლეობა 3.7 მლნ."                          ← starts with fragment
```

**RAG impact:**
- Chunk 1 embedding: topic = natural movement + GDP mixed → diluted similarity
- Chunk 2: starts with "ით." (suffix) → meaningless first token → embedding noise
- User query "GDP 2024" → correct chunk never top-1 → chat misses the data

**Resolution — prefer `\n\n` paragraph boundary before word boundary:**

`apps/ingestion-service/src/main/java/com/geostat/ingestion/chunk/strategy/FixedSizeChunker.java`

```java
List<TextChunk> chunk(String text, int maxChars, int overlap) {
    if (text == null || text.isBlank() || maxChars <= 0) return List.of();
    String normalized = text.trim();
    if (normalized.length() <= maxChars) {
        return List.of(new TextChunk(0, normalized));
    }

    List<TextChunk> chunks = new ArrayList<>();
    int start    = 0;
    int sequence = 0;

    while (start < normalized.length()) {
        int end = Math.min(start + maxChars, normalized.length());

        if (end < normalized.length()) {
            // PRIORITY 1: paragraph boundary \n\n — semantically ideal split
            int paraBreak = normalized.lastIndexOf("\n\n", end);
            if (paraBreak > start + maxChars / 4) {
                // split AFTER the \n\n (include it in the previous chunk for context)
                end = paraBreak + 2;
            } else {
                // PRIORITY 2: word boundary (existing fallback)
                int wordBreak = normalized.lastIndexOf(' ', end);
                if (wordBreak > start + maxChars / 4) {
                    end = wordBreak;
                }
                // PRIORITY 3: hard cut at maxChars (last resort, no good boundary found)
            }
        }

        String piece = normalized.substring(start, end).trim();
        if (!piece.isEmpty()) {
            chunks.add(new TextChunk(sequence++, piece));
        }
        if (end >= normalized.length()) break;
        start = Math.max(end - overlap, start + 1);
    }
    return chunks;
}
```

**Unit tests:**

```java
// FixedSizeChunkerTest:

@Test
void chunk_splitsAtParagraphBoundary_preferredOverWordBoundary() {
    // Two paragraphs, total > maxChars
    String text = "ბუნებრივი მოძრაობა 2024 წელს შეადგინა 1.2 ათასი ადამიანი." +
                  "\n\n" +
                  "GDP გაიზარდა 8.7%-ით 2024 წელს — უმაღლესი 2019 წლის შემდეგ.";
    // set maxChars to force a split
    List<TextChunk> chunks = chunker.chunk(text, 80, 15);

    // Must split at \n\n, not mid-sentence
    assertThat(chunks).hasSizeGreaterThanOrEqualTo(2);
    // First chunk ends at paragraph boundary — no fragment start in second chunk
    assertThat(chunks.get(1).text()).doesNotStartWith("ით");
    assertThat(chunks.get(1).text()).startsWith("GDP");
}

@Test
void chunk_fallsBackToWordBoundary_whenNoParagraphBreakNearEnd() {
    // No \n\n in text — must still split at word boundary
    String text = "a".repeat(400) + " " + "b".repeat(400);
    List<TextChunk> chunks = chunker.chunk(text, 500, 50);
    assertThat(chunks).hasSizeGreaterThanOrEqualTo(2);
    // No chunk starts mid-word
    assertThat(chunks.get(1).text()).startsWith("b");
}

@Test
void chunk_shortText_returnsSingleChunk() {
    String text = "GDP 8.7%.\n\nMovement 1.2%.";
    assertThat(chunker.chunk(text, 800, 120)).hasSize(1);
}

@Test
void chunk_preservesStatisticalContent_intactWithinChunk() {
    String gdp = "GDP გაიზარდა 8.7%-ით 2024 წელს.";
    String pop  = "მოსახლეობა შეადგინა 3.7 მლნ. ადამიანი.";
    String text = gdp + "\n\n" + pop;
    List<TextChunk> chunks = chunker.chunk(text, 50, 10);
    // Each stat fact must appear COMPLETE in at least one chunk
    boolean gdpComplete = chunks.stream().anyMatch(c -> c.text().contains("8.7%"));
    boolean popComplete = chunks.stream().anyMatch(c -> c.text().contains("3.7 მლნ."));
    assertThat(gdpComplete).isTrue();
    assertThat(popComplete).isTrue();
}
```

**ფაილები:**
- Update: `apps/ingestion-service/.../chunk/strategy/FixedSizeChunker.java` — `\n\n` priority split

**Acceptance criteria:**
- Text with `\n\n` paragraph boundaries: chunk split at `\n\n`, not mid-paragraph.
- No chunk begins with a sentence fragment (word suffix like "ით.", "ს." etc).
- Statistical facts (numbers, %, Georgian units) stay within one chunk (not split mid-number).
- Fallback to word boundary when no `\n\n` near end of window.
- All existing `FixedSizeChunkerTest` tests pass (overlap, short text, hard-cut cases).

---

### OPS-01 — `S3RawHtmlArchive` disabled: parser fixes L-1-15…L-1-23 require full re-crawl 🟠

**Root cause — `CorpusReparseWorker.loadHtml()` line 92–99:**

```java
private Document loadHtml(CorpusEntity corpus, DocumentEntity document) throws Exception {
    if (document.getRawStorageKey() != null && !document.getRawStorageKey().isBlank()) {
        // Archive key present → load HTML from S3/MinIO — zero network cost
        var archived = rawHtmlArchive.load(document.getRawStorageKey());
        if (archived.isPresent()) {
            return Jsoup.parse(new String(archived.get()), document.getCanonicalUrl());
        }
    }
    return refetch(corpus, document).html(); // ← network re-crawl — costs time + server load
}
```

**Production state:**

```yaml
# application-custom.yml:
geostat:
  ingestion:
    archive:
      enabled: false   # ← NoOpRawHtmlArchive active
```

With `enabled=false`:
- `document.raw_storage_key = NULL` for ALL existing documents
- `CorpusReparseWorker`: falls to `refetch()` for every document
- 15,000 documents × avg 2.5s re-fetch = **10+ hours** to apply parser fixes

With `enabled=true` (MinIO configured):
- Every crawled page → stored as S3 object under `{corpusName}/{urlHash}.html`
- `CorpusReparseWorker`: reads from MinIO → re-parse in memory → no network
- 15,000 documents × avg 5ms parse = **~75 seconds** to apply ALL parser fixes

**Architectural decision (owner-level — must be made before deploying L-1-15..L-1-23):**

| Option | Cost | Benefit |
|--------|------|---------|
| Enable S3 archive now | MinIO setup (~1h), ~45MB storage | Re-parse in seconds forever after |
| Accept re-crawl for now | PERF-01 required (~45min after fix) | No infra change |
| Enable archive + retroactive backfill | Re-crawl once to populate archive | Future re-parses are instant |

**Recommended path:** Enable archive + re-crawl once (with PERF-01). Future parser fixes: instant.

**Steps to enable (if owner decides to):**

```yaml
# apps/ingestion-service/src/main/resources/application-custom.yml:
geostat:
  ingestion:
    archive:
      enabled: true
      provider: s3
      bucket: geostat-raw-html
      region: us-east-1
      endpoint: http://minio:9000   # local MinIO
      access-key: ${MINIO_ACCESS_KEY}
      secret-key: ${MINIO_SECRET_KEY}
```

```yaml
# docker-compose (ops/):
minio:
  image: minio/minio:latest
  command: server /data --console-address ":9001"
  environment:
    MINIO_ROOT_USER: ${MINIO_ACCESS_KEY}
    MINIO_ROOT_PASSWORD: ${MINIO_SECRET_KEY}
  volumes:
    - minio_data:/data
  ports:
    - "9000:9000"
    - "9001:9001"   # console
```

**This item is a decision gate for the junior:** implement L-1-15..L-1-23 first, THEN owner decides: enable archive → re-parse from archive, OR re-crawl.

**ფაილები:**
- Update: `apps/ingestion-service/src/main/resources/application-custom.yml` — archive block (if decision = enable)
- Update: `ops/docker-compose` or `ops/config/` — MinIO service (if decision = enable)

**Acceptance criteria (if archive enabled):**
- After one re-crawl: all documents have `raw_storage_key` set.
- `CorpusReparseWorker.reparseDocument()` uses archive, not network, for 100% of docs.
- `CorpusReparseService` can apply all parser fixes to 15,000 docs in < 5 minutes.

---

### ARCH-09 — `chunk.embedding_model`: vector versioning for safe model upgrade 🟠

**Root cause — `ChunkEntity` has `chunk_strategy` but no embedding model field:**

```java
// DocumentChunkWriter.replaceChunks() line 45:
entity.setChunkStrategy(FixedSizeChunker.STRATEGY_ID);  // "fixed-size-v1" ✅
// MISSING: entity.setEmbeddingModel("gemini/text-embedding-004")
```

**Scenario — Gemini text-embedding-005 released:**

```
Qdrant collection "geostat_chunks": dimension=768 (text-embedding-004)
New model: dimension=1024 → incompatible collection
OR same dimension but different semantic space → silent quality degradation

Without embedding_model field:
  → cannot query "which chunks used old model" → WHERE clause impossible
  → full collection wipe required (all chunks re-embedded)
  → no incremental migration path

With embedding_model field:
  → SELECT id FROM chunk WHERE embedding_model != 'gemini/text-embedding-005'
  → re-embed ONLY changed chunks → zero downtime migration
```

**Resolution:**

**Step 1 — V20 migration: `chunk.embedding_model` column:**

```sql
-- V20:
ALTER TABLE ingestion.chunk
  ADD COLUMN IF NOT EXISTS embedding_model TEXT;

COMMENT ON COLUMN ingestion.chunk.embedding_model IS
  'Identifier of the embedding model used: "gemini/text-embedding-004".
   Set by EmbeddingService at embed time (not at chunk creation time).
   NULL = not yet embedded.
   Use for: incremental re-embedding when model changes.
   Query: SELECT id FROM chunk WHERE embedding_model != :newModel → re-embed.';

-- Backfill existing embedded chunks with current model
UPDATE ingestion.chunk
SET embedding_model = 'gemini/text-embedding-004'
WHERE embedding_status = 'embedded'
  AND embedding_model IS NULL;
```

**Step 2 — `EmbeddingService`: set `embedding_model` on chunk after successful embed:**

```java
// EmbeddingService (or QdrantEmbeddingService):
public void embed(List<ChunkEntity> chunks) {
    // ... call Gemini embedding API ...
    String modelId = embeddingProperties.modelId(); // from application.yml

    for (ChunkEntity chunk : chunks) {
        // embed chunk.text → vector
        chunk.setEmbeddingStatus(EmbeddingStatus.EMBEDDED);
        chunk.setEmbeddingModel(modelId);  // ← set model version at embed time
        chunk.setEmbeddedAt(Instant.now());
    }
    chunkRepository.saveAll(chunks);

    // Qdrant payload: include embedding_model for metadata filter
    // payload.put("embedding_model", modelId);
}
```

**Step 3 — `embeddingProperties.modelId()` from config:**

```yaml
# application-custom.yml:
geostat:
  ingestion:
    embedding:
      model-id: "gemini/text-embedding-004"   # update here when upgrading
      retry-interval-ms: 900000
```

**Step 4 — `ModelMigrationJob`: incremental re-embedding on model upgrade:**

```java
@Component
public class ModelMigrationJob {

    /**
     * Re-embeds all chunks that were embedded with a different model than current.
     *
     * Triggered manually by operator after model upgrade.
     * Safe to re-run: idempotent — skips chunks already on current model.
     *
     * Estimated time: 15,000 docs × 15 chunks = 225,000 chunks × 50ms/batch = ~3h
     * Run with: POST /admin/embedding/migrate
     */
    public MigrationStats migrateToCurrentModel(UUID corpusId) {
        String currentModel = embeddingProperties.modelId();
        List<UUID> staleChunkIds = jdbc.queryForList("""
            SELECT id FROM ingestion.chunk
            WHERE corpus_id       = ?
              AND embedding_status = 'embedded'
              AND (embedding_model IS NULL OR embedding_model != ?)
            """, UUID.class, corpusId, currentModel);

        log.info("[model-migration] {} chunks to re-embed for model {}",
            staleChunkIds.size(), currentModel);

        // Batch re-embed
        Lists.partition(staleChunkIds, 100).forEach(embeddingQueue::enqueueAll);
        return new MigrationStats(staleChunkIds.size(), currentModel);
    }
}
```

**Unit tests:**

```java
// EmbeddingServiceTest:

@Test
void embed_setsEmbeddingModel_onChunk() {
    List<ChunkEntity> chunks = List.of(new ChunkEntity(), new ChunkEntity());
    service.embed(chunks);
    assertThat(chunks).allMatch(c ->
        "gemini/text-embedding-004".equals(c.getEmbeddingModel()));
}

// ModelMigrationJobTest:
@Test
void migrate_findsChunks_withOldModel() {
    // given: 5 chunks with "text-embedding-003", 3 with "text-embedding-004" (current)
    MigrationStats stats = job.migrateToCurrentModel(corpusId);
    assertThat(stats.chunksToMigrate()).isEqualTo(5);
    verify(embeddingQueue, times(1)).enqueueAll(argThat(ids -> ids.size() == 5));
}
```

**ფაილები:**
- V20 migration — `chunk.embedding_model` + backfill
- Update: `apps/ingestion-service/.../embedding/EmbeddingService.java` — set `embedding_model` post-embed
- New: `apps/ingestion-service/.../embedding/ModelMigrationJob.java`
- Update: `apps/ingestion-service/src/main/resources/application-custom.yml` — `embedding.model-id`

**Acceptance criteria:**
- After embed: `chunk.embedding_model = "gemini/text-embedding-004"`.
- Qdrant payload includes `embedding_model` field.
- On model upgrade: `ModelMigrationJob` identifies stale chunks → re-embeds only those.
- Zero chunks with `embedding_model = NULL` after first embed cycle.

---

### ARCH-10 — `QualityThresholds` global → per-corpus configuration 🟡

**Root cause — `CorpusReparseWorker.java` line 76 and `CrawlRunStore.java` line 254:**

```java
// Both use the same global bean:
decision = corpusQualityGate.evaluate(cleanResult.profileDocument().get(),
    qualityThresholds);  // ← injected ONCE at startup, same for ALL corpora
```

**Problem with two corpora:**

```
geostat-portal (statistical publications):
  - Dense tables, long structured data → minContentLength: 200 is reasonable

geostat-news (short news articles):
  - 2-3 paragraph articles → minContentLength: 200 rejects 30% of valid news
  - OR: if lowered to 80 → portal landing pages (100 chars) slip through
```

**Resolution — per-corpus `QualityThresholds` via `corpus-quality-gate.yaml`:**

**Step 1 — `CorpusQualityGateConfigLoader`: load thresholds per corpus (already exists for gate metrics):**

Extend existing `CorpusQualityGateConfig` to include threshold overrides:

```yaml
# ops/eval/corpus-quality-gate.yaml:
corpora:
  geostat-portal:
    thresholds:
      minContentLength: 200
      maxBoilerplateRatio: 0.6
    metrics:
      - id: truncation_rate
        # ...

  geostat-news:
    thresholds:
      minContentLength: 80    # ← lower: news articles are shorter
      maxBoilerplateRatio: 0.5
    metrics:
      - id: truncation_rate
        # ...
```

**Step 2 — `CrawlRunStore` and `CorpusReparseWorker`: resolve thresholds per corpus:**

```java
// Replace: corpusQualityGate.evaluate(doc, globalThresholds)
// With:
QualityThresholds corpusThresholds = qualityGateConfigLoader
    .thresholdsForCorpus(corpus.getName())
    .orElse(globalQualityThresholds);  // fallback to global if not configured

decision = corpusQualityGate.evaluate(cleanResult.profileDocument().get(),
    corpusThresholds);
```

**Unit tests:**

```java
// CrawlRunStoreTest:

@Test
void fetchAndPersist_uses_corpusSpecificThresholds() {
    // given: geostat-news corpus with minContentLength=80
    // and a document with 90-char content
    // global threshold: minContentLength=200 → would SKIP
    // corpus threshold: minContentLength=80  → should ACCEPT

    CorpusEntity newsCorpus = corpusNamed("geostat-news");
    FetchedPage page = pageWith(90charContent());

    PersistedPage result = store.persistFetched(frontierId, runId, page, configFor(newsCorpus));

    DocumentEntity doc = documentRepository.findById(result.documentId()).orElseThrow();
    assertThat(doc.getFetchStatus()).isEqualTo(DocumentFetchStatus.parsed); // ACCEPTED
}
```

**ფაილები:**
- Update: `ops/eval/corpus-quality-gate.yaml` — `thresholds` block per corpus
- Update: `apps/ingestion-service/.../parse/quality/CorpusQualityGateConfig.java` — `thresholds` field
- Update: `apps/ingestion-service/.../parse/quality/CorpusQualityGateConfigLoader.java` — `thresholdsForCorpus()`
- Update: `apps/ingestion-service/.../crawl/runner/CrawlRunStore.java` — per-corpus threshold resolution
- Update: `apps/ingestion-service/.../parse/reparse/CorpusReparseWorker.java` — same

**Acceptance criteria:**
- `geostat-news` corpus uses its own thresholds, not global.
- Global `QualityThresholds` bean used as fallback when corpus has no override.
- `corpus-quality-gate.yaml` YAML parse error → startup failure with clear message.

---

### PERF-06 — Parallel workers: per-worker `Thread.sleep` → domain-level `Semaphore` rate control 🟡

**Root cause — PERF-01 race condition with rate limit:**

```java
// CrawlRunner parallel worker (PERF-01):
CompletableFuture.runAsync(() -> {
    // ... fetch + persist ...
    Thread.sleep(config.rateLimitMs());  // ← per-worker, independent
}, pool)

// 8 workers, all finishing their previous page at ~same time:
// T=0ms:   W1 finishes sleep, starts fetch
// T=1ms:   W2 finishes sleep, starts fetch
// T=2ms:   W3 finishes sleep, starts fetch
// ...
// T=8ms:   W8 finishes sleep, starts fetch
// → 8 simultaneous HTTP requests to geostat.ge ← burst, not 500ms spacing
// Intended: 2 req/sec (1/500ms), Actual: 16 req/sec burst
```

**Resolution — `Semaphore` as domain-level token bucket:**

```java
// CrawlRunner: one semaphore shared by all workers for the same corpus/domain
private final ScheduledExecutorService rateClock =
    Executors.newSingleThreadScheduledExecutor();

void runCrawl(UUID runId) throws InterruptedException {
    RunConfig config = crawlRunStore.loadRunConfig(runId);
    crawlRunStore.markRunning(runId);

    // 1 permit = only 1 fetch starts at a time
    // Released after rateLimitMs → enforces global domain rate
    Semaphore domainRateLimiter = new Semaphore(1);

    ExecutorService pool = Executors.newFixedThreadPool(config.workerThreads(), ...);

    // ... batch loop ...
    CompletableFuture.runAsync(() -> {
        try {
            // Acquire before fetch — blocks until permit available
            domainRateLimiter.acquire();
            try {
                Optional<FetchedPage> page =
                    crawlRunStore.fetchHtml(frontierId, runId, config);
                // ...
            } finally {
                // Release after rateLimitMs — next worker can start
                rateClock.schedule(
                    domainRateLimiter::release,
                    config.rateLimitMs(),
                    TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }, pool)
    // ...
}
```

**Effect:**
```
T=0ms:   W1 acquires permit, starts fetch
T=500ms: W1 releases permit (scheduled), W2 acquires immediately
T=1000ms: W2 releases, W3 acquires
→ exactly 2 req/sec, regardless of number of workers
→ workers still process in parallel (parse + persist while W(n+1) fetches)
```

**Unit tests:**

```java
// CrawlRunnerTest:

@Test
void runCrawl_enforcesGlobalRateLimit_notPerWorker() throws Exception {
    // given: 4 workers, rateLimitMs=200ms, 8 pages
    // track HTTP fetch timestamps
    List<Long> fetchTimes = new CopyOnWriteArrayList<>();
    doAnswer(inv -> {
        fetchTimes.add(System.currentTimeMillis());
        return Optional.of(mockPage());
    }).when(crawlRunStore).fetchHtml(any(), any(), any());

    runner.runCrawl(runId);

    // verify: consecutive fetch times differ by >= 200ms (not all simultaneous)
    for (int i = 1; i < fetchTimes.size(); i++) {
        long gap = fetchTimes.get(i) - fetchTimes.get(i - 1);
        assertThat(gap).isGreaterThanOrEqualTo(190); // 10ms tolerance
    }
}
```

**ფაილები:**
- Update: `apps/ingestion-service/.../crawl/runner/CrawlRunner.java` — replace `Thread.sleep()` with `Semaphore` + `ScheduledExecutorService`

**Acceptance criteria:**
- With 8 workers and `rateLimitMs=500`: max 2 HTTP requests/sec to geostat.ge.
- Workers still process concurrently (parse/persist while next fetch waits for permit).
- `rateLimitMs=0`: no semaphore used (bypass for testing).
- Single domain never receives burst > 1 req per `rateLimitMs`.

---

---

## Enrichment / Reparse / Parser Gap Analysis — BUG-ENRICH-01..03 + PERF-12 + PARSE-GAP-01 + SCALE-01 + CFG-02

> სენიორ gap analysis: enrichment, reparse, playwright, backfill, JSON-LD, scalability.

---

### BUG-ENRICH-01 — `EnrichmentRunExecutor`: `@Transactional` + Gemini + retry sleep per service 🔴

**Root cause — `SummaryEnrichmentService.java` line 73 + `EnrichmentRunExecutor.java` line 68–88:**

```java
// SummaryEnrichmentService.java:
@Transactional                         // ← DB connection acquired HERE
public void enrichDocument(UUID documentId) {
    enrichmentRunExecutor.run(
        documentId, ...,
        document -> summaryDeriver.derive(toContext(document)),  // GEMINI CALL inside TX
        ...);
}

// EnrichmentRunExecutor.run():
run.setStatus(EnrichmentRunStatus.running);
enrichmentRunRepository.save(run);         // DB write (connection held)

T result = deriveWithRetry(document, maxRetries, derive); // GEMINI: 1–2s inside TX!
// if Gemini fails → sleepBackoff():
//   Thread.sleep(500L * attempt)  ← 500ms + 1000ms + 1500ms SLEEPING WHILE HOLDING CONNECTION
// maxRetries=3: up to 3000ms sleep + Gemini time = 5–6s total connection hold

persist.accept(document, result);
documentRepository.save(document);        // DB write
enrichmentRunRepository.save(run);        // DB write
// ← connection released AFTER 2–6 seconds
```

**PERF-09 context:** PERF-09 removes `@Transactional` from `DocumentEnrichmentOrchestrator`. But each individual service (`SummaryEnrichmentService`, `KeywordEnrichmentService`, `EntityEnrichmentService`, `TitleVectorEnrichmentService`, `SummaryVectorEnrichmentService`, `TopicAssignEnrichmentService`, `LocalePairEnrichmentService`, `PageKindEnrichmentService`) still has its own `@Transactional` wrapping the full `EnrichmentRunExecutor.run()` including the Gemini call and retry sleeps.

```
8 enrichment services × 1 @Transactional each × (1–2s Gemini + 0–3s retry sleep)
= each service holds a DB connection for up to 5–6 seconds during enrichment
With EnrichmentBackfillService running 1 doc at a time (PERF-12):
  connection held for: 8 services × 5s avg = 40s per document
  But services run sequentially per doc → HikariCP pool: 1 connection occupied for 40s
With parallel backfill (after PERF-12 fix): 10 docs × 1 connection = 10 connections occupied
```

**Resolution — three-phase split for `EnrichmentRunExecutor.run()`:**

ფაილი: `apps/ingestion-service/.../enrichment/runner/EnrichmentRunExecutor.java`

```java
/**
 * Three-phase execution to avoid holding DB connection during external calls.
 *
 * Phase 1 — LOAD + MARK RUNNING (short TX ~10ms)
 * Phase 2 — DERIVE (no TX — Gemini call + retries)
 * Phase 3 — PERSIST RESULT (short TX ~10ms)
 */
public <T> void run(
        UUID documentId,
        EnrichmentDeriverKind deriverKind,
        String modelVersion,
        int maxRetries,
        Predicate<DocumentEntity> skipWhen,
        Function<DocumentEntity, T> derive,
        BiConsumer<DocumentEntity, T> persist,
        String logLabel) {

    // Phase 1: load + idempotency check + mark running — short TX
    EnrichmentRunHandle handle = loadAndMarkRunning(
        documentId, deriverKind, modelVersion, skipWhen, logLabel);
    if (handle == null) return; // skipped or already completed

    Instant started = handle.startedAt();

    // Phase 2: external call — NO DB connection held
    // Retries + sleepBackoff() happen here WITHOUT a DB transaction
    try {
        T result = deriveWithRetry(handle.document(), maxRetries, derive);

        // Phase 3: save result — short TX
        saveResult(handle, result, persist, started, logLabel);
    } catch (Exception e) {
        markFailed(handle, e, started, logLabel);
    }
}

@Transactional
EnrichmentRunHandle loadAndMarkRunning(...) {
    DocumentEntity document = documentRepository.findById(documentId).orElse(null);
    if (document == null || skipWhen.test(document)) return null;

    if (enrichmentRunRepository.existsByDocument_IdAndDeriverKindAndModelVersionAndStatus(
            documentId, deriverKind, modelVersion, EnrichmentRunStatus.completed)) {
        return null;
    }
    EnrichmentRunEntity run = enrichmentRunRepository
        .findByDocument_IdAndDeriverKindAndModelVersion(documentId, deriverKind, modelVersion)
        .orElseGet(() -> newRun(document, deriverKind, modelVersion));
    if (run.getStatus() == EnrichmentRunStatus.completed) return null;

    run.setStatus(EnrichmentRunStatus.running);
    run.setStartedAt(Instant.now());
    run.setError(null);
    enrichmentRunRepository.save(run);
    return new EnrichmentRunHandle(run, document, Instant.now());
}

@Transactional
void saveResult(EnrichmentRunHandle handle, T result,
                BiConsumer<DocumentEntity, T> persist, Instant started, String logLabel) {
    DocumentEntity fresh = documentRepository.findById(handle.document().getId()).orElseThrow();
    persist.accept(fresh, result);
    documentRepository.save(fresh);
    Instant finished = Instant.now();
    EnrichmentRunEntity run = enrichmentRunRepository.findById(handle.run().getId()).orElseThrow();
    run.setStatus(EnrichmentRunStatus.completed);
    run.setFinishedAt(finished);
    run.setDurationMs((int)(finished.toEpochMilli() - started.toEpochMilli()));
    enrichmentRunRepository.save(run);
}

@Transactional
void markFailed(EnrichmentRunHandle handle, Exception e, Instant started, String logLabel) {
    Instant finished = Instant.now();
    EnrichmentRunEntity run = enrichmentRunRepository.findById(handle.run().getId()).orElseThrow();
    run.setStatus(EnrichmentRunStatus.failed);
    run.setFinishedAt(finished);
    run.setDurationMs((int)(finished.toEpochMilli() - started.toEpochMilli()));
    run.setError(truncate(e.getMessage(), 500));
    enrichmentRunRepository.save(run);
}

record EnrichmentRunHandle(EnrichmentRunEntity run, DocumentEntity document, Instant startedAt) {}
```

**Each calling service: remove `@Transactional` annotation:**

```java
// SummaryEnrichmentService.java — REMOVE @Transactional:
// @Transactional  ← DELETE THIS LINE
public void enrichDocument(UUID documentId) {
    enrichmentRunExecutor.run(...);
}
// Same for: KeywordEnrichmentService, EntityEnrichmentService,
// TitleVectorEnrichmentService, SummaryVectorEnrichmentService,
// TopicAssignEnrichmentService, LocalePairEnrichmentService, PageKindEnrichmentService
```

**Unit tests:**

```java
// EnrichmentRunExecutorTest:
@Test
void run_doesNotHoldTransaction_duringDeriveCall() {
    doAnswer(inv -> {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive())
            .as("No TX during Gemini call").isFalse();
        return "summary";
    }).when(deriveFunction).apply(any());

    executor.run(documentId, SUMMARY, "v1", 3, d -> false,
        deriveFunction, (d, r) -> {}, "test");
}

@Test
void run_doesNotHoldTransaction_duringRetryBackoff() {
    AtomicInteger attempt = new AtomicInteger(0);
    doAnswer(inv -> {
        if (attempt.getAndIncrement() < 2) throw new RuntimeException("rate limit");
        return "ok";
    }).when(deriveFunction).apply(any());

    // should NOT hold connection during the 500ms + 1000ms sleep
    executor.run(documentId, SUMMARY, "v1", 3, d -> false,
        deriveFunction, (d, r) -> {}, "test");

    verify(deriveFunction, times(3)).apply(any());
}
```

**ფაილები სარედაქციოდ:**
- `apps/ingestion-service/.../enrichment/runner/EnrichmentRunExecutor.java` — three-phase refactor
- Remove `@Transactional` from all 8 enrichment services

**Acceptance criteria:**
- [ ] `@Transactional` removed from all 8 enrichment service `enrichDocument()` methods
- [ ] `EnrichmentRunExecutor.run()` has no `@Transactional` on the public method
- [ ] `loadAndMarkRunning()`, `saveResult()`, `markFailed()` each have own short `@Transactional`
- [ ] `deriveWithRetry()` + `sleepBackoff()` executes with NO active DB transaction
- [ ] Integration: 10 concurrent enrichments → HikariCP pool usage ≤ 3 connections at any moment

---

### BUG-REPARSE-01 — `CorpusReparseWorker`: HTTP fetch inside `@Transactional` 🔴

**Root cause — `CorpusReparseWorker.java` lines 58–104:**

```java
@Transactional                                     // ← TX acquired
public ReparseOutcome reparseDocument(CorpusEntity corpus, UUID documentId) throws Exception {
    DocumentEntity document = documentRepository.findById(documentId).orElseThrow();
    var html = loadHtml(corpus, document);          // ← MAY CALL HTTP
    // ...
}

private org.jsoup.nodes.Document loadHtml(...) throws Exception {
    if (document.getRawStorageKey() != null && !document.getRawStorageKey().isBlank()) {
        var archived = rawHtmlArchive.load(document.getRawStorageKey()); // S3 load
        if (archived.isPresent()) {
            return Jsoup.parse(...); // ← OK: S3 read, fast
        }
    }
    return refetch(corpus, document).html(); // ← HTTP FETCH INSIDE TX when S3 disabled!
}
```

**When this triggers:** OPS-01 states `S3RawHtmlArchive` is disabled by default. With S3 disabled:
- `rawStorageKey` is null for all documents → always falls through to `refetch()`
- `refetch()` → `pageFetcher.fetch()` = HTTP request (crawler4j) inside open `@Transactional`
- HTTP request: 500ms–3s per page, connection held for entire duration

**Resolution — same PERF-02 pattern:**

```java
// CorpusReparseWorker.java — split @Transactional from HTTP:

public ReparseOutcome reparseDocument(CorpusEntity corpus, UUID documentId) throws Exception {
    // Phase 1: load document data — short TX
    DocumentEntity document = loadDocument(documentId);

    // Phase 2: get HTML — NO TX (may do HTTP fetch)
    org.jsoup.nodes.Document html = loadHtml(corpus, document);

    // Phase 3: parse + save — TX
    return parseAndPersist(corpus, document, html);
}

@Transactional(readOnly = true)
DocumentEntity loadDocument(UUID documentId) {
    return documentRepository.findById(documentId).orElseThrow();
}

// No @Transactional — may do HTTP
org.jsoup.nodes.Document loadHtml(CorpusEntity corpus, DocumentEntity document) throws Exception {
    if (document.getRawStorageKey() != null && !document.getRawStorageKey().isBlank()) {
        var archived = rawHtmlArchive.load(document.getRawStorageKey());
        if (archived.isPresent()) {
            return Jsoup.parse(new String(archived.get()), document.getCanonicalUrl());
        }
    }
    return refetch(corpus, document).html(); // HTTP here — no TX held
}

@Transactional
ReparseOutcome parseAndPersist(CorpusEntity corpus, DocumentEntity document,
                               org.jsoup.nodes.Document html) {
    // ... all existing parse + save + chunk + postPersist logic ...
}
```

**ფაილები სარედაქციოდ:**
- `apps/ingestion-service/.../parse/reparse/CorpusReparseWorker.java` — split `@Transactional`

**Acceptance criteria:**
- [ ] `@Transactional` removed from public `reparseDocument()` method
- [ ] `loadHtml()` / `refetch()` called outside any TX context
- [ ] `parseAndPersist()` wraps all DB writes in a single short `@Transactional`
- [ ] When S3 archive is disabled: HTTP fetch does not hold DB connection

---

### BUG-PLAYWRIGHT-01 — `PlaywrightRefetchService`: Playwright (3–10s) inside `@Transactional` 🔴

**Root cause — `PlaywrightRefetchService.java` lines 86–121:**

```java
@Transactional                                    // ← TX acquired
boolean refetchOne(CorpusEntity corpus, String url) {
    var page = playwrightFetcher.get().fetch(url); // PLAYWRIGHT: headless browser 3–10s!
    HtmlContentCleaner.CleanedContent cleaned = contentCleaner.clean(page.html());
    // ...
    documentRepository.save(document);
    documentChunkWriter.replaceChunks(...);        // DB writes inside TX
    postPersistPipeline.afterDocumentPersisted(...);
    return true;
}
// ← TX released AFTER Playwright completes (3–10 seconds)
```

**Playwright fetch time: 3–10s** (headless Chromium, JavaScript rendering). DB connection held for entire duration.

**Resolution:**

```java
// PlaywrightRefetchService.java:

boolean refetchOne(CorpusEntity corpus, String url) {
    try {
        // Phase 1: Playwright fetch — NO TX (3–10s)
        var page = playwrightFetcher.get().fetch(url);
        HtmlContentCleaner.CleanedContent cleaned = contentCleaner.clean(page.html());
        if (cleaned.text().length() < minContentChars) return false;

        // Phase 2: persist result — short TX
        persistRefetchedPage(corpus, url, page, cleaned);
        return true;
    } catch (Exception e) {
        return false;
    }
}

@Transactional
void persistRefetchedPage(CorpusEntity corpus, String url,
                          FetchedPage page, HtmlContentCleaner.CleanedContent cleaned) {
    String urlHash = UrlHasher.hash(url);
    DocumentEntity document = documentRepository
        .findByCorpusIdAndUrlHash(corpus.getId(), urlHash)
        .orElseGet(DocumentEntity::new);
    // ... set all fields ...
    documentRepository.save(document);
    documentChunkWriter.replaceChunks(...);
    localePairLinker.link(...);
    postPersistPipeline.afterDocumentPersisted(document.getId(), corpus.getId());
}
```

**ფაილები სარედაქციოდ:**
- `apps/ingestion-service/.../quality/PlaywrightRefetchService.java` — remove `@Transactional` from `refetchOne()`, add `persistRefetchedPage()` with `@Transactional`

**Acceptance criteria:**
- [ ] `@Transactional` removed from `refetchOne()`
- [ ] Playwright fetch and HTML clean happen outside any TX
- [ ] All DB writes in `persistRefetchedPage()` are atomic `@Transactional`
- [ ] Playwright timeout (10s default): no connection held during timeout

---

### PERF-12 — `EnrichmentBackfillService` + `CorpusReparseService`: sequential loops 🟠

**Root cause — `EnrichmentBackfillService.runBackfill()` line 110:**

```java
for (UUID documentId : documentIds) {       // 4,215 docs — sequential, no parallelism
    enrichmentOrchestrator.enrichDocumentForBackfill(documentId); // ~4–6s each
}
// Total: 4,215 × 5s avg = 5.9 hours
```

**`CorpusReparseService.runReparse()` line 97 — identical pattern:**

```java
for (UUID documentId : documentIds) {
    reparseWorker.reparseDocument(corpus, documentId); // ~2s each
}
// Total: 4,215 × 2s = 2.3 hours
```

**Resolution — use `ExecutorService` with configurable parallelism:**

`EnrichmentProperties.java` — add `backfillWorkerThreads`:

```yaml
# application-custom.yml:
geostat:
  ingestion:
    enrichment:
      backfill-worker-threads: 5   # 5 parallel enrichments
      reparse-worker-threads: 8    # 8 parallel reparses (parse is CPU-bound, no LLM)
```

`EnrichmentBackfillService.runBackfill()`:

```java
void runBackfill(String corpusName, List<UUID> documentIds, Instant startedAt) {
    int threads = enrichmentProperties.backfillWorkerThreads(); // default: 5
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    AtomicInteger processed = new AtomicInteger(0);

    try {
        List<CompletableFuture<Void>> futures = documentIds.stream()
            .map(documentId -> CompletableFuture.runAsync(() -> {
                try {
                    enrichmentOrchestrator.enrichDocumentForBackfill(documentId);
                    int done = processed.incrementAndGet();
                    progress.set(EnrichmentBackfillProgress.running(
                        corpusName, documentIds.size(), done, startedAt));
                } catch (Exception e) {
                    log.warn("Enrichment backfill failed document {}: {}",
                        documentId, e.getMessage());
                    processed.incrementAndGet();
                }
            }, pool))
            .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    } finally {
        pool.shutdown();
        backfillRunning.set(false);
    }

    catalogRefreshAfterBatch.refreshIfConfigured("enrichment-backfill");
    // ...
}
```

```
After fix:
  Backfill 5 workers × 5s avg = 4,215 ÷ 5 × 5s = ~70 minutes (5× faster)
  Reparse  8 workers × 2s avg = 4,215 ÷ 8 × 2s = ~18 minutes (8× faster)

Note: Gemini rate limit (1,500 req/min) is the real bottleneck for enrichment.
With 5 workers × ~3 LLM calls/doc/5s = ~180 req/min — safely within quota.
```

**ფაილები სარედაქციოდ:**
- `apps/ingestion-service/.../enrichment/runner/EnrichmentBackfillService.java` — `ExecutorService` with configurable threads
- `apps/ingestion-service/.../parse/reparse/CorpusReparseService.java` — same pattern
- `apps/ingestion-service/.../enrichment/runner/EnrichmentProperties.java` — add `backfillWorkerThreads()`
- `apps/ingestion-service/src/main/resources/application-custom.yml` — add `backfill-worker-threads: 5`

**Acceptance criteria:**
- [ ] Backfill and reparse run parallel (configurable thread count)
- [ ] `AtomicInteger` counter for accurate progress reporting
- [ ] Pool shut down properly after completion (no thread leak)
- [ ] Gemini quota: 5 workers stays well within 1,500 req/min limit
- [ ] `backfillRunning` flag correctly released even if pool throws

---

### PARSE-GAP-01 — JSON-LD structured data: `publishedAt`, `navBreadcrumb`, `@type` ignored 🟠

**Root cause — `PageDisplayMetadataExtractor.extract()` line 16–27:**

```java
public DisplayMetadata extract(Document html, String pageTitle, List<String> sectionPath) {
    String metaDescription = firstNonBlank(
        metaContent(html, "description"),       // <meta name="description">
        metaProperty(html, "og:description"),   // og:description
        metaProperty(html, "twitter:description"));
    // <script type="application/ld+json"> — COMPLETELY IGNORED
}
```

geostat.ge pages have JSON-LD with exactly the data we need for L-1-14 (`publishedAt`) and L-1-17 (`navBreadcrumb`):

```json
{
  "@context": "https://schema.org",
  "@type": "Article",
  "datePublished": "2025-03-15T10:00:00+04:00",
  "description": "საქართველოს მოსახლეობის ბუნებრივი მოძრაობა...",
  "breadcrumb": {
    "@type": "BreadcrumbList",
    "itemListElement": [
      {"@type": "ListItem", "position": 1, "name": "სტატისტიკა"},
      {"@type": "ListItem", "position": 2, "name": "მოსახლეობა"},
      {"@type": "ListItem", "position": 3, "name": "ბუნებრივი მოძრაობა"}
    ]
  }
}
```

**`@type` for page_kind detection (free, deterministic):**

```json
{ "@type": "Dataset" }      → page_kind = "dataset"
{ "@type": "NewsArticle" }  → page_kind = "news"
{ "@type": "FAQPage" }      → page_kind = "faq"
{ "@type": "WebPage" }      → page_kind = "portal" (if no better match)
```

**Resolution — new `JsonLdExtractor` utility:**

ფაილი: `apps/ingestion-service/src/main/java/com/geostat/ingestion/parse/JsonLdExtractor.java`

```java
/**
 * Extracts structured data from <script type="application/ld+json"> elements.
 *
 * Provides:
 *   - publishedAt:    Article.datePublished (ISO-8601 string)
 *   - navBreadcrumb:  BreadcrumbList.itemListElement names joined with " > "
 *   - pageType:       schema.org @type → maps to our PageKind
 *   - description:    Article.description (higher quality than meta description)
 */
@Component
public class JsonLdExtractor {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public record JsonLdResult(
        @Nullable String publishedAt,
        @Nullable String navBreadcrumb,
        @Nullable String description,
        @Nullable String schemaType       // raw @type value
    ) {}

    public JsonLdResult extract(Document html) {
        Elements scripts = html.select("script[type=application/ld+json]");
        for (Element script : scripts) {
            String json = script.data().strip();
            if (json.isEmpty()) continue;
            try {
                JsonNode root = MAPPER.readTree(json);
                // Handle @graph array (some sites wrap in @graph)
                if (root.has("@graph") && root.get("@graph").isArray()) {
                    for (JsonNode node : root.get("@graph")) {
                        JsonLdResult r = extractFromNode(node);
                        if (r.hasAnyData()) return r;
                    }
                }
                JsonLdResult r = extractFromNode(root);
                if (r.hasAnyData()) return r;
            } catch (Exception e) {
                // malformed JSON-LD — skip silently
            }
        }
        return new JsonLdResult(null, null, null, null);
    }

    private JsonLdResult extractFromNode(JsonNode node) {
        String publishedAt  = textOrNull(node, "datePublished");
        String description  = textOrNull(node, "description");
        String schemaType   = textOrNull(node, "@type");
        String breadcrumb   = extractBreadcrumb(node);
        return new JsonLdResult(publishedAt, breadcrumb, description, schemaType);
    }

    private String extractBreadcrumb(JsonNode node) {
        // Try direct breadcrumb field
        JsonNode bc = node.get("breadcrumb");
        if (bc == null) bc = node; // node itself might be a BreadcrumbList
        if (bc == null) return null;

        JsonNode items = bc.get("itemListElement");
        if (items == null || !items.isArray()) return null;

        List<String> names = new ArrayList<>();
        for (JsonNode item : items) {
            String name = textOrNull(item, "name");
            if (name != null && !name.isBlank()) names.add(name.strip());
        }
        if (names.isEmpty()) return null;
        return String.join(" > ", names);
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode f = node.get(field);
        return (f != null && f.isTextual() && !f.asText().isBlank()) ? f.asText().strip() : null;
    }
}
```

**Integration into `JsoupContentExtractor` (or `PageDisplayMetadataExtractor`):**

```java
// JsoupContentExtractor.extract() — after existing metadata extraction:

JsonLdExtractor.JsonLdResult jsonLd = jsonLdExtractor.extract(html);

// publishedAt: JSON-LD source > <time datetime="..."> fallback (L-1-14)
String publishedAt = jsonLd.publishedAt() != null
    ? jsonLd.publishedAt()
    : extractTimeElement(html);

// navBreadcrumb: JSON-LD BreadcrumbList > CSS nav fallback (L-1-17)
String navBreadcrumb = jsonLd.navBreadcrumb() != null
    ? jsonLd.navBreadcrumb()
    : SectionPathExtractor.extractNavBreadcrumb(html);

// description: JSON-LD description > og:description > meta description
String description = jsonLd.description() != null
    ? jsonLd.description()
    : resolvedMetaDescription;
```

**`@type` → `page_kind` mapping in `PageKindEnrichmentService`:**

```java
// PageKindEnrichmentService or JsonLdExtractor:
public static String schemaTypeToPageKind(String schemaType) {
    if (schemaType == null) return null;
    return switch (schemaType.toLowerCase()) {
        case "dataset"         -> "dataset";
        case "newsarticle",
             "article"         -> "news";
        case "faqpage"         -> "faq";
        case "report",
             "technicalarticle"-> "report";
        default                -> null; // unknown — let other detection run
    };
}
```

**Unit tests:**

```java
// JsonLdExtractorTest:

@Test
void extract_parsesDatePublished() {
    String html = "<html><head><script type='application/ld+json'>"
        + "{\"@type\":\"Article\",\"datePublished\":\"2025-03-15T10:00:00+04:00\"}"
        + "</script></head></html>";
    JsonLdExtractor.JsonLdResult r = extractor.extract(Jsoup.parse(html));
    assertThat(r.publishedAt()).isEqualTo("2025-03-15T10:00:00+04:00");
}

@Test
void extract_parsesGeorgianBreadcrumb() {
    String html = "<html><head><script type='application/ld+json'>"
        + "{\"breadcrumb\":{\"@type\":\"BreadcrumbList\","
        + "\"itemListElement\":["
        + "{\"name\":\"სტატისტიკა\"},{\"name\":\"მოსახლეობა\"},{\"name\":\"ბუნებრივი მოძრაობა\"}"
        + "]}}</script></head></html>";
    JsonLdExtractor.JsonLdResult r = extractor.extract(Jsoup.parse(html));
    assertThat(r.navBreadcrumb()).isEqualTo("სტატისტიკა > მოსახლეობა > ბუნებრივი მოძრაობა");
}

@Test
void extract_returnsEmpty_forMalformedJson() {
    String html = "<html><head><script type='application/ld+json'>{invalid}</script></head></html>";
    JsonLdExtractor.JsonLdResult r = extractor.extract(Jsoup.parse(html));
    assertThat(r.publishedAt()).isNull();
    assertThat(r.navBreadcrumb()).isNull();
}
```

**ფაილები სარედაქციოდ:**
- New: `apps/ingestion-service/.../parse/JsonLdExtractor.java`
- Update: `apps/ingestion-service/.../parse/profile/JsoupContentExtractor.java` — inject `JsonLdExtractor`, use for `publishedAt`, `navBreadcrumb`, `description`
- Update: `apps/ingestion-service/.../enrichment/pagekind/PageKindEnrichmentService.java` — `schemaTypeToPageKind()` for deterministic detection

**Acceptance criteria:**
- [ ] `JsonLdExtractor` parses `datePublished` → `publishedAt` field
- [ ] `BreadcrumbList.itemListElement` → `navBreadcrumb` (Georgian characters preserved)
- [ ] `@type: Dataset/NewsArticle/FAQPage` → `page_kind` without Gemini call
- [ ] Malformed JSON-LD: silently skipped, no exception propagation
- [ ] `@graph` wrapper: correctly handled
- [ ] L-1-14 `publishedAt` uses JSON-LD as primary source, `<time>` as fallback
- [ ] L-1-17 `navBreadcrumb` uses JSON-LD as primary source, CSS nav as fallback

---

### SCALE-01 — Backfill + Reparse: full document ID list loaded into memory 🟡

**Root cause — `EnrichmentBackfillService.resolveDocumentIds()` line 93–100:**

```java
return documentRepository
    .findByCorpus_IdAndFetchStatus(corpusId, DocumentFetchStatus.parsed)
    .stream()
    .map(DocumentEntity::getId)  // ← loads ALL DocumentEntity objects, extracts ID
    .toList();                   // ← full list in heap
// 4,215 now: ~4,215 DocumentEntity objects in heap = ~8MB (manageable)
// 500,000 docs: ~500,000 DocumentEntity × ~2KB avg = ~1GB heap → OOM
```

**`CorpusReparseService.queueReparse()` line 68 — identical pattern.**

**Resolution — dedicated repository method returning only IDs:**

`DocumentRepository.java`:

```java
/**
 * Returns document IDs only — avoids loading full entity graph into memory.
 * Scales to 500K+ documents without heap pressure.
 */
@Query("SELECT d.id FROM DocumentEntity d " +
       "WHERE d.corpus.id = :corpusId AND d.fetchStatus = :status")
List<UUID> findIdsByCorpusIdAndFetchStatus(
    @Param("corpusId") UUID corpusId,
    @Param("fetchStatus") DocumentFetchStatus status);
```

```java
// EnrichmentBackfillService.resolveDocumentIds() — REPLACE:
// OLD:
return documentRepository.findByCorpus_IdAndFetchStatus(corpusId, DocumentFetchStatus.parsed)
    .stream().map(DocumentEntity::getId).toList();

// NEW:
return documentRepository.findIdsByCorpusIdAndFetchStatus(corpusId, DocumentFetchStatus.parsed);
```

**Same fix for `CorpusReparseService.queueReparse()`.**

**ფაილები სარედაქციოდ:**
- `apps/ingestion-service/.../persistence/repository/DocumentRepository.java` — `findIdsByCorpusIdAndFetchStatus()`
- `apps/ingestion-service/.../enrichment/runner/EnrichmentBackfillService.java` — use new method
- `apps/ingestion-service/.../parse/reparse/CorpusReparseService.java` — use new method

**Acceptance criteria:**
- [ ] `findByCorpus_IdAndFetchStatus()` not called for ID-only use cases
- [ ] `findIdsByCorpusIdAndFetchStatus()` returns `List<UUID>` with SELECT d.id only
- [ ] 500K documents: query loads ~16MB UUIDs (not ~1GB entities)

---

### CFG-02 — `respectRobotsTxt`: corpus policy requires explicit `true` 🟡

**Root cause — `Crawler4jPageFetcher.fetch()` line 55:**

```java
if (CorpusPolicy.respectRobotsTxt(corpus) && !infrastructure.robotsServer().allows(webUrl)) {
    throw new RobotsBlockedException(url);
}
```

`respectRobotsTxt` is **conditional** — if false or not configured, robots.txt is completely bypassed. `geostat-portal-policy.yaml` does not explicitly set this field.

**What geostat.ge/robots.txt says:**

```
User-agent: *
Crawl-delay: 1
Disallow: /ka/contact
Disallow: /en/contact
Disallow: /ka/search
Disallow: /en/search
```

If `respectRobotsTxt=false`: crawler hits `/ka/search` with automated queries → potential IP ban.

**Resolution — add explicit setting to corpus policy YAML:**

`ops/config/corpus/geostat-portal-policy.yaml`:

```yaml
crawl:
  respectRobotsTxt: true   # ← add explicitly; ethical compliance + avoids IP ban
  crawlDelay: 1000         # ← matches robots.txt Crawl-delay: 1 (1 second)
```

**Verify `CorpusPolicy.respectRobotsTxt()` default:**

```java
// CorpusPolicy.java — ensure default is TRUE (safe default):
public static boolean respectRobotsTxt(CorpusEntity corpus) {
    CrawlConfig config = corpus.getCrawlConfig();
    if (config == null) return true;  // ← default: respect robots.txt
    return config.isRespectRobotsTxt(); // must default to true in CrawlConfig
}
```

**ფაილები სარედაქციოდ:**
- `ops/config/corpus/geostat-portal-policy.yaml` — add `respectRobotsTxt: true` + `crawlDelay: 1000`
- `apps/ingestion-service/.../crawl/policy/CorpusPolicy.java` — verify `respectRobotsTxt()` defaults to `true`

**Acceptance criteria:**
- [ ] `geostat-portal-policy.yaml` explicitly declares `respectRobotsTxt: true`
- [ ] `CorpusPolicy.respectRobotsTxt()` returns `true` when corpus config is null (safe default)
- [ ] `/ka/search`, `/en/search`, `/ka/contact` blocked by robots.txt check
- [ ] `crawlDelay: 1000` matches robots.txt `Crawl-delay: 1`

---
