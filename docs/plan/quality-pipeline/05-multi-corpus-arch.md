## Multi-Corpus / Page-Kind / Network — Growth Architecture Backlog

> **ჯუნიორისთვის:** ეს 5 item ახლა blocking არ არის — Phase 1–7 შემდეგ. მაგრამ **ყოველი სტრიქონი სავალდებულოა**: ხედვა არ უნდა დაიკარგოს, ნებისმიერი implementation მხოლოდ ამ design-ის გაგრძელება უნდა იყოს. ოდნავი გადახვევა = architectural debt.

---

### ARCH-04 — `CrawlOrchestrator` port: N corpus parallel crawl 🟠

**პრობლემა — root cause:**
ახლა ingestion-service გაშვება = ერთი corpus-ის crawl. მეორე corpus-ისთვის ხელახლა გაშვება = serial, blocking, operator-dependent. სისტემა ვერ scale-დება მოწვევის გარეშე.

**ღრმა ანალიზი:**
`ops/config/corpus/` directory-ში ერთზე მეტი `*-policy.yaml` ფაილი შეიძლება არსებობდეს. `CorpusConfigurationLoader` უკვე per-corpus ლოგიკაა. DB `corpus_id` partition-ი უკვე არსებობს. **ინფრასტრუქტურა უკვე multi-corpus-ready**. რაც არ არსებობს — orchestration layer, რომელიც ყველა corpus-ს parallel-ად ამუშავებს.

**სწორი design:**

```
ops/config/corpus/
  geostat-portal-policy.yaml   → corpus "geostat-portal"
  census2024-policy.yaml       → corpus "census2024"        ← future
  cpi-policy.yaml              → corpus "cpi"               ← future

CrawlOrchestrator (port — libs/platform-contracts)
  DefaultCrawlOrchestrator (impl — ingestion-service)
    - auto-discovers all *-policy.yaml files at startup
    - creates one CrawlJob per corpus
    - runs jobs via ExecutorService with configurable thread pool
    - each job is fully isolated (own PageFetcher, own policy, own DB corpus_id)
```

**Resolution steps:**

**ნაბიჯი 1 — `CrawlJob` domain record `libs/platform-contracts`-ში:**

შექმენი:
`libs/platform-contracts/src/main/java/com/geostat/platform/crawl/CrawlJob.java`

```java
package com.geostat.platform.crawl;

import java.util.UUID;

/**
 * Immutable descriptor for a single corpus crawl run.
 * Created by CrawlOrchestrator from a *-policy.yaml file.
 */
public record CrawlJob(
    String    corpusName,   // e.g. "geostat-portal"
    UUID      corpusId,     // from DB — looked up by corpusName
    CorpusPolicy policy,    // loaded from *-policy.yaml
    ParseProfile profile    // loaded from *-parse.yaml (same corpus prefix)
) {}
```

**ნაბიჯი 2 — `CrawlOrchestrator` port `libs/platform-contracts`-ში:**

შექმენი:
`libs/platform-contracts/src/main/java/com/geostat/platform/crawl/CrawlOrchestrator.java`

```java
package com.geostat.platform.crawl;

import java.util.List;

/**
 * Port: discovers all configured corpora and orchestrates their crawl runs.
 * Implementations determine concurrency, retry, and scheduling policy.
 */
public interface CrawlOrchestrator {

    /**
     * Discover all corpus configurations in the configured directory
     * and return one CrawlJob per corpus.
     */
    List<CrawlJob> discoverJobs();

    /**
     * Execute all discovered jobs with the configured concurrency.
     * Blocks until all jobs complete (or timeout is reached).
     *
     * @param jobs  list of jobs to run (typically from discoverJobs())
     */
    void executeAll(List<CrawlJob> jobs);

    /**
     * Execute a single corpus crawl job synchronously.
     * Used for manual trigger via REST or CLI.
     */
    void executeSingle(CrawlJob job);
}
```

**ნაბიჯი 3 — `DefaultCrawlOrchestrator` ingestion-service-ში:**

შექმენი:
`apps/ingestion-service/src/main/java/com/geostat/ingestion/crawl/DefaultCrawlOrchestrator.java`

