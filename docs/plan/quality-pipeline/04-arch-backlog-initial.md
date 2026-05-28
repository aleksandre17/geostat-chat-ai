## Execution Order (bottom-up, strict)

```
Phase 1 — Parser + policy fix (ingestion-service კოდი)
  L-1-01  DefaultParseProfile + HtmlContentCleaner: aside/.related/.sidebar remove
  L-1-03  MarkerBoilerplateStripper + DefaultParseProfile: download/date patterns
  L-1-04  CorpusPolicy + LinkDiscoverer: ?page=N filter + RoutingUrlFilter sync

Phase 2 — Data fill (REST calls to running ingestion-service)
  L-1-02a POST /corpus/geostat-portal/enrichment:backfill {"onlyMissing":false,"limit":5000}
  L-1-02b POST /corpus/geostat-portal/authority:recompute
  L-1-05  POST /corpus/geostat-portal/topics:remine  + admin approve
  MV REFRESH (სამივე)

Phase 3 — DB indexes (migration)
  L0-01 + L0-02  → V17 migration in ingestion-service (keyword GIN + target index)

Phase 4 — Chat-API catalog wiring
  L3-01   → switch default to catalog.source=derived
  L3-03   → DerivedMinimalTopicCatalog: load rules from topic_cluster DB rows

Phase 5 — Enable retrieval + confidence routing
  L2-01   → application-custom.yml: RETRIEVAL_ENABLED=true (eval baseline first)
  L2-02   → wire ResponseRouter + RetrievalConfidenceAssessor into ChatService
  L2-03   → calibrate DefaultConfidenceAssessor thresholds

Phase 6 — Query Understanding (U07)
  L4-01   → GEOSTAT_CHAT_QUERY_UNDERSTANDING_ENABLED=true
  L4-02   → QueryUnderstandingPipeline: pass spellFixed to classifier
  L4-03   → deprecate QueryRouter (@Deprecated)

Phase 7 — Prompt cleanup
  L5-01   → remove domain hardcode from chat-prompts.yaml
  L5-02   → remove intent taxonomy from main prompt

Phase 8 — Feedback loop + remaining
  L-1-06  → enrichment_version increment in DocumentEnrichmentOrchestrator
  L0-03   → CachingIntentClassifier @Primary + JdbcQueryIntentCacheStore
  L0-04   → CatalogMvFreshnessChecker
  L4-04   → Georgian spell-fix (YamlQueryTypoCorrector)
  L4-05   → entity enrichment from document.entities JSONB
  L1-01   → score_boost investigation in retrieval-service
  L5-03   → AiResponseParser fallback through ResponseRouter
  L-1-07  → feedback → authority wiring (owner approval needed first)
```

---

## Open Questions — განახლებული (investigate before implementing)

| # | კითხვა | სად | Status |
|---|--------|-----|--------|
| OQ-1 | `score_boost` — retrieval-service-ი ხომ არ იყენებს Qdrant score-ის modulation-ისთვის? | `apps/retrieval-service` Qdrant query builder | ❓ |
| OQ-2 | `pg_trgm` — target Postgres-ზე extension ხელმისაწვდომია? `SELECT * FROM pg_extension WHERE extname='pg_trgm'` | DB | ❓ |
| OQ-3 | Re-parse trigger — `CorpusReparseWorker` endpoint-ი არსებობს? L-1-01 fix-ის შემდეგ affected documents-ი re-parse-ისთვის | `apps/ingestion-service/CorpusController` | ❓ |
| OQ-4 | Qdrant re-index after chunk delete — pagination docs-ის წაშლის შემდეგ Qdrant-ი auto-sync ხდება? ვინ trigger-ს აკეთებს? | `DocumentChunkWriter`, `VectorIndexSync` | ❓ |
| OQ-5 | eval baseline — `ops/eval/run-eval.py` გამართულია Phase 2 changes-ის წინ გასაშვებად? | `ops/eval/` | ❓ |
| OQ-6 | L-1-07 feedback loop — cross-service DB access ან RabbitMQ event — owner-ი approve-ავს? | Architecture decision | ❓ |

---

## Architecture Evolution — Backlog (implement after Phase 1–7, owner approval required)

ეს სამი item ახლა **blocking არ არის**, მაგრამ სისტემის გრძელვადიანი ზრდისა და SPA-ready მომავლისთვის **სავალდებულოა**. ჯუნიორმა Phase 1–7 შემდეგ ეს სექცია წაიკითხოს და owner-ს შეუთანხმოს.

---

### ARCH-01 — `PageFetcher` port: crawl fetch layer-ი abstraction-ის გარეშეა 🔴 (SPA risk)

**პრობლემა — root cause:**
`crawler4j`-ს `PageFetcher` concrete impl პირდაპირ inject-ია ingestion-service-ში. `ContentExtractor` port (`libs/platform-contracts`) სწორ ადგილზეა, მაგრამ **fetch layer-ს port არ აქვს**. შედეგი:

```
ახლა:
  Crawler4jPageFetcher (concrete) → HTTP GET → static HTML → Jsoup

SPA-ზე გადასვლის შემდეგ (React/Vue client-only):
  Crawler4jPageFetcher → HTTP GET → "<div id='root'></div>" → Jsoup → ""
  ↑ მთელი corpus ცარიელი. არანაირი warning. silent failure.
```

**რატომ არის ეს critical:**
`ContentExtractor` interface სწორია — HTML-ს იღებს და content-ს ამოიღებს. მაგრამ **HTML-ის მოწოდება** (fetch) hardcoded-ია crawler4j-ზე. SPA, headless, API — ყველა ეს option-ი ახალ concrete impl-ს მოითხოვს ბირთვის შეცვლის გარეშე — **თუ port არსებობს**.

**Resolution steps:**

