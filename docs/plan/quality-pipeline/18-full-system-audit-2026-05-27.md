# Full System Audit — 2026-05-27

**Scope:** All four layers — fetch/crawl, parse/query/retrieval, DB/config/YAML, tests/CI.  
**Method:** Four parallel explore agents, each reading source files in full.  
**Format:** Composer-ready directive. Each issue has an ID, exact file + line, severity, and a concrete fix.

---

## CRITICAL — fix before any production deployment

### C-01 · Main crawl never uses `renderMode` — always crawler4j regardless of corpus

| | |
|---|---|
| **Files** | `CrawlRunStore.java` L67–68, 179–184; `Crawler4jPageFetcher.java` L90+; `RoutingPageFetcher.java` L29–39 |
| **Impact** | `agriculture-ge` (SPA, `renderMode: headless`) gets `<div id="root"/>` shell on every frontier fetch — zero indexable content. |
| **Fix** | In `CrawlRunStore.fetchHtml`, resolve `ParseProfile` for the corpus name, build `com.geostat.platform.crawl.FetchOptions` via `FetchOptions.forProfile(profile)`, and delegate to the injected `RoutingPageFetcher`. Remove the hard `Crawler4jPageFetcher` dependency for the main loop. Gate headless on `playwright.enabled=true` — throw at job start if corpus needs headless and Playwright is off. |

### C-02 · `agriculture-ge-policy.yaml` `includePatterns` silently rejects all links

| | |
|---|---|
| **Files** | `ops/config/corpus/agriculture-ge-policy.yaml` L27–28; `PolicyUrlFilter.java` L42–50 |
| **Impact** | `PolicyUrlFilter` runs `fullPath.contains(pattern)` where `fullPath` is **path+query only** (no scheme/host). The pattern `^https://agriculture\\.geostat\\.ge/` never matches. With a non-empty include list, every discovered URL is rejected — crawl stalls after seeds. |
| **Fix** | Replace with path-only patterns matching V33 DB policy: `"/"`, `"/vegetation"`, `"/animal-husbandry"`, `"/aquaculture"`, `"/food-balance"`, `"/main-info"`. Or teach `PolicyUrlFilter` to apply regex against the full URL when the pattern starts with `^https`. |

### C-03 · `query_intent_cache` table was dropped; backend adapter still writes/reads it

| | |
|---|---|
| **Files** | `V17__document_link.sql` L35 (`DROP TABLE ingestion.query_intent_cache`); `JdbcQueryIntentCacheStore.java` L48–52, 72–78; `apps/backend/application-custom.yml` L86 |
| **Impact** | `geostat.chat.query.intent-cache-enabled=true` (off by default) causes SQL errors on every cache read/write. Intent cache is permanently broken until the table is recreated. |
| **Fix** | Add a backend Flyway migration (`apps/backend/src/main/resources/db/migration/V{N}__recreate_query_intent_cache.sql`) that creates `chat.query_intent_cache` with the schema `JdbcQueryIntentCacheStore` expects, or move the DDL from V15/V17 into backend migrations. |

### C-04 · Intent cache corrupts `STATISTICAL` → `FACTUAL` on round-trip

| | |
|---|---|
| **Files** | `JdbcQueryIntentCacheStore.java` L89–106 |
| **Impact** | `put()` persists `STATISTICAL` as `"factual"`. `get()` maps `"factual"` → `QueryIntentKind.FACTUAL`. After a cache hit, statistical queries are misrouted as conceptual. |
| **Fix** | Store and restore the same enum name: use `kind.name().toLowerCase()` for persistence and `QueryIntentKind.valueOf(stored.toUpperCase())` for retrieval. Remove the `STATISTICAL`→`"factual"` alias. Add a round-trip test for every `QueryIntentKind`. |

### C-05 · `fetchMode` in DB seed migrations is dead metadata — operators misled