```java
@Component
public class DefaultCrawlOrchestrator implements CrawlOrchestrator {

    private final CorpusConfigurationLoader configLoader;
    private final CorpusRepository          corpusRepository;
    private final PageFetcher               fetcher;         // RoutingPageFetcher (ARCH-01)
    private final CrawlProperties           props;

    @Override
    public List<CrawlJob> discoverJobs() {
        // Scans ops/config/corpus/*-policy.yaml
        return configLoader.loadAllPolicies().stream()
            .map(policy -> {
                UUID corpusId = corpusRepository.findByName(policy.corpus())
                    .orElseThrow(() -> new IllegalStateException(
                        "No corpus registered for name: " + policy.corpus()));
                ParseProfile profile = configLoader.loadParseProfile(policy.corpus());
                return new CrawlJob(policy.corpus(), corpusId, policy, profile);
            })
            .toList();
    }

    @Override
    public void executeAll(List<CrawlJob> jobs) {
        int threads = Math.min(jobs.size(), props.getConcurrentCorpora());
        ExecutorService pool = Executors.newFixedThreadPool(threads,
            r -> new Thread(r, "corpus-crawl-" + Thread.currentThread().getId()));
        try {
            List<Future<?>> futures = jobs.stream()
                .map(job -> pool.submit(() -> executeSingle(job)))
                .toList();
            for (Future<?> f : futures) {
                f.get(props.getCrawlTimeoutHours(), TimeUnit.HOURS);
            }
        } catch (Exception e) {
            log.error("Corpus crawl failed", e);
            throw new CrawlExecutionException("One or more corpus crawls failed", e);
        } finally {
            pool.shutdownNow();
        }
    }

    @Override
    public void executeSingle(CrawlJob job) {
        log.info("[{}] Crawl starting — seeds: {}", job.corpusName(),
            job.policy().seeds());
        // Delegates to existing Crawler4j setup, parameterized with job.policy()
        // and job.profile()
        crawlRunner.run(job);
        log.info("[{}] Crawl complete", job.corpusName());
    }
}
```

**ნაბიჯი 4 — `CrawlProperties` `application-custom.yml`-ში:**

```yaml
geostat:
  ingestion:
    crawl:
      concurrentCorpora: 2          # max parallel corpus crawls
      crawlTimeoutHours: 6          # per-corpus max runtime
      corpusConfigDir: ops/config/corpus
```

```java
@ConfigurationProperties(prefix = "geostat.ingestion.crawl")
public record CrawlProperties(
    int    concurrentCorpora,
    int    crawlTimeoutHours,
    String corpusConfigDir
) {}
```

**ნაბიჯი 5 — `CorpusConfigurationLoader` update — `loadAllPolicies()` method:**

არსებული `CorpusConfigurationLoader`-ში დაამატე:

```java
/**
 * Discovers and loads all *-policy.yaml files from the corpus config directory.
 * New corpus = new YAML file — no code change required.
 */
public List<CorpusPolicy> loadAllPolicies() {
    Path dir = Paths.get(props.getCorpusConfigDir());
    try (Stream<Path> files = Files.list(dir)) {
        return files
            .filter(p -> p.getFileName().toString().endsWith("-policy.yaml"))
            .map(this::loadPolicyFromPath)
            .toList();
    } catch (IOException e) {
        throw new CorpusConfigException("Cannot scan corpus config dir: " + dir, e);
    }
}
```

**ნაბიჯი 6 — unit tests:**

```java
// DefaultCrawlOrchestratorTest:
@Test
void discoverJobs_returnsOneJobPerPolicyFile() {
    // given: two *-policy.yaml files in temp dir
    writeTempFile("geostat-portal-policy.yaml", GEOSTAT_POLICY_YAML);
    writeTempFile("census2024-policy.yaml",     CENSUS_POLICY_YAML);

    List<CrawlJob> jobs = orchestrator.discoverJobs();

    assertThat(jobs).hasSize(2);
    assertThat(jobs).extracting(CrawlJob::corpusName)
        .containsExactlyInAnyOrder("geostat-portal", "census2024");
}

@Test
void executeAll_runsJobsConcurrently_withinThreadLimit() {
    // given: 3 jobs, concurrentCorpora: 2
    // assert: max 2 threads active simultaneously (CountDownLatch-based test)
}

@Test
void executeSingle_logsStartAndComplete() {
    orchestrator.executeSingle(sampleJob());
    // verify crawlRunner.run() called with correct job
}
```

**Acceptance criteria:**
- `discoverJobs()` auto-discovers ALL `*-policy.yaml` files — zero code change for new corpus.
- `executeAll()` respects `concurrentCorpora` limit — never spawns more threads than configured.
- Existing single-corpus crawl behavior preserved — `executeSingle()` works identically.
- Adding `census2024-policy.yaml` + `census2024-parse.yaml` to `ops/config/corpus/` = census2024 corpus crawled next run.

---

### ARCH-05 — `NetworkPolicy`: virtual domain, staging, basic auth, DNS override 🟠

**პრობლემა — root cause:**
`Crawler4jStaticPageFetcher` (ARCH-01) hardcodes HTTP behavior: TLS verification always on, robots.txt always respected, no auth, real DNS only. Second site on virtual domain (staging, private network, self-signed cert) = crawl failure.

**ღრმა ანალიზი:**
Virtual domain სცენარები production-ში რეალურია:
- `staging.geostat.ge` — QA environment, self-signed cert, basic auth
- `internal.census.local` — private network, DNS not in public DNS
- `192.168.1.100` — IP-based, TLS invalid
- `docker-compose` service names — `http://app:8080` (CI integration test)

ყოველ სცენარს **ერთი YAML ფაილი** უნდა ახდენდეს კონფიგურირებას. კოდი არ უნდა შეიცვალოს.

**Resolution steps:**

**ნაბიჯი 1 — `NetworkPolicy` record `libs/platform-contracts`-ში:**

შექმენი:
`libs/platform-contracts/src/main/java/com/geostat/platform/crawl/NetworkPolicy.java`