**ნაბიჯი 1 — `PageFetcher` port `libs/platform-contracts`-ში:**

შექმენი:
`libs/platform-contracts/src/main/java/com/geostat/platform/crawl/PageFetcher.java`

```java
package com.geostat.platform.crawl;

/**
 * Port: fetches a web page and returns its raw content for parsing.
 * Implementations may use HTTP, headless browser, or API depending on corpus render mode.
 */
public interface PageFetcher {

    /**
     * @param url         canonical URL to fetch
     * @param options     fetch configuration (timeout, user-agent, render mode)
     * @return            fetched page content; never null
     * @throws PageFetchException on unrecoverable fetch failure
     */
    FetchedPage fetch(String url, FetchOptions options) throws PageFetchException;
}
```

`libs/platform-contracts`-ში ასევე შექმენი:

```java
// FetchedPage.java
package com.geostat.platform.crawl;

public record FetchedPage(
    String url,
    String html,          // rendered HTML (from HTTP or headless)
    int    httpStatus,
    String contentType,
    RenderMode renderMode // which strategy was used
) {}

// FetchOptions.java
public record FetchOptions(
    RenderMode renderMode,  // STATIC | HEADLESS | API
    int        timeoutMs,
    String     userAgent
) {
    public static FetchOptions defaults() {
        return new FetchOptions(RenderMode.STATIC, 10_000, "GeostatBot/1.0");
    }
}

// RenderMode.java
public enum RenderMode { STATIC, HEADLESS, API }

// PageFetchException.java
public class PageFetchException extends RuntimeException {
    public PageFetchException(String url, String reason, Throwable cause) {
        super("Failed to fetch [" + url + "]: " + reason, cause);
    }
}
```

**ნაბიჯი 2 — `Crawler4jStaticPageFetcher` (existing logic → adapter):**

შექმენი:
`apps/ingestion-service/src/main/java/com/geostat/ingestion/crawl/fetch/Crawler4jStaticPageFetcher.java`

```java
@Component
@ConditionalOnProperty(name = "geostat.ingestion.crawl.fetch-mode", havingValue = "static", matchIfMissing = true)
public class Crawler4jStaticPageFetcher implements PageFetcher {

    private final Crawler4jFetchInfrastructure crawler4j; // existing bean

    @Override
    public FetchedPage fetch(String url, FetchOptions options) throws PageFetchException {
        // wrap existing crawler4j HTTP fetch logic here
        // extract from wherever it currently lives inline
    }
}
```

**ნაბიჯი 3 — `HeadlessBrowserPageFetcher` (SPA support — stub now, real later):**

შექმენი:
`apps/ingestion-service/src/main/java/com/geostat/ingestion/crawl/fetch/HeadlessBrowserPageFetcher.java`

```java
@Component
@ConditionalOnProperty(name = "geostat.ingestion.crawl.fetch-mode", havingValue = "headless")
public class HeadlessBrowserPageFetcher implements PageFetcher {

    // Playwright Java dependency (when approved):
    // implementation 'com.microsoft.playwright:playwright:1.x.x'

    @Override
    public FetchedPage fetch(String url, FetchOptions options) throws PageFetchException {
        // try (Playwright playwright = Playwright.create()) {
        //     Browser browser = playwright.chromium().launch(
        //         new BrowserType.LaunchOptions().setHeadless(true));
        //     Page page = browser.newPage();
        //     page.navigate(url, new Page.NavigateOptions()
        //         .setTimeout(options.timeoutMs()));
        //     page.waitForLoadState(LoadState.NETWORKIDLE);
        //     String html = page.content();  // fully rendered DOM
        //     return new FetchedPage(url, html, 200, "text/html", RenderMode.HEADLESS);
        // }
        throw new UnsupportedOperationException(
            "HeadlessBrowserPageFetcher not yet wired — add Playwright dependency and implement");
    }
}
```

**ნაბიჯი 4 — `RoutingPageFetcher` (primary bean):**

შექმენი:
`apps/ingestion-service/src/main/java/com/geostat/ingestion/crawl/fetch/RoutingPageFetcher.java`

```java
@Primary
@Component
public class RoutingPageFetcher implements PageFetcher {

    private final Crawler4jStaticPageFetcher staticFetcher;
    private final Optional<HeadlessBrowserPageFetcher> headlessFetcher;
    private final CorpusConfigurationLoader configLoader;

    @Override
    public FetchedPage fetch(String url, FetchOptions options) throws PageFetchException {
        return switch (options.renderMode()) {
            case STATIC   -> staticFetcher.fetch(url, options);
            case HEADLESS -> headlessFetcher
                .orElseThrow(() -> new PageFetchException(url, "headless not configured", null))
                .fetch(url, options);
            case API      -> throw new PageFetchException(url, "API mode not yet implemented", null);
        };
    }
}
```

**ნაბიჯი 5 — `ParseProfile` / policy YAML-ში `renderMode` field:**

`libs/platform-contracts/src/main/java/com/geostat/platform/parse/ParseProfile.java` record-ში დაამატე:
```java
public record ParseProfile(
    // ... existing fields ...
    RenderMode renderMode  // STATIC (default) | HEADLESS | API
) {
    public ParseProfile {
        // ...
        if (renderMode == null) renderMode = RenderMode.STATIC;
    }
}
```

`geostat-portal-parse.yaml`-ში:
```yaml
renderMode: static   # ახლა. SPA-ზე გადასვლისას: headless
```

**ნაბიჯი 6 — `CorpusConfigurationLoader.ParseProfileYaml`-ში field:**
```java
public String renderMode; // "static" | "headless" | "api"

ParseProfile toModel(String fallbackCorpus) {
    RenderMode mode = renderMode == null ? RenderMode.STATIC
        : RenderMode.valueOf(renderMode.toUpperCase());
    return new ParseProfile(..., mode);
}
```