| | |
|---|---|
| **Files** | `V2__seed_geostat_portal_corpus.sql` L14; `V33__seed_agriculture_ge_corpus.sql` L38; `CorpusPolicy.java` |
| **Impact** | `"fetchMode": "playwright"` in V33 leads operators to believe DB policy activates Playwright — it does not. No code reads `fetchMode` from the policy JSON. |
| **Fix** | Option A (preferred): remove `fetchMode` from both seed SQLs and add comment pointing to `{corpus}-parse.yaml` `renderMode`. Option B: wire `fetchMode` → `RenderMode` in `CrawlJobService` startup (align naming: DB uses `fetchMode`, YAML uses `renderMode`). |

### C-06 · CI never runs Java tests — all 119+ tests are local-only

| | |
|---|---|
| **Files** | `.github/workflows/ci.yml` L201 (`./gradlew build -x test`) |
| **Impact** | ArchUnit rules, regression tests, migration tests, and behavior tests never gate merges. Any introduced breakage is invisible in CI. |
| **Fix** | Add a `test` matrix job to `ci.yml` running `./gradlew test` for each module: `apps/backend`, `apps/ingestion-service`, `apps/retrieval-service`, `libs/platform-contracts`, `libs/embedding-adapters`, `libs/qdrant-client`. Use Java 21 toolchain. Fail on test failure. |

---

## HIGH — fix before corpus goes live or before next sprint

### H-01 · Platform `PageFetcher` port not on production crawl path (dead adapters)

| | |
|---|---|
| **Files** | `RoutingPageFetcher.java`; `Crawler4jStaticPageFetcher.java`; `HeadlessBrowserPageFetcher.java` |
| **Fix** | Resolved by C-01: wire `RoutingPageFetcher` into `CrawlRunStore`. Also wire into `DocumentFreshnessRefreshService` and `CorpusReparseWorker` to replace direct `Crawler4jPageFetcher` injection in freshness/reparse paths. |

### H-02 · Freshness refresh and reparse always use static HTTP fetcher

| | |
|---|---|
| **Files** | `DocumentFreshnessRefreshService.java` L36, 95; `CorpusReparseWorker.java` L39, 150–151 |
| **Fix** | Both classes inject `Crawler4jPageFetcher` directly. Replace with `RoutingPageFetcher` (platform port) and build `FetchOptions.forProfile(parseProfile)` per corpus using `CorpusConfigurationLoader.parseProfileFor(corpusName)`. |

### H-03 · `PlaywrightPageFetcher` hardcodes `200` and request URL — misses redirects

| | |
|---|---|
| **Files** | `PlaywrightPageFetcher.java` L68–72 |
| **Fix** | After `page.navigate()`, capture `page.url()` as `finalUrl` and use `page.mainFrame().response().status()` for `statusCode`. Build `new FetchedPage(requestedUrl, page.url(), statusCode, document, ...)` using the full constructor. |

### H-04 · `HeadlessBrowserPageFetcher` NPE on null `Document`

| | |
|---|---|
| **Files** | `HeadlessBrowserPageFetcher.java` L46–48 |
| **Fix** | Mirror `Crawler4jStaticPageFetcher` null guard: `internal.html() == null ? "" : internal.html().outerHtml()`. |

### H-05 · Conditional GET skips robots.txt check

| | |
|---|---|
| **Files** | `Crawler4jPageFetcher.java` L94–118 |
| **Fix** | Run `robotsServer().allows(webUrl)` before the conditional HTTP branch, not only on the full-fetch branch. |

### H-06 · `ConditionalHttpFetcher` ignores `NetworkPolicy` — no timeout, no TLS control

| | |
|---|---|
| **Files** | `ConditionalHttpFetcher.java` L20–33; `Crawler4jStaticPageFetcher.java` L36 |
| **Fix** | Accept `NetworkPolicy` + timeout in `fetch()`. Build `HttpClient` with `connectTimeout`, optional `SSLContext` (when `tlsVerify=false`), `Authenticator` (when `basicAuth` set). `Crawler4jStaticPageFetcher` should pass `options.network()` and `options.timeoutMs()` instead of `FetchOptions.none()`. |

