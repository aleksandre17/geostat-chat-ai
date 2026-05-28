> **Session 2026-05-27 (continued):** L-1-12 (redirect canonical_url + original_url migration), L-1-13 (CuratedUrlVerifier startup check), L-1-14 (publishedAt extraction), L-1-15 (nested element dedup ancestor-aware) implemented.
> **Session 2026-05-27:** L-1-16 (languageDefaultFallback in inferLanguage), L-1-17 (navBreadcrumb field + SectionPathExtractor + V21 migration), L-1-18 (removeSelectors YAML expansion), L-1-19 (addSelectors ParseProfile + extractBody merge) implemented.
> **Session 2026-05-27:** L-1-20 (ExtractionStats blocks list + joinedText paragraph boundaries), L-1-21 (TextSanitizer PUA/invisible/bidi removal), L-1-22 (legacyClean WARN + TextSanitizer + hardcoded boilerplate) implemented.
### L-1-08 — `DocumentValidationPipeline`: parse-შემდეგ validation gate 🔴

**პრობლემა — root cause:**
DB-ში ხვდება: ტექსტი შუა სიტყვაში გაჭრილი, გამეორებული პარაგრაფები, ცარიელი content, null language. Parser-ი HTML-ს ამოიღებს მაგრამ **validation gate არ არსებობს** — ნებისმიერი output-ი DB-ში ჩაიწერება.

**5 გაუმჯობესება ჩვეულებრივ design-თან შედარებით:**
1. `DocumentQuality.SKIP` — portal pages: persisted but NOT indexed (not REJECT, not GOOD)
2. `content_hash` normalization — strip + lowercase + whitespace-collapse before SHA-256
3. `ValidationSummaryLogger` → `truncation_rate` + `repetition_rate` metrics DB query-ით
4. `ON CONFLICT DO NOTHING` — chunk insert DB-level last defense
5. MV rebuilt with `quality_score IN ('good','degraded') AND page_kind != 'portal'`

**Resolution steps:**

**ნაბიჯი 1 — `DocumentQuality` enum `libs/platform-contracts`-ში:**

შექმენი:
`libs/platform-contracts/src/main/java/com/geostat/platform/parse/DocumentQuality.java`

```java
package com.geostat.platform.parse;

/**
 * Quality classification of a parsed document.
 *
 * GOOD     — passes all validators; persisted + indexed in Qdrant
 * DEGRADED — minor issues found (auto-fixed where possible); persisted + indexed
 * SKIP     — valid HTML but zero statistical content (e.g. portal landing page);
 *            persisted (fetch_status='parsed') but excluded from MV + Qdrant
 * REJECT   — content too short or structurally invalid; NOT persisted to DB
 */
public enum DocumentQuality {
    GOOD, DEGRADED, SKIP, REJECT
}
```

**ნაბიჯი 2 — `ValidationResult` record:**

შექმენი:
`libs/platform-contracts/src/main/java/com/geostat/platform/parse/ValidationResult.java`

```java
public record ValidationResult(
    DocumentQuality  quality,
    List<String>     violations,
    CleanedDocument  fixedDoc      // null if no fix; non-null if auto-corrected
) {
    public static ValidationResult ok() {
        return new ValidationResult(DocumentQuality.GOOD, List.of(), null);
    }
    public static ValidationResult degrade(String code, String detail) {
        return new ValidationResult(DocumentQuality.DEGRADED,
            List.of(code + ": " + detail), null);
    }
    public static ValidationResult fixed(String code, String detail, CleanedDocument doc) {
        return new ValidationResult(DocumentQuality.DEGRADED,
            List.of(code + ": " + detail), doc);
    }
    public static ValidationResult skip(String reason) {
        return new ValidationResult(DocumentQuality.SKIP,
            List.of("skip: " + reason), null);
    }
    public static ValidationResult reject(String code, String detail) {
        return new ValidationResult(DocumentQuality.REJECT,
            List.of(code + ": " + detail), null);
    }
    public boolean isRejected() { return quality == DocumentQuality.REJECT; }
    public boolean isSkip()     { return quality == DocumentQuality.SKIP; }
}
```

**ნაბიჯი 3 — `DocumentValidator` port:**

შექმენი:
`libs/platform-contracts/src/main/java/com/geostat/platform/parse/DocumentValidator.java`

```java
public interface DocumentValidator {
    ValidationResult validate(CleanedDocument doc, ParseProfile profile);
}
```

**ნაბიჯი 4 — `ValidationOutcome` record:**

```java
public record ValidationOutcome(
    CleanedDocument  document,
    DocumentQuality  quality,
    List<String>     violations
) {
    public boolean isRejected()    { return quality == DocumentQuality.REJECT; }
    public boolean isSkip()        { return quality == DocumentQuality.SKIP; }
    public boolean shouldPersist() { return quality != DocumentQuality.REJECT; }
}
```

**ნაბიჯი 5 — 4 concrete validators:**

პაკეტი: `apps/ingestion-service/src/main/java/com/geostat/ingestion/parse/validation/`

```java
// MinContentLengthValidator.java
@Component
public class MinContentLengthValidator implements DocumentValidator {
    private static final int REJECT_BELOW  = 30;
    private static final int DEGRADE_BELOW = 100;

    @Override
    public ValidationResult validate(CleanedDocument doc, ParseProfile profile) {
        int len = doc.body() == null ? 0 : doc.body().strip().length();
        if (len < REJECT_BELOW)  return ValidationResult.reject("content_too_short", "length=" + len);
        if (len < DEGRADE_BELOW) return ValidationResult.degrade("content_short",    "length=" + len);
        if (PageKind.PORTAL.equals(doc.pageKind()))
            return ValidationResult.skip("portal_landing_no_statistical_content");
        return ValidationResult.ok();
    }
}

// TruncationDetector.java
@Component
public class TruncationDetector implements DocumentValidator {
    private static final int MIN_LENGTH = 200;
    private static final int LOOK_BACK  = 40;

    @Override
    public ValidationResult validate(CleanedDocument doc, ParseProfile profile) {
        String body = doc.body();
        if (body == null || body.length() < MIN_LENGTH) return ValidationResult.ok();
        String tail = body.substring(body.length() - Math.min(LOOK_BACK, body.length()));
        boolean endsWithPunctuation = tail.matches(".*[.!?:;\u00bb\\])\"]\\s*$");
        boolean endsWithShortWord   = tail.matches(".*\\s+\\S{1,4}\\s*$");
        if (!endsWithPunctuation && !endsWithShortWord) {
            return ValidationResult.degrade("truncated_text", "ends abruptly: ..." + tail.strip());
        }
        return ValidationResult.ok();
    }
}

// ParagraphRepetitionDetector.java — AUTO-FIX
@Component
public class ParagraphRepetitionDetector implements DocumentValidator {
    private static final int MIN_PARA_LEN = 25;

    @Override
    public ValidationResult validate(CleanedDocument doc, ParseProfile profile) {
        if (doc.body() == null || doc.body().isBlank()) return ValidationResult.ok();
        String[] paragraphs = doc.body().split("[\\r\\n]{2,}");
        Set<String> seen = new LinkedHashSet<>();
        int duplicates = 0;
        for (String p : paragraphs) {
            String key = p.strip().toLowerCase().replaceAll("\\s+", " ");
            if (key.length() < MIN_PARA_LEN) { seen.add(p.strip()); continue; }
            if (!seen.add(key)) duplicates++;
        }
        if (duplicates > 0) {
            String fixed = String.join("\n\n", seen);
            return ValidationResult.fixed("paragraph_duplicates_removed",
                duplicates + " duplicates removed", doc.withBody(fixed));
        }
        return ValidationResult.ok();
    }
}

// LanguageConsistencyValidator.java — AUTO-FIX
@Component
public class LanguageConsistencyValidator implements DocumentValidator {
    @Override
    public ValidationResult validate(CleanedDocument doc, ParseProfile profile) {
        if (doc.language() != null && !doc.language().isBlank()) return ValidationResult.ok();
        String fallback = (profile.language() != null
            && profile.language().defaultFallback() != null)
            ? profile.language().defaultFallback() : "ka";
        return ValidationResult.fixed("language_null_fallback",
            "set to defaultFallback=" + fallback, doc.withLanguage(fallback));
    }
}
```

**ნაბიჯი 6 — `DocumentValidationPipeline`:**

```java
@Component
public class DocumentValidationPipeline {

    private final List<DocumentValidator> validators; // Spring auto-collects all @Component beans

    public ValidationOutcome validate(CleanedDocument doc, ParseProfile profile) {
        CleanedDocument current = doc;
        List<String> allViolations = new ArrayList<>();
        DocumentQuality worst = DocumentQuality.GOOD;

        for (DocumentValidator v : validators) {
            ValidationResult r = v.validate(current, profile);
            allViolations.addAll(r.violations());
            if (r.quality().ordinal() > worst.ordinal()) worst = r.quality();
            if (r.isRejected()) {
                log.warn("[validation] REJECT url={} reasons={}", doc.canonicalUrl(), r.violations());
                return new ValidationOutcome(doc, DocumentQuality.REJECT, allViolations);
            }
            if (r.fixedDoc() != null) current = r.fixedDoc(); // chain auto-fix
            if (r.isSkip()) break; // no further checks needed
        }
        return new ValidationOutcome(current, worst, allViolations);
    }
}
```

**ნაბიჯი 7 — `DocumentIngestionPipeline`-ში pipeline call:**

```java
// after extraction:
ValidationOutcome outcome = validationPipeline.validate(extracted, profile);

if (outcome.isRejected()) {
    metrics.increment("documents.rejected");
    return; // NOT persisted
}

Document document = mapper.toEntity(outcome.document())
    .withQualityScore(outcome.quality().name().toLowerCase())
    .withValidationViolations(outcome.violations().toArray(String[]::new))
    .withPageKind(pageKindDetector.detect(fetchedPage, profile));

documentRepository.upsert(document);

if (outcome.isSkip()) return; // persisted, but NOT enqueued for enrichment/Qdrant
```

**Unit tests:**

```java
// MinContentLengthValidatorTest:
@Test void rejects_emptyBody()    { assertThat(validate("")).isRejected(); }
@Test void skips_portalPage()     { assertThat(validate(portalDoc)).isSkip(); }
@Test void degrades_shortBody()   { assertThat(validate("x".repeat(50))).isDegraded(); }

// TruncationDetectorTest:
@Test void degrades_abruptEnd()   {
    String long_truncated = "x".repeat(300) + "მარც";
    assertThat(validate(long_truncated)).isDegraded().hasViolation("truncated_text");
}
@Test void ok_properEnding()      { assertThat(validate(TEXT_ENDING_WITH_PERIOD)).isGood(); }

// ParagraphRepetitionDetectorTest:
@Test void removesRepetitions_autoFix() {
    String body = "პირველი პარაგრაფი.\n\nმეორე.\n\nპირველი პარაგრაფი.";
    ValidationResult r = validator.validate(docWithBody(body), profile);
    assertThat(r.fixedDoc().body()).doesNotContain("პირველი პარაგრაფი.\n\nპირველი");
    assertThat(r.violations()).anyMatch(v -> v.contains("paragraph_duplicates_removed"));
}

// DocumentValidationPipelineTest:
@Test void chainsAutoFix_acrossValidators() {
    // doc: duplicate paragraphs + null language
    // pipeline: duplicates removed AND language set to "ka"
    ValidationOutcome o = pipeline.validate(dirtyDoc, profile);
    assertThat(o.document().language()).isEqualTo("ka");
    assertThat(countParagraphs(o.document().body())).isLessThan(originalCount);
}
@Test void stopsOnReject_immediately() {
    ValidationOutcome o = pipeline.validate(emptyDoc, profile);
    assertThat(o.isRejected()).isTrue();
}
```