```java
package com.geostat.platform.crawl;

import java.util.Map;

/**
 * Network-level fetch configuration for a corpus crawl.
 * Covers TLS, auth, DNS overrides, and robots.txt policy.
 * Loaded from the 'network' section of *-policy.yaml.
 *
 * <p>All fields are optional — defaults match production-safe behavior.
 */
public record NetworkPolicy(
    boolean             tlsVerify,        // default: true; false for self-signed certs
    boolean             respectRobotsTxt, // default: true; false for staging
    BasicAuthCredential basicAuth,        // null = no auth
    Map<String,String>  dnsOverrides,     // "hostname" -> "IP"; empty = real DNS
    String              userAgent         // null = FetchOptions default
) {
    /** Production-safe defaults — no overrides. */
    public static NetworkPolicy defaults() {
        return new NetworkPolicy(true, true, null, Map.of(), null);
    }

    public boolean hasBasicAuth() { return basicAuth != null; }
    public boolean hasDnsOverrides() { return !dnsOverrides.isEmpty(); }
}
```

```java
// BasicAuthCredential.java — same package
public record BasicAuthCredential(
    String username,
    String password   // must come from env var: "${ENV_VAR}" resolved at load time
) {
    public BasicAuthCredential {
        if (username == null || username.isBlank())
            throw new IllegalArgumentException("basicAuth.username must not be blank");
        if (password == null || password.isBlank())
            throw new IllegalArgumentException(
                "basicAuth.password must not be blank — use ${ENV_VAR} in YAML");
    }
}
```

**ნაბიჯი 2 — `FetchOptions`-ში `NetworkPolicy` field (ARCH-01 update):**

```java
public record FetchOptions(
    RenderMode    renderMode,
    int           timeoutMs,
    String        userAgent,
    NetworkPolicy network       // never null — use NetworkPolicy.defaults()
) {
    public static FetchOptions defaults() {
        return new FetchOptions(
            RenderMode.STATIC, 10_000, "GeostatBot/1.0", NetworkPolicy.defaults());
    }

    public static FetchOptions forProfile(ParseProfile profile) {
        NetworkPolicy net = profile.networkPolicy() != null
            ? profile.networkPolicy()
            : NetworkPolicy.defaults();
        RenderMode mode = profile.renderMode() != null
            ? profile.renderMode()
            : RenderMode.STATIC;
        return new FetchOptions(mode, 10_000, net.userAgent() != null
            ? net.userAgent() : "GeostatBot/1.0", net);
    }
}
```

**ნაბიჯი 3 — `Crawler4jStaticPageFetcher` reads `NetworkPolicy`:**

```java
@Override
public FetchedPage fetch(String url, FetchOptions options) throws PageFetchException {
    NetworkPolicy net = options.network();

    CrawlConfig config = new CrawlConfig();
    config.setUserAgentString(options.userAgent());

    // TLS verification
    if (!net.tlsVerify()) {
        config.setValidateCertificates(false);
        // or install accept-all TrustManager for OkHttp/Apache HttpClient
    }

    // robots.txt
    config.setRespectNoFollow(!net.respectRobotsTxt());
    // Note: crawler4j does not have a direct setRespectRobotsTxt —
    // subclass WebCrawler and override shouldVisit() to return true always
    // when net.respectRobotsTxt() == false

    // basic auth
    if (net.hasBasicAuth()) {
        config.addHeader("Authorization",
            basicAuthHeader(net.basicAuth().username(), net.basicAuth().password()));
    }

    // DNS overrides — custom HostnameVerifier + custom DNS resolver
    if (net.hasDnsOverrides()) {
        applyDnsOverrides(config, net.dnsOverrides());
        // Implementation: OkHttp Dns interface or custom HostnameVerifier
        // For crawler4j + Apache HttpClient:
        //   SystemDefaultDnsResolver extended with override map
    }

    // ... existing fetch logic ...
}

private String basicAuthHeader(String user, String pass) {
    String credentials = user + ":" + pass;
    return "Basic " + Base64.getEncoder()
        .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
}
```

**ნაბიჯი 4 — `CorpusConfigurationLoader` — `network` section პარსინგი:**

```java
// Inner YAML DTO:
public static class PolicyYaml {
    public String          corpus;
    public List<String>    seeds;
    public NetworkPolicyYaml network;
    // ... other fields

    public static class NetworkPolicyYaml {
        public boolean             tlsVerify        = true;
        public boolean             respectRobotsTxt = true;
        public BasicAuthYaml       basicAuth;        // null if absent
        public Map<String,String>  dnsOverrides     = Map.of();
        public String              userAgent;

        public NetworkPolicy toModel(Environment env) {
            BasicAuthCredential cred = basicAuth == null ? null :
                new BasicAuthCredential(
                    env.resolvePlaceholders(basicAuth.username),
                    env.resolvePlaceholders(basicAuth.password));
            return new NetworkPolicy(
                tlsVerify, respectRobotsTxt, cred, dnsOverrides, userAgent);
        }
    }

    public static class BasicAuthYaml {
        public String username;
        public String password;  // "${ENV_VAR}" — Spring Environment resolves
    }
}
```