### H-07 · `DefaultCrawlOrchestrator.executeAll` does not wait for crawl completion

| | |
|---|---|
| **Files** | `DefaultCrawlOrchestrator.java` L87–89, 100–106; `CrawlJobService.java` L106–116 |
| **Fix** | `startJob()` returns a `runId`. Poll `CrawlJobService.getJobStatus(runId)` until terminal state, or replace with `CompletableFuture` that completes on job terminal event. The current `future.get(timeout)` only waits for the enqueue call, not crawl completion. |

### H-08 · `DefaultCrawlOrchestrator` never called from scheduler/API

| | |
|---|---|
| **Files** | `DefaultCrawlOrchestrator.java`; `CorpusCrawlScheduler.java` L112–113; `IngestionController.java` |
| **Fix** | Wire `discoverJobs()` + `executeAll()` into either `CorpusCrawlScheduler` (replace per-corpus loop) or add an admin API endpoint `POST /admin/crawl/all`. Without this, multi-corpus YAML discovery provides no runtime value. |

### H-09 · Topic detection uses raw query text, not spell-fixed/expanded text

| | |
|---|---|
| **Files** | `ChatService.java` L260–271; `ChatPipelineCoordinator.java` L72–73 |
| **Fix** | Pass `analyzed.normalized()` or `analyzed.retrievalText()` to `TopicDetector.detect()` when `QueryUnderstandingPipeline` is enabled. Align topic detection input with the text used for RAG retrieval. |

### H-10 · `GeminiIntentClassifier` registered as extra `IntentClassifier` bean — ambiguous wiring

| | |
|---|---|
| **Files** | `GeminiIntentClassifier.java` L17–18 |
| **Fix** | Remove `implements IntentClassifier` from `GeminiIntentClassifier`. Keep it as an internal delegate called only inside `RoutingIntentClassifier`. It should not be a candidate for `@Autowired IntentClassifier`. |

### H-11 · No backend ArchUnit — chat application layer imports infrastructure directly

| | |
|---|---|
| **Files** | `SmallTalkHandler.java` L4–7; `PromptBuilder.java` L8; `RetrievalContextService.java` L3; `apps/backend/build.gradle.kts` |
| **Fix** | Add `testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")` to backend. Add `ArchitectureBoundaryTest` mirroring ingestion patterns. Rule: no class in `..application..` may access `..infrastructure..` concrete types directly. |

### H-12 · Missing `CorpusConfigurationLoaderTest` — agriculture-ge YAML loading untested

| | |
|---|---|
| **Files** | `CorpusConfigurationLoader.java`; `ops/config/corpus/agriculture-ge-*.yaml` |
| **Fix** | Add `CorpusConfigurationLoaderTest`: point `ParseProperties` at `ops/config/corpus`, assert `loadAllPolicies()` returns both corpora, assert `parseProfileFor("agriculture-ge").renderMode() == RenderMode.HEADLESS`, assert key selectors and `pageKindRules` load correctly. |

---

## MEDIUM — important quality improvements, no immediate production breakage

### M-01 · Duplicate `ConditionalHttpFetcher` instances — two `HttpClient` pools

| | |
|---|---|
| **Files** | `Crawler4jPageFetcher.java` L54; `Crawler4jStaticPageFetcher.java` L27 |
| **Fix** | Extract to a single `@Component ConditionalHttpFetcher` bean injected into both callers. |

### M-02 · `ChunkHasher` / `content_hash` not populated on chunk write