**ნაბიჯი 7 — unit tests:**

```java
// RoutingPageFetcherTest:
@Test
void routesToStaticFetcher_forStaticMode() {
    FetchOptions opts = new FetchOptions(RenderMode.STATIC, 5000, "bot");
    router.fetch("https://example.com", opts);
    verify(staticFetcher).fetch(any(), any());
    verifyNoInteractions(headlessFetcher);
}

@Test
void routesToHeadless_forHeadlessMode() {
    FetchOptions opts = new FetchOptions(RenderMode.HEADLESS, 10000, "bot");
    router.fetch("https://example.com", opts);
    verify(headlessFetcher).fetch(any(), any());
}
```

**ფაილები:**
- New: `libs/platform-contracts/.../crawl/PageFetcher.java` + `FetchedPage.java` + `FetchOptions.java` + `RenderMode.java` + `PageFetchException.java`
- New: `apps/ingestion-service/.../crawl/fetch/Crawler4jStaticPageFetcher.java`
- New: `apps/ingestion-service/.../crawl/fetch/HeadlessBrowserPageFetcher.java` (stub)
- New: `apps/ingestion-service/.../crawl/fetch/RoutingPageFetcher.java`
- Update: `libs/platform-contracts/.../parse/ParseProfile.java` — `renderMode` field
- Update: `apps/ingestion-service/.../parse/profile/CorpusConfigurationLoader.java` — parse field
- Update: `ops/config/corpus/geostat-portal-parse.yaml` — `renderMode: static`

**Acceptance criteria:**
- `RoutingPageFetcher` unit tests pass (static + headless routing).
- `HeadlessBrowserPageFetcher` is a valid stub (throws `UnsupportedOperationException`).
- Existing ingestion-service integration tests pass — no behavior change with `renderMode: static`.
- `geostat-portal-parse.yaml`-ში `renderMode: headless`-ზე გადართვა = Playwright fetcher-ი გამოიყენება.

**SPA migration playbook (future — one-liner):**
```yaml
# ops/config/corpus/geostat-portal-parse.yaml
renderMode: headless  # ← ეს ერთი ცვლილება. სხვა არაფერი.
```

---

### ARCH-02 — `ExtractionStrategyRegistry`: one-size-fits-all extractor → per-corpus/page-type strategies 🟠

**პრობლემა — root cause:**
`JsoupContentExtractor` ერთი universal impl-ია ყველა corpus-ისა და page type-ისთვის. YAML `ParseProfile` record-ი კარგია მარტივი selector configs-ისთვის, მაგრამ **ვერ გამოხატავს**:

```
if page is news:
  lead_text = first <p> after <h1> in <article>
  download block = <div class="news-attachments"> → exclude
else if page is dataset:
  lead_text = <meta name="description">
  table extraction = aggressive (dataset = table-heavy)
else if page is portal landing:
  lead_text = NULL (portal pages have no meaningful lead)
  section_path = extract from <nav.category-nav>
```

ამ ლოგიკის YAML-ში გამოხატვა შეუძლებელია. ერთი universal extractor = ყველა page type-ს ერთნაირად ექცევა = quality ceiling.

**სწორი სტრუქტურა — Strategy Registry:**

```
corpus + page_kind
      ↓
ExtractionStrategyRegistry.resolve(corpus, pageKind)
      ↓
┌────────────────────────────────────────────────────┐
│ GeostatNewsExtractionStrategy                      │
│   lead = first prose <p> after <h1> in <article>  │
│   exclude = .news-attachments, .related-news       │
│                                                    │
│ GeostatDatasetExtractionStrategy                   │
│   lead = <meta description>                        │
│   extractTables = true, aggressive                 │
│                                                    │
│ GeostatPortalExtractionStrategy                    │
│   lead = null (portal pages have no lead)          │
│   section_path = from <nav.category-nav>           │
│                                                    │
│ YamlConfiguredStrategy (fallback — current)        │
│   uses ParseProfile record from YAML               │
└────────────────────────────────────────────────────┘
      ↓
ContentExtractor.extract() — port unchanged
```

**Resolution steps:**

**ნაბიჯი 1 — `ExtractionStrategy` interface `libs/platform-contracts`-ში:**

შექმენი:
`libs/platform-contracts/src/main/java/com/geostat/platform/parse/ExtractionStrategy.java`

```java
package com.geostat.platform.parse;

/**
 * Per-corpus or per-page-kind extraction strategy.
 * Implementations encapsulate site-specific extraction logic that cannot be
 * expressed purely through ParseProfile YAML selectors.
 *
 * <p>Strategies are registered by {@link ExtractionStrategyRegistry} and
 * resolved at runtime by corpus name + page kind.
 */
public interface ExtractionStrategy {

    /**
     * Unique strategy identifier (e.g. "geostat-news", "geostat-dataset").
     * Used for logging and registry lookup.
     */
    String strategyId();

    /**
     * Returns true if this strategy handles the given corpus + page kind combination.
     * Evaluated in registration order; first match wins.
     */
    boolean supports(String corpusName, String pageKind);

    /**
     * Extract structured content from the given HTML page.
     * May use Jsoup, regex, or any other parsing technique.
     *
     * @param page    raw HTML + URL
     * @param profile base profile (YAML-loaded) as hint — may be ignored
     * @return        extracted document content
     */
    CleanedDocument extract(HtmlPageInput page, ParseProfile profile);
}
```

**ნაბიჯი 2 — `ExtractionStrategyRegistry` `libs/platform-contracts`-ში:**

