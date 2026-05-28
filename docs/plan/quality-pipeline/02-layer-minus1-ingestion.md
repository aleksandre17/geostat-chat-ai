## Layer -1 — ingestion-service (crawl / parse / enrich)

> **Session 2026-05-27:** L-1-01 (parser selectors, HtmlContentCleaner, parse YAML), L-1-04 (PolicyUrlFilter query-string fix, LinkDiscoverer → RoutingUrlFilter, policy YAML), L-1-06 (enrichment_version increment) implemented.
> L-1-02, L-1-05: operational — require running services. L-1-07: plan item only, pending architecture approval.

ეს Layer ყველაფრის საფუძველია. სანამ L0–L5 fixes-ს ვაკეთებთ, ეს უნდა გამოსწორდეს.

**Enrichment pipeline sequence (სავალდებულო თანმიმდევრობა):**
```
L-1-01 (parser fix)
    ↓
L-1-03 (boilerplate filter in parse profile)
    ↓
L-1-04 (pagination dedup in corpus policy)
    ↓
L-1-02 (enrichment:backfill — Gemini + YAKE + page_kind)
    ↓
L-1-05 (authority:recompute — PageRank)
    ↓
L-1-06 (topics:remine — K-means + Gemini labels)
    ↓
MV REFRESH (სამივე MV)
```

---

### L-1-01 — `lead_text` cross-contamination: `aside`/`.related`/`.sidebar` არ იშლება 🔴

**Root cause (დადასტურებული):**
`DefaultParseProfile.REMOVE_SELECTORS`-ში `aside`, `.related`, `.sidebar`, `.related-articles` **არ არის**. `PageDisplayMetadataExtractor.extractLeadParagraph()` root-ს ირჩევს `main, article, [role=main]`-ს, მაგრამ ამ root-ში ზოგჯერ related-articles sidebar ხვდება — პირველი prose `<p>` სხვა article-ის title-ია.

**ზუსტი flow:**
```
JsoupContentExtractor.extract()
  → profile.removeSelectors() → remove from clone
  → PageDisplayMetadataExtractor.extract()
     → extractLeadParagraph()
        → root = main/article/[role=main]
        → root.select("p") — first with length≥40 + looksLikeProse()
        → but aside/.related still in root DOM  ← BUG HERE
```

**Profile architecture context (სავალდებულო წაკითხვა):**
სისტემა YAML-driven-ია: `ops/config/corpus/geostat-portal-parse.yaml` → `CorpusConfigurationLoader` → `ParseProfile`. parse profile YAML-ი `ops/config/corpus/`-ში გეგმით, მაგრამ ეს directory gitignored-ია. fallback: `DefaultParseProfile.GEOSTAT_PORTAL` Java constant.

ამიტომ fix **ორ ადგილში** მიდის:

**Resolution steps:**

**ნაწილი A — `DefaultParseProfile.java` (code fallback):**

1. გახსენი:
   `apps/ingestion-service/src/main/java/com/geostat/ingestion/parse/profile/DefaultParseProfile.java`

2. `REMOVE_SELECTORS` list-ში (ახლა: `"nav"`, `"footer"`, `"header"`, `".rightbar-wrapper"`, `".pagination"` და სხვა) დაამატე:
   ```java
   "aside",
   ".related",
   ".related-articles",
   "[class*='related']",
   ".sidebar",
   "[class*='sidebar']",
   ".recommended",
   "[class*='recommended']"
   ```

3. გახსენი:
   `apps/ingestion-service/src/main/java/com/geostat/ingestion/parse/HtmlContentCleaner.java`

   `legacyClean()` method-ში (profile disabled path):
   ```java
   // BEFORE:
   clone.select("script, style, nav, footer, header, noscript, iframe, svg").remove();
   // AFTER:
   clone.select("script, style, nav, footer, header, noscript, iframe, svg, " +
       "aside, .related, .related-articles, [class*='related'], " +
       ".sidebar, [class*='sidebar']").remove();
   ```

**ნაწილი B — parse profile YAML + profile enable (architectural fix):**

