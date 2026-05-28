## YAML vs DB — კონფიგურაცია vs მონაცემი (junior must memorize this)

> **ეს section სავალდებულოა.** ნებისმიერი implementation-ის წინ წაიკითხე. ყოველი ფაილის ადგილი — YAML თუ DB — განსაზღვრულია. გადახვევა = architectural regression.

### პირველი პრინციპი: კონფიგურაცია vs მონაცემი

```
კონფიგურაცია (YAML)                    მონაცემი (DB)
────────────────────────────────        ──────────────────────────────────
HOW to crawl + parse                    WHAT was crawled
HOW to filter boilerplate               WHAT content was found
HOW to detect page kinds                WHAT page_kind each document has
HOW to expand user queries              WHAT documents/chunks/topics exist
HOW to evaluate corpus quality          WHAT quality scores were measured
```

**კონფიგურაცია** → YAML. DB-ში გადატანა = architecture violation.
**მონაცემი** → DB. YAML-ში = single truth violation.

### ყოველი YAML ფაილი — ზუსტი წესი

| ფაილი | ადგილი | DB sync? |
|-------|--------|----------|
| `*-parse.yaml` | YAML `ops/config/corpus/` | **არა** — ingestion behavior config; git = audit trail |
| `*-policy.yaml` | YAML `ops/config/corpus/` | **არა** — crawl policy; staging vs prod |
| `*-terminology.yaml` | YAML ახლა → `JdbcTerminologyRepository` 1000+ term-ზე | **ახლა არა** |
| `*-topic-catalog.yaml` | YAML — editorial overlay | **არა** — ML clusters ცალკეა DB-ში |
| `corpus-quality-gate.yaml` | YAML metric names only + Java beans for SQL | **არა** — ARCH-03 |

### Runtime reading flow — ზუსტად ასე

```
Startup (once)
  CorpusConfigurationLoader
    reads: *-parse.yaml         -> ParseProfile         (memory, per corpus)
    reads: *-policy.yaml        -> CorpusPolicy         (memory, per corpus)
    reads: *-terminology.yaml   -> List<TermEntry>       (memory, per corpus)
    reads: *-topic-catalog.yaml -> List<TopicEntry>     (memory, per corpus)
    reads: ingestion.corpus (DB) -> corpus_id lookup by name

Runtime — Crawl (ingestion-service)
  PageFetcher            -> fetch HTML
  PageKindDetector       -> ParseProfile.pageKindRules   (memory)
  JsoupContentExtractor  -> ParseProfile.removeSelectors (memory)
  DocumentRepository     -> document + page_kind          -> DB

Runtime — Chat API (backend)
  YamlTerminologyQueryExpander -> List<TermEntry>         (memory)
  DerivedCatalogReader         -> topic_cluster (DB, ML clusters)
  YamlTopicCatalog             -> List<TopicEntry>        (memory, editorial)
  CatalogResponseAssembler     -> merges DB clusters + YAML catalog
```

### DB ახალი column — V18 migration (BLOCKING, ARCH-07)

```sql
ALTER TABLE ingestion.document
  ADD COLUMN IF NOT EXISTS page_kind VARCHAR(64);
CREATE INDEX IF NOT EXISTS idx_document_page_kind
  ON ingestion.document (corpus_id, page_kind) WHERE page_kind IS NOT NULL;
UPDATE ingestion.document SET page_kind = 'unknown' WHERE page_kind IS NULL;
```

`page_kind` = `pageKindRules` YAML detection-ის result, per-document, stored in DB.
**ახალი ცხრილი ახლა არ სჭირდება.** V18 column only.
Future (owner approval): `catalog_topic` table (admin UI), `terminology` table (1000+ terms).

### topic_cluster (DB) vs topic-catalog.yaml — სავალდებელი განსხვავება

```
topic_cluster (DB)                       *-topic-catalog.yaml (YAML)
──────────────────────────────────       ──────────────────────────────────
ML k-means auto-discovered               Human-verified editorial catalog
Cluster labels = Gemini-generated        displayKa/En = live nav labels
URLs = heuristic from corpus             URLs = HTTP 200 verified
Updates automatically on :remine         Updates via git commit
DerivedCatalogReader reads this          YamlTopicCatalog reads this
```

ეს **არ არის დუბლიკაცია**. `CatalogResponseAssembler` merge-ავს ორივეს.
**არასდროს** ჩაანაცვლო ერთი მეორით.

### "YAML ჯერ DB-ში, შემდეგ DB-დან?" — პასუხი: **არა**

YAML config DB sync = uncontrolled runtime changes + unnecessary complexity.
YAML git-ში = version control + review + rollback.
**config-ისა და data-ის boundary sacred-ია.**
გამონაკლისი (ახლა არ ვაკეთებთ): admin UI runtime config edit → DB. ეს ახალი feature-ია, არა refactor.

