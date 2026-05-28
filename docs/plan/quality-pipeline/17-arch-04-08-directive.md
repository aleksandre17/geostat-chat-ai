# ARCH-04..08 — Implementation Directive for Junior (Composer)

> **ჯუნიორისთვის:** სენიორმა შეაფასა და დაწერა ეს directive. ყველა გადაწყვეტილება მიღებულია — შენი ამოცანაა implementation.
> სრული spec: `docs/plan/quality-pipeline/05-multi-corpus-arch.md`.
> ეს ფაილი — **გამარტივებული, ეტაპობრივი directive** + **რა არ გჭირდება** (უკვე done).
>
> **ოქმი:**
> 1. ყოველ task-ამდე წაიკითხე შესაბამისი section (`05-multi-corpus-arch.md`)
> 2. Read ყველა file-ი modification-ამდე
> 3. Check lints — fix before commit
> 4. სენიორი ამოწმებს acceptance criteria-ს

---

## Current state (senior verified — 2026-05-27)

| Item | სტატუსი | შენიშვნა |
|------|---------|----------|
| **ARCH-04** CrawlOrchestrator port | ❌ NOT done | `CrawlJob`, `CrawlOrchestrator` — არ არსებობს |
| **ARCH-05** NetworkPolicy record | ❌ NOT done | `NetworkPolicy`, `BasicAuthCredential` — არ არსებობს |
| **ARCH-06** PageKindDetector port + impls | ❌ NOT done | `PageKindDetector`, `UrlPatternPageKindDetector`, etc — 0 files |
| **ARCH-07** `document.page_kind` column | ✅ DONE | `V10__document_enrichment_columns.sql` (line 10) — column exists |
| **ARCH-08** `pageKindRules` in parse.yaml | ❌ NOT done | `geostat-portal-parse.yaml` has no `pageKindRules` section |

**ARCH-07 skip:** V18 migration for `page_kind` column-ი **არ დაწეროთ** — V10-ში უკვე გაკეთდა. V18 = `V18__chunk_content_hash_document_quality.sql` (სხვა content). Column, index, check constraint ყველა არსებობს.

---

## Execution phases — strict order

```
Phase A (parallel — no dependencies):
  A-1: ARCH-05 — NetworkPolicy + BasicAuthCredential records
  A-2: ARCH-08 — pageKindRules + rootSelectorStrategy in geostat-portal-parse.yaml

Phase B (after Phase A):
  B-1: ARCH-06 — PageKindDetector port + UrlPatternPageKindDetector + HtmlSignalPageKindDetector + RoutingPageKindDetector
       ↳ depends on: NetworkPolicy in FetchOptions + ParseProfile (A-1), parse.yaml rules (A-2)

Phase C (after Phase B):
  C-1: ARCH-04 — CrawlOrchestrator port + DefaultCrawlOrchestrator + CorpusConfigurationLoader.loadAllPolicies()
       ↳ depends on: PageKindDetector wired into crawl pipeline (B-1)
```

---

## Phase A-1 — ARCH-05: NetworkPolicy (platform-contracts)

**Source:** `05-multi-corpus-arch.md` → "ARCH-05" section, steps 1–4.

### Files to create:

**`libs/platform-contracts/src/main/java/com/geostat/platform/crawl/NetworkPolicy.java`**
```java
package com.geostat.platform.crawl;

import java.util.Map;

/**
 * Network-level fetch configuration for a corpus crawl.
 * All fields optional — defaults match production-safe behavior.
 * Loaded from the 'network' section of *-policy.yaml.
 */
public record NetworkPolicy(
    boolean             tlsVerify,
    boolean             respectRobotsTxt,
    BasicAuthCredential basicAuth,
    Map<String,String>  dnsOverrides,
    String              userAgent
) {
    public static NetworkPolicy defaults() {
        return new NetworkPolicy(true, true, null, Map.of(), null);
    }

    public boolean hasBasicAuth()    { return basicAuth != null; }
    public boolean hasDnsOverrides() { return !dnsOverrides.isEmpty(); }
}
```

**`libs/platform-contracts/src/main/java/com/geostat/platform/crawl/BasicAuthCredential.java`**
```java
package com.geostat.platform.crawl;

/**
 * Basic HTTP authentication credential.
 * Password must come from env var (e.g. "${ENV_VAR}") — never hardcode.
 */
public record BasicAuthCredential(String username, String password) {
    public BasicAuthCredential {
        if (username == null || username.isBlank())
            throw new IllegalArgumentException("basicAuth.username must not be blank");
        if (password == null || password.isBlank())
            throw new IllegalArgumentException(
                "basicAuth.password must not be blank — use ${ENV_VAR} in YAML");
    }
}
```

