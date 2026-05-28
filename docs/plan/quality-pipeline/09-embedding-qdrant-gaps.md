## Embedding / Vector Index Gap Analysis — PERF-07..10 + ARCH-11..13

> **Session 2026-05-27:** PERF-07 (embedBatch), PERF-08 (saveAll), PERF-09 (@Transactional removed from orchestrator), PERF-10 (batch link dedup), ARCH-12 (HNSW + quantization + payload indexes) implemented.
> **Session 2026-05-27 (cont.):** ARCH-13 implemented — `QdrantVectorStore.buildPoint()` adds `navBreadcrumb`, `publishedAt`, `embeddingModel`; Qdrant RAM sizing comment in `application-custom.yml`.
> ARCH-11: covered by PERF-07.
> **Session 2026-05-27 (cont. 2):**
> QDRANT-01 ✅ — `QdrantCollectionManager.ensurePayloadIndexes()` now registers `navBreadcrumb` + `corpusName` (ARCH-12 was partial — these two were missing).
> QDRANT-02 ✅ — `QdrantSearchStore.search()` (retrieval-service) adds `serveState=live` as Qdrant `must` filter; previously only post-fetch Java filter.
> QDRANT-03 — `RetrievedChunk` extension with `navBreadcrumb`/`publishedAt` fields — requires platform-contracts change; deferred to owner decision.

> შემდეგი 7 item ღიაა. სენიორ gap analysis-ის შედეგი: embedding + Qdrant ლაირი.
> ყველა 🔴 item Phase 5 (indexing) პარალელურად უნდა გადაიჭრას.

---

### PERF-07 — `ChunkVectorIndexer`: `embedding.embed()` per-chunk → batch API call 🔴

**Root cause — `ChunkVectorIndexer.java` lines 102–107:**

```java
float[][] vectors = new float[chunks.size()][];
for (int i = 0; i < chunks.size(); i++) {
    ChunkEntity chunk = chunks.get(i);
    vectors[i] = embedding.embed(chunk.getText());  // ONE Gemini API call per chunk
    chunk.setEmbeddingModel(embedding.modelId());
}
```

```
1 document × 15 chunks = 15 individual Gemini embedding requests
10,000 documents = 150,000 Gemini API calls for embedding alone
Gemini text-embedding-004: 1,500 requests/min quota
→ 150,000 calls ÷ 1,500 req/min = 100 minutes just for embedding
With batch:
  10,000 calls (1 per document, each with 15 texts) = ~7 minutes
  = 15× faster + 15× lower API cost
```

**Root cause 2 — `EmbeddingPort` missing `embedBatch()` method:**

`libs/embedding-adapters/src/main/java/com/geostat/embedding/EmbeddingPort.java` — current state:

```java
public interface EmbeddingPort {
    String modelId();
    int dimensions();
    float[] embed(String text);
    // MISSING: float[][] embedBatch(List<String> texts);
}
```

**Resolution:**

**Step 1 — Add `embedBatch()` to `EmbeddingPort`:**

ფაილი: `libs/embedding-adapters/src/main/java/com/geostat/embedding/EmbeddingPort.java`

```java
import java.util.List;

public interface EmbeddingPort {

    String modelId();
    int dimensions();
    float[] embed(String text);

    /**
     * Batch embed multiple texts in a single API call.
     *
     * Contract:
     *   - results[i] corresponds to texts.get(i)
     *   - texts must not be empty
     *   - max 100 texts per call (Gemini batch limit)
     *
     * Default: falls back to individual embed() calls.
     * Gemini adapter MUST override with real batch request.
     */
    default float[][] embedBatch(List<String> texts) {
        float[][] results = new float[texts.size()][];
        for (int i = 0; i < texts.size(); i++) {
            results[i] = embed(texts.get(i));
        }
        return results;
    }
}
```

**Step 2 — Gemini adapter real batch implementation:**

ფაილი: `libs/embedding-adapters/src/main/java/com/geostat/embedding/GeminiEmbeddingAdapter.java`

```java
@Override
public float[][] embedBatch(List<String> texts) {
    if (texts.isEmpty()) return new float[0][];

    // Gemini batchEmbedContents API supports max 100 texts per request.
    // For batches > 100: split into groups and call sequentially.
    //
    // REST endpoint: POST /v1/models/{model}:batchEmbedContents
    // Request body:
    //   {
    //     "requests": [
    //       { "model": "models/text-embedding-004",
    //         "content": { "parts": [{ "text": "ბუნებრივი მოძრაობა" }] }
    //       },
    //       ...
    //     ]
    //   }
    // Response:
    //   { "embeddings": [ { "values": [0.123, ...] }, ... ] }

    float[][] results = new float[texts.size()][];
    int offset = 0;
    int batchMax = 100;
    while (offset < texts.size()) {
        int end = Math.min(offset + batchMax, texts.size());
        List<String> batch = texts.subList(offset, end);
        float[][] batchResult = callGeminiBatchEmbed(batch);  // real HTTP call
        System.arraycopy(batchResult, 0, results, offset, batchResult.length);
        offset = end;
    }
    return results;
}

private float[][] callGeminiBatchEmbed(List<String> texts) {
    // Build JSON request, call Gemini REST, parse response
    // Reuse existing GeminiRestClient / WebClient / RestTemplate pattern
    // Handle rate limit 429 with exponential backoff (same as embed())
    // Return float[][] with embeddings in input order
}
```

**Step 3 — `ChunkVectorIndexer`: replace per-chunk loop with single `embedBatch()` call:**

ფაილი: `apps/ingestion-service/src/main/java/com/geostat/ingestion/index/ChunkVectorIndexer.java`

```java
// lines 102–107 — REPLACE:
// OLD (N individual API calls):
float[][] vectors = new float[chunks.size()][];
for (int i = 0; i < chunks.size(); i++) {
    ChunkEntity chunk = chunks.get(i);
    vectors[i] = embedding.embed(chunk.getText());
    chunk.setEmbeddingModel(embedding.modelId());
}

// NEW (single batch API call):
List<String> texts = chunks.stream().map(ChunkEntity::getText).toList();
float[][] vectors = embedding.embedBatch(texts);   // ONE API call for all chunks
String modelId = embedding.modelId();
for (ChunkEntity chunk : chunks) {
    chunk.setEmbeddingModel(modelId);
}
```

**Unit tests:**