---
ნებისმიერ fix-ზე სავალდებულოა:

| კანონი | მოთხოვნა |
|--------|---------|
| **No degradation** | ნებისმიერი ცვლილება უნდა **გააუმჯობეს** ან **შეინარჩუნოს** არსებული ხარისხი — არასდროს დაქვეიტო |
| **Port/layer integrity** | `ChatService` → domain ports (`DerivedCatalogReader`, `SpellFixer`) — არა infrastructure concrete classes |
| **Single truth** | DB/YAML/corpus → ერთი წყარო. Java-ში domain literals (URL slugs, person names) — **აკრძალული** |
| **No parallel paths** | `QueryRouter` deprecated კი, მაგრამ წაშლა მხოლოდ U07 stable-ის შემდეგ |
| **Test the fix** | ყოველ item-ს unit/smoke test უნდა ახლდეს |
| **Minimal diff** | fix scope minimum — drive-by refactor-ი აკრძალული |

---


---

## Architectural Decisions — New Kind / YAML Reload / Auto-Updates (junior must know)

> **ეს სამი კითხვა სავალდებულოა.** ნებისმიერი ნაბიჯი, რომელიც crawl-ს, config-ს, ან scheduling-ს ეხება — ჯერ ეს section-ი წაიკითხე.

---

### AD-01 — ახალი page kind → DB migration საჭიროა?

**პასუხი: არა. Zero DB migration.**

`document.page_kind` column-ი `VARCHAR(64)` — free-form string, არა enum, არა FK.
ნებისმიერი ახალი string value ავტომატურად ჩაიწერება.

**ოპერაცია — ახალი kind "press_release":**

```
ნაბიჯი 1 — YAML (სავალდებულო):
  ops/config/corpus/geostat-portal-parse.yaml
  pageKindRules-ში დაამატე:
    - kind: press_release
      urlPatterns: ["/ka/news/press-release/", "/en/news/press-release/"]
      priority: 12

ნაბიჯი 2 — Java (optional, მხოლოდ თუ ცალკე extraction logic სჭირდება):
  apps/ingestion-service/.../parse/strategy/GeostatPressReleaseExtractionStrategy.java
  @Component — supports("geostat-portal", "press_release")
  ExtractionStrategyRegistry auto-picks it up

ნაბიჯი 3 — DB migration: არა
  document.page_kind VARCHAR(64) — 'press_release' ჩაიწერება ავტომატურად
  ALTER TABLE: არ სჭირდება

ნაბიჯი 4 — quality gate (optional):
  ops/eval/corpus-quality-gate.yaml — ახალი gate entry
  apps/ingestion-service/.../quality/PressReleaseBoilerplateMetric.java — @Component bean
```

**რატომ VARCHAR და არა enum:**
`PageKind` Java class-ი string constants-ით (არა Java enum) — ეს **განზრახი design-ია**.
Enum = compile-time lock-in = DB `ALTER TABLE ADD VALUE` ყოველ ახალ kind-ზე.
String = open for extension, zero schema change. OCP დაცულია.

---

### AD-02 — YAML ცვლილება → რესტარტი და ხელახლა წამოღება

**ახლანდელი ქცევა (სწორია):**

```
git commit (YAML change) → CI build → deploy → service restart
        ↓
Spring Boot @PostConstruct
        ↓
CorpusConfigurationLoader.loadAll()
  reads: all *-parse.yaml     → ParseProfile  (memory, per corpus)
  reads: all *-policy.yaml    → CorpusPolicy  (memory, per corpus)
  reads: all *-terminology.yaml → List<TermEntry> (memory)
  reads: all *-topic-catalog.yaml → List<TopicEntry> (memory)
        ↓
CrawlOrchestrator.discoverJobs() — ახალი config-ით
```

**autoContinue: true — კრიტიკული:**

```yaml
# geostat-portal-policy.yaml
limits:
  autoContinue: true   # restart ≠ full recrawl
```

crawler4j frontier (visited URL queue) ინახება disk-ზე.
Restart → crawler გრძელდება იქიდან სადაც გაჩერდა.
Full recrawl = frontier-ის manual წაშლა (operator action).

**YAML ცვლილების შემდეგ re-ingestion — რა სჭირდება:**