| | |
|---|---|
| **Files** | `DocumentChunkWriter.java` L119–143; `ChunkHasher.java`; `V18__chunk_content_hash_document_quality.sql` |
| **Fix** | Call `ChunkHasher.hash(text)` in `DocumentChunkWriter.buildChunk()` before `saveAll`. The dedup index on `content_hash` is currently ineffective for new writes. |

### M-03 · `ChunkEntity` missing `embedding_status` and `content_hash` JPA fields

| | |
|---|---|
| **Files** | `ChunkEntity.java`; `V18`, `V23`, `V25` migrations |
| **Fix** | Add `@Column(name = "embedding_status")` and `@Column(name = "content_hash")` to `ChunkEntity`. Prevents future JPA use from silently omitting or corrupting these fields. |

### M-04 · `HtmlContentCleaner` legacy path still active with profile disabled (default in code)

| | |
|---|---|
| **Files** | `ParseProperties.java` L9–10; `HtmlContentCleaner.java` L74–96 |
| **Fix** | Code record default for `profile.enabled` is `false`. Production sets `INGESTION_PARSE_PROFILE_ENABLED:true`. Align the code default to `true`, or add a startup assertion that fails when corpus crawl runs with profile disabled. |

### M-05 · `JsoupContentExtractor` coupled to concrete `YamlConfiguredStrategy`

| | |
|---|---|
| **Files** | `JsoupContentExtractor.java` L9, 70–72 |
| **Fix** | Add `boolean isDefaultFallback()` to `ExtractionStrategy` interface. `YamlConfiguredStrategy` returns `true`; all others return `false`. Replace `instanceof YamlConfiguredStrategy` with `strategy.isDefaultFallback()`. |

### M-06 · `HybridRetrieverPort` implemented but not wired into product retrieval path

| | |
|---|---|
| **Files** | `HybridRetriever.java` L24–29; `RetrievalController.java` L16–24; `QdrantRetrievalService.java` |
| **Fix** | When `geostat.retrieval.hybrid.enabled=true`, make `HybridRetriever` the `@Primary` `RetrievalPort`. Or expose via a separate endpoint and call from `RetrievalContextService`. Resolve the dead port or remove it. |

### M-07 · `geostat-portal-parse.yaml` on classpath duplicates `ops/config/corpus/geostat-portal-parse.yaml`

| | |
|---|---|
| **Files** | `src/main/resources/parse-profiles/geostat-portal-parse.yaml`; `ops/config/corpus/geostat-portal-parse.yaml` |
| **Fix** | Single source of truth: move to `ops/config/corpus` only, delete the classpath copy. `CorpusConfigurationLoader` already falls back to classpath for legacy support — the ops copy takes priority and should be canonical. |

### M-08 · `YamlCatalogResponseAssembler` drops entity/expansion signals

| | |
|---|---|
| **Files** | `CatalogResponseAssembler.java` L14–19; `YamlCatalogResponseAssembler.java` |
| **Fix** | Override `assemble(AnalyzedQuery analyzed)` in `YamlCatalogResponseAssembler` to use `analyzed.retrievalText()` instead of `analyzed.normalized()`. |

### M-09 · Duplicate HTTP cache columns (`http_etag` + `etag_http`) on `document`

| | |
|---|---|
| **Files** | `V5__document_freshness_headers.sql`; `V24__document_http_cache_fields.sql`; `DocumentEntity.java` L81–91; `CrawlRunStore.java` L507–509 |
| **Fix** | Add a migration to drop the V5 `http_etag` column (or mark deprecated in entity). Update all reads/writes to use only `etag_http`. |

### M-10 · `.env.example` missing all ingestion environment variables

| | |
|---|---|
| **Files** | `ops/config/.env.example` |
| **Fix** | Add all `INGESTION_*`, `CRAWL_*`, `INGESTION_PLAYWRIGHT_ENABLED`, `INGESTION_PARSE_CONFIG_DIR`, `QDRANT_*`, `EMBEDDING_*` placeholders with descriptions. |

### M-11 · `FetchOptions.forProfile()` factory never called in production