```java
package com.geostat.platform.parse;

import java.util.List;

/**
 * Resolves the appropriate ExtractionStrategy for a given corpus and page kind.
 * Falls back to a YAML-configured default if no specific strategy matches.
 */
public interface ExtractionStrategyRegistry {

    /**
     * Resolve strategy for corpus + pageKind.
     * Implementations must always return a non-null strategy
     * (fallback to YamlConfiguredStrategy if no specific match).
     */
    ExtractionStrategy resolve(String corpusName, String pageKind);
}
```

**ნაბიჯი 3 — `DefaultExtractionStrategyRegistry` ingestion-service-ში:**

შექმენი:
`apps/ingestion-service/src/main/java/com/geostat/ingestion/parse/strategy/DefaultExtractionStrategyRegistry.java`

```java
@Component
public class DefaultExtractionStrategyRegistry implements ExtractionStrategyRegistry {

    private final List<ExtractionStrategy> strategies;
    private final YamlConfiguredStrategy yamlFallback;

    // Spring injects ALL ExtractionStrategy beans automatically
    public DefaultExtractionStrategyRegistry(
            List<ExtractionStrategy> strategies,
            YamlConfiguredStrategy yamlFallback) {
        this.strategies = strategies;
        this.yamlFallback = yamlFallback;
    }

    @Override
    public ExtractionStrategy resolve(String corpusName, String pageKind) {
        return strategies.stream()
            .filter(s -> s.supports(corpusName, pageKind))
            .findFirst()
            .orElse(yamlFallback);
    }
}
```

**ნაბიჯი 4 — `YamlConfiguredStrategy` (current JsoupContentExtractor → adapter):**

შექმენი:
`apps/ingestion-service/src/main/java/com/geostat/ingestion/parse/strategy/YamlConfiguredStrategy.java`

```java
@Component
public class YamlConfiguredStrategy implements ExtractionStrategy {

    private final JsoupContentExtractor extractor;         // existing bean
    private final CorpusConfigurationLoader configLoader;  // existing bean

    @Override
    public String strategyId() { return "yaml-configured-fallback"; }

    @Override
    public boolean supports(String corpusName, String pageKind) {
        return true; // catch-all fallback
    }

    @Override
    public CleanedDocument extract(HtmlPageInput page, ParseProfile profile) {
        // delegates entirely to existing JsoupContentExtractor
        return extractor.extract(page, profile);
    }
}
```

**ნაბიჯი 5 — `GeostatNewsExtractionStrategy` (first real strategy — geostat-specific):**

შექმენი:
`apps/ingestion-service/src/main/java/com/geostat/ingestion/parse/strategy/GeostatNewsExtractionStrategy.java`

```java
@Component
public class GeostatNewsExtractionStrategy implements ExtractionStrategy {

    @Override
    public String strategyId() { return "geostat-news"; }

    @Override
    public boolean supports(String corpusName, String pageKind) {
        return "geostat-portal".equals(corpusName) && "news".equals(pageKind);
    }

    @Override
    public CleanedDocument extract(HtmlPageInput page, ParseProfile profile) {
        Document html = Jsoup.parse(page.html(), page.canonicalUrl());

        // Remove noise specific to news pages
        html.select("script, style, nav, footer, header, aside, " +
                    ".related-articles, .news-attachments, " +
                    ".social-share, .breadcrumb, .rightbar-wrapper").remove();

        // news page structure: title from <h1> in article, NOT html.title()
        // (html.title() adds " - საქართველოს სტატისტიკის ეროვნული სამსახური" suffix)
        Element article = html.selectFirst("article, .news-content, main");
        String title = article != null && article.selectFirst("h1") != null
            ? article.selectFirst("h1").text().strip()
            : html.title().strip();

        // lead: first prose paragraph INSIDE article, after h1
        String leadText = extractNewsLead(article);

        // body: all meaningful paragraphs in article
        String body = article != null
            ? article.select("p, li, h2, h3, h4").stream()
                .map(e -> e.text().strip())
                .filter(t -> t.length() > 40)
                .filter(t -> !isNewsBoilerplate(t))
                .collect(java.util.stream.Collectors.joining(" "))
            : "";

        String language = html.select("html").attr("lang");
        language = language.isBlank() ? null : language.split("-")[0].toLowerCase();

        return new CleanedDocument(title, body, language,
            List.of(), null, leadText, null, 0, 0);
    }

    private String extractNewsLead(Element article) {
        if (article == null) return null;
        // skip h1, take first substantive paragraph
        for (Element p : article.select("p")) {
            String text = p.text().strip();
            if (text.length() >= 60 && looksLikeProse(text)) {
                return text;
            }
        }
        return null;
    }

    private boolean looksLikeProse(String text) {
        long letterCount = text.chars()
            .filter(Character::isLetter).count();
        return letterCount > text.length() * 0.5;
    }

    private boolean isNewsBoilerplate(String text) {
        return text.startsWith("თარიღი:") || text.startsWith("Date:")
            || text.startsWith("გადმოწერა") || text.startsWith("Download")
            || text.startsWith("PDF") || text.startsWith("CSV");
    }
}
```

**ნაბიჯი 6 — `JsoupContentExtractor` → registry-ის გამოყენება:**

`JsoupContentExtractor.java`-ში inject `ExtractionStrategyRegistry`:

```java
@Component
public class JsoupContentExtractor implements ContentExtractor {

    private final ExtractionStrategyRegistry registry;
    // ... other fields

    @Override
    public CleanedDocument extract(HtmlPageInput page, ParseProfile profile) {
        // page_kind at this point is unknown (not yet enriched)
        // use "unknown" → resolves to YamlConfiguredStrategy (current behavior)
        // after enrichment, re-parse can use correct strategy
        ExtractionStrategy strategy = registry.resolve(profile.corpus(), "unknown");
        return strategy.extract(page, profile);
    }
}
```

**ნაბიჯი 7 — unit tests:**