```java
// ChunkVectorIndexerTest:

@Test
void indexDocumentInternal_callsEmbedBatch_never_embedPerChunk() {
    // given: document with 15 chunks
    when(embeddingPort.embedBatch(anyList()))
        .thenReturn(vectors15);

    indexer.indexDocumentInternal(documentId, corpusId);

    // then: embedBatch called ONCE with all 15 texts
    verify(embeddingPort, times(1))
        .embedBatch(argThat(list -> list.size() == 15));
    // verify: individual embed() never called
    verify(embeddingPort, never()).embed(anyString());
}

// GeminiEmbeddingAdapterTest:

@Test
void embedBatch_returnsVectors_inSameOrderAsInput() {
    // given: 3 Georgian texts
    List<String> texts = List.of(
        "ბუნებრივი მოძრაობა", "GDP 8.7%", "მოსახლეობა 3.7 მლნ");

    float[][] results = adapter.embedBatch(texts);

    assertThat(results).hasSize(3);
    Arrays.stream(results).forEach(vec ->
        assertThat(vec).hasSize(768)); // text-embedding-004 dimensions
}

@Test
void embedBatch_splits_intoGroupsOf100_whenLargeInput() {
    // given: 250 texts → should make 3 Gemini calls (100+100+50)
    List<String> texts = IntStream.range(0, 250)
        .mapToObj(i -> "text " + i).toList();

    adapter.embedBatch(texts);

    verify(geminiClient, times(3)).batchEmbed(anyList());
}

@Test
void embedBatch_defaultFallback_producesCorrectResults() {
    // given: EmbeddingPort using default embedBatch() implementation
    EmbeddingPort port = new EmbeddingPort() {
        public String modelId() { return "test"; }
        public int dimensions() { return 3; }
        public float[] embed(String t) { return new float[]{1f, 2f, 3f}; }
    };
    float[][] results = port.embedBatch(List.of("a", "b"));
    assertThat(results).hasSize(2);
    assertThat(results[0]).containsExactly(1f, 2f, 3f);
}
```

**ფაილები სარედაქციოდ:**
- `libs/embedding-adapters/src/main/java/com/geostat/embedding/EmbeddingPort.java` — `embedBatch()` + default impl
- `libs/embedding-adapters/src/main/java/com/geostat/embedding/GeminiEmbeddingAdapter.java` — real batch call
- `apps/ingestion-service/.../index/ChunkVectorIndexer.java` — lines 102–107 replacement

**Acceptance criteria:**
- [ ] `embedBatch()` method exists on `EmbeddingPort` interface with default fallback
- [ ] `GeminiEmbeddingAdapter.embedBatch()` makes single HTTP call for up to 100 texts
- [ ] Input > 100 texts: splits into sub-batches of 100, results merged in order
- [ ] `ChunkVectorIndexer` calls `embedBatch()` once per document, never `embed()` individually
- [ ] All chunks get correct `embedding_model` field set
- [ ] Gemini quota usage: 1 request per document (vs 15 before)

---

### PERF-08 — `ChunkVectorIndexer`: `save()` per chunk → `saveAll()` for chunk + vectorIndex 🔴

**Root cause — `ChunkVectorIndexer.java` lines 111–119:**

```java
for (ChunkEntity chunk : chunks) {
    chunkRepository.save(chunk);          // UPDATE per chunk (sets embedding_model)
    VectorIndexEntity index = new VectorIndexEntity();
    index.setChunk(chunk);
    index.setCollectionName(collectionName);
    index.setPointId(chunk.getId().toString());
    index.setIndexVersion(indexVersion);
    vectorIndexRepository.save(index);    // INSERT per chunk
}
```

```
15 chunks = 30 separate DB round-trips (2 per chunk: 1 UPDATE + 1 INSERT)
10,000 documents = 300,000 individual DB writes for vector indexing alone
Each DB round-trip: ~1ms network + ~2ms execution = ~3ms
300,000 × 3ms = 900 seconds (15 minutes) just for DB writes
With saveAll() + Hibernate batch_size=50:
  300,000 ÷ 50 = 6,000 batch executions = ~30 seconds
  = 30× faster
```

**Resolution — collect all entities, then two batch writes:**

ფაილი: `apps/ingestion-service/src/main/java/com/geostat/ingestion/index/ChunkVectorIndexer.java`

```java
// REPLACE lines 111–119:
// OLD (per-chunk individual saves):
for (ChunkEntity chunk : chunks) {
    chunkRepository.save(chunk);
    VectorIndexEntity index = new VectorIndexEntity();
    index.setChunk(chunk);
    index.setCollectionName(collectionName);
    index.setPointId(chunk.getId().toString());
    index.setIndexVersion(indexVersion);
    vectorIndexRepository.save(index);
}

// NEW (two batch writes):
List<VectorIndexEntity> vectorIndexEntities = new ArrayList<>(chunks.size());
for (ChunkEntity chunk : chunks) {
    // chunk.embeddingModel already set above (PERF-07)
    VectorIndexEntity index = new VectorIndexEntity();
    index.setChunk(chunk);
    index.setCollectionName(collectionName);
    index.setPointId(chunk.getId().toString());
    index.setIndexVersion(indexVersion);
    vectorIndexEntities.add(index);
}
// TWO batch DB operations instead of 30 individual ones
chunkRepository.saveAll(chunks);                    // batch UPDATE
vectorIndexRepository.saveAll(vectorIndexEntities); // batch INSERT
```

**Note:** `application-custom.yml` Hibernate batch config already specified in CFG-01:

```yaml
spring:
  jpa:
    properties:
      hibernate:
        jdbc:
          batch_size: 50
        order_inserts: true
        order_updates: true
```

**Unit tests:**

```java
// ChunkVectorIndexerTest:

@Test
void indexDocumentInternal_usesSaveAll_notIndividualSave() {
    // given: document with 15 chunks
    when(embeddingPort.embedBatch(anyList())).thenReturn(vectors15);

    indexer.indexDocumentInternal(documentId, corpusId);

    // then: saveAll called ONCE with 15 chunks
    verify(chunkRepository, times(1))
        .saveAll(argThat(list -> ((List<?>) list).size() == 15));
    verify(vectorIndexRepository, times(1))
        .saveAll(argThat(list -> ((List<?>) list).size() == 15));

    // verify: individual save() never called
    verify(chunkRepository, never()).save(any(ChunkEntity.class));
    verify(vectorIndexRepository, never()).save(any(VectorIndexEntity.class));
}

@Test
void indexDocumentInternal_vectorIndexEntities_haveCorrectFields() {
    when(embeddingPort.embedBatch(anyList())).thenReturn(vectors15);
    when(properties.indexing().indexVersion()).thenReturn("v2");

    indexer.indexDocumentInternal(documentId, corpusId);

    ArgumentCaptor<List<VectorIndexEntity>> captor =
        ArgumentCaptor.forClass(List.class);
    verify(vectorIndexRepository).saveAll(captor.capture());

    List<VectorIndexEntity> saved = captor.getValue();
    assertThat(saved).hasSize(15);
    saved.forEach(idx -> {
        assertThat(idx.getCollectionName()).isNotBlank();
        assertThat(idx.getPointId()).isNotBlank();
        assertThat(idx.getIndexVersion()).isEqualTo("v2");
        assertThat(idx.getChunk()).isNotNull();
    });
}
```