4. შექმენი `ops/config/corpus/geostat-portal-parse.yaml`:

   **მნიშვნელოვანი:** `ops/config/corpus/` directory gitignored-ია (`ops/config/*` in `.gitignore`). parse profiles **სეკრეტი არ არის** — ეს config-ია. შექმენი committed YAML:
   ```
   apps/ingestion-service/src/main/resources/parse-profiles/geostat-portal-parse.yaml
   ```
   და `CorpusConfigurationLoader.loadParseProfile()` classpath-საც შეამოწმე (ახლა მხოლოდ filesystem). **ან** უფრო მარტივი: `ops/config/corpus/` gitignore-იდან exclude-ი parse profile YAML-ებისთვის:
   ```
   # .gitignore-ში:
   ops/config/*
   !ops/config/corpus/
   ops/config/corpus/*.env
   ops/config/corpus/*.key
   ```
   ეს ნიშნავს: `ops/config/corpus/geostat-portal-parse.yaml` commit-ში მოხვდება, `.env`/`.key` ფაილები — არა.

5. `ops/config/corpus/geostat-portal-parse.yaml` შიგთავსი:
   ```yaml
   corpus: geostat-portal
   rootSelectors:
     - ".value-databases-section"
     - ".archive-section"
     - ".news-section"
     - "main"
     - "article"
     - "[role=main]"
     - ".content-area"
     - "#content"
   removeSelectors:
     - "script"
     - "style"
     - "noscript"
     - "iframe"
     - "svg"
     - "nav"
     - "footer"
     - "header"
     - "aside"
     - ".related"
     - ".related-articles"
     - "[class*='related']"
     - ".sidebar"
     - "[class*='sidebar']"
     - ".recommended"
     - ".rightbar-wrapper"
     - ".fixed-contact-blocks"
     - ".social-static-icons"
     - ".pagination"
     - ".accessibility-notice"
     - ".cookie-banner"
     - ".social-share"
     - ".breadcrumb"
     - "geostat-chat-widget"
     - "#skip-to-content"
   boilerplateMarkers:
     ka:
       - "უკან დაბრუნება"
       - "სრულად ნახვა"
       - "არქივი"
       - "გამოიწერეთ სიახლეები"
       - "თარიღი:"
       - "გადმოწერა"
     en:
       - "skip to content"
       - "read more"
       - "archive"
       - "Date:"
       - "Download"
       - "CSV Download"
     containsKa:
       - "ვებგვერდის ადაპტირებული ვერსია"
       - "საქსტატის ოფიციალური ვებგვერდი"
       - "Crafted by"
     containsEn:
       - "adapted version of the website"
       - "official website of geostat"
       - "Crafted by"
       - "Form #1"
       - "Form #2"
   language:
     inferFrom:
       - "htmlLang"
       - "urlSegment"
       - "metaContentLanguage"
   ```

6. **Enable profile system** — `apps/ingestion-service/src/main/resources/application-custom.yml`-ში:
   ```yaml
   geostat:
     ingestion:
       parse:
         profile:
           enabled: true
         config-dir: ops/config/corpus
   ```

**ნაწილი C — unit test:**

7. შექმენი ან განაახლე `PageDisplayMetadataExtractorTest`:
   ```java
   @Test
   void leadText_doesNotPickRelatedArticleTitle() {
       String html = """
           <html><body><main>
             <article><p>მთავარი სტატიის ტექსტი, რომელიც საკმარისად გრძელია.</p></article>
             <aside class="related-articles">
               <p>ცხოვრების დონის მაჩვენებლები (სიღარიბის მაჩვენებლები) - 2025</p>
             </aside>
           </main></body></html>""";
       DisplayMetadata meta = extractor.extract(
           Jsoup.parse(html), "მთავარი სტატიის", List.of());
       assertThat(meta.leadText()).contains("მთავარი სტატიის ტექსტი");
       assertThat(meta.leadText()).doesNotContain("სიღარიბის მაჩვენებლები");
   }
   ```

**ფაილები:**
- `apps/ingestion-service/src/main/java/com/geostat/ingestion/parse/profile/DefaultParseProfile.java`
- `apps/ingestion-service/src/main/java/com/geostat/ingestion/parse/HtmlContentCleaner.java`
- `.gitignore` (parse profiles commit-ში)
- New: `ops/config/corpus/geostat-portal-parse.yaml`
- `apps/ingestion-service/src/main/resources/application-custom.yml`

**Acceptance criteria:**
```sql
SELECT COUNT(DISTINCT lead_text)::float / COUNT(*) AS uniqueness_ratio
FROM ingestion.document
WHERE lead_text IS NOT NULL AND language = 'ka';
-- target: ≥ 0.90
```
unit test გადადის. `INGESTION_PARSE_PROFILE_ENABLED=true` production-ში.