| ცვლილება | re-crawl? | action |
|----------|-----------|--------|
| ახალი `pageKindRules` | არა | მხოლოდ ახლად crawled docs კლასიფიცირდება; ძველი `page_kind='unknown'` |
| `removeSelectors` / `boilerplateMarkers` | არა | `POST /corpus/geostat-portal/enrichment:backfill` — re-extracts without re-crawl |
| ახალი `seeds` ან `curatedUrls` | დიახ | re-crawl საჭიროა — ახალი URL-ები frontier-ში |
| `pageKindRules` + ძველი docs re-classify | partial | re-crawl affected URL subset ან admin script |
| `renderMode: static` → `headless` (SPA migration) | დიახ | full recrawl with Playwright fetcher |

**Hot reload — future, ახლა არ სჭირდება:**

```java
// Future option: @RefreshScope + FileWatcher (Spring Cloud Config)
// CorpusConfigurationLoader @RefreshScope → file change → reload without restart
// ახლა: restart = acceptable, no complexity overhead
```

---

### AD-03 — ავტომატური განახლებები: ახლა vs სწორი design

**ახლა — ყველაფერი manual:**

```
POST /corpus/geostat-portal/crawl:start           ← manual trigger
POST /corpus/geostat-portal/enrichment:backfill   ← manual trigger
POST /corpus/geostat-portal/authority:recompute   ← manual trigger
POST /corpus/geostat-portal/topics:remine         ← manual trigger
REFRESH MATERIALIZED VIEW ingestion.mv_*          ← manual SQL
```

**სწორი design — CrawlScheduler + event chain:**

```
Layer 1: Nightly Crawl
  @Scheduled(cron = "0 2 * * *")   ← ყოველ ღამე 02:00
  CrawlOrchestrator.executeAll()
    → autoContinue: true = incremental (new/changed pages only)
    → fires: CrawlCompletionEvent

Layer 2: Post-Crawl Hook Chain (CrawlCompletionEvent listener)
  EnrichmentBackfillJob.run(onlyMissing=true)   ← ახლად crawled docs only
  AuthorityRecomputeJob.run()
  MaterializedViewRefresher.refreshAll()
    REFRESH ingestion.mv_topic_summary
    REFRESH ingestion.mv_corpus_coverage

Layer 3: Weekly Deep Refresh
  @Scheduled(cron = "0 3 * * 0")   ← კვირაში ერთხელ, კვირადღე 03:00
  TopicRemineJob.run()
    → k-means on updated corpus
    → admin approval gate (does NOT auto-publish — manual confirm required)
```

**Java implementation skeleton:**

```java
// apps/ingestion-service/.../schedule/CrawlScheduler.java
@Component
@ConditionalOnProperty("geostat.ingestion.schedule.enabled", havingValue = "true")
public class CrawlScheduler {

    @Scheduled(cron = "${geostat.ingestion.schedule.crawlCron}")
    public void nightlyCrawl() {
        log.info("[scheduler] nightly crawl starting");
        orchestrator.executeAll(orchestrator.discoverJobs());
        // CrawlCompletionEvent fires → enrichment → MV refresh
    }

    @Scheduled(cron = "${geostat.ingestion.schedule.deepRemineCron}")
    public void weeklyTopicRemine() {
        log.info("[scheduler] weekly topic remine starting");
        topicRemineJob.run();
        // does NOT auto-publish — admin must approve
    }
}

// apps/ingestion-service/.../schedule/CrawlCompletionListener.java
@Component
public class CrawlCompletionListener implements ApplicationListener<CrawlCompletionEvent> {

    @Override
    public void onApplicationEvent(CrawlCompletionEvent event) {
        log.info("[post-crawl] corpus={} — starting enrichment chain", event.corpusName());
        enrichmentBackfillJob.run(event.corpusId(), true);  // onlyMissing=true
        authorityRecomputeJob.run(event.corpusId());
        mvRefresher.refreshAll();
    }
}
```

**`application-custom.yml`:**

```yaml
geostat:
  ingestion:
    schedule:
      enabled: false               # true in production; false in dev/test
      crawlCron: "0 2 * * *"       # nightly 02:00
      deepRemineCron: "0 3 * * 0"  # weekly Sunday 03:00
```

**YAML config change → auto pipeline (CI/CD):**

```
git push (YAML change)
  → CI: gradle build + tests
  → Docker image: new YAML baked in
  → CD: rolling restart (ingestion-service)
  → CorpusConfigurationLoader reads new YAML at startup
  → next @Scheduled crawl uses new config automatically
  → zero data migration needed
```

**Acceptance criteria (CrawlScheduler):**
- `schedule.enabled: false` → no `@Scheduled` beans created — dev mode safe
- `schedule.enabled: true` + `crawlCron` → nightly crawl fires at 02:00
- `CrawlCompletionEvent` fires → enrichment chain runs → MV refreshed
- Weekly remine does NOT auto-publish — admin approval required
- Unit tests: `@SpringBootTest` with `schedule.enabled: false` — no timers leak

---

---