**ფაილები სარედაქციოდ:**
- `apps/ingestion-service/.../index/ChunkVectorIndexer.java` — lines 111–119: loop → `saveAll()`

**Acceptance criteria:**
- [ ] `chunkRepository.save()` never called inside `indexDocumentInternal()`
- [ ] `vectorIndexRepository.save()` never called inside `indexDocumentInternal()`
- [ ] `chunkRepository.saveAll()` called once with all chunks
- [ ] `vectorIndexRepository.saveAll()` called once with all `VectorIndexEntity` instances
- [ ] Each `VectorIndexEntity` has `chunk`, `collectionName`, `pointId`, `indexVersion` set
- [ ] Hibernate batch_size=50 from CFG-01 applies to both writes

---

### PERF-09 — `DocumentEnrichmentOrchestrator`: `@Transactional` wraps all LLM calls 🔴

**Root cause — `DocumentEnrichmentOrchestrator.java` lines 54–77:**

```java
@Transactional                                           // DB connection acquired HERE
public void enrichDocument(UUID documentId) {
    enrichDocument(documentId, true);
}

private void enrichDocument(UUID documentId, boolean includeEntities) {
    summaryEnrichmentService.enrichDocument(documentId);      // Gemini summary:   ~1–2s
    localePairEnrichmentService.enrichDocument(documentId);   // DB only:          ~20ms
    keywordEnrichmentService.enrichDocument(documentId);      // Gemini keywords:  ~500ms
    if (includeEntities) {
        entityEnrichmentService.enrichDocument(documentId);   // Gemini entities:  ~1–2s
    }
    pageKindEnrichmentService.enrichDocument(documentId);     // rule-based:       ~5ms
    titleVectorEnrichmentService.enrichDocument(documentId);  // Gemini embed:     ~200ms
    summaryVectorEnrichmentService.enrichDocument(documentId);// Gemini embed:     ~200ms
    topicAssignEnrichmentService.enrichDocument(documentId);  // Qdrant nearest:   ~100ms
    documentQdrantLifecycleSync.syncDocument(documentId);     // Qdrant HTTP:      ~200ms
}
// DB connection released HERE — after ~4–6 seconds of holding!
```

```
HikariCP pool size (CFG-01): 40 connections
EnrichmentBackfillService: 10 documents in parallel
10 documents × 4–6s connection hold = pool exhausted after 4 concurrent enrichments
→ HikariCP: "Connection is not available, request timed out after 30000ms"
→ enrichment fails under concurrency

Worse: if Gemini returns 429 (rate limit), retry wait = 60 seconds
→ connection held for 60+ seconds during retry
→ total pool starvation for all other DB operations (crawl, parse, chunk)
```

**Same anti-pattern as PERF-02** (HTTP fetch inside `@Transactional` CrawlRunStore). The fix follows the same three-phase pattern.

**Resolution — three-phase decomposition:**

ფაილი: `apps/ingestion-service/src/main/java/com/geostat/ingestion/enrichment/runner/DocumentEnrichmentOrchestrator.java`

```java
@Service
@Profile("db")
@ConditionalOnProperty(prefix = "geostat.ingestion.enrichment", name = "enabled", havingValue = "true")
public class DocumentEnrichmentOrchestrator {

    // same constructor with all services injected

    /**
     * Full enrichment: summary + keywords + entities + vectors + topic + Qdrant sync.
     *
     * Three-phase pattern to avoid holding DB connection during LLM calls:
     *   Phase 1 — LOAD  (short @Transactional ~10ms): read document data
     *   Phase 2 — ENRICH (no @Transactional ~4–6s): all external calls
     *   Phase 3 — SAVE  (short @Transactional ~20ms): persist results
     */
    public void enrichDocument(UUID documentId) {
        runEnrichment(documentId, true);
    }

    /** Backfill cutover: gate derivers only; skip Gemini entities. */
    public void enrichDocumentForBackfill(UUID documentId) {
        runEnrichment(documentId, false);
    }

    private void runEnrichment(UUID documentId, boolean includeEntities) {
        // Phase 1: load — connection held ~10ms
        EnrichmentInput input = loadInput(documentId);

        // Phase 2: external calls — NO DB connection held
        EnrichmentResults results = computeEnrichments(input, includeEntities);

        // Phase 3: save — connection held ~20ms
        persistResults(documentId, results);

        // Qdrant sync: OUTSIDE transaction (HTTP call to vector DB)
        documentQdrantLifecycleSync.syncDocument(documentId);
    }

    @Transactional(readOnly = true)
    EnrichmentInput loadInput(UUID documentId) {
        // Each enrichment service reads DocumentEntity independently at present.
        // After refactor: load once here, pass as EnrichmentInput.
        // If services are not refactored yet: they can still each load independently —
        // the key is removing @Transactional from the orchestrator.
        return new EnrichmentInput(documentId);
    }

    // No @Transactional — all LLM/Qdrant calls happen here
    EnrichmentResults computeEnrichments(EnrichmentInput input, boolean includeEntities) {
        summaryEnrichmentService.enrichDocument(input.documentId());
        localePairEnrichmentService.enrichDocument(input.documentId());
        keywordEnrichmentService.enrichDocument(input.documentId());
        if (includeEntities) {
            entityEnrichmentService.enrichDocument(input.documentId());
        }
        pageKindEnrichmentService.enrichDocument(input.documentId());
        titleVectorEnrichmentService.enrichDocument(input.documentId());
        summaryVectorEnrichmentService.enrichDocument(input.documentId());
        topicAssignEnrichmentService.enrichDocument(input.documentId());
        return new EnrichmentResults(); // marker — individual services write to DB
    }

    @Transactional
    void persistResults(UUID documentId, EnrichmentResults results) {
        // Individual services already persisted their own results above
        // in their own short @Transactional methods.
        // This method exists for future consolidation: gathering all writes
        // into one atomic transaction when services are refactored to be stateless.
    }

    record EnrichmentInput(UUID documentId) {}
    record EnrichmentResults() {}
}
```

**Important note on migration path:**

The current enrichment services (`SummaryEnrichmentService`, `KeywordEnrichmentService`, etc.) each have their own `@Transactional` `enrichDocument(UUID)` method. This is actually fine — they each do: load → call Gemini → save — which is the same anti-pattern per service.

**Full resolution (in order):**