| | |
|---|---|
| **Files** | `FetchOptions.java` L24–36 |
| **Fix** | Resolved by C-01/H-01 wiring. Add explicit call sites in `CrawlRunStore`, `DocumentFreshnessRefreshService`, `CorpusReparseWorker`. |

### M-12 · Missing `RoutingPageFetcherTest` and `Crawler4jStaticPageFetcherTest`

| | |
|---|---|
| **Fix** | `RoutingPageFetcherTest`: mock optional fetchers, verify routing by `RenderMode`, verify `orElseThrow` for missing fetcher. `Crawler4jStaticPageFetcherTest`: mock/wiremock `ConditionalHttpFetcher`, verify 304 handling, IOException → `PageFetchException`, user-agent forwarding. |

### M-13 · `FlywayMigrationIntegrationTest` does not assert `agriculture-ge` corpus seed

| | |
|---|---|
| **Files** | `FlywayMigrationIntegrationTest.java` L38–41 |
| **Fix** | After migrate, query `ingestion.corpus` for both `geostat-portal` and `agriculture-ge` names. Assert `renderMode`/`fetchMode` and `status = 'active'`. |

### M-14 · Env variable naming inconsistency across services

| | |
|---|---|
| **Files** | `backend/application-custom.yml` L69 (`RETRIEVAL_DEFAULT_CORPUS`); `retrieval-service/application-custom.yml` L4 (`RETRIEVAL_DEFAULT_COLLECTION`) |
| **Fix** | Align on `RETRIEVAL_DEFAULT_CORPUS` across both services. Document the mapping in `ops/config/.env.example`. |

### M-15 · `DefaultCrawlOrchestrator` passes only `corpusName` — `CrawlJob.profile` unused

| | |
|---|---|
| **Files** | `DefaultCrawlOrchestrator.java` L61–63, 100–105; `CrawlJobService.java` L62–88 |
| **Fix** | Pass `renderMode` from `CrawlJob.profile()` into `IngestionJobRequest` or resolve profile inside `CrawlRunner` via `CorpusConfigurationLoader`. This closes the gap where orchestrator loads profile but crawl ignores it. |

### M-16 · Dual policy sources (YAML `CorpusPolicyV2` vs DB `CorpusPolicy`) not kept in sync

| | |
|---|---|
| **Files** | `CorpusPolicySyncService.java` L71–97; `geostat-portal-policy.yaml` L20–23 |
| **Fix** | Extend `CorpusPolicySyncService.sync()` to write crawl limits (`workerThreads`, `crawlDelay`, `rateLimitMs`) from YAML into DB policy JSON, or stop reading those from DB and always read from YAML at crawl startup. Single source of truth — YAML is canonical. |

### M-17 · Ingestion ArchUnit has only 3 narrow rules — missing package-level boundaries

| | |
|---|---|
| **Files** | `ArchitectureBoundaryTest.java` L16–59 |
| **Fix** | Add general package boundary rules: `parse` must not access `crawl.frontier`; `enrichment` must not import fetch infrastructure; `crawl` must not import `parse.quality`. |

---

## LOW

### L-01 · Dead code: `PageNotModifiedException`

**File:** `PageNotModifiedException.java` — no callers. Delete; 304 is handled via `FetchedPage.notModified()`.

### L-02 · Dead config: `IdentitySpellFixer` `@Bean` never injected

**File:** `QueryUnderstandingConfiguration.java` L58–61. Remove the bean method; `RoutingSpellFixer` is `@Primary`.

### L-03 · `RoutingPageFetcher.API` mode throws without implementation

**File:** `RoutingPageFetcher.java` L38. Remove `API` from `RenderMode` enum if not planned, or add a comment `// planned: v2`.

### L-04 · `PlaywrightRefetchService` ignores corpus network policy (DNS overrides) on P3-03b path