**ფაილები:**
- New: `libs/platform-contracts/.../parse/DocumentQuality.java`
- New: `libs/platform-contracts/.../parse/ValidationResult.java`
- New: `libs/platform-contracts/.../parse/DocumentValidator.java`
- New: `libs/platform-contracts/.../parse/ValidationOutcome.java`
- New: `apps/ingestion-service/.../parse/validation/MinContentLengthValidator.java`
- New: `apps/ingestion-service/.../parse/validation/TruncationDetector.java`
- New: `apps/ingestion-service/.../parse/validation/ParagraphRepetitionDetector.java`
- New: `apps/ingestion-service/.../parse/validation/LanguageConsistencyValidator.java`
- New: `apps/ingestion-service/.../parse/validation/DocumentValidationPipeline.java`
- Update: `apps/ingestion-service/.../DocumentIngestionPipeline.java`

**Acceptance criteria:**
- Empty body → REJECT, NOT in DB.
- Duplicate paragraphs → AUTO-FIX, `validation_violations` contains `paragraph_duplicates_removed`.
- Portal page kind → SKIP: persisted to DB, excluded from enrichment queue and Qdrant.
- Null language → AUTO-FIX to `defaultFallback: ka`.
- REJECT stops pipeline immediately; auto-fixes chain correctly across validators.
- All unit tests pass.

---

### L-1-09 — `chunk.content_hash`: DB-level chunk dedup 🟠

**პრობლემა — root cause:**
Chunking pipeline re-run → same text re-chunked → duplicate `chunk` rows in DB.
Pagination duplicates (L-1-04 fix შემდეგ) სადაც კვლავ ანალოგიური content მოვიდა → near-identical chunks → Qdrant-ში duplicate vectors → retrieval quality degrades.

**Resolution steps:**

**ნაბიჯი 1 — V19 Flyway migration:**

შექმენი:
`apps/ingestion-service/src/main/resources/db/migration/V19__chunk_content_hash_document_quality.sql`

```sql
-- V19: chunk dedup hash + document quality columns + MV quality filter

-- 1. document quality tracking
ALTER TABLE ingestion.document
  ADD COLUMN IF NOT EXISTS quality_score VARCHAR(16)
    CHECK (quality_score IN ('good', 'degraded', 'skip', 'rejected')),
  ADD COLUMN IF NOT EXISTS validation_violations TEXT[];

COMMENT ON COLUMN ingestion.document.quality_score IS
  'good | degraded | skip | rejected. skip = valid HTML, zero statistical content.
   rejected = not persisted (value only in audit log).';

COMMENT ON COLUMN ingestion.document.validation_violations IS
  'Array of violation codes: {truncated_text, paragraph_duplicates_removed,
   language_null_fallback, content_too_short, ...}';

UPDATE ingestion.document SET quality_score = 'good' WHERE quality_score IS NULL;

CREATE INDEX IF NOT EXISTS idx_document_quality
  ON ingestion.document (corpus_id, quality_score);

-- 2. chunk content-hash for dedup
ALTER TABLE ingestion.chunk
  ADD COLUMN IF NOT EXISTS content_hash CHAR(64);

COMMENT ON COLUMN ingestion.chunk.content_hash IS
  'SHA-256 hex of: strip(lowercase(collapse_whitespace(chunk_text))).
   Must match ChunkHasher.hash() normalization exactly.';

UPDATE ingestion.chunk
SET content_hash = encode(
    digest(regexp_replace(lower(trim(chunk_text)), '\s+', ' ', 'g'), 'sha256'),
    'hex')
WHERE content_hash IS NULL AND chunk_text IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_chunk_content_hash_corpus
  ON ingestion.chunk (corpus_id, content_hash)
  WHERE content_hash IS NOT NULL;

-- 3. Rebuild mv_topic_summary with quality filter
DROP MATERIALIZED VIEW IF EXISTS ingestion.mv_topic_summary;
CREATE MATERIALIZED VIEW ingestion.mv_topic_summary AS
SELECT
  d.corpus_id,
  d.id          AS document_id,
  d.page_kind,
  d.language,
  d.canonical_url,
  d.title,
  d.lead_text,
  d.summary_ka,
  d.summary_en,
  d.quality_score,
  COUNT(c.id)   AS chunk_count
FROM  ingestion.document d
LEFT  JOIN ingestion.chunk c ON c.document_id = d.id
WHERE d.fetch_status   = 'parsed'
  AND d.quality_score IN ('good', 'degraded')
  AND d.page_kind      IS DISTINCT FROM 'portal'
  AND COALESCE(length(d.content_text), 0) >= 100
GROUP BY d.corpus_id, d.id
WITH DATA;

CREATE UNIQUE INDEX IF NOT EXISTS idx_mv_topic_summary_doc
  ON ingestion.mv_topic_summary (corpus_id, document_id);
```

**ნაბიჯი 2 — `ChunkHasher`:**

შექმენი:
`apps/ingestion-service/src/main/java/com/geostat/ingestion/chunk/ChunkHasher.java`

```java
@Component
public class ChunkHasher {

    /**
     * Stable SHA-256 for deduplication.
     * Normalization MUST match V19 migration SQL:
     *   regexp_replace(lower(trim(chunk_text)), '\s+', ' ', 'g')
     *
     * Do NOT change normalization without updating V19 and recomputing all hashes.
     */
    public String hash(String chunkText) {
        if (chunkText == null || chunkText.isBlank()) return null;
        String normalized = chunkText.strip()
            .toLowerCase()
            .replaceAll("\\s+", " ");
        return Hashing.sha256()
            .hashString(normalized, StandardCharsets.UTF_8)
            .toString();
    }
}
```

**ნაბიჯი 3 — chunk insert — `ON CONFLICT DO NOTHING`:**

`ChunkRepository.insertChunk()`:

```java
/**
 * Inserts chunk. Silently skips on content_hash conflict (duplicate).
 * ON CONFLICT DO NOTHING = last line of defense after ParagraphRepetitionDetector.
 * Never throws on duplicate — caller does not need to handle.
 */
public void insertChunk(Chunk chunk) {
    jdbc.update("""
        INSERT INTO ingestion.chunk
          (id, document_id, corpus_id, chunk_text, chunk_index,
           embedding_status, content_hash)
        VALUES (?, ?, ?, ?, ?, 'pending', ?)
        ON CONFLICT (corpus_id, content_hash) DO NOTHING
        """,
        chunk.id(), chunk.documentId(), chunk.corpusId(),
        chunk.text(), chunk.chunkIndex(),
        hasher.hash(chunk.text()));
}
```

**Unit tests:**

```java
// ChunkHasherTest:
@Test void hash_stable_acrossWhitespace() {
    assertThat(hasher.hash("სოფლის  მეურნეობა"))
        .isEqualTo(hasher.hash("სოფლის მეურნეობა"));
}
@Test void hash_stable_acrossCase() {
    assertThat(hasher.hash("ABC")).isEqualTo(hasher.hash("abc"));
}
@Test void hash_differs_forDifferentContent() {
    assertThat(hasher.hash("A")).isNotEqualTo(hasher.hash("B"));
}
@Test void hash_null_forBlankInput() {
    assertThat(hasher.hash("   ")).isNull();
}

// ChunkRepositoryIntegrationTest:
@Test void insertChunk_silentlySkips_onDuplicate() {
    repo.insertChunk(chunk("same text"));
    repo.insertChunk(chunk("same text").withNewId()); // same text, new id
    assertThat(repo.countByCorpus(corpusId)).isEqualTo(1);
}
```

**ფაილები:**
- New: `apps/ingestion-service/.../db/migration/V19__chunk_content_hash_document_quality.sql`
- New: `apps/ingestion-service/.../chunk/ChunkHasher.java`
- Update: `apps/ingestion-service/.../chunk/ChunkRepository.java`

**Acceptance criteria:**
- V19 migration runs without error; existing chunks get `content_hash` computed.
- `UNIQUE INDEX` on `(corpus_id, content_hash)` created.
- Inserting same chunk text twice = 1 DB row, no exception.
- MV rebuilt: `quality_score IN ('good','degraded')` + `page_kind != 'portal'` + `length >= 100`.
- `ChunkHasher.hash()` normalization matches V19 SQL exactly.

---

### L-1-10 — Quality gates: `truncation_rate` + `repetition_rate` + `skip_rate` 🟠

**პრობლემა — root cause:**
`corpus-quality-gate.yaml`-ში ახალი gate entries-ი სჭირდება Java `QualityMetric` beans-ს (ARCH-03 pattern). ამ beans-ების გარეშე `QualityGateRunner` WARN-ს log-ავს და gates-ს skip-ავს.

**Resolution steps:**

**ნაბიჯი 1 — 3 ახალი metric bean:**

```java
// TruncationRateMetric.java
@Component("truncation_rate")
public class TruncationRateMetric implements QualityMetric {
    @Override public String id() { return "truncation_rate"; }
    @Override public String description() {
        return "Share of parsed docs with truncated_text validation violation";
    }
    @Override public double compute(UUID corpusId) {
        Double r = jdbc.queryForObject("""
            SELECT SUM(CASE WHEN 'truncated_text' = ANY(validation_violations)
                       THEN 1 ELSE 0 END)::float / NULLIF(COUNT(*), 0)
            FROM ingestion.document
            WHERE corpus_id = ? AND fetch_status = 'parsed'
            """, Double.class, corpusId);
        return r != null ? r : 0.0;
    }
}

// RepetitionRateMetric.java
@Component("repetition_rate")
public class RepetitionRateMetric implements QualityMetric {
    @Override public String id() { return "repetition_rate"; }
    @Override public String description() {
        return "Share of parsed docs with paragraph_duplicates_removed auto-fix";
    }
    @Override public double compute(UUID corpusId) {
        Double r = jdbc.queryForObject("""
            SELECT SUM(CASE WHEN 'paragraph_duplicates_removed' = ANY(validation_violations)
                       THEN 1 ELSE 0 END)::float / NULLIF(COUNT(*), 0)
            FROM ingestion.document
            WHERE corpus_id = ? AND fetch_status = 'parsed'
            """, Double.class, corpusId);
        return r != null ? r : 0.0;
    }
}

// SkipRateMetric.java
@Component("skip_rate")
public class SkipRateMetric implements QualityMetric {
    @Override public String id() { return "skip_rate"; }
    @Override public String description() {
        return "Share of parsed docs classified as SKIP (portal landings, no statistical content)";
    }
    @Override public double compute(UUID corpusId) {
        Double r = jdbc.queryForObject("""
            SELECT SUM(CASE WHEN quality_score = 'skip'
                       THEN 1 ELSE 0 END)::float / NULLIF(COUNT(*), 0)
            FROM ingestion.document
            WHERE corpus_id = ? AND fetch_status = 'parsed'
            """, Double.class, corpusId);
        return r != null ? r : 0.0;
    }
}
```

**ნაბიჯი 2 — `corpus-quality-gate.yaml` განახლება:**