---

### L-1-02 — enrichment pipeline გასაშვებია 3 889 document-ზე 🔴

**Root cause (დადასტურებული):**
`geostat.ingestion.enrichment.enabled` default-ი **`false`**. `DocumentPostPersistPipeline` enrichment-ს trigger-ს არ აკეთებს. ყველა document-ს `enrichment_version=0`.

**Sequence — ზუსტი REST calls (ingestion-service):**

1. **ჯერ L-1-01 და L-1-03 — parser fix** (`lead_text` + boilerplate).

2. **Enable enrichment** `application.yml`/`application-custom.yml`-ში:
   ```yaml
   geostat:
     ingestion:
       enrichment:
         enabled: true
   ```
   ან per-request: backfill endpoint თვითონ enriches, `enabled` flag-ი მხოლოდ per-document trigger-სთვის საჭიროა.

3. **Enrichment backfill** (Gemini + YAKE + page_kind — entities **გამოტოვდება** backfill-ში, ეს ნორმალურია):
   ```http
   POST /corpus/geostat-portal/enrichment:backfill
   Content-Type: application/json
   {"onlyMissing": true, "limit": 5000}
   ```
   პროგრესი: `GET /corpus/geostat-portal/enrichment/status`

   **მნიშვნელოვანი:** `onlyMissing=true` ნიშნავს — enriches only docs missing completed **summary OR page_kind** runs. `false` — ყველა. პირველ run-ზე გამოიყენე `false` (ყველა unenriched).

4. **Authority recompute** (PageRank — enrichment-ის შემდეგ, რადგან navigation pages-ი page_kind-ით განისაზღვრება):
   ```http
   POST /corpus/geostat-portal/authority:recompute
   ```
   ეს `JGraphTPageRankAuthorityDeriver`-ს გაუშვებს: parent→child graph, 0.85 damping, freshness decay, min-max normalize.

5. **Verify:**
   ```sql
   SELECT page_kind, COUNT(*) FROM ingestion.document
   GROUP BY page_kind ORDER BY COUNT(*) DESC;
   -- page_kind='unknown' < 10% target
   SELECT COUNT(*) FROM ingestion.document WHERE authority_score > 0;
   -- > 3000 target
   ```

**ფაილები:**
- `apps/ingestion-service/src/main/java/com/geostat/ingestion/enrichment/runner/EnrichmentBackfillService.java`
- `apps/ingestion-service/src/main/java/com/geostat/ingestion/enrichment/DocumentEnrichmentOrchestrator.java`
- `apps/ingestion-service/src/main/java/com/geostat/ingestion/enrichment/authority/JGraphTPageRankAuthorityDeriver.java`
- `apps/ingestion-service/src/main/java/com/geostat/ingestion/api/CorpusController.java` (endpoints)

**Acceptance criteria:**
- `page_kind = 'unknown'` < 10%.
- `authority_score = 0.0` < 10%.
- `summary_ka IS NULL` < 20% (navigation/unknown pages-ი ნორმალურია).

---

### L-1-03 — boilerplate: download/date patterns `MarkerBoilerplateStripper`-ში არ არის 🟠

**Root cause (დადასტურებული):**
`MarkerBoilerplateStripper.isBoilerplateParagraph()` markers-ს `ParseProfile.boilerplateMarkers()`-იდან იღებს. `DefaultParseProfile.GEOSTAT_PORTAL`-ის `STARTS_WITH_KA`/`STARTS_WITH_EN` სიები **არ შეიცავს** download/date patterns-ს. შედეგად chunk-ებში ხვდება:
```
"Date: 16 February 2026 Download"
"CSV Download Cover Form #1 Form #2"
```

**ეს item L-1-01-ის ნაწილია.** `geostat-portal-parse.yaml`-ში (L-1-01 step 5) `boilerplateMarkers` სექცია უკვე შეიცავს `"თარიღი:"`, `"გადმოწერა"`, `"Date:"`, `"Download"`, `"CSV Download"`, `"Form #1"`, `"Form #2"` — **ცალკე code change საჭირო არ არის.**