**ნაბიჯი 5 — `*-policy.yaml`-ს ახალი `network` section:**

```yaml
# ops/config/corpus/geostat-portal-policy.yaml (production — defaults, no network section needed)
# defaults apply: tlsVerify=true, respectRobotsTxt=true, no auth

---

# ops/config/corpus/census2024-staging-policy.yaml (example — staging virtual domain)
corpus: census2024-staging

seeds:
  - https://internal.census.local/ka

hostPolicy:
  allowedHosts: [internal.census.local]
  subdomains:
    mode: list
    allow: []

network:
  tlsVerify: false                          # self-signed cert on staging
  respectRobotsTxt: false                   # staging robots.txt blocks all bots
  userAgent: "GeostatBot-staging/1.0"
  basicAuth:
    username: "${CENSUS_STAGING_USER}"      # env var — never hardcode credentials
    password: "${CENSUS_STAGING_PASS}"
  dnsOverrides:
    "internal.census.local": "192.168.1.100"

limits:
  maxDepth: 3
  maxPagesPerRun: 500
  rateLimitMs: 100
  respectRobotsTxt: false                   # redundant with network.respectRobotsTxt — both must be false
```

**ნაბიჯი 6 — unit tests:**

```java
// NetworkPolicyTest:
@Test
void defaults_areProdSafe() {
    NetworkPolicy net = NetworkPolicy.defaults();
    assertThat(net.tlsVerify()).isTrue();
    assertThat(net.respectRobotsTxt()).isTrue();
    assertThat(net.hasBasicAuth()).isFalse();
    assertThat(net.hasDnsOverrides()).isFalse();
}

// CorpusConfigurationLoaderTest:
@Test
void loadsNetworkPolicy_fromYaml() {
    CorpusPolicy policy = loader.loadPolicy("census2024-staging");
    assertThat(policy.network().tlsVerify()).isFalse();
    assertThat(policy.network().respectRobotsTxt()).isFalse();
    assertThat(policy.network().hasBasicAuth()).isTrue();
    assertThat(policy.network().basicAuth().username()).isEqualTo("test-user");
}

@Test
void basicAuthCredential_rejectsBlankPassword() {
    assertThatThrownBy(() -> new BasicAuthCredential("user", ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("${ENV_VAR}");
}

// Crawler4jStaticPageFetcherTest:
@Test
void setsBasicAuthHeader_whenNetworkPolicyHasCredentials() {
    FetchOptions opts = new FetchOptions(
        RenderMode.STATIC, 5000, "bot",
        new NetworkPolicy(true, true,
            new BasicAuthCredential("user", "pass"), Map.of(), null));
    // verify Authorization header set on crawler4j config
}
```

**Acceptance criteria:**
- `geostat-portal-policy.yaml`-ში `network` section არ არის → `NetworkPolicy.defaults()` გამოიყენება — behavior unchanged.
- `census2024-staging-policy.yaml`-ში `network.tlsVerify: false` → TLS validation off — no code change.
- Env var `${CENSUS_STAGING_USER}` unresolvable → `IllegalStateException` at startup (fail fast, not silent).
- Unit tests pass: defaults, YAML loading, basic auth header, blank password rejection.

---

### ARCH-06 — `PageKindDetector` port + URL-pattern + HTML-signal detectors 🟠

**პრობლემა — root cause:**
სისტემაში `page_kind` concept-ი არ არსებობს. ყველა document `page_kind = null`. Chat layer, extraction, quality gates ბრმა არიან page type-ის მიმართ — ერთი strategy ყველა page type-ისთვის = quality ceiling.

**ღრმა ანალიზი:**
geostat.ge-ზე მინიმუმ 5 განსხვავებული page type არსებობს სხვადასხვა DOM structure-ით:

| page_kind | URL pattern | DOM სტრუქტურა | extraction needs |
|---|---|---|---|
| `news` | `/single-news/` | `<article>`, `<h1>` in article | lead from article, sidebar excluded |
| `dataset` | `/open-data/`, `/modules/categories/` | table-heavy, `.value-databases-section` | aggressive table extraction |
| `publication` | `/publications/` | PDF embed, description | meta description as lead |
| `portal` | `/ka$`, `/en$` | landing page, no main content | lead = null |
| `category` | `/modules/categories/` with no data | nav structure | section_path only |

ამ განსხვავებების გარეშე `JsoupContentExtractor` ყველა page-ს ერთნაირად ექცევა.

**Resolution steps:**

**ნაბიჯი 1 — `PageKindRule` record `libs/platform-contracts`-ში:**

შექმენი:
`libs/platform-contracts/src/main/java/com/geostat/platform/crawl/PageKindRule.java`

```java
package com.geostat.platform.crawl;

import java.util.List;

/**
 * A single page-kind detection rule, loaded from YAML pageKindRules section.
 * URL patterns support both substring matching and full regex.
 */
public record PageKindRule(
    String       kind,          // e.g. "news", "dataset", "publication"
    List<String> urlPatterns,   // substring OR anchored regex ("^..." or "...$")
    int          priority       // higher = wins when multiple rules match
) {}
```