```yaml
  - metric: truncation_rate
    description: "Share of parsed docs with truncated_text violation"
    target: "<= 0.02"
    currentBaseline: null   # measure after L-1-08 deployed + backfill run
    blocks: [enrichment_backfill]

  - metric: repetition_rate
    description: "Share of parsed docs with paragraph_duplicates_removed auto-fix"
    target: "<= 0.01"
    currentBaseline: null
    blocks: [enrichment_backfill]

  - metric: skip_rate
    description: "Share of parsed docs classified SKIP (portal landings)"
    target: "<= 0.30"
    currentBaseline: null
    blocks: []              # informational only — does not block downstream
```

**Unit tests:**

```java
// TruncationRateMetricTest:
@Test void compute_zero_whenNoViolations() {
    // 10 docs, none with truncated_text
    assertThat(metric.compute(corpusId)).isCloseTo(0.0, within(0.001));
}
@Test void compute_correctRatio_withViolations() {
    // 10 docs, 2 with truncated_text
    assertThat(metric.compute(corpusId)).isCloseTo(0.20, within(0.01));
}
@Test void compute_counts_onlyTruncation_not_otherViolations() {
    // doc with ['truncated_text', 'paragraph_duplicates_removed']
    // truncation_rate = 0.10 (1/10)
    // repetition_rate = 0.10 (1/10) — independently measured
    assertThat(truncationMetric.compute(corpusId)).isCloseTo(0.10, within(0.01));
    assertThat(repetitionMetric.compute(corpusId)).isCloseTo(0.10, within(0.01));
}
```

**ფაილები:**
- New: `apps/ingestion-service/.../quality/TruncationRateMetric.java`
- New: `apps/ingestion-service/.../quality/RepetitionRateMetric.java`
- New: `apps/ingestion-service/.../quality/SkipRateMetric.java`
- Update: `ops/eval/corpus-quality-gate.yaml` — 3 new gate entries

**Acceptance criteria:**
- `QualityGateRunner` resolves all 3 beans by name — no WARN "bean not found".
- Metrics computed correctly from `validation_violations[]` and `quality_score` arrays.
- `corpus-quality-gate.yaml` valid YAML, 3 new entries load without parse error.
- After L-1-08 deployed + backfill: `currentBaseline` measured and updated in YAML.

---

---

### L-1-11 — breadcrumb `section_path`: removeSelectors-მდე წაკითხვა (BUG) 🔴

**პრობლემა — root cause:**
`geostat-portal-parse.yaml`-ში:
```yaml
removeSelectors:
  - ".breadcrumb-wrapper"   # ← ამოიღება
  - ".breadcrumb"           # ← ამოიღება
```
`JsoupContentExtractor.extract()` — **პირველ ნაბიჯად** `removeSelectors`-ს ახდენს DOM-ზე. შედეგი: breadcrumb-ი წაიშლება **სანამ** `document.section_path` წაიკითხება. `section_path` = NULL ყველა document-ზე.

`section_path` კი კრიტიკულია:
- Topic clustering-ისთვის: "სოფლის მეურნეობა > მარცვლეული" = cluster context
- Chat catalog navigation: breadcrumb = canonical topic path
- Quality gate: `section_path IS NULL` rate = 100% ახლა

**Resolution steps:**

**ნაბიჯი 1 — `JsoupContentExtractor.extract()` order fix:**

`apps/ingestion-service/src/main/java/com/geostat/ingestion/parse/profile/JsoupContentExtractor.java`

```java
@Override
public CleanedDocument extract(HtmlPageInput page, ParseProfile profile) {
    Document doc = Jsoup.parse(page.html(), page.canonicalUrl());

    // STEP 1: read breadcrumb BEFORE any removal
    // breadcrumb selectors will be removed in step 2 — read them now
    String sectionPath = extractSectionPath(doc);

    // STEP 2: remove noise (nav, footer, breadcrumb, sidebar, etc.)
    profile.removeSelectors().forEach(sel -> doc.select(sel).remove());

    // STEP 3: find root container (firstMatch)
    Element root = findRoot(doc, profile);

    // STEP 4: apply boilerplate markers
    String body = applyBoilerplateMarkers(root, profile);

    // STEP 5: extract metadata
    String title    = extractTitle(doc);
    String leadText = extractLeadText(root, profile);
    String language = inferLanguage(doc, page.canonicalUrl(), profile);

    return new CleanedDocument(
        title, body, language,
        List.of(), sectionPath, leadText, null, 0, 0
    );
}

/**
 * Extracts breadcrumb-based hierarchical path.
 * MUST be called BEFORE removeSelectors to preserve breadcrumb DOM.
 *
 * Examples:
 *   "სტატისტიკა > მოსახლეობა > ბუნებრივი მოძრაობა"
 *   "Statistics > Population > Natural Movement"
 */
private String extractSectionPath(Document doc) {
    // try known geostat.ge breadcrumb selectors (from DOM analysis)
    Elements crumbs = doc.select(
        ".breadcrumb-wrapper a, " +
        ".breadcrumb a, " +
        "nav[aria-label*=breadcrumb] a, " +
        "ol.breadcrumb li a, " +
        ".header-breadcrumb a"
    );
    if (crumbs.isEmpty()) return null;

    String path = crumbs.stream()
        .map(e -> e.text().strip())
        .filter(t -> !t.isBlank())
        .filter(t -> t.length() > 1) // exclude single-char nav artifacts
        .collect(java.util.stream.Collectors.joining(" > "));

    return path.isBlank() ? null : path;
}
```

**ნაბიჯი 2 — unit tests:**

```java
// JsoupContentExtractorTest:
@Test
void extractsSectionPath_beforeRemovingBreadcrumb() {
    String html = """
        <html><body>
          <nav class="breadcrumb-wrapper">
            <a href="/ka">მთავარი</a>
            <a href="/ka/modules/categories/316">მოსახლეობა</a>
            <a href="#">ბუნებრივი მოძრაობა</a>
          </nav>
          <main><p>სოფლის მეურნეობის შესახებ ინფორმაცია...</p></main>
        </body></html>""";
    CleanedDocument doc = extractor.extract(
        new HtmlPageInput(html, "https://www.geostat.ge/ka/test"), profile);

    assertThat(doc.sectionPath())
        .isEqualTo("მთავარი > მოსახლეობა > ბუნებრივი მოძრაობა");
    // breadcrumb must NOT appear in body (was removed after reading)
    assertThat(doc.body()).doesNotContain("მთავარი");
}

@Test
void sectionPath_isNull_whenNoBreadcrumb() {
    String html = "<html><body><main><p>" + "x".repeat(200) + "</p></main></body></html>";
    CleanedDocument doc = extractor.extract(
        new HtmlPageInput(html, "https://www.geostat.ge/ka/test"), profile);
    assertThat(doc.sectionPath()).isNull();
}
```

**ფაილები:**
- Update: `apps/ingestion-service/.../parse/profile/JsoupContentExtractor.java`

**Acceptance criteria:**
- `section_path` populated for any page with `.breadcrumb-wrapper a` elements.
- Breadcrumb text does NOT appear in `body` (removed after read).
- `section_path` NULL for pages with no breadcrumb.
- Existing extraction tests pass — body content unchanged.

---

### L-1-12 — HTTP redirect → `canonical_url` must be final URL 🔴

**პრობლემა — root cause:**

```
GET https://www.geostat.ge/ka/page/X
  → 301 Moved → https://www.geostat.ge/ka/page/Y (new slug)

document.canonical_url = "/ka/page/X"  ← stored as original
Chat API link = "/ka/page/X"           ← dead link for user
Crawl dedup = by original URL          ← /page/X AND /page/Y both crawled = duplicate doc
```

**Resolution steps:**

**ნაბიჯი 1 — `FetchedPage` record-ში `finalUrl` field (ARCH-01 update):**

`libs/platform-contracts/src/main/java/com/geostat/platform/crawl/FetchedPage.java`:

```java
public record FetchedPage(
    String url,         // original requested URL (as enqueued)
    String finalUrl,    // URL after all redirects — use THIS as canonical_url
    String html,
    int    httpStatus,
    String contentType,
    RenderMode renderMode
) {
    /** Returns finalUrl if it differs from url (redirect happened), else url. */
    public String canonicalUrl() {
        return finalUrl != null && !finalUrl.equals(url) ? finalUrl : url;
    }
}
```

**ნაბიჯი 2 — `Crawler4jStaticPageFetcher` — track redirect chain:**

```java
@Override
public FetchedPage fetch(String url, FetchOptions options) throws PageFetchException {
    // crawler4j provides WebURL with final redirect URL
    // after following redirects, capture the final URL
    String finalUrl = crawlResult.getFinalUrl(); // final after redirect chain
    if (finalUrl == null) finalUrl = url;        // no redirect

    return new FetchedPage(
        url,
        finalUrl,
        crawlResult.getHtml(),
        crawlResult.getStatusCode(),
        crawlResult.getContentType(),
        RenderMode.STATIC
    );
}
```

**ნაბიჯი 3 — `DocumentIngestionPipeline` — use `finalUrl` as canonical:**

```java
// Use page.canonicalUrl() — returns finalUrl if redirect occurred
Document document = mapper.toEntity(outcome.document())
    .withCanonicalUrl(fetchedPage.canonicalUrl())  // ← finalUrl, not original
    .withOriginalUrl(fetchedPage.url())             // ← keep original for audit
    // ...
```

**ნაბიჯი 4 — V20 migration — `document.original_url` column:**

```sql
-- V20:
ALTER TABLE ingestion.document
  ADD COLUMN IF NOT EXISTS original_url TEXT;
  -- stores pre-redirect URL when canonical_url was updated by redirect
COMMENT ON COLUMN ingestion.document.original_url IS
  'Pre-redirect URL if canonical_url was updated after following redirect.
   NULL if no redirect occurred.';
```

**ნაბიჯი 5 — `PolicyUrlFilter` — deduplicate by finalUrl:**

```java
// shouldEnqueue() — after getting finalUrl from response:
// if finalUrl already visited → skip
// add finalUrl (not original) to visited set
```

**ნაბიჯი 6 — unit tests:**

```java
// Crawler4jStaticPageFetcherTest:
@Test
void fetch_setsFinalUrl_onRedirect() {
    // mock crawler4j: original="/page/X", redirect="/page/Y"
    FetchedPage result = fetcher.fetch("https://www.geostat.ge/ka/page/X", opts);
    assertThat(result.finalUrl()).isEqualTo("https://www.geostat.ge/ka/page/Y");
    assertThat(result.canonicalUrl()).isEqualTo("https://www.geostat.ge/ka/page/Y");
    assertThat(result.url()).isEqualTo("https://www.geostat.ge/ka/page/X");
}

@Test
void fetch_finalUrlEqualsUrl_whenNoRedirect() {
    FetchedPage result = fetcher.fetch("https://www.geostat.ge/ka", opts);
    assertThat(result.canonicalUrl()).isEqualTo(result.url());
}
```

**ფაილები:**
- Update: `libs/platform-contracts/.../crawl/FetchedPage.java` — `finalUrl` + `canonicalUrl()`
- Update: `apps/ingestion-service/.../crawl/fetch/Crawler4jStaticPageFetcher.java`
- Update: `apps/ingestion-service/.../DocumentIngestionPipeline.java`
- New: V20 migration — `document.original_url`

**Acceptance criteria:**
- `canonical_url` in DB = final URL after redirect chain.
- `original_url` populated when redirect occurred, NULL otherwise.
- Same content under two URLs (redirect) = 1 document row (finalUrl dedup).
- Chat API links never return 301 to user.

---

### L-1-13 — `CuratedUrlVerifier`: staleness detection on startup 🟠

