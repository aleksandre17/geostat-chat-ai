## Layer 0 — Database / Materialized Views

*ეს Layer-ი L-1 fixes-ის შემდეგ.*

---

### L0-01 — cluster matching: substring scan → GIN trigram index 🟠

**პრობლემა:**
`JdbcDerivedCatalogReader.queryClusterIds()` SQL:
```sql
WHERE position(lower(tk.keyword) IN ?) > 0
```
`mv_topic_keywords.keyword` სვეტზე index არ არის — ყოველ query-ზე full table scan. "gdp" matches "gdp growth" AND "non-gdp region" — false positives.

**Resolution steps:**
1. შეამოწმე, `pg_trgm` extension ხელმისაწვდომია თუ არა:
   ```sql
   SELECT * FROM pg_extension WHERE extname = 'pg_trgm';
   ```
   თუ არ არის: `CREATE EXTENSION IF NOT EXISTS pg_trgm;` — migration-ში.
2. შექმნი `apps/ingestion-service/src/main/resources/db/migration/V17__catalog_keyword_index.sql`:
   ```sql
   CREATE EXTENSION IF NOT EXISTS pg_trgm;
   CREATE INDEX IF NOT EXISTS idx_mv_topic_keywords_keyword_trgm
       ON ingestion.mv_topic_keywords USING gin (keyword gin_trgm_ops);
   CREATE INDEX IF NOT EXISTS idx_curation_override_target
       ON ingestion.curation_override (target);
   ```
3. `REFRESH MATERIALIZED VIEW CONCURRENTLY ingestion.mv_topic_keywords;` — index-ი MV-ზე rebuild-ს საჭიროებს.
4. `JdbcDerivedCatalogReader.queryClusterIds()` SQL-ში შეცვალე `position(...)` → `tk.keyword % ?` (trigram similarity) ან `tk.keyword ILIKE '%' || ? || '%'` (index-aided ILIKE).

**ფაილები:**
- New: `apps/ingestion-service/src/main/resources/db/migration/V17__catalog_keyword_index.sql`
- `apps/backend/src/main/java/com/geostat/chat/infrastructure/catalog/JdbcDerivedCatalogReader.java`

**Acceptance criteria:**
- `EXPLAIN ANALYZE` on cluster matching query shows index scan (not seq scan) on `mv_topic_keywords`.
- False positive test: query "მოსახლეობა" → matches population cluster, NOT agriculture cluster.

---

### L0-02 — `curation_override(target)` index არ არის 🟡

> **შენიშვნა:** V17 migration-ში შედის (ნახე L0-01 step 2). ცალკე task არ საჭირო.

---

### L0-03 — `CachingIntentClassifier` not wired — `query_intent_cache` unused 🟡

**პრობლემა:**
V15 migration-ში `query_intent_cache` ცხრილი შეიქმნა. `CachingIntentClassifier` bean-ი `@Primary` არ არის — intent ყოველ query-ზე Gemini-ს ეკითხება.

**Resolution steps:**
1. `apps/backend/src/main/java/com/geostat/chat/infrastructure/config/QueryUnderstandingConfiguration.java`-ში:
   - დაამატე `@ConditionalOnProperty("geostat.chat.query.intent-cache-enabled")` სათავეში `CachingIntentClassifier` bean-ს.
   - როდესაც `intent-cache-enabled: true`, `CachingIntentClassifier`-ი უნდა იყოს `@Primary`.
2. `CachingIntentClassifier`-ს cache store სჭირდება — გამოიყენე `query_intent_cache` ცხრილი ახალი `JdbcQueryIntentCacheStore` adapter-ის მეშვეობით, რომელიც `DerivedCatalogReader`-ისგან დამოუკიდებელ JDBC pool-ს გამოიყენებს (ან catalog pool-ს, თუ `source=derived`).
3. `application-custom.yml`-ში `intent-cache-enabled: false` default — ჩართვა evaluation-ის შემდეგ.

**ფაილები:**
- `apps/backend/src/main/java/com/geostat/chat/infrastructure/config/QueryUnderstandingConfiguration.java`
- New: `apps/backend/src/main/java/com/geostat/chat/infrastructure/query/JdbcQueryIntentCacheStore.java`

---