```java
// DefaultExtractionStrategyRegistryTest:
@Test
void resolvesGeostatNews_forNewsPageKind() {
    ExtractionStrategy resolved = registry.resolve("geostat-portal", "news");
    assertThat(resolved.strategyId()).isEqualTo("geostat-news");
}

@Test
void fallsBackToYaml_forUnknownPageKind() {
    ExtractionStrategy resolved = registry.resolve("geostat-portal", "unknown");
    assertThat(resolved.strategyId()).isEqualTo("yaml-configured-fallback");
}

@Test
void fallsBackToYaml_forUnknownCorpus() {
    ExtractionStrategy resolved = registry.resolve("other-corpus", "news");
    assertThat(resolved.strategyId()).isEqualTo("yaml-configured-fallback");
}

// GeostatNewsExtractionStrategyTest:
@Test
void newsStrategy_extractsLeadFromArticle_notSidebar() {
    String html = """
        <html><body>
          <article>
            <h1>სოფლის მეურნეობის სტატისტიკა 2025</h1>
            <p>საქართველოში 2025 წელს სოფლის მეურნეობის წარმოება 12%-ით გაიზარდა.
               ეს გაზრდა განპირობებულია მარცვლეული კულტურების...</p>
          </article>
          <aside class="related-articles">
            <p>ცხოვრების დონის მაჩვენებლები - 2025</p>
          </aside>
        </body></html>""";
    CleanedDocument doc = strategy.extract(
        new HtmlPageInput(html, "https://geostat.ge/ka/single-news/123"), null);
    assertThat(doc.leadText()).contains("12%-ით გაიზარდა");
    assertThat(doc.leadText()).doesNotContain("ცხოვრების დონის");
}
```

**ფაილები:**
- New: `libs/platform-contracts/.../parse/ExtractionStrategy.java`
- New: `libs/platform-contracts/.../parse/ExtractionStrategyRegistry.java`
- New: `apps/ingestion-service/.../parse/strategy/DefaultExtractionStrategyRegistry.java`
- New: `apps/ingestion-service/.../parse/strategy/YamlConfiguredStrategy.java`
- New: `apps/ingestion-service/.../parse/strategy/GeostatNewsExtractionStrategy.java`
- Update: `apps/ingestion-service/.../parse/profile/JsoupContentExtractor.java`

**Acceptance criteria:**
- `DefaultExtractionStrategyRegistry` unit tests pass.
- `GeostatNewsExtractionStrategy` unit test — no related-articles contamination in lead.
- Existing ingestion integration tests pass (YAML fallback behavior unchanged).
- ახალი strategy-ს დასამატებლად: 1 Java class + `@Component` — სხვა კოდი არ იცვლება.

---

### ARCH-03 — `QualityMetric` Strategy: SQL `corpus-quality-gate.yaml`-დან Java-ში 🟠

**პრობლემა — root cause:**
`ops/eval/corpus-quality-gate.yaml`-ში SQL strings YAML-ში:

```yaml
metric:
  sql: |
    SELECT SUM(CASE WHEN content_text ILIKE '%adapted version%'
               THEN 1 ELSE 0 END)::float / NULLIF(COUNT(*),0)
    FROM ingestion.document
    WHERE corpus_id = :corpusId AND fetch_status = 'parsed'
```

ეს **4 პრობლემა** ქმნის:
1. **IDE-ს მხარდაჭერა ნული** — SQL string YAML-ში = syntax highlighting, validation, refactoring = nothing
2. **Schema coupling** — `ingestion.document`, `content_text` column rename → YAML silent break
3. **Untestable** — unit test SQL fragments in YAML string = impossible
4. **Abstraction break** — DB schema knowledge config layer-ში

**სწორი სტრუქტურა:**

```yaml
# YAML — მხოლოდ declaration, SQL-ი კი არა:
gates:
  - metric: boilerplate_ratio    # ← bean name, SQL nowhere near here
    target: "<= 0.05"
    blocks: [enrichment_backfill]
```

```java
// Java — SQL typed, testable, IDE-supported:
@Component("boilerplate_ratio")
public class BoilerplateRatioMetric implements QualityMetric { ... }
```

**Resolution steps:**

**ნაბიჯი 1 — `QualityMetric` port `libs/platform-contracts`-ში:**

შექმენი:
`libs/platform-contracts/src/main/java/com/geostat/platform/quality/QualityMetric.java`

```java
package com.geostat.platform.quality;

import java.util.UUID;

/**
 * Port: computes a single quality metric for a given corpus.
 * Implementations are named Spring beans referenced by id in corpus-quality-gate.yaml.
 *
 * <p>Implementations live in ingestion-service (infrastructure layer).
 * SQL and DB access belong here — not in YAML configuration.
 */
public interface QualityMetric {

    /** Unique metric id — must match the {@code metric:} value in quality gate YAML. */
    String id();

    /**
     * Compute the metric value for the given corpus.
     *
     * @param corpusId  corpus UUID
     * @return          computed double value (ratio, count, or percentage)
     */
    double compute(UUID corpusId);

    /** Human-readable description of what this metric measures. */
    String description();
}
```

**ნაბიჯი 2 — `GateResult` + `QualityGateEvaluator`:**

```java
// libs/platform-contracts:
public record GateResult(
    String metricId,
    double value,
    String target,     // e.g. "<= 0.05"
    boolean passed,
    List<String> blocks  // downstream phases blocked if failed
) {}

public interface QualityGateEvaluator {
    List<GateResult> evaluate(UUID corpusId, List<GateDefinition> gates);
}

public record GateDefinition(
    String metric,      // bean name
    String target,      // threshold expression
    List<String> blocks // what to block on failure
) {}
```

**ნაბიჯი 3 — metric implementations ingestion-service-ში:**