**პრობლემა — root cause:**
`geostat-portal-policy.yaml`-ში 66 `curatedUrls` — ყოველი numeric ID (`/modules/categories/189`). CMS migration ან category merge = URL stale = 404 = silently skipped = important content never crawled. **No alert, no detection.**

**Resolution steps:**

**ნაბიჯი 1 — `CuratedUrlVerifier` ingestion-service-ში:**

შექმენი:
`apps/ingestion-service/src/main/java/com/geostat/ingestion/crawl/CuratedUrlVerifier.java`

```java
@Component
public class CuratedUrlVerifier {

    private final PageFetcher fetcher;

    /**
     * Verifies all curatedUrls from the given policy via HTTP HEAD.
     * Logs WARN for any URL returning 4xx/5xx.
     * Does NOT modify the policy — alerts only.
     * Owner must decide whether to remove or update stale URLs.
     *
     * Called at: ApplicationReadyEvent (startup) — non-blocking, async.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void verifyOnStartup() {
        configLoader.loadAllPolicies().forEach(policy -> {
            log.info("[curated-url-verify] corpus={} checking {} URLs",
                policy.corpus(), policy.curatedUrls().size());
            verifyCuratedUrls(policy);
        });
    }

    public VerificationReport verifyCuratedUrls(CorpusPolicy policy) {
        List<StaleUrl> stale = new ArrayList<>();
        for (String url : policy.curatedUrls()) {
            try {
                FetchedPage page = fetcher.fetch(url,
                    new FetchOptions(RenderMode.STATIC, 5_000,
                        "GeostatBot-verify/1.0", NetworkPolicy.defaults()));
                if (page.httpStatus() >= 400) {
                    stale.add(new StaleUrl(url, page.httpStatus()));
                    log.warn("[curated-url-verify] STALE corpus={} url={} status={}",
                        policy.corpus(), url, page.httpStatus());
                }
            } catch (PageFetchException e) {
                stale.add(new StaleUrl(url, -1));
                log.warn("[curated-url-verify] UNREACHABLE corpus={} url={} error={}",
                    policy.corpus(), url, e.getMessage());
            }
        }
        if (stale.isEmpty()) {
            log.info("[curated-url-verify] corpus={} — all {} URLs healthy",
                policy.corpus(), policy.curatedUrls().size());
        } else {
            log.error("[curated-url-verify] corpus={} — {} STALE URLs found. " +
                "Update ops/config/corpus/{}-policy.yaml curatedUrls.",
                policy.corpus(), stale.size(), policy.corpus());
        }
        return new VerificationReport(policy.corpus(), stale);
    }

    public record StaleUrl(String url, int httpStatus) {}
    public record VerificationReport(String corpus, List<StaleUrl> staleUrls) {
        public boolean hasStale() { return !staleUrls.isEmpty(); }
    }
}
```

**ნაბიჯი 2 — unit tests:**

```java
// CuratedUrlVerifierTest:
@Test
void verify_logsWarn_forStaleUrl() {
    when(fetcher.fetch(eq("https://www.geostat.ge/ka/modules/categories/999"), any()))
        .thenReturn(new FetchedPage("...", "...", 404, "text/html", RenderMode.STATIC));
    VerificationReport report = verifier.verifyCuratedUrls(policyWith("...999"));
    assertThat(report.hasStale()).isTrue();
    assertThat(report.staleUrls()).hasSize(1);
    assertThat(report.staleUrls().get(0).httpStatus()).isEqualTo(404);
}

@Test
void verify_returnsEmpty_whenAllHealthy() {
    when(fetcher.fetch(any(), any()))
        .thenReturn(new FetchedPage("...", "...", 200, "text/html", RenderMode.STATIC));
    VerificationReport report = verifier.verifyCuratedUrls(validPolicy);
    assertThat(report.hasStale()).isFalse();
}
```

**ფაილები:**
- New: `apps/ingestion-service/.../crawl/CuratedUrlVerifier.java`

**Acceptance criteria:**
- On startup: WARN logged for any curatedUrl returning 4xx/5xx.
- Non-blocking: verification runs async, does not delay crawl start.
- Does NOT modify YAML or policy — alerts only.
- Unit tests: stale detection, healthy report.

---

### L-1-14 — `published_at` extraction for news documents 🟠

**პრობლემა — root cause:**
`document.published_at` = NULL ყველა news document-ზე. Chat-ი ვერ ფილტრავს "2025 წლის სიახლეები" — DB-ს date field არ აქვს. MV-ში date ordering შეუძლებელია.

**Resolution steps:**

**ნაბიჯი 1 — `GeostatNewsExtractionStrategy`-ში (ARCH-02) date extraction:**

```java
// GeostatNewsExtractionStrategy.java — extract() method-ში:

private Instant extractPublishedAt(Document doc) {
    // Priority 1: JSON-LD datePublished (most reliable — schema.org structured data)
    for (Element script : doc.select("script[type=application/ld+json]")) {
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("\"datePublished\"\\s*:\\s*\"([^\"]+)\"")
            .matcher(script.html());
        if (m.find()) {
            try { return Instant.parse(m.group(1)); } catch (Exception ignored) {}
        }
    }
    // Priority 2: <time datetime="..."> — HTML5 semantic element
    Element time = doc.selectFirst("time[datetime], .news-date[datetime]");
    if (time != null && !time.attr("datetime").isBlank()) {
        try { return Instant.parse(time.attr("datetime")); } catch (Exception ignored) {}
    }
    // Priority 3: OpenGraph article:published_time
    String og = doc.select("meta[property=article:published_time]").attr("content");
    if (!og.isBlank()) {
        try { return Instant.parse(og); } catch (Exception ignored) {}
    }
    // Priority 4: visible date text near h1 (heuristic — last resort)
    Element dateEl = doc.selectFirst(".news-items-section .news-date, .article-date, time");
    if (dateEl != null) {
        String text = dateEl.text().strip();
        // Georgian date format: "15 მარტი, 2025" — parse separately if needed
        // for now: log and return null (don't guess)
        log.debug("[date-extract] unstructured date text found: {}", text);
    }
    return null;
}
```

**ნაბიჯი 2 — `CleanedDocument` record-ში `publishedAt` field:**

`libs/platform-contracts/src/main/java/com/geostat/platform/parse/CleanedDocument.java`:

```java
public record CleanedDocument(
    String  title,
    String  body,
    String  language,
    List<String> metaKeywords,
    String  sectionPath,
    String  leadText,
    String  pageKind,
    int     wordCount,
    int     tableCount,
    Instant publishedAt    // ← new field; null if not extractable
) {}
```

**ნაბიჯი 3 — V20 migration — column already planned (`document.published_at`):**

```sql
-- included in V20 (DB-GAP-04):
ALTER TABLE ingestion.document
  ADD COLUMN IF NOT EXISTS published_at TIMESTAMPTZ;
```

**ნაბიჯი 4 — unit tests:**

```java
// GeostatNewsExtractionStrategyTest:
@Test
void extractsPublishedAt_fromJsonLd() {
    String html = """
        <html><head>
          <script type="application/ld+json">
            {"@type":"NewsArticle","datePublished":"2025-03-15T10:00:00Z"}
          </script>
        </head><body><article><h1>Title</h1><p>""" + "x".repeat(100) + """
        </p></article></body></html>""";
    CleanedDocument doc = strategy.extract(new HtmlPageInput(html, "https://x.ge"), null);
    assertThat(doc.publishedAt()).isEqualTo(Instant.parse("2025-03-15T10:00:00Z"));
}

@Test
void extractsPublishedAt_fromTimeElement() {
    String html = "<html><body><article><h1>T</h1>" +
        "<time datetime='2024-11-01T09:30:00Z'>1 November</time>" +
        "<p>" + "x".repeat(100) + "</p></article></body></html>";
    CleanedDocument doc = strategy.extract(new HtmlPageInput(html, "https://x.ge"), null);
    assertThat(doc.publishedAt()).isEqualTo(Instant.parse("2024-11-01T09:30:00Z"));
}

@Test
void publishedAt_isNull_whenNoDateSignalFound() {
    CleanedDocument doc = strategy.extract(htmlWithNoDate(), null);
    assertThat(doc.publishedAt()).isNull(); // acceptable — no guess
}
```

**ფაილები:**
- Update: `libs/platform-contracts/.../parse/CleanedDocument.java` — `publishedAt` field
- Update: `apps/ingestion-service/.../parse/strategy/GeostatNewsExtractionStrategy.java`
- V20 migration — `document.published_at` column

**Acceptance criteria:**
- JSON-LD `datePublished` → `published_at` populated.
- `<time datetime>` → `published_at` populated.
- No date signal → `published_at` = NULL (no guessing).
- `published_at` indexed in DB for date-range queries.

---

### L-1-15 — Nested element text duplication: `<p>` inside `<li>` 🔴

**Root cause — კოდიდან პირდაპირ:**

`JsoupContentExtractor.extractBody()` line 84:
```java
Elements candidates = root.select("h1, h2, h3, h4, h5, h6, p, li");
```

Jsoup's `.select()` returns **all matching descendants** in document order — parents AND children independently. If `<li>` contains `<p>`, ორივე ხვდება `candidates`-ში. `element.text()` returns **full text including all children**. შედეგი:

```html
<!-- input DOM -->
<article>
  <ul>
    <li>
      <p>ბუნებრივი მოძრაობის მაჩვენებელი 2024 წელს შეადგინა 1.2%.</p>
    </li>
    <li>სხვა ელემენტი</li>
  </ul>
  <p>ზოგადი ინფორმაცია მოსახლეობაზე.</p>
</article>

<!-- candidates list (document order):
     li[0].text() = "ბუნებრივი მოძრაობის მაჩვენებელი 2024 წელს შეადგინა 1.2%."
     p[0].text()  = "ბუნებრივი მოძრაობის მაჩვენებელი 2024 წელს შეადგინა 1.2%."   ← DUPLICATE
     li[1].text() = "სხვა ელემენტი"
     p[1].text()  = "ზოგადი ინფორმაცია მოსახლეობაზე."
-->
body = "ბუნებრივი... 1.2%. ბუნებრივი... 1.2%. სხვა ელემენტი. ზოგადი..."
                         ↑ same sentence twice ← LLM reads as two facts
```

**Impact:**
- `ParagraphRepetitionDetector` (L-1-08) catches it → `validation_violations = ['paragraph_duplicates_removed']`
- `quality_score` degraded or SKIP → lost content
- RAG embedding-ი = duplicate vector noise
- Chat-ი returns duplicated content mid-sentence

**Resolution — `extractBody()` refactor: ancestor-aware dedup:**

`apps/ingestion-service/src/main/java/com/geostat/ingestion/parse/profile/JsoupContentExtractor.java`