`corpus-quality-gate.yaml`-ში `boilerplate_ratio` gate-ი ამ პრობლემას tracking-ს უკეთებს:
```yaml
target: "<= 0.05"
currentBaseline: 0.86   ← 86% docs contain boilerplate
blocks: [enrichment_backfill]
```

**L-1-01 დასრულების შემდეგ verify:**
```sql
SELECT COUNT(*) FROM ingestion.chunk
WHERE text ILIKE '%Date: %Download%'
   OR text ILIKE '%CSV Download%'
   OR text ILIKE 'Form #%';
-- target after re-parse: 0
```
`corpus-quality-gate.yaml`-ის `boilerplate_ratio` gate: `<= 0.05` (ახლა 0.86).

---

### L-1-04 — pagination dedup: `PolicyUrlFilter` path-only + `LinkDiscoverer` legacy bypass 🟠

**Root cause (დადასტურებული — ორი bug):**

**Bug A:** `PolicyUrlFilter.shouldEnqueue()` → `pathAllowed()` — `uri.getPath()` query string-ს არ შეიცავს. `?page=2` invisible to filter.

**Bug B:** `LinkDiscoverer.java` — `corpusPolicy.isUrlAllowed(url)` legacy CorpusPolicy-ს გამოიყენებს, `RoutingUrlFilter`-ს კი — არა. YAML `excludePatterns` LinkDiscoverer-ზე არ მოქმედებს.

**Resolution steps:**

**ნაწილი A — `PolicyUrlFilter.shouldEnqueue()` fix:**

1. გახსენი:
   `apps/ingestion-service/src/main/java/com/geostat/ingestion/parse/profile/PolicyUrlFilter.java`

2. `shouldEnqueue()` method-ში host check-ის შემდეგ, `pathAllowed()` call-ის ნაცვლად:
   ```java
   // BEFORE: return pathAllowed(uri.getPath(), policy);

   // AFTER — check excludePatterns against path + query string:
   String fullPath = uri.getRawPath()
       + (uri.getRawQuery() != null ? "?" + uri.getRawQuery() : "");
   for (String pattern : policy.excludePatterns()) {
       if (pattern != null && !pattern.isBlank() && fullPath.contains(pattern)) {
           return false;
       }
   }
   if (policy.includePatterns().isEmpty()) return true;
   for (String pattern : policy.includePatterns()) {
       if (pattern != null && !pattern.isBlank() && fullPath.contains(pattern)) {
           return true;
       }
   }
   return false;
   ```

   `pathAllowed()` static method-ი ტესტებიდან გამოიყენება — შეინარჩუნე, მაგრამ `shouldEnqueue()`-ს გადაუყვანე ახალ logic-ზე.

**ნაწილი B — `LinkDiscoverer` → `RoutingUrlFilter`:**

3. გახსენი:
   `apps/ingestion-service/src/main/java/com/geostat/ingestion/crawl/LinkDiscoverer.java`

4. Constructor-ში inject `RoutingUrlFilter`:
   ```java
   private final RoutingUrlFilter routingUrlFilter;
   // constructor-ში დაამატე parameter
   ```

5. URL check შეცვალე:
   ```java
   // BEFORE: if (!corpusPolicy.isUrlAllowed(url)) continue;
   // AFTER:
   if (!routingUrlFilter.shouldEnqueue(url, corpusEntity)) continue;
   ```

**ნაწილი C — `geostat-portal-policy.yaml` (L-1-01-ის ნაწილი):**

6. `ops/config/corpus/geostat-portal-policy.yaml`:
   ```yaml
   corpus: geostat-portal
   seeds:
     - "https://www.geostat.ge/ka"
     - "https://www.geostat.ge/en"
   hostPolicy:
     allowedHosts: ["www.geostat.ge", "geostat.ge"]
     subdomains:
       mode: list
       allow: ["br.geostat.ge", "sna.geostat.ge"]
   excludePatterns:
     - "?page="
     - "&page="
     - "/login"
     - "/admin"
     - "/search?"
   ```

**ნაწილი D — არსებული pagination rows წაშლა:**

7. Preview:
   ```sql
   SELECT COUNT(*) FROM ingestion.document
   WHERE canonical_url ~ '[?&]page=[0-9]+';
   ```

8. Delete (cascades to `chunk`, `vector_index` — Qdrant sync auto-triggers):
   ```sql
   DELETE FROM ingestion.document
   WHERE canonical_url ~ '[?&]page=[0-9]+';
   ```
   შემდეგ: `POST /corpus/geostat-portal/sync:cleanup` (Qdrant orphan cleanup).