### Files to update:

**`libs/platform-contracts/src/main/java/com/geostat/platform/crawl/FetchOptions.java`**

Read the file. Add `NetworkPolicy network` as the last field. Update compact constructor to default `if (network == null) network = NetworkPolicy.defaults()`. Add static factory:
```java
public static FetchOptions forProfile(ParseProfile profile) {
    NetworkPolicy net = profile.networkPolicy() != null
        ? profile.networkPolicy() : NetworkPolicy.defaults();
    RenderMode mode = profile.renderMode() != null
        ? profile.renderMode() : RenderMode.STATIC;
    return new FetchOptions(mode, 10_000,
        net.userAgent() != null ? net.userAgent() : "GeostatBot/1.0", net);
}
```

**`libs/platform-contracts/src/main/java/com/geostat/platform/parse/ParseProfile.java`**

Read the file. Add two new fields at the end of the record:
- `NetworkPolicy networkPolicy` — nullable, default `NetworkPolicy.defaults()` in compact constructor
- `List<PageKindRule> pageKindRules` — nullable, default `List.of()` in compact constructor
- `String defaultPageKind` — nullable, default `"unknown"` in compact constructor
- `String rootSelectorStrategy` — nullable, default `"firstMatch"` in compact constructor

**NOTE:** `PageKindRule` and `PageKind` classes will be created in Phase B (ARCH-06). If Phase A-1 runs before Phase B, add `pageKindRules` as `Object` temporarily OR wait and do ParseProfile update together with Phase B. **Recommended: do ParseProfile update in Phase B-1 together with ARCH-06.**

So for Phase A-1: create only `NetworkPolicy.java`, `BasicAuthCredential.java`, add `NetworkPolicy network` field to `FetchOptions.java`.

### Tests:

**`apps/ingestion-service/src/test/java/com/geostat/ingestion/crawl/fetch/NetworkPolicyTest.java`**
```java
@Test void defaults_areProdSafe() {
    var net = NetworkPolicy.defaults();
    assertThat(net.tlsVerify()).isTrue();
    assertThat(net.respectRobotsTxt()).isTrue();
    assertThat(net.hasBasicAuth()).isFalse();
}

@Test void basicAuthCredential_rejectsBlankPassword() {
    assertThatThrownBy(() -> new BasicAuthCredential("user", ""))
        .isInstanceOf(IllegalArgumentException.class);
}
```

---

## Phase A-2 — ARCH-08: pageKindRules in geostat-portal-parse.yaml

**Source:** `05-multi-corpus-arch.md` → "ARCH-08" section.

**File:** `apps/ingestion-service/src/main/resources/parse-profiles/geostat-portal-parse.yaml`
(also check `ops/config/corpus/geostat-portal-parse.yaml` — update whichever exists; update BOTH if both exist)

Append these fields to the existing YAML:
```yaml
rootSelectorStrategy: firstMatch

pageKindRules:
  - kind: news
    urlPatterns: ["/single-news/", "/ka/single-news/", "/en/single-news/"]
    priority: 10
  - kind: dataset
    urlPatterns: ["/ka/open-data/", "/en/open-data/"]
    priority: 10
  - kind: publication
    urlPatterns: ["/ka/publications/", "/en/publications/",
                  "/ka/news/statistikuri-publikacia/", "/en/news/statistikuri-publikacia/"]
    priority: 10
  - kind: infographic
    urlPatterns: ["/ka/infographics/", "/en/infographics/"]
    priority: 10
  - kind: portal
    urlPatterns:
      - "^https://www\\.geostat\\.ge/ka$"
      - "^https://www\\.geostat\\.ge/en$"
      - "^https://www\\.geostat\\.ge/$"
    priority: 20
  - kind: category
    urlPatterns: ["/modules/categories/"]
    priority: 5
  defaultPageKind: unknown
```

**Acceptance:** `CorpusConfigurationLoader` must parse these fields without exception. The loader's inner YAML DTO must have corresponding fields. Read `CorpusConfigurationLoader.java` first — if it has no `pageKindRules` field in its inner DTO, add it. Do NOT touch the Java logic for detection yet — that is ARCH-06 (Phase B).

---

## Phase B-1 — ARCH-06: PageKindDetector port + implementations

**Source:** `05-multi-corpus-arch.md` → "ARCH-06" section, steps 1–9.