**ნაბიჯი 2 — `PageKind` constants class `libs/platform-contracts`-ში:**

შექმენი:
`libs/platform-contracts/src/main/java/com/geostat/platform/crawl/PageKind.java`

```java
package com.geostat.platform.crawl;

/**
 * Well-known page kind identifiers.
 * Not an enum — new kinds can be added in YAML without code change.
 * Java constants exist only for code that switches on known kinds.
 */
public final class PageKind {
    public static final String NEWS        = "news";
    public static final String DATASET     = "dataset";
    public static final String PUBLICATION = "publication";
    public static final String PORTAL      = "portal";
    public static final String CATEGORY    = "category";
    public static final String INFOGRAPHIC = "infographic";
    public static final String UNKNOWN     = "unknown";

    private PageKind() {}  // no instances
}
```

**ნაბიჯი 3 — `PageKindDetector` port `libs/platform-contracts`-ში:**

შექმენი:
`libs/platform-contracts/src/main/java/com/geostat/platform/crawl/PageKindDetector.java`

```java
package com.geostat.platform.crawl;

/**
 * Port: classifies a fetched page into a page kind.
 *
 * <p>Implementations may use URL patterns (fast, no parsing),
 * HTML signals (DOM meta tags, structured data), or a combination.
 * Must always return a non-null kind — use {@link PageKind#UNKNOWN} as fallback.
 */
public interface PageKindDetector {

    /**
     * Detect the page kind for the given fetched page.
     *
     * @param page    fetched page with URL and raw HTML
     * @param profile corpus parse profile (contains pageKindRules from YAML)
     * @return        page kind string — never null, never blank
     */
    String detect(FetchedPage page, ParseProfile profile);
}
```

**ნაბიჯი 4 — `UrlPatternPageKindDetector` ingestion-service-ში:**

შექმენი:
`apps/ingestion-service/src/main/java/com/geostat/ingestion/crawl/kind/UrlPatternPageKindDetector.java`

```java
@Component
public class UrlPatternPageKindDetector implements PageKindDetector {

    /**
     * URL pattern evaluation:
     *   - Pattern starting with "^" or ending with "$" → treated as full regex
     *   - Otherwise → substring containment (case-insensitive)
     * Multiple matching rules → highest priority wins.
     * Tie → first in list wins.
     */
    @Override
    public String detect(FetchedPage page, ParseProfile profile) {
        String url = page.url().toLowerCase();

        return profile.pageKindRules().stream()
            .filter(rule -> rule.urlPatterns().stream()
                .anyMatch(pattern -> matchesPattern(url, pattern)))
            .max(Comparator.comparingInt(PageKindRule::priority))
            .map(PageKindRule::kind)
            .orElse(PageKind.UNKNOWN);
    }

    private boolean matchesPattern(String url, String pattern) {
        if (pattern.startsWith("^") || pattern.endsWith("$")) {
            return url.matches(pattern.toLowerCase());
        }
        return url.contains(pattern.toLowerCase());
    }
}
```

**ნაბიჯი 5 — `HtmlSignalPageKindDetector` ingestion-service-ში:**

შექმენი:
`apps/ingestion-service/src/main/java/com/geostat/ingestion/crawl/kind/HtmlSignalPageKindDetector.java`

```java
@Component
public class HtmlSignalPageKindDetector implements PageKindDetector {

    /**
     * DOM-based fallback when URL patterns give no match.
     * Signal priority (highest to lowest):
     *   1. JSON-LD @type (most authoritative — publisher-declared)
     *   2. OpenGraph og:type
     *   3. Schema.org itemtype attribute
     *   4. HTML structural signals (article tag, table density)
     */
    @Override
    public String detect(FetchedPage page, ParseProfile profile) {
        Document doc = Jsoup.parse(page.html(), page.url());

        // 1. JSON-LD structured data — most authoritative
        String jsonLdKind = detectFromJsonLd(doc);
        if (!PageKind.UNKNOWN.equals(jsonLdKind)) return jsonLdKind;

        // 2. OpenGraph type
        String ogType = doc.select("meta[property=og:type]").attr("content").toLowerCase();
        if ("article".equals(ogType) || "newsarticle".equals(ogType)) return PageKind.NEWS;

        // 3. Schema.org itemtype
        String schemaType = doc.select("[itemtype]").attr("itemtype").toLowerCase();
        if (schemaType.contains("schema.org/dataset"))      return PageKind.DATASET;
        if (schemaType.contains("schema.org/newsarticle"))  return PageKind.NEWS;
        if (schemaType.contains("schema.org/article"))      return PageKind.NEWS;

        // 4. Structural signals — heuristic, lowest confidence
        if (doc.selectFirst("article") != null
            && doc.selectFirst("article h1") != null)   return PageKind.NEWS;
        if (countDataTables(doc) >= 2)                   return PageKind.DATASET;

        return PageKind.UNKNOWN;
    }

    private String detectFromJsonLd(Document doc) {
        for (Element el : doc.select("script[type=application/ld+json]")) {
            String json = el.html().toLowerCase();
            if (json.contains("\"newsarticle\"") || json.contains("\"article\""))
                return PageKind.NEWS;
            if (json.contains("\"dataset\""))
                return PageKind.DATASET;
            if (json.contains("\"webpage\"") && json.contains("\"breadcrumb\""))
                return PageKind.PORTAL;
        }
        return PageKind.UNKNOWN;
    }

    /** Count tables with 2+ columns and 3+ rows — indicates real data, not layout tables. */
    private int countDataTables(Document doc) {
        return (int) doc.select("table").stream()
            .filter(t -> t.select("tr").size() >= 3
                      && t.select("tr:first-child td, tr:first-child th").size() >= 2)
            .count();
    }
}
```