**unit test:**
```java
@Test
void shouldEnqueue_returnsFalse_forPaginatedUrl() {
    CorpusPolicyV2 policy = policyWithExclude("?page=");
    assertThat(filter.shouldEnqueue(
        "https://www.geostat.ge/ka/relationsOfCategory/100/post?page=2", policy))
        .isFalse();
    assertThat(filter.shouldEnqueue(
        "https://www.geostat.ge/ka/relationsOfCategory/100/post", policy))
        .isTrue();
}
```

**ფაილები:**
- `apps/ingestion-service/src/main/java/com/geostat/ingestion/parse/profile/PolicyUrlFilter.java`
- `apps/ingestion-service/src/main/java/com/geostat/ingestion/crawl/LinkDiscoverer.java`
- New: `ops/config/corpus/geostat-portal-policy.yaml`

**Acceptance criteria:**
```sql
SELECT COUNT(*) FROM ingestion.document
WHERE canonical_url ~ '[?&]page=[0-9]+';
-- target: 0
```
unit test გადადის. `EXPLAIN` on LinkDiscoverer call path shows `RoutingUrlFilter.shouldEnqueue()`.

---

### L-1-05 — topic remine: 5 coarse clusters → granular taxonomy 🔴

**Root cause (დადასტურებული):**
K-means k = `sqrt(N/10)`. enrichment-ის დროს მხოლოდ ~287 doc იყო enriched (summary-ებით), ამიტომ k = sqrt(287/10) ≈ 5. ყველა cluster label Gemini-მ "Statistical Data..." ვარიანტებად სახელდა, რადგან sample docs ყველა ერთნაირი iყო.

enrichment-ის შემდეგ (L-1-02): ~3 500+ doc-ი ექნება summary → k = sqrt(3500/10) ≈ **18–20 cluster** → granular taxonomy.

**Resolution steps:**

1. **L-1-02 სრულად დასრულების შემდეგ** — შეამოწმე doc count with summary:
   ```sql
   SELECT COUNT(*) FROM ingestion.document
   WHERE summary_ka IS NOT NULL AND fetch_status = 'parsed';
   -- target: > 2000 before remine
   ```

2. **Topics remine:**
   ```http
   POST /corpus/geostat-portal/topics:remine
   ```
   ეს `TopicRemineService`-ს გაუშვებს: deletes old clusters → `SmileKMeansEngine` K-means → `GeminiTopicClusterLabeler` labels → saves new `topic_cluster` rows.

   **⚠️ warning:** remine wipes `approved=true` clusters. თუ manually approved clusters გაქვს — document-ი (`approved_by`, `approved_at`) — backup:
   ```sql
   SELECT * FROM ingestion.topic_cluster WHERE approved = true;
   ```

3. **Admin approval** — remine-ის შემდეგ clusters `approved=false`. უნდა approve-ი:
   ```http
   POST /corpus/geostat-portal/topics/{clusterId}/approve
   ```
   ან bulk approve ყველაზე:
   ```http
   POST /corpus/geostat-portal/topics:approve-all
   ```
   (თუ ეს endpoint-ი არ არსებობს — `TopicClusterAdminService.approve(id)` პირდაპირ)

4. **MV refresh (სამივე):**
   ```sql
   REFRESH MATERIALIZED VIEW CONCURRENTLY ingestion.mv_topic_keywords;
   REFRESH MATERIALIZED VIEW CONCURRENTLY ingestion.mv_portal_link;
   REFRESH MATERIALIZED VIEW CONCURRENTLY ingestion.mv_specific_link;
   ```
   და `INSERT INTO ingestion.catalog_view_refresh VALUES ('ingestion.mv_topic_keywords', now()) ON CONFLICT (view_name) DO UPDATE SET refreshed_at = now()` — სამივე MV-ზე.

**ფაილები:**
- `apps/ingestion-service/src/main/java/com/geostat/ingestion/enrichment/topic/TopicRemineService.java`
- `apps/ingestion-service/src/main/java/com/geostat/ingestion/enrichment/topic/SmileKMeansEngine.java`
- `apps/ingestion-service/src/main/java/com/geostat/ingestion/catalog/topic/TopicClusterAdminService.java`
- `apps/ingestion-service/src/main/java/com/geostat/ingestion/api/CorpusController.java`