```java
private ExtractionStats extractBody(Element root, ParseProfile profile) {
    if (root == null) return new ExtractionStats("", 0, 0);

    List<String> blocks = new ArrayList<>();
    int total = 0, boilerplate = 0;

    // Collect candidate elements ordered by document position
    Elements candidates = new Elements();
    candidates.addAll(root.select("h1, h2, h3, h4, h5, h6, p, li"));
    if (profile.extractTables()) {
        candidates.addAll(root.select("table"));
    }

    // Build set of candidate elements that ARE ALREADY COVERED by an ancestor
    // in the same candidates list — skip the child to avoid text duplication.
    //
    // Algorithm: for each candidate, check if any of its ancestors also appear
    // in the candidate set. If yes → this element's text is already included
    // in the ancestor's .text() output → skip it.
    Set<Element> covered = new HashSet<>();
    for (Element el : candidates) {
        // walk up the DOM; if any ancestor is also in candidates → this is covered
        Element parent = el.parent();
        while (parent != null && parent != root) {
            if (candidates.contains(parent)) {
                covered.add(el);
                break;
            }
            parent = parent.parent();
        }
    }

    if (candidates.isEmpty()) {
        String fallback = root.text().trim().replaceAll("\\s+", " ");
        total = fallback.isBlank() ? 0 : 1;
        if (!fallback.isBlank() && boilerplateStripper.isBoilerplateParagraph(fallback, profile)) {
            boilerplate = 1;
        }
        return new ExtractionStats(fallback, total, boilerplate);
    }

    for (Element block : candidates) {
        if (covered.contains(block)) continue; // skip: text already covered by ancestor

        String text = block.text().trim().replaceAll("\\s+", " ");
        if (text.isBlank()) continue;

        total++;
        if (boilerplateStripper.isBoilerplateParagraph(text, profile)) {
            boilerplate++;
            continue;
        }
        blocks.add(text);
    }
    return new ExtractionStats(String.join(" ", blocks), total, boilerplate);
}
```

> **Design note:** `candidates.contains()` uses Jsoup's element identity (reference equality by node identity) — O(n) per call, but candidate lists are typically < 200 elements. No performance concern for document-level parsing. If profiling shows otherwise, replace with `Set<Element>` built at construction time.

**Unit tests:**

```java
// JsoupContentExtractorTest:

@Test
void extractBody_noDuplication_when_pInsideLi() {
    String html = """
        <html><body><article>
          <ul>
            <li><p>ბუნებრივი მოძრაობა 1.2%.</p></li>
            <li>სხვა ელემენტი</li>
          </ul>
          <p>ზოგადი ინფორმაცია.</p>
        </article></body></html>""";
    CleanedDocument doc = extractor.extract(new HtmlPageInput(html, "https://x.ge"), profile);
    String body = doc.body();

    // "ბუნებრივი..." must appear exactly once
    assertThat(countOccurrences(body, "ბუნებრივი მოძრაობა 1.2%")).isEqualTo(1);
    // all three content blocks present
    assertThat(body).contains("ბუნებრივი მოძრაობა 1.2%.");
    assertThat(body).contains("სხვა ელემენტი");
    assertThat(body).contains("ზოგადი ინფორმაცია.");
}

@Test
void extractBody_noDuplication_deeplyNested_hInsideDiv_insideLi() {
    // h3 inside li — must emit h3 text once (ancestor is li, but h3 wins as the richer element)
    String html = """
        <html><body><article>
          <ul>
            <li><h3>თავი: მოსახლეობა</h3></li>
          </ul>
        </article></body></html>""";
    CleanedDocument doc = extractor.extract(new HtmlPageInput(html, "https://x.ge"), profile);
    assertThat(countOccurrences(doc.body(), "მოსახლეობა")).isEqualTo(1);
}

@Test
void extractBody_plainLi_withoutChildP_included() {
    String html = """
        <html><body><article>
          <ul><li>პირდაპირი ელემენტი</li></ul>
        </article></body></html>""";
    CleanedDocument doc = extractor.extract(new HtmlPageInput(html, "https://x.ge"), profile);
    assertThat(doc.body()).contains("პირდაპირი ელემენტი");
}
```

**ფაილები:**
- Update: `apps/ingestion-service/.../parse/profile/JsoupContentExtractor.java` — `extractBody()` + `Set<Element> covered`

**Acceptance criteria:**
- `<p>` inside `<li>`: text appears exactly once in body.
- `<h3>` inside `<li>`: heading text appears exactly once.
- Plain `<li>` (no child candidates): text still extracted.
- `ParagraphRepetitionDetector` violation rate drops measurably after fix (quality gate `repetition_rate` improves).
- All existing extraction unit tests pass.

---

### L-1-16 — `language = null`: `defaultFallback` YAML → Java gap 🔴

**Root cause — კოდიდან პირდაპირ:**

```yaml
# geostat-portal-parse.yaml:
language:
  defaultFallback: ka   # ← defined in YAML
```

```java
// JsoupContentExtractor.inferLanguage() — line 127–147:
private static String inferLanguage(Document html, String url, ParseProfile profile) {
    for (String source : profile.languageInferFrom()) {
        // ... tries htmlLang, urlSegment, metaContentLanguage ...
    }
    return null;  // ← defaultFallback: ka NEVER READ — profile.languageDefaultFallback() not called
}
```

`CleanedDocument(language=null)` bubbles into:
1. `BoilerplateStripper.isBoilerplateParagraph()` — cannot choose ka vs en markers → **uses both** (union) → false positives: legitimate Georgian sentences containing English sub-strings might be stripped.
2. `document.language IS NULL` in DB → `MV WHERE language = 'ka'` filter (if any) = **document excluded**.
3. `ChunkEmbeddingService` metadata `language=null` → Qdrant filter by language broken.

**Resolution — two-step fix:**

**Step 1 — `ParseProfile` record: expose `languageDefaultFallback()`:**

`libs/platform-contracts/src/main/java/com/geostat/platform/parse/ParseProfile.java`:

```java
public record ParseProfile(
    String              corpus,
    String              rootSelectorStrategy,
    List<String>        rootSelectors,
    List<String>        removeSelectors,
    // ... other fields ...
    LanguageConfig      language
) {
    public record LanguageConfig(
        List<String> inferFrom,
        String       defaultFallback   // ← must be mapped from YAML
    ) {}

    public List<String> languageInferFrom() {
        return language != null ? language.inferFrom() : List.of();
    }

    /** Never null — returns configured fallback or hardcoded 'ka'. */
    public String languageDefaultFallback() {
        if (language != null
                && language.defaultFallback() != null
                && !language.defaultFallback().isBlank()) {
            return language.defaultFallback();
        }
        return "ka"; // safe default for geostat domain
    }
}
```

**Step 2 — `inferLanguage()`: apply fallback at end:**

```java
private static String inferLanguage(Document html, String url, ParseProfile profile) {
    for (String source : profile.languageInferFrom()) {
        if ("htmlLang".equalsIgnoreCase(source)) {
            String lang = html.select("html").attr("lang");
            if (!lang.isBlank()) return lang.split("-")[0].toLowerCase();
        } else if ("urlSegment".equalsIgnoreCase(source)) {
            String fromUrl = UrlLocaleInferer.infer(url, null);
            if (fromUrl != null && !fromUrl.isBlank()) return fromUrl;
        } else if ("metaContentLanguage".equalsIgnoreCase(source)) {
            String meta = html.select("meta[http-equiv=content-language]").attr("content");
            if (!meta.isBlank()) return meta.split("-")[0].toLowerCase();
        }
    }
    // YAML defaultFallback applied — never return null
    return profile.languageDefaultFallback();
}
```

**Unit tests:**

```java
// JsoupContentExtractorTest:

@Test
void inferLanguage_returnsDefaultFallback_whenNoSignal() {
    // no html lang attr, no /ka/ in URL, no meta tag
    String html = "<html><body><p>" + "x".repeat(100) + "</p></body></html>";
    CleanedDocument doc = extractor.extract(
        new HtmlPageInput(html, "https://www.geostat.ge/unknown"), kaFallbackProfile);
    assertThat(doc.language()).isEqualTo("ka"); // fallback applied
}

@Test
void inferLanguage_htmlLangWins_overFallback() {
    String html = "<html lang='en'><body><p>" + "x".repeat(100) + "</p></body></html>";
    CleanedDocument doc = extractor.extract(
        new HtmlPageInput(html, "https://www.geostat.ge/en/page"), kaFallbackProfile);
    assertThat(doc.language()).isEqualTo("en"); // explicit signal wins
}

@Test
void inferLanguage_urlSegmentKa_wins() {
    String html = "<html><body><p>" + "x".repeat(100) + "</p></body></html>";
    CleanedDocument doc = extractor.extract(
        new HtmlPageInput(html, "https://www.geostat.ge/ka/modules/categories/189"),
        kaFallbackProfile);
    assertThat(doc.language()).isEqualTo("ka");
}

@Test
void inferLanguage_neverReturnsNull() {
    // profile with empty inferFrom list
    ParseProfile emptyInfer = profileWithNoInferSources();
    CleanedDocument doc = extractor.extract(
        new HtmlPageInput("<html><body><p>text</p></body></html>", "https://x.ge"),
        emptyInfer);
    assertThat(doc.language()).isNotNull();
    assertThat(doc.language()).isEqualTo("ka");
}
```

**ფაილები:**
- Update: `libs/platform-contracts/.../parse/ParseProfile.java` — `LanguageConfig` record + `languageDefaultFallback()`
- Update: `apps/ingestion-service/.../parse/profile/JsoupContentExtractor.java` — `inferLanguage()` + fallback

**Acceptance criteria:**
- `document.language` is NEVER NULL in DB after this fix.
- `htmlLang` signal → correct language code.
- `/ka/` URL segment → `"ka"`.
- No signal at all → `"ka"` (defaultFallback from YAML).
- `BoilerplateStripper` always gets non-null language — ka vs en markers correctly separated.
- Qdrant chunk metadata `language` never null.

---

### L-1-17 — `sectionPath`: heading list ≠ navigation hierarchy — implement real breadcrumb 🟠

**Root cause — architectural gap:**

```java
// SectionPathExtractor.java line 21:
Elements headings = root.select("h1, h2, h3");
// Returns: ["მოსახლეობა", "ბუნებრივი მოძრაობა", "გარდაცვლობა"]
//                                 ↑ these are CONTENT headings, not site navigation

// Missing: <nav class="breadcrumb-wrapper">
//   <a href="/ka">მთავარი</a>
//   <a href="/ka/modules/categories/316">მოსახლეობა</a>
//   <a href="#">ბუნებრივი მოძრაობა</a>
// → "მთავარი > მოსახლეობა > ბუნებრივი მოძრაობა"  ← THIS is the topic path
```

**Two concepts — both needed:**

| Field | Source | Value | Purpose |
|---|---|---|---|
| `sectionPath` (current) | h1, h2, h3 in `main` | `["მოსახლეობა", "ბუნებრივი მოძრაობა"]` | RAG chunk context heading |
| `navBreadcrumb` (missing) | `nav.breadcrumb a` | `"სტატისტიკა > მოსახლეობა > ბუნებრივი მოძრაობა"` | Topic cluster assignment, Chat navigation |

**Resolution — `SectionPathExtractor` extension + `navBreadcrumb` field:**

**Step 1 — `CleanedDocument`: add `navBreadcrumb` field:**

```java
// CleanedDocument.java:
public record CleanedDocument(
    String        title,
    String        body,
    String        language,
    List<String>  sectionPath,     // existing: h1/h2/h3 heading list
    String        metaDescription,
    String        leadText,
    String        displayDescription,
    int           totalBlocks,
    int           boilerplateBlocks,
    String        navBreadcrumb,   // NEW: "სტატისტიკა > მოსახლეობა > ბუნებრივი მოძრაობა"
    Instant       publishedAt      // (L-1-14)
) {}
```

**Step 2 — `SectionPathExtractor`: add `extractNavBreadcrumb()` — reads original `html` (before removal):**