**Dependencies:** Phase A must be complete. `NetworkPolicy` in FetchOptions ✅, `pageKindRules` in parse.yaml ✅.

### Files to create in `libs/platform-contracts`:

**`com/geostat/platform/crawl/PageKind.java`** — constants class (not enum). Values: `NEWS`, `DATASET`, `PUBLICATION`, `PORTAL`, `CATEGORY`, `INFOGRAPHIC`, `UNKNOWN`. Private constructor. No Spring annotations.

**`com/geostat/platform/crawl/PageKindRule.java`** — record:
```java
public record PageKindRule(String kind, java.util.List<String> urlPatterns, int priority) {}
```

**`com/geostat/platform/crawl/PageKindDetector.java`** — port interface:
```java
public interface PageKindDetector {
    String detect(FetchedPage page, ParseProfile profile);
}
```

### Update `ParseProfile.java` (libs/platform-contracts):

Read the file first. Add to the end of the record:
- `List<PageKindRule> pageKindRules` — `if (pageKindRules == null) pageKindRules = List.of();`
- `String defaultPageKind` — `if (defaultPageKind == null) defaultPageKind = PageKind.UNKNOWN;`
- `String rootSelectorStrategy` — `if (rootSelectorStrategy == null) rootSelectorStrategy = "firstMatch";`

(If `NetworkPolicy networkPolicy` was not added in Phase A-1, add it here too.)

### Files to create in `apps/ingestion-service`:

Package: `com.geostat.ingestion.crawl.kind`

**`UrlPatternPageKindDetector.java`** (`@Component`):
- Reads `profile.pageKindRules()`
- For each rule: tests each `urlPattern` against the URL
  - Pattern starts with `^` or ends with `$` → `url.matches(pattern.toLowerCase())`
  - Otherwise → `url.toLowerCase().contains(pattern.toLowerCase())`
- Multiple matching rules → highest `priority` wins
- Returns `profile.defaultPageKind()` if no match

**`HtmlSignalPageKindDetector.java`** (`@Component`):
Signal priority (high → low):
1. JSON-LD `@type`: "NewsArticle"/"Article" → `news`; "Dataset" → `dataset`; "WebPage"+"Breadcrumb" → `portal`
2. OG `og:type`: "article"/"newsarticle" → `news`
3. Schema.org `itemtype`: check for `schema.org/Dataset`, `schema.org/NewsArticle`, `schema.org/Article`
4. DOM structural: `article + h1` inside → `news`; `≥2 data tables` → `dataset`
5. Default: `PageKind.UNKNOWN`

**`RoutingPageKindDetector.java`** (`@Primary @Component`):
- Injects `UrlPatternPageKindDetector` + `HtmlSignalPageKindDetector`
- Tries URL first; if `UNKNOWN` → try HTML
- Logs result + which detector was used at DEBUG level

### Update `CorpusConfigurationLoader.java`:

Read the file. In the inner YAML DTO class:
- Add `List<PageKindRuleYaml> pageKindRules` field
- Add `String defaultPageKind` field
- Add inner class `PageKindRuleYaml { String kind; List<String> urlPatterns; int priority; }`
- Map these to `PageKindRule` records in `toModel()`

### Tests:

`apps/ingestion-service/src/test/java/com/geostat/ingestion/crawl/kind/UrlPatternPageKindDetectorTest.java`:
- `/single-news/` → `news`
- exact URL regex `^https://www.geostat.ge/ka$` → `portal`
- unknown URL → `unknown`
- overlapping patterns → higher priority wins

`apps/ingestion-service/src/test/java/com/geostat/ingestion/crawl/kind/HtmlSignalPageKindDetectorTest.java`:
- `og:type=article` → `news`
- JSON-LD `"@type": "Dataset"` → `dataset`
- no signals → `unknown`

`apps/ingestion-service/src/test/java/com/geostat/ingestion/crawl/kind/RoutingPageKindDetectorTest.java`:
- URL pattern match → URL result used (no HTML parse)
- URL = unknown → HTML signal result used

---

## Phase C-1 — ARCH-04: CrawlOrchestrator

**Source:** `05-multi-corpus-arch.md` → "ARCH-04" section, steps 1–6.

**Dependencies:** Phase B complete. `PageKindDetector` available in crawl pipeline.

### Files to create in `libs/platform-contracts`:

**`com/geostat/platform/crawl/CrawlJob.java`** — record:
```java
public record CrawlJob(
    String      corpusName,
    java.util.UUID corpusId,
    CorpusPolicyV2 policy,
    ParseProfile   profile
) {}
```
(Check import for `CorpusPolicyV2` — it may be in a different package. Read existing corpus policy types.)