1. Remove `@Transactional` from `DocumentEnrichmentOrchestrator.enrichDocument()` — **immediate fix, prevents pool starvation at orchestrator level**
2. For each enrichment service: split into `loadForEnrichment()` (short read TX) + `callLlm()` (no TX) + `saveEnrichmentResult()` (short write TX) — **Phase 5 work**

**Unit tests:**

```java
// DocumentEnrichmentOrchestratorTest:

@Test
void enrichDocument_noActiveTransaction_duringGeminiSummaryCalls() {
    doAnswer(inv -> {
        // This assertion runs INSIDE summaryEnrichmentService.enrichDocument()
        assertThat(TransactionSynchronizationManager.isActualTransactionActive())
            .as("No transaction must be active during LLM call")
            .isFalse();
        return null;
    }).when(summaryEnrichmentService).enrichDocument(any(UUID.class));

    orchestrator.enrichDocument(documentId);

    verify(summaryEnrichmentService, times(1)).enrichDocument(documentId);
}

@Test
void enrichDocumentForBackfill_skipsEntityEnrichment() {
    orchestrator.enrichDocumentForBackfill(documentId);

    verify(entityEnrichmentService, never()).enrichDocument(any(UUID.class));
    verify(summaryEnrichmentService, times(1)).enrichDocument(documentId);
}

@Test
void enrichDocument_callsQdrantSync_afterAllEnrichments() {
    InOrder order = inOrder(
        summaryEnrichmentService,
        keywordEnrichmentService,
        documentQdrantLifecycleSync);

    orchestrator.enrichDocument(documentId);

    order.verify(summaryEnrichmentService).enrichDocument(documentId);
    order.verify(keywordEnrichmentService).enrichDocument(documentId);
    order.verify(documentQdrantLifecycleSync).syncDocument(documentId);
}
```

**ფაილები სარედაქციოდ:**
- `apps/ingestion-service/.../enrichment/runner/DocumentEnrichmentOrchestrator.java` — remove `@Transactional` from `enrichDocument()` and `enrichDocumentForBackfill()`

**Acceptance criteria:**
- [ ] `@Transactional` annotation removed from both public `enrichDocument()` and `enrichDocumentForBackfill()` methods
- [ ] Private `enrichDocument(UUID, boolean)` has no `@Transactional`
- [ ] Each enrichment service's own `@Transactional` methods remain untouched
- [ ] `documentQdrantLifecycleSync.syncDocument()` called after enrichment services (Qdrant call = outside any TX)
- [ ] Integration test: 10 concurrent enrichments → no HikariCP timeout
- [ ] TransactionSynchronizationManager: no active TX during LLM service calls

---

### PERF-10 — `LinkDiscoverer`: N individual `existsByCrawlRun_IdAndUrlHash()` → batch 🟠

**Root cause — `LinkDiscoverer.java` line 57:**

```java
for (Element link : links) {
    String abs = link.absUrl("href");
    // ... filtering ...
    String hash = UrlHasher.hash(abs);
    if (urlFrontierRepository.existsByCrawlRun_IdAndUrlHash(crawlRunId, hash)) {
        continue;   // ONE SELECT EXISTS per link
    }
    // ... create frontier entity ...
}
```

```
geostat.ge: avg 50 links per page
10,000 pages × 50 links = 500,000 individual SELECT EXISTS queries
Each query = 1 index lookup + 1 round-trip = ~2ms
500,000 × 2ms = 1,000 seconds (16 minutes) just for link dedup checks

With batch IN query:
  10,000 single queries = ~50ms total
  = 1,000× fewer DB round-trips
```

**Resolution — collect all hashes, one batch EXISTS query, filter in memory:**

ფაილი: `apps/ingestion-service/src/main/java/com/geostat/ingestion/crawl/frontier/LinkDiscoverer.java`

```java
@Component
public class LinkDiscoverer {

    private final UrlFrontierRepository urlFrontierRepository;

    public LinkDiscoverer(UrlFrontierRepository urlFrontierRepository) {
        this.urlFrontierRepository = urlFrontierRepository;
    }

    public List<UrlFrontierEntity> discover(
            UUID crawlRunId, CorpusEntity corpus,
            UrlFrontierEntity parent, Document html, int maxDepth) {

        if (parent.getDepth() >= maxDepth) return List.of();

        // Step 1: collect all valid candidate URLs (NO DB calls in this loop)
        record Candidate(String url, String hash) {}
        List<Candidate> candidates = new ArrayList<>();
        Set<String> seenOnPage = new HashSet<>();

        for (Element link : html.select("a[href]")) {
            String abs = link.absUrl("href");
            if (abs.isBlank() || !seenOnPage.add(abs)) continue;

            URI uri;
            try { uri = URI.create(abs); }
            catch (IllegalArgumentException e) { continue; }

            if (!"http".equalsIgnoreCase(uri.getScheme())
                    && !"https".equalsIgnoreCase(uri.getScheme())) continue;

            if (!CorpusPolicy.isHostAllowed(corpus, uri.getHost())) continue;
            if (!CorpusPolicy.isUrlAllowed(corpus, abs)) continue;

            // Apply URL normalization (L-1-26): strip utm_*, fbclid etc.
            String normalized = UrlNormalizer.normalize(abs);
            candidates.add(new Candidate(normalized, UrlHasher.hash(normalized)));
        }

        if (candidates.isEmpty()) return List.of();

        // Step 2: ONE batch DB query for all candidate hashes
        Set<String> allHashes = candidates.stream()
            .map(Candidate::hash)
            .collect(Collectors.toSet());

        Set<String> alreadyQueued = new HashSet<>(
            urlFrontierRepository.findExistingHashesByCrawlRunAndHashIn(
                crawlRunId, allHashes));
        // SQL: SELECT url_hash FROM ingestion.url_frontier
        //      WHERE crawl_run_id = :crawlRunId
        //        AND url_hash IN (:hashes)
        // ONE query, index lookup on (crawl_run_id, url_hash)

        // Step 3: build new frontier entities (in-memory, no DB calls)
        List<UrlFrontierEntity> discovered = new ArrayList<>();
        for (Candidate c : candidates) {
            if (alreadyQueued.contains(c.hash())) continue;

            UrlFrontierEntity frontier = new UrlFrontierEntity();
            frontier.setUrl(c.url());
            frontier.setUrlHash(c.hash());
            frontier.setDepth(parent.getDepth() + 1);
            frontier.setParentUrl(parent.getUrl());
            frontier.setStatus(FrontierStatus.queued);
            frontier.setAttemptCount(0);
            discovered.add(frontier);
        }

        // Caller (CrawlRunStore) calls urlFrontierRepository.saveAll(discovered) — PERF-04
        return discovered;
    }
}
```

**New repository method:**

ფაილი: `apps/ingestion-service/.../persistence/repository/UrlFrontierRepository.java`