```java
// SectionPathExtractor.java

/**
 * Extracts navigation breadcrumb path from the ORIGINAL document
 * (before removeSelectors cleans it).
 *
 * Must be called with the original parsed Document, NOT the clone.
 * (JsoupContentExtractor already passes `html` not `clone` to SectionPathExtractor.)
 *
 * Returns: "სტატისტიკა > მოსახლეობა > ბუნებრივი მოძრაობა"
 * Returns: null if no breadcrumb nav found.
 *
 * Selector priority:
 *  1. .breadcrumb-wrapper a  (geostat.ge primary breadcrumb)
 *  2. .breadcrumb a          (fallback)
 *  3. nav[aria-label*=breadcrumb] a  (semantic HTML5)
 *  4. ol.breadcrumb li a    (Bootstrap-style)
 */
public static String extractNavBreadcrumb(Document html) {
    Elements links = html.select(
        ".breadcrumb-wrapper a, " +
        ".breadcrumb a, " +
        "nav[aria-label*=breadcrumb] a, " +
        "ol.breadcrumb li a"
    );
    if (links.isEmpty()) return null;

    String path = links.stream()
        .map(e -> e.text().strip())
        .filter(t -> !t.isBlank() && t.length() > 1)
        .distinct()  // dedup — some sites repeat last crumb
        .collect(java.util.stream.Collectors.joining(" > "));

    return path.isBlank() ? null : path;
}
```

**Step 3 — `JsoupContentExtractor.extract()`: call both extractors:**

```java
@Override
public CleanedDocument extract(HtmlPageInput page, ParseProfile profile) {
    Document html  = Jsoup.parse(page.html(), page.canonicalUrl());
    Document clone = html.clone();

    // removeSelectors on clone only
    for (String selector : profile.removeSelectors()) {
        if (selector != null && !selector.isBlank()) {
            clone.select(selector).remove();
        }
    }

    Element root = selectRoot(clone, profile.rootSelectors());
    String  title        = resolveTitle(html, root);
    String  navBreadcrumb = SectionPathExtractor.extractNavBreadcrumb(html); // ← reads original
    List<String> sectionPath = profile.preserveHeadings()
        ? SectionPathExtractor.extract(html) : List.of();
    String language      = inferLanguage(html, page.canonicalUrl(), profile);

    ExtractionStats stats = extractBody(root, profile);
    String body = boilerplateStripper.stripFromBody(stats.joinedText(), profile);
    PageDisplayMetadataExtractor.DisplayMetadata display =
        displayMetadataExtractor.extract(html, title, sectionPath);

    return new CleanedDocument(
        title, body, language,
        sectionPath, display.metaDescription(), display.leadText(),
        display.displayDescription(), stats.totalBlocks(), stats.boilerplateBlocks(),
        navBreadcrumb, null  // publishedAt: null here, set by GeostatNewsExtractionStrategy
    );
}
```

**Step 4 — V20 migration: `document.nav_breadcrumb` column:**

```sql
ALTER TABLE ingestion.document
  ADD COLUMN IF NOT EXISTS nav_breadcrumb TEXT;
COMMENT ON COLUMN ingestion.document.nav_breadcrumb IS
    'Navigation breadcrumb from site nav (e.g. "სტატისტიკა > მოსახლეობა > ბუნებრივი მოძრაობა").
     Extracted before removeSelectors. Null if page has no breadcrumb nav.
     Used for: topic cluster assignment, Chat catalog navigation, MV joins.';

CREATE INDEX IF NOT EXISTS idx_document_nav_breadcrumb
  ON ingestion.document USING gin(to_tsvector(''simple'', COALESCE(nav_breadcrumb, '''')));
```

**Unit tests:**

```java
// SectionPathExtractorTest:

@Test
void extractNavBreadcrumb_fromBreadcrumbWrapper() {
    String html = """
        <html><body>
          <nav class="breadcrumb-wrapper">
            <a href="/ka">მთავარი</a>
            <a href="/ka/modules/categories/316">მოსახლეობა</a>
            <a href="#">ბუნებრივი მოძრაობა</a>
          </nav>
          <main><h1>ბუნებრივი მოძრაობა</h1></main>
        </body></html>""";
    assertThat(SectionPathExtractor.extractNavBreadcrumb(Jsoup.parse(html)))
        .isEqualTo("მთავარი > მოსახლეობა > ბუნებრივი მოძრაობა");
}

@Test
void extractNavBreadcrumb_returnsNull_whenNoBreadcrumb() {
    String html = "<html><body><main><h1>სტატია</h1></main></body></html>";
    assertThat(SectionPathExtractor.extractNavBreadcrumb(Jsoup.parse(html))).isNull();
}

@Test
void extractNavBreadcrumb_deduplicates_repeatedLastCrumb() {
    // some CMS repeat the current page title in both h1 and breadcrumb last item
    String html = """
        <html><body>
          <nav class="breadcrumb-wrapper">
            <a href="/ka">მთავარი</a>
            <a href="/ka/page">გვერდი</a>
            <a href="#">გვერდი</a>  <!-- duplicate last -->
          </nav>
        </body></html>""";
    // "გვერდი" appears once due to .distinct()
    assertThat(SectionPathExtractor.extractNavBreadcrumb(Jsoup.parse(html)))
        .isEqualTo("მთავარი > გვერდი");
}
```

**ფაილები:**
- Update: `libs/platform-contracts/.../parse/CleanedDocument.java` — `navBreadcrumb` field
- Update: `apps/ingestion-service/.../parse/SectionPathExtractor.java` — `extractNavBreadcrumb()`
- Update: `apps/ingestion-service/.../parse/profile/JsoupContentExtractor.java` — call `extractNavBreadcrumb(html)`
- V20 migration — `document.nav_breadcrumb` column + GIN index

**Acceptance criteria:**
- `nav_breadcrumb` populated for pages with `.breadcrumb-wrapper` nav.
- `nav_breadcrumb` null for pages without breadcrumb.
- Duplicate last crumb removed.
- `sectionPath` (h1/h2/h3) unchanged — both fields populated independently.
- Topic cluster logic can use `nav_breadcrumb` for domain-specific hierarchy.

---

### L-1-18 — `removeSelectors`: `aside`, `figure`, hidden elements not removed 🟡

**Root cause:**

```java
// JsoupContentExtractor: after removing removeSelectors, root = "main" or "article"
// Then: root.select("h1, h2, h3, p, li") — still matches inside:
//   <aside>  — "ასევე იხილეთ" link lists → nav noise in li blocks
//   <figure> — captions not content
//   <figcaption> — image alt-text descriptions
//   [hidden] — HTML hidden attribute: JS-rendered content not yet visible
//   [aria-hidden=true] — screen-reader-hidden decorative elements
```

**Resolution — `geostat-portal-parse.yaml` additions:**

```yaml
# geostat-portal-parse.yaml — add to removeSelectors:
removeSelectors:
  # ... existing selectors ...

  # Semantic structural noise — inside main/article but not content
  - "aside"
  - "figure"
  - "figcaption"

  # Hidden elements — CSS display:none equiv in HTML attributes
  # (Jsoup doesn't evaluate CSS — must use attribute selectors)
  - "[hidden]"
  - "[aria-hidden=true]"

  # Decorative / interactive widgets inside content area
  - ".tabs-navigation"          # tab switcher widgets (not the tab content)
  - ".share-buttons"            # share widget
  - ".related-articles"         # "ასევე იხილეთ" sidebar block
  - ".print-button"             # print icon areas
  - "[role=complementary]"      # ARIA aside equivalent
  - "[role=navigation]"         # any remaining navigation regions
```

**Why NOT removing `<aside>` via YAML removeSelector earlier:**

`<aside>` could be inside `main` or `article` — the root container. `removeSelectors` runs on `clone` before `selectRoot()`, so it will remove `<aside>` even if nested inside root. Safe.

**Unit tests:**

```java
// JsoupContentExtractorTest:

@Test
void extract_removesAside_beforeBodyExtraction() {
    String html = """
        <html><body><main>
          <p>მთავარი კონტენტი სტატიისა.</p>
          <aside>
            <ul><li><a href="...">ასევე იხილეთ: სხვა სტატია</a></li></ul>
          </aside>
        </main></body></html>""";
    CleanedDocument doc = extractor.extract(
        new HtmlPageInput(html, "https://x.ge"), profileWithAsideRemoval);
    assertThat(doc.body()).contains("მთავარი კონტენტი");
    assertThat(doc.body()).doesNotContain("ასევე იხილეთ");
}

@Test
void extract_removesAriaHiddenElements() {
    String html = """
        <html><body><article>
          <p>სტატია.</p>
          <div aria-hidden="true"><p>decorative overlay text</p></div>
        </article></body></html>""";
    CleanedDocument doc = extractor.extract(
        new HtmlPageInput(html, "https://x.ge"), profileWithAriaHiddenRemoval);
    assertThat(doc.body()).doesNotContain("decorative overlay");
}

@Test
void extract_removesFigcaption() {
    String html = """
        <html><body><article>
          <p>ტექსტი სტატიაში.</p>
          <figure>
            <img src="chart.png" />
            <figcaption>სქემა 1. GDP ზრდა</figcaption>
          </figure>
        </article></body></html>""";
    CleanedDocument doc = extractor.extract(
        new HtmlPageInput(html, "https://x.ge"), profileWithFigureRemoval);
    assertThat(doc.body()).doesNotContain("სქემა 1");
    assertThat(doc.body()).contains("ტექსტი სტატიაში");
}
```

**ფაილები:**
- Update: `ops/config/corpus/geostat-portal-parse.yaml` — add 9 new `removeSelectors`

**Acceptance criteria:**
- `<aside>` content (nav links) does NOT appear in `body`.
- `[aria-hidden=true]` elements excluded.
- `<figure>` / `<figcaption>` excluded.
- `[role=navigation]` excluded.
- Real content (outside these elements) unchanged.

---

### L-1-19 — `<div>` direct text content missed by `h1-h6, p, li` selector 🟡

**Root cause:**

```java
// line 84:
Elements candidates = root.select("h1, h2, h3, h4, h5, h6, p, li");
// fallback (line 89): only activated if candidates is EMPTY

// Problem: modern HTML often uses divs directly:
// <div class="stat-value">47 მლნ. ლარი</div>
// <div class="description">GDP 2024 წელს გაიზარდა 8.7%-ით.</div>
// Neither is captured by candidates; fallback also misses it (candidates not empty)
```

**Resolution — two-layer approach:**

**Layer 1 — YAML `addSelectors` field (new, opt-in):**

```yaml
# geostat-portal-parse.yaml:
addSelectors:
  # div elements with meaningful content classes — dataset/infographic pages
  - ".stat-value"
  - ".stat-description"
  - ".dataset-description"
  - "dd"          # definition list values — often used for statistics
  - "dt"          # definition list terms
  - "blockquote"  # quoted statistics/official statements
```

**Layer 2 — `JsoupContentExtractor.extractBody()` — merge `addSelectors`:**

```java
private ExtractionStats extractBody(Element root, ParseProfile profile) {
    // ...
    Elements candidates = new Elements();
    candidates.addAll(root.select("h1, h2, h3, h4, h5, h6, p, li"));

    // addSelectors: site-specific elements with direct text content
    // only add if not already covered by standard selectors
    for (String extraSel : profile.addSelectors()) {
        if (extraSel != null && !extraSel.isBlank()) {
            candidates.addAll(root.select(extraSel));
        }
    }

    if (profile.extractTables()) {
        candidates.addAll(root.select("table"));
    }

    // ancestor-aware dedup (L-1-15) runs on the merged candidates
    Set<Element> covered = buildCoveredSet(candidates, root);
    // ...
}
```

**`ParseProfile` record: add `addSelectors()`:**