**ნაბიჯი 6 — `RoutingPageKindDetector` (primary bean) ingestion-service-ში:**

შექმენი:
`apps/ingestion-service/src/main/java/com/geostat/ingestion/crawl/kind/RoutingPageKindDetector.java`

```java
@Primary
@Component
public class RoutingPageKindDetector implements PageKindDetector {

    private final UrlPatternPageKindDetector urlDetector;
    private final HtmlSignalPageKindDetector htmlDetector;

    /**
     * Detection order:
     *   1. URL patterns — fast, no HTML parsing, highest precision
     *   2. HTML signals — DOM parse, used only if URL gives UNKNOWN
     *
     * Rationale: URL patterns are publisher-controlled (URL structure is stable).
     * HTML signals are fallback for pages with unpredictable URL schemes.
     */
    @Override
    public String detect(FetchedPage page, ParseProfile profile) {
        String kind = urlDetector.detect(page, profile);
        if (!PageKind.UNKNOWN.equals(kind)) {
            log.debug("[{}] page_kind={} (URL pattern)", page.url(), kind);
            return kind;
        }
        kind = htmlDetector.detect(page, profile);
        log.debug("[{}] page_kind={} (HTML signal)", page.url(), kind);
        return kind;
    }
}
```

**ნაბიჯი 7 — `geostat-portal-parse.yaml`-ში `pageKindRules` section (ARCH-06-ის main deliverable):**

```yaml
# ops/config/corpus/geostat-portal-parse.yaml — append to existing file:

pageKindRules:
  # Higher priority = wins when multiple patterns match.
  # Pattern starting with ^ or ending with $ = full regex. Otherwise = substring.

  - kind: news
    urlPatterns:
      - "/single-news/"
      - "/ka/single-news/"
      - "/en/single-news/"
    priority: 10

  - kind: dataset
    urlPatterns:
      - "/ka/open-data/"
      - "/en/open-data/"
    priority: 10

  - kind: publication
    urlPatterns:
      - "/ka/publications/"
      - "/en/publications/"
      - "/ka/news/statistikuri-publikacia/"
      - "/en/news/statistikuri-publikacia/"
    priority: 10

  - kind: infographic
    urlPatterns:
      - "/ka/infographics/"
      - "/en/infographics/"
      - "section.infographic"
    priority: 10

  - kind: portal
    urlPatterns:
      - "^https://www\\.geostat\\.ge/ka$"
      - "^https://www\\.geostat\\.ge/en$"
      - "^https://www\\.geostat\\.ge/$"
    priority: 20   # exact match — highest priority

  - kind: category
    urlPatterns:
      - "/modules/categories/"
    priority: 5    # lowest — dataset rules override where patterns overlap

  default: unknown
```

**ნაბიჯი 8 — `ParseProfile` record-ში ახალი fields:**

`libs/platform-contracts/.../parse/ParseProfile.java`:

```java
public record ParseProfile(
    String              corpus,
    List<String>        rootSelectors,
    String              rootSelectorStrategy,   // "firstMatch" | "all" (default: firstMatch)
    List<String>        removeSelectors,
    BoilerplateMarkers  boilerplateMarkers,
    boolean             stripLeading,
    boolean             stripTrailing,
    boolean             extractTables,
    boolean             preserveHeadings,
    LanguageConfig      language,
    RenderMode          renderMode,             // ARCH-01
    NetworkPolicy       networkPolicy,          // ARCH-05 (nullable — defaults apply)
    List<PageKindRule>  pageKindRules,          // ARCH-06
    String              defaultPageKind         // fallback if no rule matches (default: "unknown")
) {
    public ParseProfile {
        if (renderMode     == null) renderMode     = RenderMode.STATIC;
        if (networkPolicy  == null) networkPolicy  = NetworkPolicy.defaults();
        if (pageKindRules  == null) pageKindRules  = List.of();
        if (defaultPageKind == null) defaultPageKind = PageKind.UNKNOWN;
        if (rootSelectorStrategy == null) rootSelectorStrategy = "firstMatch";
    }
}
```

**ნაბიჯი 9 — unit tests:**