შექმენი პაკეტი:
`apps/ingestion-service/src/main/java/com/geostat/ingestion/quality/`

```java
// BoilerplateRatioMetric.java
@Component("boilerplate_ratio")
public class BoilerplateRatioMetric implements QualityMetric {

    private final JdbcTemplate jdbc;

    @Override public String id() { return "boilerplate_ratio"; }

    @Override public String description() {
        return "Share of parsed docs whose content_text contains accessibility/footer boilerplate";
    }

    @Override
    public double compute(UUID corpusId) {
        Double result = jdbc.queryForObject("""
            SELECT
              SUM(CASE
                WHEN content_text ILIKE '%adapted version of the website%'
                  OR content_text ILIKE '%ვებგვერდის ადაპ%'
                THEN 1 ELSE 0
              END)::float / NULLIF(COUNT(*), 0)
            FROM ingestion.document
            WHERE corpus_id = ? AND fetch_status = 'parsed'
            """, Double.class, corpusId);
        return result != null ? result : 0.0;
    }
}

// EmptyBodyRateMetric.java
@Component("empty_body_rate")
public class EmptyBodyRateMetric implements QualityMetric {

    @Override public String id() { return "empty_body_rate"; }

    @Override public String description() {
        return "Share of parsed docs with content_text shorter than 30 chars";
    }

    @Override
    public double compute(UUID corpusId) {
        Double result = jdbc.queryForObject("""
            SELECT
              SUM(CASE WHEN COALESCE(length(content_text), 0) < 30
                  THEN 1 ELSE 0 END)::float / NULLIF(COUNT(*), 0)
            FROM ingestion.document
            WHERE corpus_id = ? AND fetch_status = 'parsed'
            """, Double.class, corpusId);
        return result != null ? result : 0.0;
    }
}

// ChunkCoverageMetric.java
@Component("chunk_coverage")
public class ChunkCoverageMetric implements QualityMetric {

    @Override public String id() { return "chunk_coverage"; }

    @Override public String description() {
        return "Share of parsed docs that produced at least one chunk";
    }

    @Override
    public double compute(UUID corpusId) {
        Double result = jdbc.queryForObject("""
            SELECT COUNT(DISTINCT c.document_id)::float
                 / NULLIF((SELECT COUNT(*) FROM ingestion.document
                           WHERE corpus_id = ? AND fetch_status = 'parsed'), 0)
            FROM ingestion.chunk c
            WHERE c.corpus_id = ?
            """, Double.class, corpusId, corpusId);
        return result != null ? result : 0.0;
    }
}

// SummaryCoverageMetric.java
@Component("summary_coverage")
public class SummaryCoverageMetric implements QualityMetric {

    @Override public String id() { return "summary_coverage"; }

    @Override public String description() {
        return "Layer 2 — share of parsed docs with non-blank summary_ka or summary_en";
    }

    @Override
    public double compute(UUID corpusId) {
        Double result = jdbc.queryForObject("""
            SELECT
              SUM(CASE WHEN COALESCE(summary_ka,'') <> ''
                            OR COALESCE(summary_en,'') <> ''
                  THEN 1 ELSE 0 END)::float / NULLIF(COUNT(*), 0)
            FROM ingestion.document
            WHERE corpus_id = ? AND fetch_status = 'parsed'
            """, Double.class, corpusId);
        return result != null ? result : 0.0;
    }
}
```

**ნაბიჯი 4 — `corpus-quality-gate.yaml` refactor (SQL ამოიღე, metric bean name-ი დარჩეს):**

`ops/eval/corpus-quality-gate.yaml`:
```yaml
# Corpus quality gates — thresholds and block rules only.
# SQL and computation logic lives in QualityMetric implementations (ingestion-service).
# See: apps/ingestion-service/src/main/java/com/geostat/ingestion/quality/
corpus: geostat-portal

gates:
  - metric: boilerplate_ratio
    description: "Share of parsed docs containing accessibility/footer boilerplate"
    target: "<= 0.05"
    currentBaseline: 0.86
    blocks: [enrichment_backfill, derived_catalog_cutover]

  - metric: empty_body_rate
    description: "Share of parsed docs with content_text < 30 chars"
    target: "<= 0.03"
    currentBaseline: 0.119
    blocks: [index, enrichment_backfill]

  - metric: chunk_coverage
    description: "Share of parsed docs with at least one chunk"
    target: ">= 0.95"
    currentBaseline: 0.893
    blocks: [eval_gate]

  - metric: summary_coverage
    description: "Share of parsed docs with non-blank summary_ka or summary_en"
    target: ">= 0.95"
    currentBaseline: 0.889
    blocks: [derived_catalog_cutover]

informational:
  - metric: page_kind_portal_share
    description: "Share of docs classified as portal landings"
    target: "<= 0.30"
    currentBaseline: 0.50
```

**ნაბიჯი 5 — `QualityGateRunner` (replaces YAML SQL executor):**

შექმენი:
`apps/ingestion-service/src/main/java/com/geostat/ingestion/quality/QualityGateRunner.java`