**`com/geostat/platform/crawl/CrawlOrchestrator.java`** — port interface:
```java
public interface CrawlOrchestrator {
    java.util.List<CrawlJob> discoverJobs();
    void executeAll(java.util.List<CrawlJob> jobs);
    void executeSingle(CrawlJob job);
}
```

### Update `CorpusConfigurationLoader.java`:

Add `loadAllPolicies()` method:
```java
public List<CorpusPolicyV2> loadAllPolicies() {
    Path dir = Paths.get(props.getCorpusConfigDir());
    try (Stream<Path> files = Files.list(dir)) {
        return files
            .filter(p -> p.getFileName().toString().endsWith("-policy.yaml"))
            .map(p -> loadPolicy(p.getFileName().toString().replace("-policy.yaml", "")))
            .toList();
    } catch (IOException e) {
        throw new CorpusConfigException("Cannot scan corpus config dir: " + dir, e);
    }
}
```
(Adapt to actual method signature — read the file first.)

### Create `DefaultCrawlOrchestrator.java`:

`apps/ingestion-service/src/main/java/com/geostat/ingestion/crawl/DefaultCrawlOrchestrator.java`

```java
@Component
public class DefaultCrawlOrchestrator implements CrawlOrchestrator {

    // Inject: CorpusConfigurationLoader, CorpusRepository, CrawlProperties,
    //         and whatever crawlRunner/CrawlService currently starts a crawl

    @Override
    public List<CrawlJob> discoverJobs() {
        return configLoader.loadAllPolicies().stream()
            .map(policy -> {
                UUID corpusId = corpusRepository.findByName(policy.corpus())
                    .orElseThrow(() -> new IllegalStateException(
                        "No corpus for name: " + policy.corpus()))
                    .getId();
                ParseProfile profile = configLoader.parseProfileFor(policy.corpus());
                return new CrawlJob(policy.corpus(), corpusId, policy, profile);
            })
            .toList();
    }

    @Override
    public void executeAll(List<CrawlJob> jobs) {
        int threads = Math.min(jobs.size(), props.concurrentCorpora());
        ExecutorService pool = Executors.newFixedThreadPool(threads,
            r -> new Thread(r, "corpus-crawl"));
        try {
            jobs.stream()
                .map(job -> pool.submit(() -> executeSingle(job)))
                .forEach(f -> { try { f.get(props.crawlTimeoutHours(), TimeUnit.HOURS); }
                                catch (Exception e) { throw new RuntimeException(e); } });
        } finally {
            pool.shutdownNow();
        }
    }

    @Override
    public void executeSingle(CrawlJob job) {
        // Delegate to whatever class currently triggers a crawl
        // (read CrawlController or IngestionJobService to find the right entry point)
    }
}
```

Add `CrawlProperties` record to `application-custom.yml`:
```yaml
geostat:
  ingestion:
    crawl:
      concurrentCorpora: ${CRAWL_CONCURRENT_CORPORA:2}
      crawlTimeoutHours: ${CRAWL_TIMEOUT_HOURS:6}
```

### Tests:

`DefaultCrawlOrchestratorTest`:
- `discoverJobs()` returns one job per `*-policy.yaml` file
- `executeAll()` respects `concurrentCorpora` limit (CountDownLatch test)
- `executeSingle()` calls the right crawl method

---

## Cross-cutting: zero hardcodes

- **No corpus names in Java** — only in YAML (`geostat-portal`, `census2024`, etc.)
- **No page kind strings in Java** — use `PageKind.NEWS`, `PageKind.DATASET` constants only
- **No URL patterns in Java** — only in `geostat-portal-parse.yaml`
- **All thresholds in `application-custom.yml`** with `${ENV_VAR:default}` pattern

---

## Acceptance criteria (overall)

| Criterion | Check |
|-----------|-------|
| New corpus = new YAML file, zero Java change | `census2024-policy.yaml` + `census2024-parse.yaml` → crawled automatically |
| New page kind = new YAML rule, zero Java change | Add rule to `pageKindRules` → detected |
| `NetworkPolicy.defaults()` = production-safe | `tlsVerify=true`, `respectRobotsTxt=true`, no auth |
| Staging corpus YAML with `network.tlsVerify: false` | TLS off — no code change |
| Existing single-corpus crawl unchanged | `geostat-portal` crawl behavior identical |
| All unit tests pass | Phase A + B + C tests green |
| No layer violations | ArchUnit `enrichmentMustNotAccessUrlFrontier` still passes |