```java
/**
 * Returns the subset of urlHashes that already exist in the frontier
 * for the given crawl run. Single batch query instead of N individual
 * existsByCrawlRun_IdAndUrlHash() calls.
 */
@Query("SELECT f.urlHash FROM UrlFrontierEntity f " +
       "WHERE f.crawlRun.id = :crawlRunId AND f.urlHash IN :hashes")
List<String> findExistingHashesByCrawlRunAndHashIn(
    @Param("crawlRunId") UUID crawlRunId,
    @Param("hashes") Set<String> hashes);
```

**V20 migration — index for this query:**

```sql
-- V20 (if not already present):
CREATE INDEX IF NOT EXISTS idx_url_frontier_crawl_run_hash
  ON ingestion.url_frontier (crawl_run_id, url_hash);
-- Supports: findExistingHashesByCrawlRunAndHashIn (PERF-10)
-- Supports: existsByCrawlRun_IdAndUrlHash (existing query, can now be deprecated)
```

**Unit tests:**

```java
// LinkDiscovererTest:

@Test
void discover_callsSingleBatchQuery_notOneQueryPerLink() {
    when(urlFrontierRepository.findExistingHashesByCrawlRunAndHashIn(any(), any()))
        .thenReturn(List.of());

    linkDiscoverer.discover(crawlRunId, corpus, parent, pageWith50Links(), 5);

    // then: batch query called ONCE with all 50 hashes
    verify(urlFrontierRepository, times(1))
        .findExistingHashesByCrawlRunAndHashIn(
            eq(crawlRunId),
            argThat(hashes -> ((Set<?>) hashes).size() == 50));

    // verify: old per-link query never called
    verify(urlFrontierRepository, never())
        .existsByCrawlRun_IdAndUrlHash(any(), anyString());
}

@Test
void discover_excludesAlreadyQueuedLinks() {
    String queuedHash = UrlHasher.hash(
        UrlNormalizer.normalize("https://www.geostat.ge/ka/page/1"));
    when(urlFrontierRepository.findExistingHashesByCrawlRunAndHashIn(any(), any()))
        .thenReturn(List.of(queuedHash));

    List<UrlFrontierEntity> result =
        linkDiscoverer.discover(crawlRunId, corpus, parent, pageWithKnownLink(), 5);

    assertThat(result).noneMatch(f -> f.getUrlHash().equals(queuedHash));
}

@Test
void discover_returnsEmpty_whenMaxDepthReached() {
    when(parent.getDepth()).thenReturn(5);
    assertThat(linkDiscoverer.discover(crawlRunId, corpus, parent, anyPage(), 5))
        .isEmpty();
    verifyNoInteractions(urlFrontierRepository);
}

@Test
void discover_normalizesUrls_beforeHashing() {
    // page contains: https://www.geostat.ge/ka/news?utm_source=google
    // normalized to: https://www.geostat.ge/ka/news
    List<UrlFrontierEntity> result =
        linkDiscoverer.discover(crawlRunId, corpus, parent, pageWithTrackingUrl(), 5);

    assertThat(result)
        .anyMatch(f -> f.getUrl().equals("https://www.geostat.ge/ka/news"))
        .noneMatch(f -> f.getUrl().contains("utm_source"));
}
```

**ფაილები სარედაქციოდ:**
- `apps/ingestion-service/.../crawl/frontier/LinkDiscoverer.java` — batch hash check, URL normalization
- `apps/ingestion-service/.../persistence/repository/UrlFrontierRepository.java` — `findExistingHashesByCrawlRunAndHashIn()`
- V20 migration — `idx_url_frontier_crawl_run_hash` index (if not present from PERF-04/L-1-26)

**Acceptance criteria:**
- [ ] `existsByCrawlRun_IdAndUrlHash()` never called in `discover()`
- [ ] `findExistingHashesByCrawlRunAndHashIn()` called once per page with all candidate hashes
- [ ] Already-queued links correctly excluded from returned list
- [ ] URLs normalized (L-1-26) before hashing — no tracking params in frontier
- [ ] `discover()` returns empty list without any DB call when `parent.depth >= maxDepth`
- [ ] `IN` clause safe for 1–1,000 hashes (typical geostat.ge page: 30–80 links)

---

### ARCH-11 — `EmbeddingPort`: batch method contract + adapter verification 🟠

**Context:**

PERF-07 adds `embedBatch()` to `EmbeddingPort`. This item ensures the contract is documented and both adapters (Gemini + stub/mock) implement it correctly.

**Current state — `EmbeddingPort.java`:**

```java
// libs/embedding-adapters/.../EmbeddingPort.java — CURRENT (1 method):
public interface EmbeddingPort {
    String modelId();
    int dimensions();
    float[] embed(String text);
}
```

**Required state — after PERF-07:**

```java
// AFTER PERF-07:
public interface EmbeddingPort {
    String modelId();
    int dimensions();
    float[] embed(String text);

    /**
     * Batch embed. Default falls back to loop of embed().
     * All production adapters MUST override with real batch.
     *
     * Contract:
     *   - results.length == texts.size()
     *   - results[i] corresponds to texts.get(i)
     *   - each result has length == dimensions()
     *   - texts.size() <= 100 per call (Gemini limit; caller must split)
     */
    default float[][] embedBatch(List<String> texts) {
        float[][] r = new float[texts.size()][];
        for (int i = 0; i < texts.size(); i++) r[i] = embed(texts.get(i));
        return r;
    }
}
```

**Adapters that MUST override `embedBatch()`:**

| Adapter | File | Override required |
|---------|------|-------------------|
| `GeminiEmbeddingAdapter` | `libs/embedding-adapters/.../GeminiEmbeddingAdapter.java` | YES — real HTTP batch call |
| `MockEmbeddingAdapter` (test) | `apps/ingestion-service/src/test/.../MockEmbeddingAdapter.java` | YES — return random/deterministic float[][] |
| `StubEmbeddingAdapter` (dev profile) | wherever it lives | YES — can use default fallback |

**Contract enforcement (ArchUnit test):**

```java
// EmbeddingPortContractTest.java:
@ArchTest
static final ArchRule embeddingPort_allAdapters_implementBatch =
    classes()
        .that().implement(EmbeddingPort.class)
        .and().haveNameNotMatching(".*Stub.*")  // stubs allowed to use default
        .should().haveMethod("embedBatch", List.class);
```

**Acceptance criteria:**
- [ ] `EmbeddingPort.embedBatch()` has clear Javadoc contract (order preservation, max 100 texts)
- [ ] `GeminiEmbeddingAdapter.embedBatch()` overrides with real batch HTTP call
- [ ] `MockEmbeddingAdapter.embedBatch()` returns correct-length float[][] (for unit tests)
- [ ] ArchUnit test enforces that production adapters override `embedBatch()`
- [ ] Default fallback produces identical results to N individual `embed()` calls