```java
@Component
public class QualityGateRunner {

    private final Map<String, QualityMetric> metricsByName;
    private final QualityGateConfigLoader configLoader;

    // Spring injects all QualityMetric beans automatically
    public QualityGateRunner(
            List<QualityMetric> metrics,
            QualityGateConfigLoader configLoader) {
        this.metricsByName = metrics.stream()
            .collect(Collectors.toMap(QualityMetric::id, m -> m));
        this.configLoader = configLoader;
    }

    public List<GateResult> runGates(String corpusName, UUID corpusId) {
        QualityGateConfig config = configLoader.load(corpusName);
        List<GateResult> results = new ArrayList<>();

        for (GateDefinition gate : config.gates()) {
            QualityMetric metric = metricsByName.get(gate.metric());
            if (metric == null) {
                log.warn("No QualityMetric bean found for id '{}' — gate skipped", gate.metric());
                continue;
            }
            double value = metric.compute(corpusId);
            boolean passed = evaluateTarget(value, gate.target());
            results.add(new GateResult(gate.metric(), value, gate.target(),
                passed, passed ? List.of() : gate.blocks()));
        }
        return results;
    }

    private boolean evaluateTarget(double value, String target) {
        // parse "<= 0.05", ">= 0.95", "< 0.03" etc.
        target = target.strip();
        if (target.startsWith("<=")) return value <= Double.parseDouble(target.substring(2).strip());
        if (target.startsWith(">=")) return value >= Double.parseDouble(target.substring(2).strip());
        if (target.startsWith("<"))  return value <  Double.parseDouble(target.substring(1).strip());
        if (target.startsWith(">"))  return value >  Double.parseDouble(target.substring(1).strip());
        throw new IllegalArgumentException("Unparseable target expression: " + target);
    }
}
```

**ნაბიჯი 6 — unit tests:**

```java
// BoilerplateRatioMetricTest:
@Test
void compute_returnsCorrectRatio_whenBoilerplateExists() {
    // given: 8 docs with boilerplate, 2 without, out of 10 parsed
    // when:
    double ratio = metric.compute(corpusId);
    // then:
    assertThat(ratio).isCloseTo(0.80, within(0.01));
}

// QualityGateRunnerTest:
@Test
void runGates_returnsFailed_whenThresholdExceeded() {
    when(boilerplateMetric.compute(corpusId)).thenReturn(0.86);
    List<GateResult> results = runner.runGates("geostat-portal", corpusId);
    GateResult boilerplateGate = results.stream()
        .filter(r -> r.metricId().equals("boilerplate_ratio")).findFirst().orElseThrow();
    assertThat(boilerplateGate.passed()).isFalse();
    assertThat(boilerplateGate.blocks()).contains("enrichment_backfill");
}

@Test
void runGates_returnsPass_afterQualityImproved() {
    when(boilerplateMetric.compute(corpusId)).thenReturn(0.02); // after fix
    List<GateResult> results = runner.runGates("geostat-portal", corpusId);
    assertThat(results.stream()
        .filter(r -> r.metricId().equals("boilerplate_ratio"))
        .findFirst().orElseThrow().passed()).isTrue();
}
```

**ფაილები:**
- New: `libs/platform-contracts/.../quality/QualityMetric.java`
- New: `libs/platform-contracts/.../quality/GateResult.java`
- New: `libs/platform-contracts/.../quality/GateDefinition.java`
- New: `apps/ingestion-service/.../quality/BoilerplateRatioMetric.java`
- New: `apps/ingestion-service/.../quality/EmptyBodyRateMetric.java`
- New: `apps/ingestion-service/.../quality/ChunkCoverageMetric.java`
- New: `apps/ingestion-service/.../quality/SummaryCoverageMetric.java`
- New: `apps/ingestion-service/.../quality/QualityGateRunner.java`
- Update: `ops/eval/corpus-quality-gate.yaml` — SQL სრულად ამოიღე

**Acceptance criteria:**
- ყველა `QualityMetric` unit test pass.
- `QualityGateRunner` test — failed gate-ი სწორ `blocks` list-ს აბრუნებს.
- `corpus-quality-gate.yaml`-ში SQL string = ნული.
- ახალი metric-ის დასამატებლად: 1 `@Component` class + YAML-ში 1 gate entry — სხვა კოდი არ იცვლება.

---

ეს items ახლა **blocking არ არის** — Phase 1–7 fixes-ი უფრო დიდ impact-ს მოიტანს. ჩაიწერება BACKLOG-ად მომავალი sprint-ებისთვის.

---

### DB-01 — `curation_override` UNIQUE constraint semantically მცდარია `rename_topic`-ისთვის 🟡

**პრობლემა:**
```sql
CONSTRAINT uq_override_url_action UNIQUE (url_hash, action)
```
`rename_topic` action-ი cluster-ს ეხება (`target = cluster_id::text`), არა URL-ს. ანუ თუ ორი სხვადასხვა cluster-ის rename-ი ერთი და იმავე URL hash-ს (შემთხვევით) ემთხვევა — INSERT fails. constraint semantically url-based action-ებისთვისაა (boost, demote, exclude, pin_as_portal), `rename_topic`-ისთვის კი — `(target, action)` უნდა იყოს unique.

**Resolution steps:**

1. შექმენი ingestion-service-ში ახალი migration:
   `apps/ingestion-service/src/main/resources/db/migration/V18__fix_curation_override_rename_topic.sql`

2. migration-ის შიგთავსი:
   ```sql
   -- rename_topic rows-ისთვის unique უნდა იყოს (target, action), არა (url_hash, action)
   -- ჯერ drop old constraint
   ALTER TABLE ingestion.curation_override
       DROP CONSTRAINT IF EXISTS uq_override_url_action;

   -- url-based actions: boost, demote, exclude, pin_as_portal — unique by (url_hash, action)
   CREATE UNIQUE INDEX IF NOT EXISTS uq_curation_url_action
       ON ingestion.curation_override (url_hash, action)
       WHERE action IN ('boost', 'demote', 'exclude', 'pin_as_portal');

   -- target-based actions: rename_topic — unique by (target, action)
   CREATE UNIQUE INDEX IF NOT EXISTS uq_curation_target_action
       ON ingestion.curation_override (target, action)
       WHERE action = 'rename_topic';

   -- index for target lookups (eliminates N+1 correlated subquery)
   CREATE INDEX IF NOT EXISTS idx_curation_override_target
       ON ingestion.curation_override (target)
       WHERE target IS NOT NULL;
   ```

   **შენიშვნა:** V17-ში (L0-01) `idx_curation_override_target` უკვე შეიძლება დაემატა — თუ ასეა, V18-ში ამ index-ის CREATE-ი გამოტოვე.