```java
// ParseProfile.java:
public record ParseProfile(
    // ... existing fields ...
    List<String> removeSelectors,
    List<String> addSelectors,      // ← NEW: site-specific extra selectors
    // ...
) {
    public List<String> addSelectors() {
        return addSelectors != null ? addSelectors : List.of();
    }
}
```

**Unit tests:**

```java
// JsoupContentExtractorTest:

@Test
void extract_capturesDivStatValue_viaAddSelectors() {
    String html = """
        <html><body><div class="value-databases-section">
          <div class="stat-value">47 მლნ. ლარი</div>
          <div class="stat-description">GDP 2024 წელს გაიზარდა 8.7%-ით.</div>
        </div></body></html>""";
    // profile has addSelectors: [".stat-value", ".stat-description"]
    CleanedDocument doc = extractor.extract(
        new HtmlPageInput(html, "https://x.ge"), profileWithAddSelectors);
    assertThat(doc.body()).contains("47 მლნ. ლარი");
    assertThat(doc.body()).contains("8.7%-ით");
}

@Test
void extract_defaultProfile_withoutAddSelectors_doesNotCrash() {
    // profile.addSelectors() returns empty list → no extra candidates
    CleanedDocument doc = extractor.extract(
        new HtmlPageInput("<html><body><p>text</p></body></html>", "https://x.ge"),
        defaultProfile);
    assertThat(doc.body()).isNotBlank();
}
```

**ფაილები:**
- Update: `libs/platform-contracts/.../parse/ParseProfile.java` — `addSelectors()` field
- Update: `apps/ingestion-service/.../parse/profile/JsoupContentExtractor.java` — merge `addSelectors` into `candidates`
- Update: `ops/config/corpus/geostat-portal-parse.yaml` — add `addSelectors` section

**Acceptance criteria:**
- `<div class="stat-value">` captured in `body` when listed in `addSelectors`.
- `addSelectors` default = empty list — existing behavior unchanged.
- Ancestor-aware dedup (L-1-15) still prevents duplication when `<div>` wraps `<p>`.
- No regression on standard `h1-h6, p, li` extraction.

---

---

### L-1-20 — `stripFromBody` paragraph boundary loss: design flaw fix 🔴

**Root cause — კოდიდან პირდაპირ:**

```java
// JsoupContentExtractor.extractBody() line 110:
return new ExtractionStats(String.join(" ", blocks), total, boilerplate);
//                                         ↑ single space — all paragraph boundaries destroyed

// MarkerBoilerplateStripper.stripFromBody() line 88:
String[] parts = text.split("\\s{2,}|\\n+");
// receives: "ბლოკი1 ბლოკი2 ბლოკი3"  (single spaces only)
// split result: ["ბლოკი1 ბლოკი2 ბლოკი3"]  ← ONE paragraph

// stripLeading/stripTrailing operates on ONE item → never trims anything
// isBoilerplateParagraph on the whole joined string → never matches a short marker
```

**Impact chain:**
- `boilerplateMarkers` per-block filtering in `extractBody()` ← works ✅
- `stripFromBody()` → `stripLeading` / `stripTrailing` ← **never activates** — design dead code
- If a boilerplate block somehow passes `extractBody()` check (e.g. false-negative), `stripFromBody` won't catch it as second defence
- `stripLeading: true` in YAML is documented as intentional, but the code path makes it inoperative

**Resolution — `ExtractionStats` carries `List<String> blocks`; `stripFromBody` works on list:**

**Step 1 — `ExtractionStats`: pass blocks as list, not pre-joined string:**

```java
// JsoupContentExtractor.java — inner record:
private record ExtractionStats(
    List<String> blocks,          // individual filtered blocks (not yet joined)
    int totalBlocks,
    int boilerplateBlocks
) {
    /** Joins blocks preserving paragraph separation (double newline). */
    String joinedText() {
        return String.join("\n\n", blocks);   // double newline = paragraph boundary
    }
}
```

**Step 2 — `extractBody()`: return list, not joined string:**

```java
private ExtractionStats extractBody(Element root, ParseProfile profile) {
    if (root == null) return new ExtractionStats(List.of(), 0, 0);

    List<String> blocks = new ArrayList<>();
    int total = 0, boilerplate = 0;
    Elements candidates = new Elements();
    candidates.addAll(root.select("h1, h2, h3, h4, h5, h6, p, li"));
    for (String s : profile.addSelectors()) {
        if (s != null && !s.isBlank()) candidates.addAll(root.select(s));
    }
    if (profile.extractTables()) candidates.addAll(root.select("table"));

    Set<Element> covered = buildCoveredSet(candidates, root); // L-1-15

    if (candidates.isEmpty()) {
        String fallback = root.text().trim().replaceAll("\\s+", " ");
        if (fallback.isBlank()) return new ExtractionStats(List.of(), 0, 0);
        boolean bp = boilerplateStripper.isBoilerplateParagraph(fallback, profile);
        return new ExtractionStats(bp ? List.of() : List.of(fallback), 1, bp ? 1 : 0);
    }

    for (Element block : candidates) {
        if (covered.contains(block)) continue;
        String text = block.text().trim().replaceAll("\\s+", " ");
        if (text.isBlank()) continue;
        total++;
        if (boilerplateStripper.isBoilerplateParagraph(text, profile)) {
            boilerplate++;
            continue;
        }
        blocks.add(text);
    }
    return new ExtractionStats(List.copyOf(blocks), total, boilerplate);
}
```

**Step 3 — `extract()`: pass `joinedText()` (double-newline separated) to `stripFromBody`:**

```java
// JsoupContentExtractor.extract():
ExtractionStats stats = extractBody(root, profile);
// joinedText() uses "\n\n" → splitParagraphs("\s{2,}|\n+") now splits correctly
String body = boilerplateStripper.stripFromBody(stats.joinedText(), profile);
```

**Step 4 — `MarkerBoilerplateStripper.stripFromBody()`: now receives properly separated text:**

```java
// stripParagraphs on "blockA\n\nblockB\n\nblockC":
// → ["blockA", "blockB", "blockC"]  ← 3 paragraphs
// stripLeading/stripTrailing now works on individual blocks
// isBoilerplateParagraph check is a genuine second-pass defence
```

**Step 5 — `CleanedDocument.bodyText()` contract: keep `\n\n` or flatten to single space?**

```java
// CleanedDocument:
public String bodyText() { return body; }           // preserves \n\n for chunking
public String bodyFlat()  {                         // for legacy callers needing single-line
    return body.replaceAll("\\s+", " ").strip();
}
```

**Unit tests:**

```java
// MarkerBoilerplateStripperTest:

@Test
void stripFromBody_stripsLeading_whenFirstBlockIsBoilerplate() {
    // blocks joined with "\n\n"
    String text = "სრულად ნახვა\n\nბუნებრივი მოძრაობის მაჩვენებელი 2024 წელს.";
    String result = stripper.stripFromBody(text, kaProfile);
    assertThat(result).doesNotContain("სრულად ნახვა");
    assertThat(result).contains("ბუნებრივი მოძრაობის");
}

@Test
void stripFromBody_stripsTrailing_whenLastBlockIsBoilerplate() {
    String text = "ბუნებრივი მოძრაობის მაჩვენებელი.\n\nუკან დაბრუნება";
    String result = stripper.stripFromBody(text, kaProfile);
    assertThat(result).doesNotContain("უკან დაბრუნება");
    assertThat(result).contains("ბუნებრივი მოძრაობის");
}

@Test
void stripFromBody_stripsMiddle_whenMarkerBlock() {
    String text = "ბლოკი1.\n\nCSV Download\n\nბლოკი3.";
    String result = stripper.stripFromBody(text, kaProfile);
    assertThat(result).doesNotContain("CSV Download");
    assertThat(result).contains("ბლოკი1.");
    assertThat(result).contains("ბლოკი3.");
}

// JsoupContentExtractorTest:
@Test
void extractBody_joinedWithDoubleNewline_enablingParagraphStrip() {
    String html = """
        <html><body><main>
          <p>სრულად ნახვა</p>
          <p>ბუნებრივი მოძრაობის მაჩვენებელი 2024 წელს.</p>
          <p>GDP 8.7%-ით გაიზარდა.</p>
        </main></body></html>""";
    CleanedDocument doc = extractor.extract(
        new HtmlPageInput(html, "https://x.ge"), kaProfile);
    // "სრულად ნახვა" is in boilerplateMarkers.ka → stripped
    assertThat(doc.bodyText()).doesNotContain("სრულად ნახვა");
    assertThat(doc.bodyText()).contains("ბუნებრივი მოძრაობის");
    assertThat(doc.bodyText()).contains("GDP 8.7%");
}
```

**ფაილები:**
- Update: `apps/ingestion-service/.../parse/profile/JsoupContentExtractor.java` — `ExtractionStats` + `extractBody()` + `joinedText()`
- Update: `libs/platform-contracts/.../parse/CleanedDocument.java` — `bodyFlat()` method

**Acceptance criteria:**
- `stripLeading: true` → first boilerplate block removed from final body.
- `stripTrailing: true` → last boilerplate block removed from final body.
- Middle boilerplate blocks (between real content) removed.
- `bodyFlat()` returns single-space-joined string for callers that need it.
- `ParagraphRepetitionDetector` (L-1-08) rate drops — fewer false violations after fix.

---

### L-1-21 — `TextSanitizer`: icon font PUA + invisible Unicode + bidi mark removal 🔴

**Root cause:**

Jsoup's `.text()` extracts ALL text nodes, including:

```
1. Icon font characters (Unicode Private Use Area: U+E000–U+F8FF):
   <i class="icon-arrow">&#xE001;</i>  →  "\uE001" in body
   FontAwesome, geostat's custom icon font → appear as □ or invisible char in LLM input

2. Zero-width characters:
   U+200B  zero-width space
   U+200C  zero-width non-joiner
   U+200D  zero-width joiner
   U+FEFF  BOM (appears mid-text in copy-pasted Georgian content)

3. Bidirectional control marks:
   U+200E  left-to-right mark
   U+200F  right-to-left mark
   U+202A–U+202E  embedding / override / pop directional formatting

4. Soft hyphen:
   U+00AD  soft hyphen — invisible in rendering but present in text
```

**Impact:**
- LLM tokenizer sees these as valid tokens → embedding noise
- Similarity search: `"GDP\uE001 8.7%"` vs `"GDP 8.7%"` → different vectors
- `boilerplateStripper.normalize()` uses NFKC — handles `\u00A0` (NBSP) ✅ but NOT the above

**Resolution — `TextSanitizer` utility class:**

შექმენი:
`libs/platform-contracts/src/main/java/com/geostat/platform/parse/TextSanitizer.java`