```java
// UrlPatternPageKindDetectorTest:
@Test
void detects_news_bySingleNewsPattern() {
    FetchedPage page = new FetchedPage(
        "https://www.geostat.ge/ka/single-news/1756/...", "", 200, "text/html", RenderMode.STATIC);
    String kind = detector.detect(page, profileWithGeostatRules());
    assertThat(kind).isEqualTo(PageKind.NEWS);
}

@Test
void detects_portal_byExactUrlRegex() {
    FetchedPage page = new FetchedPage(
        "https://www.geostat.ge/ka", "", 200, "text/html", RenderMode.STATIC);
    String kind = detector.detect(page, profileWithGeostatRules());
    assertThat(kind).isEqualTo(PageKind.PORTAL);
}

@Test
void returns_unknown_forUnrecognizedUrl() {
    FetchedPage page = new FetchedPage(
        "https://www.geostat.ge/ka/some-unknown-path/123", "", 200, "text/html", RenderMode.STATIC);
    assertThat(detector.detect(page, profileWithGeostatRules())).isEqualTo(PageKind.UNKNOWN);
}

@Test
void highPriorityRule_winsOverLowPriority_whenBothMatch() {
    // URL matches both /modules/categories/ (priority 5, kind=category)
    // and /ka/open-data/ (priority 10, kind=dataset)
    FetchedPage page = new FetchedPage(
        "https://www.geostat.ge/ka/open-data/modules/categories/123", "", 200, "text/html", RenderMode.STATIC);
    assertThat(detector.detect(page, profileWithGeostatRules())).isEqualTo(PageKind.DATASET);
}

// HtmlSignalPageKindDetectorTest:
@Test
void detects_news_fromOgTypeArticle() {
    String html = "<html><head><meta property='og:type' content='article'/></head></html>";
    FetchedPage page = new FetchedPage("https://x.ge/unknown-path", html, 200, "text/html", RenderMode.STATIC);
    assertThat(detector.detect(page, emptyProfile())).isEqualTo(PageKind.NEWS);
}

@Test
void detects_dataset_fromJsonLd() {
    String html = """
        <html><head><script type='application/ld+json'>
          {"@type": "Dataset", "name": "CPI Data"}
        </script></head></html>""";
    FetchedPage page = new FetchedPage("https://x.ge/unknown-path", html, 200, "text/html", RenderMode.STATIC);
    assertThat(detector.detect(page, emptyProfile())).isEqualTo(PageKind.DATASET);
}

// RoutingPageKindDetectorTest:
@Test
void prefersUrlPattern_overHtmlSignal() {
    // URL matches news, HTML has og:type=webpage (would give portal)
    // URL pattern must win
    FetchedPage page = new FetchedPage(
        "https://www.geostat.ge/ka/single-news/1", htmlWithOgWebpage(), 200, "text/html", RenderMode.STATIC);
    assertThat(router.detect(page, profileWithGeostatRules())).isEqualTo(PageKind.NEWS);
}
```

**ახალი kind-ის დამატება — ოპერაცია (zero code change):**

```yaml
# geostat-portal-parse.yaml — YAML მხოლოდ
pageKindRules:
  - kind: press_release          # ← ახალი
    urlPatterns:
      - "/ka/news/press-release/"
      - "/en/news/press-release/"
    priority: 12
```

```java
// GeostatPressReleaseExtractionStrategy.java — ახალი @Component მხოლოდ
@Component
public class GeostatPressReleaseExtractionStrategy implements ExtractionStrategy {
    @Override
    public boolean supports(String corpus, String pageKind) {
        return "geostat-portal".equals(corpus) && "press_release".equals(pageKind);
    }
    // ...
}
```

**Acceptance criteria:**
- `UrlPatternPageKindDetector` unit tests pass — all 4 scenarios above.
- `HtmlSignalPageKindDetector` unit tests pass — og:type + JSON-LD.
- `RoutingPageKindDetector` — URL pattern wins over HTML signal.
- `geostat-portal-parse.yaml`-ში ახალი rule = ახალი kind detected — zero Java change.
- `ParseProfile` default fields: `renderMode=STATIC`, `networkPolicy=defaults`, `defaultPageKind=unknown`.

---

### ARCH-07 — `document.page_kind` column: Flyway V18 migration 🟠

**პრობლემა — root cause:**
ARCH-06-ის დეტექტირებული `page_kind` სად ინახება? ახლა — `document` ცხრილს `page_kind` column არ აქვს. Detection result-ი იკარგება — ყოველ crawl-ზე ხელახლა detect, quality gates ვერ ფილტრავენ page-kind-ით.

**Resolution steps:**

**ნაბიჯი 1 — Flyway V18 migration:**

შექმენი:
`apps/ingestion-service/src/main/resources/db/migration/V18__add_document_page_kind.sql`

```sql
-- V18: add page_kind to document for per-kind extraction strategy routing
-- and quality gate filtering.

ALTER TABLE ingestion.document
  ADD COLUMN IF NOT EXISTS page_kind VARCHAR(64);

COMMENT ON COLUMN ingestion.document.page_kind IS
  'Content type of the page: news | dataset | publication | portal | category | infographic | unknown. '
  'Detected by PageKindDetector at crawl time. NULL = not yet detected (pre-V18 rows).';

-- Index for per-kind quality queries and chat catalog filtering
CREATE INDEX IF NOT EXISTS idx_document_page_kind
  ON ingestion.document (corpus_id, page_kind)
  WHERE page_kind IS NOT NULL;

-- Backfill existing rows to "unknown" (will be re-detected on next crawl)
UPDATE ingestion.document
SET    page_kind = 'unknown'
WHERE  page_kind IS NULL;
```