**File:** `PlaywrightRefetchService.java` L99. Load corpus parse profile and call `fetchPage(url, FetchOptions.forProfile(profile))` instead of `fetch(url)` to honour DNS overrides.

### L-05 · Embedding vector size default mismatch

**File:** `application-custom.yml` L114 (`EMBEDDING_VECTOR_SIZE:384`) vs L95–98 comment (768-dim). Align default with chosen production provider or add startup dimension-check.

### L-06 · `agriculture-ge` missing terminology/topic-catalog/eval seeds

No `agriculture-ge-terminology.yaml`, no topic catalog, no `evaluation_query` rows. Add when corpus goes live.

### L-07 · `geostat-portal-parse.yaml` classpath copy has no `renderMode: static` explicit field

Minor clarity gap. Add `renderMode: static` for self-documentation.

### L-08 · Backend `ChatService` holds unused `QueryRouter` when pipeline enabled

**File:** `ChatService.java` L54, 98, 266–267. Not broken but dead field at runtime. Inject as `@Lazy` or guard with `@ConditionalOnProperty`.

### L-09 · Docs drift: quality-pipeline plan docs reference deleted classes

`docs/plan/quality-pipeline/03-layers-0-to-5-execution.md` mentions `YamlTopicCatalog`. Update docs to match current code.

---

## Fix Priority Order

```
Priority 1 — before agriculture-ge crawl works at all
  C-01  Wire RoutingPageFetcher into CrawlRunStore (renderMode routing)
  C-02  Fix agriculture-ge-policy.yaml includePatterns (path-only)
  H-02  Fix freshness/reparse to use RoutingPageFetcher
  H-03  Fix PlaywrightPageFetcher redirect URL + status
  H-04  Fix HeadlessBrowserPageFetcher NPE on null Document

Priority 2 — before enabling intent cache / query understanding
  C-03  Recreate query_intent_cache table
  C-04  Fix STATISTICAL→FACTUAL corruption in cache round-trip

Priority 3 — correctness and data quality
  C-05  Remove dead fetchMode from DB seeds
  H-05  Fix robots.txt on conditional GET path
  H-06  Add NetworkPolicy support to ConditionalHttpFetcher
  M-02  Populate content_hash in DocumentChunkWriter
  M-03  Add missing JPA fields to ChunkEntity
  H-09  Align TopicDetector to use analyzed.retrievalText()

Priority 4 — orchestration and multi-corpus
  H-07  Fix DefaultCrawlOrchestrator completion wait
  H-08  Wire DefaultCrawlOrchestrator into scheduler
  M-15  Pass renderMode through IngestionJobRequest
  M-16  Sync YAML policy into DB on startup

Priority 5 — CI and test coverage
  C-06  Add gradlew test jobs to ci.yml
  H-11  Add backend ArchUnit
  H-12  Add CorpusConfigurationLoaderTest
  M-12  Add RoutingPageFetcherTest + Crawler4jStaticPageFetcherTest
  M-13  Extend FlywayMigrationIntegrationTest
  M-17  Extend ingestion ArchUnit rules

Priority 6 — clean-up
  M-01  Deduplicate ConditionalHttpFetcher instances
  M-07  Single-source geostat-portal-parse.yaml
  M-09  Drop duplicate http_etag column
  M-10  Extend .env.example
  L-01  Delete PageNotModifiedException
  L-02  Delete dead IdentitySpellFixer bean
```

---

## Statistics

| Severity | Count |
|----------|-------|
| CRITICAL | 6 |
| HIGH | 12 |
| MEDIUM | 17 |
| LOW | 9 |
| **Total** | **44** |

> Most critical single issue: **C-01** — without `RoutingPageFetcher` on the main crawl path, the entire headless-corpus architecture (agriculture-ge, and any future SPA corpus) produces only empty HTML shells. Everything else from ARCH-04 through ARCH-08 is structurally complete but disconnected from actual fetch execution.