---

### ARCH-12 — `QdrantCollectionManager`: HNSW config + quantization + RAM sizing 🟡

**Current state — `QdrantCollectionManager.createCollection()` lines 44–50:**

```java
client.createCollectionAsync(
    collectionName,
    VectorParams.newBuilder()
        .setSize(vectorSize)          // e.g. 768 for text-embedding-004
        .setDistance(Distance.Cosine) // ✅ correct for normalized embeddings
        .build())
    .get();
// MISSING: HNSW config — using Qdrant defaults (m=16, ef_construction=100)
// MISSING: quantization — all vectors stored as float32 (4 bytes/dimension)
// MISSING: payload indexes — filtered search (by language/pageKind) won't use index
```

**Production RAM estimate without changes:**

```
Corpus: 10,000 docs × 15 chunks = 150,000 vectors (conservative)
        At full scale: 225,000 vectors

Float32 vectors: 225,000 × 768 × 4 bytes = 692 MB
Payload (RAM):   225,000 × 600 bytes avg = 135 MB
HNSW graph:      ~50 MB
Total RAM:       ~877 MB → Qdrant server needs 1GB+ just for this collection

With int8 Scalar Quantization:
Quantized vectors:  225,000 × 768 × 1 byte = 173 MB   ← in RAM
Original vectors:   on disk (loaded for re-scoring)
Payload:            135 MB (RAM)
HNSW graph:         50 MB
Total RAM:          ~358 MB — 2.4× reduction
Recall degradation: < 1% at this scale
```

**Resolution:**

ფაილი: `apps/ingestion-service/src/main/java/com/geostat/ingestion/index/qdrant/QdrantCollectionManager.java`

```java
@Component
@Profile("db")
public class QdrantCollectionManager {

    public void ensureCollection(String collectionName, int vectorSize) {
        Integer existingSize = existingVectorSize(collectionName);
        if (existingSize != null) {
            if (existingSize == vectorSize) {
                // Collection exists with correct size — ensure payload indexes
                ensurePayloadIndexes(collectionName);
                return;
            }
            log.warn("Qdrant collection {} size {} != required {} — recreating",
                collectionName, existingSize, vectorSize);
            deleteCollection(collectionName);
        }
        createCollection(collectionName, vectorSize);
        ensurePayloadIndexes(collectionName);
    }

    private void createCollection(String collectionName, int vectorSize) {
        try {
            // HNSW: m=16, ef_construction=128
            //   m=16: default, appropriate for 225K vectors (range: 4–64)
            //   ef_construction=128: slightly above default (100) for better recall
            //   Higher ef_construction = slower indexing but better search quality
            //   For statistical queries: precision matters more than speed
            HnswConfigDiff hnsw = HnswConfigDiff.newBuilder()
                .setM(16)
                .setEfConstruct(128)
                .build();

            // Scalar Quantization: float32 → int8
            //   4× RAM reduction with < 1% recall degradation at 225K vectors
            //   always_ram=true: keep quantized vectors in RAM for fast ANN search
            //   quantile=0.99: clip 1% of outlier values to avoid distortion
            QuantizationConfig quantization = QuantizationConfig.newBuilder()
                .setScalar(ScalarQuantization.newBuilder()
                    .setType(QuantizationType.Int8)
                    .setQuantile(0.99f)
                    .setAlwaysRam(true)
                    .build())
                .build();

            client.createCollectionAsync(
                collectionName,
                VectorParams.newBuilder()
                    .setSize(vectorSize)
                    .setDistance(Distance.Cosine)
                    .setHnswConfig(hnsw)
                    .setQuantizationConfig(quantization)
                    .build())
                .get();

            log.info("[qdrant] created collection {} (dim={}, hnsw m={}, quantization=int8)",
                collectionName, vectorSize, 16);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new QdrantOperationException("interrupted creating collection " + collectionName, e);
        } catch (ExecutionException e) {
            throw new QdrantOperationException("failed creating collection " + collectionName, e.getCause());
        }
    }

    /**
     * Creates payload field indexes for fast filtered search.
     *
     * Without indexes: Qdrant scans all vectors for each filter value.
     * With indexes: O(log n) lookup for keyword fields.
     *
     * Fields indexed:
     *   - language: "ka", "en" — filter by language in retrieval
     *   - pageKind: "statistical_page", "news", "portal" — exclude portal pages
     *   - serveState: "live", "hidden" — exclude hidden documents
     *   - navBreadcrumb: "სტატისტიკა > მოსახლეობა" — topic-path filtering (ARCH-13)
     */
    private void ensurePayloadIndexes(String collectionName) {
        ensurePayloadIndex(collectionName, "language",       PayloadSchemaType.Keyword);
        ensurePayloadIndex(collectionName, "pageKind",       PayloadSchemaType.Keyword);
        ensurePayloadIndex(collectionName, "serveState",     PayloadSchemaType.Keyword);
        ensurePayloadIndex(collectionName, "navBreadcrumb",  PayloadSchemaType.Keyword);
        ensurePayloadIndex(collectionName, "corpusName",     PayloadSchemaType.Keyword);
    }

    private void ensurePayloadIndex(
            String collection, String field, PayloadSchemaType type) {
        try {
            client.createPayloadIndexAsync(
                collection,
                field,
                PayloadIndexParams.newBuilder().setDataType(type).build(),
                null, null,
                Duration.ofSeconds(30)).get();
            log.debug("[qdrant] ensured payload index {}.{}", collection, field);
        } catch (ExecutionException e) {
            // Qdrant returns error if index already exists — safe to ignore
            log.debug("[qdrant] payload index {}.{} may already exist: {}",
                collection, field, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ... existing deleteCollection(), existingVectorSize() unchanged ...
}
```

**`application-custom.yml` — Qdrant RAM sizing documentation:**

```yaml
# apps/ingestion-service/src/main/resources/application-custom.yml

qdrant:
  host: ${QDRANT_HOST:localhost}
  port: ${QDRANT_GRPC_PORT:6334}
  # api-key: ${QDRANT_API_KEY:}  # uncomment for Qdrant Cloud

# Qdrant RAM budget for geostat-portal corpus:
#
# Collection: geostat-portal-chunks
#   Dimensions:    768 (text-embedding-004)
#   Distance:      Cosine
#   Quantization:  int8 scalar (always_ram=true)
#   HNSW:          m=16, ef_construction=128
#
# Estimated vector count (full crawl): ~150,000–225,000 chunks
# RAM usage breakdown:
#   Quantized vectors:  225,000 × 768 × 1 byte  = 173 MB  (RAM, always)
#   Original vectors:   225,000 × 768 × 4 bytes = 692 MB  (disk, loaded for re-scoring)
#   HNSW graph:         ~50 MB                            (RAM)
#   Payload (indexed):  ~135 MB                           (RAM)
#   ─────────────────────────────────────────────────────
#   Total RAM:          ~360 MB
#   Total disk:         ~760 MB
#
# Server recommendation: 1GB RAM for Qdrant (headroom for multiple queries)
```