**ნაბიჯი 2 — `Document` domain entity update:**

`apps/ingestion-service/src/main/java/com/geostat/ingestion/domain/Document.java`-ში დაამატე:

```java
public record Document(
    // ... existing fields ...
    String pageKind   // nullable until ARCH-06 detection runs; "unknown" after V18 backfill
) {
    public boolean isPageKindKnown() {
        return pageKind != null && !PageKind.UNKNOWN.equals(pageKind);
    }
}
```

**ნაბიჯი 3 — `DocumentRepository` / JDBC update:**

`insertDocument()` + `updateDocument()` ყველა SQL-ში დაამატე `page_kind` column-ი:

```java
// insert:
"""
INSERT INTO ingestion.document
  (..., page_kind)
VALUES
  (..., ?)
"""

// update (crawl re-run):
"""
UPDATE ingestion.document
SET    page_kind = ?
WHERE  id = ?
"""
```

**ნაბიჯი 4 — `CrawlPipeline`-ში `page_kind` set:**

Wherever the document is persisted after fetch (before or after `JsoupContentExtractor`):

```java
String pageKind = pageKindDetector.detect(fetchedPage, parseProfile);
Document doc = documentBuilder.build(fetchedPage)
    .withPageKind(pageKind);  // or .toBuilder().pageKind(pageKind).build()
documentRepository.upsert(doc);
```

**ნაბიჯი 5 — Quality gate update (ARCH-03 `QualityMetric` per kind):**

ახლა `BoilerplateRatioMetric`-ი global-ია. Page-kind-aware version:

```java
@Component("boilerplate_ratio_news")
public class NewsBoilerplateRatioMetric implements QualityMetric {
    @Override public String id() { return "boilerplate_ratio_news"; }
    @Override public double compute(UUID corpusId) {
        return jdbc.queryForObject("""
            SELECT SUM(CASE WHEN content_text ILIKE '%adapted version%'
                       THEN 1 ELSE 0 END)::float / NULLIF(COUNT(*), 0)
            FROM ingestion.document
            WHERE corpus_id = ? AND fetch_status = 'parsed' AND page_kind = 'news'
            """, Double.class, corpusId);
    }
}
```

```yaml
# corpus-quality-gate.yaml:
gates:
  - metric: boilerplate_ratio_news
    description: "Boilerplate ratio in news pages only"
    target: "<= 0.02"      # stricter for news — news content should be clean
    blocks: [enrichment_backfill]
```

**Acceptance criteria:**
- V18 migration runs without error on existing DB.
- `idx_document_page_kind` index created.
- Existing rows backfilled to `page_kind = 'unknown'`.
- New documents inserted by ARCH-06 have correct `page_kind` value.
- `document.isPageKindKnown()` returns `false` for "unknown", `true` for "news" etc.

---

### ARCH-08 — `pageKindRules` + `rootSelectorStrategy` in `geostat-portal-parse.yaml` 🟡

**პრობლემა — root cause:**
`geostat-portal-parse.yaml`-ში ამ ორი field-ის არარსებობა ნიშნავს:
1. ARCH-06 detector-ი გაშვების შემდეგ ყველა page-ი `unknown` რჩება — rules-ი YAML-ში არ არის.
2. `rootSelectors` priority-ი undefined — `JsoupContentExtractor` ambiguity (გარჩეული ადრე).

ეს item ARCH-06-ის deliverable-ია, მაგრამ **ცალკე tracking-ი ჭირდება** — YAML ცვლილება deploy-ი ცალკე, Java build-ი ცალკე.

**Resolution steps:**

`ops/config/corpus/geostat-portal-parse.yaml`-ში დაამატე ბოლოში:

```yaml
# --- ARCH-08 additions ---

rootSelectorStrategy: firstMatch
# Evaluation: top-to-bottom, first matching selector wins.
# "all" would cause duplication when multiple selectors exist on one page.

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

  default: unknown

# language fallback — ARCH assessment fix
language:
  inferFrom: [htmlLang, urlSegment, metaContentLanguage]
  defaultFallback: ka    # never null — ka is dominant language on geostat.ge

# render mode — ARCH-01
renderMode: static       # change to "headless" when SPA migration completes
```

**Acceptance criteria:**
- `CorpusConfigurationLoader` parses `rootSelectorStrategy` without exception.
- `CorpusConfigurationLoader` parses `pageKindRules` list — 6 rules loaded for `geostat-portal`.
- `language.defaultFallback: ka` loaded — `ParseProfile.language().defaultFallback()` == "ka".
- `renderMode: static` loaded — `ParseProfile.renderMode()` == `RenderMode.STATIC`.
- ARCH-06 `UrlPatternPageKindDetector` uses these rules — no hardcoded rules in Java.

---


---