### L0-04 — MV staleness: backend არ ამოწმებს `catalog_view_refresh` 🟡

**პრობლემა:**
V16-ში `catalog_view_refresh` ცხრილი შეიქმნა staleness tracking-ისთვის. backend არ კითხულობს — stale MV-ებით პასუხობს silent-ად.

**Resolution steps:**
1. შექმენი `apps/backend/src/main/java/com/geostat/chat/infrastructure/catalog/CatalogMvFreshnessChecker.java`:
   - `@Component`, `@ConditionalOnProperty(... "source", havingValue = "derived")`.
   - Inject `catalogJdbcTemplate`.
   - `checkFreshness()`: `SELECT view_name, refreshed_at FROM ingestion.catalog_view_refresh`.
   - თუ `refreshed_at < now() - interval` (configurable, default 60 min) → `log.warn("Catalog MV {} is stale: last refresh {}", ...)`.
2. `CatalogConfiguration.java`-ში `@PostConstruct`-ში გამოიძახე `checker.checkFreshness()`.
3. ახალი property: `geostat.chat.catalog.freshness-warn-minutes: 60`.

**ფაილები:**
- New: `apps/backend/src/main/java/com/geostat/chat/infrastructure/catalog/CatalogMvFreshnessChecker.java`
- `apps/backend/src/main/java/com/geostat/chat/infrastructure/config/CatalogConfiguration.java`

---

## Layer 1 — Qdrant / Embeddings

### L1-01 — `score_boost` გამოყენება გასარკვევია 🟡

**პრობლემა:**
`ingestion.document.score_boost` (0.5–2.0) სვეტი არსებობს, მაგრამ გაურკვეველია, retrieval-service-ი მას იყენებს Qdrant vector score-ზე თუ არა.

**Resolution steps:**
1. `apps/retrieval-service`-ში მოძებნე Qdrant query builder კლასი.
2. შეამოწმე, `score_boost` ერთვის `score`-ს, თუ ignore-ი.
3. თუ არ ერთვის — დამატე plan item retrieval-service-ის backlog-ში.
4. შედეგი ჩაწერე ამ ფაილის "Open questions" სექციაში.

---

### L1-02 — chunk metadata in retrieval response 🟡

**Resolution steps:**
1. `apps/retrieval-service` HTTP response-ში შეამოწმე `RetrievedChunk` DTO — ატარებს `section_path`, `page_kind`, `topic_cluster_id`?
2. თუ არა — `libs/platform-contracts`-ში დაამატე ეს fields `RetrievedChunk`-ში.
3. chat-api `CatalogRagLinkMerger`-ს ეს fields `page_kind`-aware cap-ისთვის სჭირდება (L2-04).

---

## Layer 2 — Retrieval / RAG Signal

### L2-01 — retrieval disabled by default 🔴

**პრობლემა:**
`geostat.retrieval.enabled: false` — Gemini-ს არ ეძლევა corpus context.

**მნიშვნელოვანი:** ჩართვა მხოლოდ L-1-01 + L-1-02 + L-1-03 დასრულების შემდეგ — unenriched + boilerplate corpus = worse retrieval.

**Resolution steps:**
1. L-1 fixes + enrichment re-run + MV refresh დასრულების შემდეგ: `application-custom.yml`-ში `RETRIEVAL_ENABLED` default-ი `true`-ზე.
2. eval baseline: გაუშვი eval suite `ops/eval/` (ნახე `run-eval.py`) retrieval enabled-ით — შეინახე scores.

---

### L2-02 — `ResponseRouter` unused; clarification logic weak 🟠

**პრობლემა:**
`ChatService`-ში clarification trigger: `if (links.isEmpty())` — chunk scores-ი არ გამოიყენება. `ResponseRouter` + `DefaultConfidenceAssessor` built, მაგრამ `ChatService`-ში არ ჩართულა.

**Resolution steps:**
1. `ChatService.java`-ში inject `RetrievalConfidenceAssessor` + `ResponseRouter`.
2. retrieval-ის შემდეგ: `RetrievalConfidence confidence = assessor.assess(chunks)`.
3. `ResponseRoute route = router.route(confidence, intent)`.
4. `switch (route)`: `ANSWER_WITH_CITATIONS` → normal flow; `SUGGESTIONS` → clarification; `CLARIFY` → `ClarificationService`; `REFUSE` → generic fallback.
5. ამოიღე `if (links.isEmpty())` clarification trigger — ჩაანაცვლე route-ით.