**Unit tests:**

```java
// QdrantCollectionManagerTest:

@Test
void ensureCollection_creates_withCosineDistance_andQuantization() {
    when(client.getCollectionInfoAsync(anyString()))
        .thenReturn(failedFuture(new RuntimeException("not found")));

    manager.ensureCollection("test-collection", 768);

    verify(client).createCollectionAsync(
        eq("test-collection"),
        argThat(params ->
            params.getDistance() == Distance.Cosine &&
            params.getSize() == 768 &&
            params.hasQuantizationConfig()));
}

@Test
void ensureCollection_createsPayloadIndexes_afterCreation() {
    when(client.getCollectionInfoAsync(anyString()))
        .thenReturn(failedFuture(new RuntimeException("not found")));

    manager.ensureCollection("test-collection", 768);

    // verify payload indexes created for all required fields
    verify(client, atLeastOnce()).createPayloadIndexAsync(
        eq("test-collection"), eq("language"), any(), any(), any(), any());
    verify(client, atLeastOnce()).createPayloadIndexAsync(
        eq("test-collection"), eq("navBreadcrumb"), any(), any(), any(), any());
}

@Test
void ensureCollection_noOp_whenVectorSizeMatches() {
    when(client.getCollectionInfoAsync("existing"))
        .thenReturn(collectionInfoFuture(768));

    manager.ensureCollection("existing", 768);

    verify(client, never()).createCollectionAsync(any(), any());
    verify(client, never()).deleteCollectionAsync(any());
}
```

**ფაილები სარედაქციოდ:**
- `apps/ingestion-service/.../index/qdrant/QdrantCollectionManager.java` — HNSW + quantization + `ensurePayloadIndexes()`
- `apps/ingestion-service/src/main/resources/application-custom.yml` — Qdrant RAM sizing comment

**Acceptance criteria:**
- [ ] New collection created with `Distance.Cosine`, `m=16`, `ef_construction=128`, `int8` quantization
- [ ] `ensurePayloadIndexes()` called after every `createCollection()` and on existing-correct-size collection
- [ ] Payload indexes: `language`, `pageKind`, `serveState`, `navBreadcrumb`, `corpusName`
- [ ] `ensureCollection()` idempotent: no-op if collection exists with correct size
- [ ] Recreates collection if vector size changes (model upgrade path — ARCH-09)
- [ ] `application-custom.yml` documents RAM sizing estimates

---

### ARCH-13 — `QdrantVectorStore.buildPoint()`: missing payload fields + complete schema doc 🟡

**Current state — `QdrantVectorStore.buildPoint()` (lines 104–148) — payload fields:**

| Field | Present | Notes |
|-------|---------|-------|
| `documentId` | ✅ | |
| `corpusId` | ✅ | |
| `corpusName` | ✅ | |
| `chunkId` | ✅ | |
| `sequenceNo` | ✅ | |
| `url` | ✅ | `canonical_url` |
| `text` | ✅ | chunk text |
| `chunkStrategy` | ✅ | |
| `language` | ✅ (conditional) | |
| `pageTitle` | ✅ (conditional) | |
| `pageDescription` | ✅ (conditional) | |
| `sectionPath` | ✅ (conditional) | heading H2/H3 path |
| `fetchedAt` | ✅ (conditional) | |
| `indexVersion` | ✅ (conditional) | |
| `serveState` | ✅ (via enrichmentPayload) | `live`/`hidden` |
| `pageKind` | ✅ (via enrichmentPayload) | |
| `scoreBoost` | ✅ (via enrichmentPayload) | |
| `navBreadcrumb` | ❌ **MISSING** | L-1-17, L-1-24 added this field |
| `publishedAt` | ❌ **MISSING** | L-1-14 added this field |
| `embeddingModel` | ❌ **MISSING** | ARCH-09 added this field |

**Resolution — add 3 missing fields to `buildPoint()`:**

ფაილი: `apps/ingestion-service/src/main/java/com/geostat/ingestion/index/qdrant/QdrantVectorStore.java`

```java
private static PointStruct buildPoint(
        ChunkEntity chunk,
        DocumentEntity document,
        CorpusEntity corpus,
        float[] vector,
        String indexVersion,
        DocumentServeState serveState) {

    List<Float> values = new ArrayList<>(vector.length);
    for (float v : vector) values.add(v);

    Map<String, io.qdrant.client.grpc.JsonWithInt.Value> payload = new HashMap<>();

    // --- Core identifiers ---
    payload.put("documentId",   value(document.getId().toString()));
    payload.put("corpusId",     value(corpus.getId().toString()));
    payload.put("corpusName",   value(corpus.getName()));
    payload.put("chunkId",      value(chunk.getId().toString()));
    payload.put("sequenceNo",   value(chunk.getSequenceNo()));

    // --- Content ---
    payload.put("url",          value(document.getCanonicalUrl()));
    payload.put("text",         value(chunk.getText()));
    payload.put("chunkStrategy",value(chunk.getChunkStrategy() == null ? "" : chunk.getChunkStrategy()));

    // --- Language ---
    if (document.getLanguage() != null) {
        payload.put("language", value(document.getLanguage()));
    }

    // --- Document metadata ---
    if (document.getTitle() != null) {
        payload.put("pageTitle", value(document.getTitle()));
    }
    if (document.getDisplayDescription() != null
            && !document.getDisplayDescription().isBlank()) {
        payload.put("pageDescription", value(document.getDisplayDescription()));
    }
    if (document.getFetchedAt() != null) {
        payload.put("fetchedAt", value(document.getFetchedAt().toString()));
    }

    // --- Navigation ---
    String sectionPath = SectionPathExtractor.joinPath(document.getSectionPath());
    if (!sectionPath.isBlank()) {
        payload.put("sectionPath", value(sectionPath));
    }
    // navBreadcrumb (L-1-17, L-1-24): actual page navigation path, e.g.
    // "სტატისტიკა > მოსახლეობა > ბუნებრივი მოძრაობა"
    // Used for Qdrant filtered search by topic area
    if (chunk.getNavBreadcrumb() != null && !chunk.getNavBreadcrumb().isBlank()) {
        payload.put("navBreadcrumb", value(chunk.getNavBreadcrumb()));
    }

    // --- Temporal ---
    // publishedAt (L-1-14): publication date extracted from JSON-LD / <time> / OpenGraph
    // Enables date-range filtering: "show me news from 2025 Q1"
    if (document.getPublishedAt() != null) {
        payload.put("publishedAt", value(document.getPublishedAt().toString()));
    }

    // --- Index versioning (ARCH-09) ---
    if (indexVersion != null && !indexVersion.isBlank()) {
        payload.put("indexVersion", value(indexVersion));
    }
    // embeddingModel: which Gemini model generated this vector
    // Used by ModelMigrationJob to identify chunks needing re-embedding
    if (chunk.getEmbeddingModel() != null && !chunk.getEmbeddingModel().isBlank()) {
        payload.put("embeddingModel", value(chunk.getEmbeddingModel()));
    }

    // --- Serving state (via enrichmentPayload) ---
    payload.putAll(enrichmentPayload(document, serveState));

    return PointStruct.newBuilder()
        .setId(id(chunk.getId()))
        .setVectors(vectors(values))
        .putAllPayload(payload)
        .build();
}
```