```java
package com.geostat.platform.parse;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Post-Jsoup text sanitization.
 *
 * Applied AFTER Jsoup's element.text() extraction, BEFORE boilerplate filtering.
 * Removes Unicode artifacts that Jsoup's .text() does not strip:
 *   - Icon font characters (Unicode Private Use Area)
 *   - Zero-width and invisible characters
 *   - Bidirectional control marks
 *   - Redundant whitespace
 *
 * Does NOT alter Georgian (U+10A0–U+10FF) or any standard Unicode range.
 */
public final class TextSanitizer {

    private TextSanitizer() {}

    // Unicode Private Use Area — web font icons (FontAwesome, custom glyph fonts)
    private static final Pattern PUA_CHARS =
        Pattern.compile("[\uE000-\uF8FF\uF0000-\uFFFFF]");

    // Zero-width and invisible formatting characters
    private static final Pattern INVISIBLE_CHARS =
        Pattern.compile("[\u200B\u200C\u200D\u00AD\uFEFF]");

    // Bidirectional control marks
    private static final Pattern BIDI_MARKS =
        Pattern.compile("[\u200E\u200F\u202A-\u202E\u2066-\u2069]");

    // Collapse multiple spaces/newlines after removal
    private static final Pattern MULTI_SPACE =
        Pattern.compile("[ \t]{2,}");

    /**
     * Full sanitization pipeline for extracted text blocks.
     *
     * Order:
     *  1. NFKC Unicode normalization (converts NBSP \u00A0 to space, etc.)
     *  2. Strip PUA icon font characters
     *  3. Strip invisible / zero-width chars
     *  4. Strip bidi marks
     *  5. Collapse multi-space (but preserve \n\n paragraph separators)
     *  6. Trim
     */
    public static String sanitize(String text) {
        if (text == null || text.isBlank()) return "";

        String s = Normalizer.normalize(text, Normalizer.Form.NFKC);
        s = PUA_CHARS.matcher(s).replaceAll("");
        s = INVISIBLE_CHARS.matcher(s).replaceAll("");
        s = BIDI_MARKS.matcher(s).replaceAll("");
        s = MULTI_SPACE.matcher(s).replaceAll(" ");
        return s.strip();
    }

    /**
     * Sanitize a single extracted text block (no newline preservation needed).
     * Used inside extractBody() per-block.
     */
    public static String sanitizeBlock(String text) {
        if (text == null || text.isBlank()) return "";
        String s = Normalizer.normalize(text, Normalizer.Form.NFKC);
        s = PUA_CHARS.matcher(s).replaceAll("");
        s = INVISIBLE_CHARS.matcher(s).replaceAll("");
        s = BIDI_MARKS.matcher(s).replaceAll("");
        return s.replaceAll("\\s+", " ").strip();
    }

    /** True if the text is non-null, non-blank after sanitization. */
    public static boolean hasContent(String text) {
        return !sanitizeBlock(text).isBlank();
    }
}
```

**Integrate into `JsoupContentExtractor.extractBody()` per-block:**

```java
// extractBody() — in the candidates loop:
for (Element block : candidates) {
    if (covered.contains(block)) continue;

    String raw  = block.text().trim();
    String text = TextSanitizer.sanitizeBlock(raw);   // ← sanitize after .text()
    text = text.replaceAll("\\s+", " ");

    if (text.isBlank()) continue;
    // ...
}
```

**Unit tests:**

```java
// TextSanitizerTest:

@Test
void sanitize_removesPrivateUseAreaChars() {
    String input = "GDP\uE001 8.7%-ით გაიზარდა.";
    assertThat(TextSanitizer.sanitizeBlock(input)).isEqualTo("GDP 8.7%-ით გაიზარდა.");
}

@Test
void sanitize_removesZeroWidthSpace() {
    String input = "მოსახლეობა\u200B2024";
    assertThat(TextSanitizer.sanitizeBlock(input)).isEqualTo("მოსახლეობა2024");
}

@Test
void sanitize_removesBom() {
    String input = "\uFEFFბუნებრივი მოძრაობა";
    assertThat(TextSanitizer.sanitizeBlock(input)).isEqualTo("ბუნებრივი მოძრაობა");
}

@Test
void sanitize_removesBidiMarks() {
    String input = "Population\u200E მოსახლეობა";
    assertThat(TextSanitizer.sanitizeBlock(input)).isEqualTo("Population მოსახლეობა");
}

@Test
void sanitize_preservesGeorgianScript() {
    String ka = "საქართველოს სტატისტიკის ეროვნული სამსახური";
    assertThat(TextSanitizer.sanitizeBlock(ka)).isEqualTo(ka);
}

@Test
void sanitize_preservesLatin() {
    String en = "National Statistics Office of Georgia";
    assertThat(TextSanitizer.sanitizeBlock(en)).isEqualTo(en);
}

@Test
void sanitize_convertsNbspToSpace() {
    // NFKC converts \u00A0 (NBSP) to regular space
    String input = "GDP\u00A08.7%";
    assertThat(TextSanitizer.sanitizeBlock(input)).isEqualTo("GDP 8.7%");
}

@Test
void sanitize_nullAndBlank_returnEmpty() {
    assertThat(TextSanitizer.sanitizeBlock(null)).isEmpty();
    assertThat(TextSanitizer.sanitizeBlock("   ")).isEmpty();
    assertThat(TextSanitizer.sanitizeBlock("\uE001\uE002")).isEmpty(); // only icons
}
```

**ფაილები:**
- New: `libs/platform-contracts/src/main/java/com/geostat/platform/parse/TextSanitizer.java`
- Update: `apps/ingestion-service/.../parse/profile/JsoupContentExtractor.java` — call `TextSanitizer.sanitizeBlock()` per block in `extractBody()`

**Acceptance criteria:**
- PUA characters (U+E000–U+F8FF) never appear in `document.content_text`.
- Zero-width space, BOM, bidi marks stripped.
- Georgian script (U+10A0–U+10FF) — unchanged.
- Latin, digits, punctuation — unchanged.
- `TextSanitizer.sanitizeBlock(null)` → `""` (no NPE).
- `boilerplateStripper.normalize()` still runs after sanitize (NFKC not doubled — idempotent).

---

### L-1-22 — `legacyClean` fallback: apply boilerplate + log WARN when profile missing 🟠

**Root cause — `HtmlContentCleaner.legacyClean()` line 48–73:**

```java
private CleanedContent legacyClean(Document html) {
    Document clone = html.clone();
    clone.select("script, style, nav, footer, header, noscript, iframe, svg").remove();
    // ↑ minimal hardcoded removal — no profile removeSelectors
    // ↑ no aside, no cookie-banner, no pagination, no modal

    String text = root == null ? "" : root.text().trim().replaceAll("\\s+", " ");
    // ↑ raw .text() dump — no boilerplateStripper call at all
    // ↑ no TextSanitizer
    // ↑ ALL sidebar links, footer, related-articles → in body
```

**When does this run:**
- `parseProperties.profile().enabled() = false` (config toggle off)
- `corpusName = null` (document ingested without corpus context)
- Any path that doesn't resolve a `ParseProfile`

**Resolution — `legacyClean()`: apply TextSanitizer + hardcoded boilerplate pass + WARN:**

```java
private static final Logger log = LoggerFactory.getLogger(HtmlContentCleaner.class);

// Minimal hardcoded boilerplate phrases — profile-agnostic, always safe to strip
private static final List<String> LEGACY_BOILERPLATE_STARTS = List.of(
    "skip to content", "გამოიწერეთ სიახლეები", "csv download",
    "უკან დაბრუნება", "სრულად ნახვა", "read more", "archive",
    "subscribe to news", "audio narration"
);
private static final List<String> LEGACY_BOILERPLATE_CONTAINS = List.of(
    "crafted by", "ვებგვერდის ადაპტირებული ვერსია",
    "official website of geostat", "საქსტატის ოფიციალური ვებგვერდი"
);

private CleanedContent legacyClean(Document html) {
    // WARN: profile-driven extraction should be used instead
    log.warn("[legacy-clean] No ParseProfile resolved — falling back to legacy extraction. " +
        "Boilerplate filtering will be limited. Ensure corpusName is set and " +
        "parseProperties.profile.enabled=true for production use.");

    Document clone = html.clone();
    // expanded hardcoded removal (aligned with L-1-18 recommendations)
    clone.select(
        "script, style, nav, footer, header, noscript, iframe, svg, " +
        "aside, figure, figcaption, [hidden], [aria-hidden=true], " +
        ".breadcrumb, .breadcrumb-wrapper, .pagination, " +
        ".cookie-banner, .social-share, .modal, .modal-csv"
    ).remove();

    Element main = clone.selectFirst("main, article, [role=main]");
    Element root = main != null ? main : clone.body();

    String title    = resolveTitle(html, root);
    List<String> sectionPath = SectionPathExtractor.extract(html);
    String language = inferLegacyLanguage(html);

    // Apply TextSanitizer + hardcoded boilerplate filter per block
    List<String> cleanBlocks = new ArrayList<>();
    if (root != null) {
        for (Element el : root.select("h1,h2,h3,h4,h5,h6,p,li")) {
            String raw   = el.text().trim();
            String block = TextSanitizer.sanitizeBlock(raw);
            if (block.isBlank()) continue;
            if (isLegacyBoilerplate(block)) continue;
            cleanBlocks.add(block);
        }
        if (cleanBlocks.isEmpty()) {
            // last resort: raw text if no semantic elements found
            String fallback = TextSanitizer.sanitize(root.text());
            if (!fallback.isBlank()) cleanBlocks.add(fallback);
        }
    }
    String text = String.join("\n\n", cleanBlocks);

    PageDisplayMetadataExtractor.DisplayMetadata display =
        displayMetadataExtractor.extract(html, title, sectionPath);
    return new CleanedContent(title, text, language, sectionPath,
        display.metaDescription(), display.leadText(), display.displayDescription());
}

private static boolean isLegacyBoilerplate(String text) {
    String lower = text.toLowerCase();
    for (String s : LEGACY_BOILERPLATE_STARTS) if (lower.startsWith(s)) return true;
    for (String s : LEGACY_BOILERPLATE_CONTAINS) if (lower.contains(s))  return true;
    return false;
}

private static String inferLegacyLanguage(Document html) {
    String lang = html.select("html").attr("lang");
    if (!lang.isBlank()) return lang.split("-")[0].toLowerCase();
    return "ka"; // safe default — never return null
}
```

**Unit tests:**

```java
// HtmlContentCleanerTest:

@Test
void legacyClean_logsWarn_andExtractsContent() {
    // when profile.enabled=false → legacyClean path
    // must: warn logged, content extracted, boilerplate removed
    Document html = Jsoup.parse("""
        <html lang="ka"><body>
          <header><nav><a>მთავარი</a></nav></header>
          <main>
            <p>ბუნებრივი მოძრაობის მაჩვენებელი.</p>
            <p>სრულად ნახვა</p>
          </main>
          <footer>Crafted by Agency</footer>
        </body></html>""");
    CleanedContent result = cleaner.clean(html);
    assertThat(result.text()).contains("ბუნებრივი მოძრაობის");
    assertThat(result.text()).doesNotContain("სრულად ნახვა");
    assertThat(result.text()).doesNotContain("Crafted by");
    assertThat(result.language()).isEqualTo("ka");
    // verify WARN logged (use LogCaptor or mock appender)
}

@Test
void legacyClean_language_neverNull() {
    Document html = Jsoup.parse("<html><body><p>text</p></body></html>");
    CleanedContent result = cleaner.clean(html);
    assertThat(result.language()).isNotNull();
    assertThat(result.language()).isEqualTo("ka"); // fallback
}
```

**ფაილები:**
- Update: `apps/ingestion-service/.../parse/HtmlContentCleaner.java` — `legacyClean()` rewrite

**Acceptance criteria:**
- `legacyClean` logs WARN on every call — operators always know it's running.
- `<aside>`, `<figure>`, `[aria-hidden]`, `.cookie-banner` removed in legacy path.
- Hardcoded boilerplate phrases stripped (ka + en).
- `TextSanitizer` applied — no PUA / invisible Unicode in legacy output.
- `language` never null — `"ka"` fallback applied.
- All legacy path tests pass.

---

---