**ფაილები:**
- `apps/backend/src/main/java/com/geostat/chat/application/chat/ChatService.java`
- `apps/backend/src/main/java/com/geostat/chat/application/retrieval/ResponseRouter.java`

---

### L2-03 — confidence thresholds not calibrated 🟡

**Resolution steps:**
1. retrieval enabled-ით (L2-01) eval queries-ზე (`ingestion.evaluation_query`) შეაგროვე top chunk scores histogram.
2. `DefaultConfidenceAssessor.java`-ში constants `HIGH_THRESHOLD=0.75`, `MEDIUM_THRESHOLD=0.55`, `LOW_THRESHOLD=0.35` — შეცვალე corpus-ის პერცენტილებით (80th, 50th, 20th).
3. ჩაიწერე calibrated values `application-custom.yml`-ში configurable properties-ად.

---

### L2-04 — intent-aware chunk cap 🟡

**Resolution steps:**
1. `CatalogRagLinkMerger.java`-ში `maxRag` parameter — ამჟამად fixed `ChatService`-ში.
2. გადაიტანე `AiChatProperties`-ში: `max-rag-factual: 6`, `max-rag-lookup: 4`, `max-rag-navigate: 2`, `max-rag-clarify: 1`.
3. `ChatService`-ში intent-ის მიხედვით გამოაწოდე სათანადო value merger-ს.

---

## Layer 3 — Catalog / Response Assembly

### L3-01 — derived mode-ში `TopicCatalog` bean 🔴

**პრობლემა:**
`YamlTopicCatalog` წაშლილია (restore-manifest შენიშვნა git status-ში). Default `catalog.source=derived` → `DerivedMinimalTopicCatalog` bean-ი უნდა იყოს active; YAML mode (`source=yaml`) კვლავ საჭიროებს `TopicCatalog` port-ს — `YamlCatalogLinkBuilder` / `TopicDetector`-ისთვის.

**Resolution steps:**
1. **რეკომენდაცია (current default):** `source=derived` — `DerivedMinimalTopicCatalog` + `DerivedCatalogReader` pipeline; presentation styles → `YamlPresentationStyleCatalog`.
2. YAML legacy mode-ისთვის: `CatalogConfiguration.java`-ში დაამატე `@Bean @ConditionalOnProperty(... "source", havingValue = "yaml")` — minimal `TopicCatalog` implement-ი, backed by `catalog-meta.yaml` (ანუ `CatalogMetaLoader`-ის შედეგი), ან migrate fully to derived.
3. **არ გამოიყენო** წაშლილი `YamlTopicCatalog` / `TopicCatalogLoader` — ისინი ADR-011-ით ამოღებულია.

**ფაილები:**
- `apps/backend/src/main/resources/application-custom.yml`
- `apps/backend/src/main/java/com/geostat/chat/infrastructure/config/CatalogConfiguration.java`

---

### L3-02 — `CatalogMetaLoader` orphaned 🟡

**Resolution steps:**
1. `apps/backend/src/main/java/com/geostat/chat/infrastructure/catalog/CatalogMetaLoader.java`-ის result-ი — მოძებნე, ვინ `@Autowire`-ს.
2. თუ არავინ — ან wire `DerivedCatalogResponseAssembler`-ში portal section fallback-ად, ან mark deprecated და წაშალე.

---

### L3-03 — `DerivedMinimalTopicCatalog` empty stub 🟠

**Resolution steps:**
1. `DerivedMinimalTopicCatalog.java` ახლა ცარიელი rules-ით. `TopicDetector` AI fallback-ზე მიდის ყოველ query-ზე.
2. ჩაშენდი rule loading from `ingestion.topic_cluster` DB rows (via `catalogJdbcTemplate`) — `label_ka` + `label_en` keywords → `Topic` enum mapping.
3. unit test: mock DB-ით — `TopicDetector` სწორ topic-ს ირჩევს.

---

## Layer 4 — Query Understanding (U07)

### L4-01 — pipeline disabled by default 🟠