**Complete Qdrant payload schema documentation:**

```yaml
# Qdrant point payload schema — geostat-portal-chunks collection
# All fields below are set by QdrantVectorStore.buildPoint()
#
# Vector: float[] (768 dims, text-embedding-004, Cosine distance)
# Point ID: chunk UUID
#
payload_schema:
  # Core identifiers
  documentId:    string   # UUID — document.id
  corpusId:      string   # UUID — corpus.id
  corpusName:    string   # corpus.name, e.g. "geostat-portal"
  chunkId:       string   # UUID — chunk.id
  sequenceNo:    integer  # chunk position within document (0-based)

  # Content
  url:           string   # document.canonical_url (normalized, no tracking params)
  text:          string   # chunk.text (the actual content to show in chat)
  chunkStrategy: string   # "fixed_size" | "paragraph" | "" — how chunk was created

  # Language
  language:      string   # "ka" | "en" | null — document language code

  # Document metadata (conditional — present when non-null/non-blank)
  pageTitle:       string   # document.title
  pageDescription: string   # document.display_description
  fetchedAt:       string   # ISO-8601 — when page was crawled

  # Navigation (conditional)
  sectionPath:   string   # joined H2/H3 headings, e.g. "Overview > Economy > GDP"
  navBreadcrumb: string   # real nav path, e.g. "სტატისტიკა > მოსახლეობა > ბუნებრივი მოძრაობა"
                          # L-1-17: extracted from <nav>, <breadcrumb>, aria-label
                          # Used for filtered search by topic category

  # Temporal (conditional — present when non-null)
  publishedAt:   string   # ISO-8601 — article publish date from JSON-LD / <time> / OG

  # Index versioning (ARCH-09)
  indexVersion:  string   # e.g. "v2" — incremented on re-index run
  embeddingModel: string  # e.g. "models/text-embedding-004" — which model generated vector

  # Serving state (always present)
  serveState:    string   # "live" | "hidden" | "dropped"
  pageKind:      string   # "statistical_page" | "news" | "portal" | "unknown"
  scoreBoost:    double   # 1.0 default; > 1.0 for high-value pages

# Payload indexes (created by QdrantCollectionManager.ensurePayloadIndexes()):
#   language, pageKind, serveState, navBreadcrumb, corpusName
#   → enables fast filtered ANN search without full vector scan
```

**Unit tests:**

```java
// QdrantVectorStoreTest:

@Test
void buildPoint_includesNavBreadcrumb_whenChunkHasIt() {
    chunk.setNavBreadcrumb("სტატისტიკა > მოსახლეობა");

    PointStruct point = invokePrivateBuildPoint(chunk, document, corpus,
        new float[768], "v1", DocumentServeState.LIVE);

    assertThat(point.getPayloadMap())
        .containsKey("navBreadcrumb");
    assertThat(point.getPayloadMap().get("navBreadcrumb").getStringValue())
        .isEqualTo("სტატისტიკა > მოსახლეობა");
}

@Test
void buildPoint_omitsNavBreadcrumb_whenNull() {
    chunk.setNavBreadcrumb(null);

    PointStruct point = invokePrivateBuildPoint(chunk, document, corpus,
        new float[768], "v1", DocumentServeState.LIVE);

    assertThat(point.getPayloadMap()).doesNotContainKey("navBreadcrumb");
}

@Test
void buildPoint_includesPublishedAt_whenDocumentHasIt() {
    document.setPublishedAt(Instant.parse("2025-03-15T10:00:00Z"));

    PointStruct point = invokePrivateBuildPoint(chunk, document, corpus,
        new float[768], "v1", DocumentServeState.LIVE);

    assertThat(point.getPayloadMap())
        .containsKey("publishedAt");
    assertThat(point.getPayloadMap().get("publishedAt").getStringValue())
        .contains("2025-03-15");
}

@Test
void buildPoint_includesEmbeddingModel_whenChunkHasIt() {
    chunk.setEmbeddingModel("models/text-embedding-004");

    PointStruct point = invokePrivateBuildPoint(chunk, document, corpus,
        new float[768], "v2", DocumentServeState.LIVE);

    assertThat(point.getPayloadMap().get("embeddingModel").getStringValue())
        .isEqualTo("models/text-embedding-004");
    assertThat(point.getPayloadMap().get("indexVersion").getStringValue())
        .isEqualTo("v2");
}

@Test
void buildPoint_alwaysHasRequiredFields() {
    PointStruct point = invokePrivateBuildPoint(chunk, document, corpus,
        new float[768], "v1", DocumentServeState.LIVE);

    // these fields must ALWAYS be present
    assertThat(point.getPayloadMap()).containsKeys(
        "documentId", "corpusId", "corpusName",
        "chunkId", "sequenceNo", "url", "text",
        "chunkStrategy", "serveState", "pageKind", "scoreBoost");
}
```

**ფაილები სარედაქციოდ:**
- `apps/ingestion-service/.../index/qdrant/QdrantVectorStore.java` — `buildPoint()`: add `navBreadcrumb`, `publishedAt`, `embeddingModel`

**Acceptance criteria:**
- [ ] `navBreadcrumb` in Qdrant payload when `chunk.navBreadcrumb` is non-null/non-blank
- [ ] `publishedAt` in Qdrant payload when `document.publishedAt` is non-null
- [ ] `embeddingModel` in Qdrant payload when `chunk.embeddingModel` is non-null/non-blank
- [ ] All existing payload fields preserved (no regression)
- [ ] YAML schema doc committed alongside the code
- [ ] Chat retrieval filter `matchKeyword("navBreadcrumb", "...")` returns correct chunks

---