**Acceptance criteria:**
```sql
SELECT COUNT(*) FROM ingestion.topic_cluster WHERE approved = true;
-- target: ≥ 15 (კარგი taxonomy)
SELECT COUNT(DISTINCT label_en) FROM ingestion.topic_cluster;
-- target: ≥ 12 distinct labels (no more "Statistical Data and Reports" ×3)
SELECT COUNT(*) FROM ingestion.mv_portal_link;
-- target: ≥ 30 (ახლა 10)
SELECT COUNT(*) FROM ingestion.mv_specific_link;
-- target: ≥ 200 (ახლა 98)
```

---

### L-1-06 — `enrichment_version` stub → real implementation 🟡

**Root cause (დადასტურებული):**
`DocumentEntity.enrichmentVersion` field არსებობს, `setEnrichmentVersion()` method-ი კომპილდება, მაგრამ **არსად არ გამოიძახება** ingestion codebase-ში. Flyway default 0 — ამიტომ ყველა document-ს `enrichment_version=0` აქვს განუხილველად.

**Resolution steps:**

1. `DocumentEnrichmentOrchestrator.enrichDocument()` — orchestration-ის ბოლოს, ყველა deriver-ის წარმატებული დასრულების შემდეგ:
   ```java
   document.setEnrichmentVersion(document.getEnrichmentVersion() + 1);
   documentRepository.save(document);
   ```

2. `EnrichmentBackfillService` — backfill-ის შემდეგ `enrichment_version` ასევე უნდა გაიზარდოს (orchestrator-ის გავლით ისედაც გაიზრდება, თუ step 1 გაკეთდა).

3. **Stale detection**: future enrichment run-ებისთვის, `findIdsNeedingP1EnrichmentBackfill()` query-ს შეიძლება დაემატოს `AND enrichment_version < CURRENT_VERSION` condition — მაგრამ ეს ოკი არ არის ახლა, გააკეთე მხოლოდ step 1.

**Acceptance criteria:**
```sql
-- after next enrichment run:
SELECT enrichment_version, COUNT(*) FROM ingestion.document
GROUP BY enrichment_version;
-- version=0: only unenriched; version≥1: enriched docs
```

---

### L-1-07 — feedback → authority_score wiring (feedback loop) 🟡

**Root cause (დადასტურებული):**
`ChatFeedbackController` → `ChatTelemetryService.recordFeedback()` → `JdbcChatTurnWriter` (ან log). **Downstream: nothing.** `document.authority_score` არასდროს update-დება feedback-ისგან. `evaluation_query` user_log-ისგან არ ივსება.

**Resolution steps:**

1. **ingestion-service-ში** შექმნი `FeedbackAuthorityAdjustmentJob` (scheduled, weekly):
   ```
   SELECT turn_id, source_url, rating FROM chat.feedback
   WHERE created_at > now() - interval '7 days'
   GROUP BY source_url
   ```
   aggregate: `up_count - down_count` per URL → adjust `score_boost` on `ingestion.document`:
   - net positive (+3 ან მეტი): `score_boost = LEAST(score_boost * 1.1, 2.0)`
   - net negative (-3 ან ნაკლები): `score_boost = GREATEST(score_boost * 0.9, 0.5)`

2. **chat-api-ში** `ChatTelemetryService`-ს დაამატე event publish (ან direct JDBC call to ingestion DB-ში `feedback_signal` ცხრილი, თუ cross-DB წვდომა შესაძლებელია).

3. **`evaluation_query` from user logs:**
   `JdbcChatTurnWriter`-ში feedback `rating='down'` → log query text + locale → periodic job: top downvoted queries add to `evaluation_query` with `source='user_log'`, `difficulty='hard'`.

   **მნიშვნელოვანი:** cross-service DB access ან event bus (RabbitMQ) — ჯერ plan item-ად ჩაიწერე, არ implement-ო სანამ owner არ approve-ავს architecture.

**ფაილები (investigate first):**
- `apps/backend/src/main/java/com/geostat/chat/application/telemetry/ChatTelemetryService.java`
- `apps/backend/src/main/java/com/geostat/chat/infrastructure/telemetry/JdbcChatTurnWriter.java`
- ingestion-service `CorpusController` — authority:recompute trigger point

---