> **Prerequisite:** L2-01 (retrieval enabled), L3-01 (catalog bean fixed).

**Resolution steps:**
1. `application-custom.yml`: `GEOSTAT_CHAT_QUERY_UNDERSTANDING_ENABLED: true`.
2. eval suite-ით compare before/after: intent classification accuracy on `ingestion.evaluation_query`.

---

### L4-02 — intent classifier gets `original`, not `spellFixed` 🟡

**Resolution steps:**
1. `QueryUnderstandingPipeline.java` line ~45:
   ```java
   QueryIntentKind intent = intentClassifier.classify(original, normalized, locale);
   ```
   შეცვალე:
   ```java
   QueryIntentKind intent = intentClassifier.classify(spellFixed, normalized, locale);
   ```
2. unit test: typo query "gdp-ა" → spell-fixed "gdp" → FACTUAL intent (არ არის UNKNOWN).

---

### L4-03 — `QueryRouter` parallel legacy — deprecate 🟠

**Resolution steps:**
1. `ChatService.java`-ში `buildContext()`-ში: `QueryRouter.route(query)` → ამჟამად ყოველთვის გამოიძახება.
2. როდესაც `query-understanding.enabled=true`, `QueryIntentMapper.map(analyzedQuery.intent())` გამოიყენე — `QueryRouter` გვერდი.
3. `QueryRouter`-ს კლასზე დაამატე `@Deprecated` annotation.
4. **ნუ წაშლი** `QueryRouter`-ს სანამ U07 production-ში stable არ არის — მხოლოდ deprecate.

---

### L4-04 — Georgian spell-fix absent 🟡

**Resolution steps:**
1. `SymSpellFixer.java`-ში `[A-Za-z]` check — Georgian-ი გამოტოვდება.
2. `YamlQueryTypoCorrector.java`-ში `query-typo-corrections.yaml` — დაამატე ხშირი ქართული typos (მაგ. "სტატისტიკა" → "სტატისტიკა", morphological variants).
3. `spell-dictionary.yaml`-ში statistical terms-ის Georgian forms დაამატე.

---

### L4-05 — entity extraction weak 🟡

**Resolution steps:**
1. `HeuristicQueryEntityExtractor.java`-ში დაამატე REGION patterns (ქართული რეგიონების სახელები), ORGANIZATION patterns (სახელმწიფო სტრუქტურები).
2. გრძელვადიანი: `document.entities` JSONB — expose via `DerivedCatalogReader` port; retrieval query enrichment.

---

## Layer 5 — Gemini Prompt + Response

### L5-01 — domain hardcode `chat-prompts.yaml`-ში 🟠

**პრობლემა:**
`src/main/resources/prompts/chat-prompts.yaml`-ში domain-specific routing examples (aquaculture vs culture) — `owner-no-domain-hardcode` rule-ის დარღვევა.

**Resolution steps:**
1. გახსენი `chat-prompts.yaml` და მოძებნე domain-specific examples ("aquaculture", "culture" ან სხვა სექტორული სიტყვები).
2. ამოიღე მაგალითები — ჩაანაცვლე generic instruction-ებით, რომლებიც catalog/corpus-ს reference-ს.
3. domain routing → `terminology-overlay.yaml` + catalog-ის responsibility (არ prompt-ში).

**ფაილები:** `apps/backend/src/main/resources/prompts/chat-prompts.yaml`

---

### L5-02 — intent taxonomy duplication prompt-ში 🟡

**Resolution steps:**
1. `chat-prompts.yaml`-ში intent taxonomy (factual/lookup/etc.) ჩამოთვლილია — U07 pipeline ამ intent-ს უკვე გამოთვლის და `PromptBuilder`-ი context-ად გადასცემს.
2. ამოიღე taxonomy definition prompt-იდან — დატოვე მხოლოდ instruction, რომ LLM-ი მიღებული intent-ის შესაბამისად უპასუხოს.

---

### L5-03 — fallback quality 🟡

**Resolution steps:**
1. `AiResponseParser.java`-ში `fallback()` method-ი catalog links-ს აბრუნებს unconditionally.
2. შეცვალე: fallback → `ResponseRouter.route(RetrievalConfidence.LOW, intent)` → route-ის შესაბამისი პასუხი.

---