3. unit test — `CurationOverrideRepositoryTest`:
   ```java
   @Test
   void twoRenameTopicOverrides_differentTargets_bothInsertSuccessfully() {
       // same url_hash, but different targets — must NOT conflict
       repo.save(override(urlHash="abc", action="rename_topic", target="cluster-1"));
       repo.save(override(urlHash="abc", action="rename_topic", target="cluster-2"));
       assertThat(repo.findByActionAndTarget("rename_topic", "cluster-1")).isPresent();
       assertThat(repo.findByActionAndTarget("rename_topic", "cluster-2")).isPresent();
   }
   ```

**Acceptance criteria:**
- Flyway migration runs without error.
- `\d ingestion.curation_override` — ძველი `uq_override_url_action` index-ი არ ჩანს; ახალი ორი partial index ჩანს.
- unit test გადადის.

---

### DB-02 — `document` fat table → eventual split (long-term) 🟢

**პრობლემა (informational — ახლა არ implement-ო):**
`ingestion.document` ცხრილი 30+ სვეტს შეიცავს: base crawl fields (V1) + enrichment fields (V10: `summary_ka`, `summary_en`, `keywords`, `entities`, `authority_score`, `page_kind`, `enrichment_version`, `score_boost`). სხვადასხვა lifecycle-ის fields ერთ ცხრილშია.

**სასურველი სამომავლო სქემა:**
```sql
ingestion.document        -- crawl identity: url, title, content_text, fetch_status, ...
ingestion.document_enrichment  -- enrichment results: summary_ka/en, keywords, entities,
                                --   page_kind, authority_score, enrichment_version, score_boost
                                --   FK: document_id REFERENCES document(id)
```

**ახლა:** ჩაიწერე plan item-ად. implement-ი მხოლოდ მას შემდეგ, რაც Phase 1–7 დასრულდება და owner approve-ს. migration მოითხოვს ingestion-service-ის სრულ refactor-ს (JPA entities, repositories, enrichment orchestrator).

---

### DB-03 — `corpus.policy` JSONB validation არ არის 🟡

**პრობლემა:**
`corpus.policy JSONB` free-form-ია — DB-ი ვერ ამოწმებს required fields-ს. `maxPagesPerRun`, `excludePatterns`, `respectRobotsTxt` — Java deserialization-ით ამოწმდება. production-ში policy-ის მცდარი structure silent bugs-ს იწვევს.

**Resolution steps:**

1. `apps/ingestion-service`-ში მოძებნე `CorpusPolicy` Java კლასი (ან record).
2. შექმენი JSON Schema ფაილი: `apps/ingestion-service/src/main/resources/schema/corpus-policy-schema.json`:
   ```json
   {
     "$schema": "http://json-schema.org/draft-07/schema#",
     "type": "object",
     "properties": {
       "maxPagesPerRun":    {"type": "integer", "minimum": 1},
       "maxDepth":          {"type": "integer", "minimum": 0},
       "respectRobotsTxt": {"type": "boolean"},
       "excludePatterns":  {"type": "array", "items": {"type": "string"}},
       "includePatterns":  {"type": "array", "items": {"type": "string"}},
       "rateLimitMs":      {"type": "integer", "minimum": 0}
     },
     "additionalProperties": false
   }
   ```
3. `CorpusController.createCorpus()` / `updatePolicy()` — validate input JSON against schema (networknt/json-schema-validator ან equivalent).
4. Postgres-level: `ALTER TABLE ingestion.corpus ADD CONSTRAINT chk_policy_is_object CHECK (jsonb_typeof(policy) = 'object')` — minimal guard.

**Acceptance criteria:**
- `POST /corpus` with invalid policy → 400 Bad Request with schema validation error.
- Existing corpus policy update with typo field → rejected.

---

### DB-04 — expired `curation_override` cleanup job არ არის 🟡

**პრობლემა:**
`curation_override.expires_at` — expired rows ბაზაში რჩება. cleanup job არ არსებობს.

**Resolution steps:**

1. ingestion-service-ში `CurationOverrideCleanupJob` scheduled job (weekly, opt-in):
   ```java
   @Scheduled(cron = "0 0 3 * * SUN") // Sunday 3 AM
   public void cleanupExpiredOverrides() {
       int deleted = jdbcTemplate.update(
           "DELETE FROM ingestion.curation_override " +
           "WHERE expires_at IS NOT NULL AND expires_at < now() - interval '7 days'");
       log.info("Cleaned up {} expired curation overrides", deleted);
   }
   ```
   7-day grace period — `now() - interval '7 days'` — რომ audit trail-ი შენარჩუნდეს.

2. `@ConditionalOnProperty("geostat.ingestion.curation.cleanup-enabled")` — default `false`.

**Acceptance criteria:**
- `SELECT COUNT(*) FROM ingestion.curation_override WHERE expires_at < now()` — 0 after job run.

---

### DB-05 — chat session history persistence (Redis/DB) 🟢

**პრობლემა (informational — ახლა არ implement-ო):**
chat session-ი in-memory-ია (`session-ttl-minutes: 30`). service restart = context loss. `chat.turn` ცხრილი ინახავს history-ს telemetry-ად, მაგრამ session reload არ ხდება.

**სამომავლო გეგმა:**
- Redis (`spring-session-data-redis`) — stateless restart-proof sessions.
- ან `chat.turn` full session reload on first message of resumed session.

**ახლა:** owner-ს შეასთავაზე Redis session store — plan item. implement-ი მხოლოდ approval-ის შემდეგ.

---
