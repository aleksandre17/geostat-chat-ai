# Composer Pipeline Handoff — geostat-chat-ai
**შექმნილია:** 2026-05-27  
**მდგომარეობა:** ingestion-service UP, geostat-portal crawl in progress  
**ამ დოკუმენტის მიზანი:** composer agent-ისთვის სრული სამუშაო გეგმა — ბოლომდე გასვლა, ყველაფერი ბაზაში, სრული pipeline

---

## 0. MANDATORY — პირველ ნაბიჯად წაიკითხე

```
.cursor/rules/owner-standards.mdc          ← არასდროს დაარღვიო
.cursor/rules/zero-gap-architecture.mdc    ← pipeline gaps = bugs
.cursor/rules/owner-no-domain-hardcode.mdc ← no hardcode
.cursor/skills/owner-agent-conduct/SKILL.md ← conduct
.cursor/skills/owner-architecture/SKILL.md  ← layout
```

### კონდუქტის წესები (დარღვევა = STOP)

- **ვერ გააუარესებ** არსებულ architecture-ს, ports-ს, layering-ს, clarity-ს — **ნულოვანი გამონაკლისი**
- **ვერ ჩუმად გადახვალ** error-ს — ყველა failure must be logged with context
- **ვერ გამოიყენებ** hardcoded domain logic-ს — YAML, ports, config
- **ვერ გადაახტები** test/verification ნაბიჯებს — ყოველი Phase ვერიფიცირებულია
- **ვერ დაამატებ** კოდ-კომენტარებს რომლებიც "ახსნიან რა კეთდება" — მხოლოდ intent/trade-off

### Architecture პრინციპები

```
domain/ ← pure business types, NO framework, NO infra
application/ ← orchestration, uses domain ports
infrastructure/ ← Spring, JPA, Qdrant, YAML loaders (adapters)
api/ ← Spring MVC, DTOs only
```

Dependency rule: `infrastructure → application → domain`. Reverse = violation.

---

## 1. CURRENT STATE — ახლანდელი მდგომარეობა

### სერვისები

| სერვისი | Port | სტატუსი |
|---|---|---|
| ingestion-service | 8093 | ✅ UP — crawling |
| backend (chat-ai) | 8080 | ❓ not verified |
| retrieval-service | 8094 | ❓ not verified |
| PostgreSQL | 5432 | ✅ (tunnel) |
| Qdrant | 6333 | ✅ (tunnel) |

### ბოლო ნაბიჯები (შესრულებული)

1. `ChunkVectorIndexer.indexDocument` → `@Transactional(propagation = REQUIRES_NEW)` — transaction isolation
2. `ChunkRepository.deleteByDocumentId` → `@Modifying @Query` bulk DELETE — O(1)
3. `VectorIndexRepository.deleteByDocumentId` → `@Modifying @Query` bulk DELETE — O(1)
4. `CrawlJobService.recoverStaleRuns()` — `@PostConstruct` startup recovery
5. `EnrichmentRunExecutor` — `Hibernate.initialize` lazy proxy force-init
6. `DocumentQdrantLifecycleSync` — `@Transactional(readOnly = true)` session open
7. Pessimistic locking → `CrawlRunStore` orchestration layer (moved from DocumentChunkWriter)
8. Binary URL guard in `CrawlRunStore.persistFetchedContent`
9. `geostat-portal-policy.yaml` — binary extensions + `/media/` excluded

### ცნობილი პრობლემები (ამ handoff-ის მიზანი)

| # | სიმძიმე | პრობლემა | ფაილი |
|---|---|---|---|
| BUG-1 | 🔴 CRITICAL | `CrawlSectionYaml` კარგავს `respectRobotsTxt` → policy load-ი fail, permissive fallback — exclude patterns NOT honored | `CorpusConfigurationLoader.java` + `geostat-portal-policy.yaml` |
| BUG-2 | 🟠 HIGH | Enrichment failures: `Failed to generate content` for Gemini | environment/quota |
| VERIFY-1 | 🟡 | DB counts post-crawl not verified | PostgreSQL |
| VERIFY-2 | 🟡 | Backend + retrieval services not started | ports 8080, 8094 |
| E2E-1 | 🟡 | No end-to-end chat query verified | full pipeline |

---

## 2. PHASE A — BUG FIXES (PRIORITY 1, ყველაზე პირველი)

### A-1: Fix `CrawlSectionYaml` — `respectRobotsTxt` unknown field

**Root cause:**  
`CorpusConfigurationLoader.CrawlSectionYaml` (nested static class) does NOT have `@JsonIgnoreProperties(ignoreUnknown = true)`.  
`geostat-portal-policy.yaml` has `crawl.respectRobotsTxt` — this field doesn't exist on `CrawlSectionYaml`.  
Jackson throws `UnrecognizedPropertyException` → entire policy YAML fails → **permissive fallback** used.  
Result: `excludePatterns` NOT honored → binary files (PDFs, etc.) re-discovered and attempted.

**ფაილი:** `apps/ingestion-service/src/main/java/com/geostat/ingestion/parse/profile/CorpusConfigurationLoader.java`

**Fix — ორი ნაბიჯი:**

**Step 1:** `CrawlSectionYaml`-ზე დაამატე `@JsonIgnoreProperties(ignoreUnknown = true)`:
```java
@SuppressWarnings("unused")
@JsonIgnoreProperties(ignoreUnknown = true)   // ← ADD THIS
static final class CrawlSectionYaml {
    public Integer workerThreads;
    public Integer crawlDelay;
    public Integer rateLimitMs;
}
```

**Step 2:** `geostat-portal-policy.yaml`-ში `respectRobotsTxt` გადაიტანე `network:` section-ში (სწორი architectural layer — network policy):

```yaml
# ops/config/corpus/geostat-portal-policy.yaml
crawl:
  crawlDelay: 1000
  workerThreads: 8
  # respectRobotsTxt removed from here — belongs in network:
network:
  respectRobotsTxt: true    # ← correct layer: NetworkPolicyYaml has this field
  tlsVerify: false           # geostat.ge has cert issues on some paths
```

**Verify:**  
Restart ingestion-service → log must show:  
```
INFO  CorpusConfigurationLoader : Loaded corpus policy from .../geostat-portal-policy.yaml
```
NOT:  
```
WARN  CorpusConfigurationLoader : Failed to load corpus policy ... — using permissive fallback
```

**Acceptance:** No WARN "permissive fallback" on startup for geostat-portal.

---

### A-2: Enrichment Gemini Failures

**Current log:**
```
WARN EnrichmentRunExecutor : summary enrichment failed for document X: Failed to generate content
WARN EnrichmentRunExecutor : entities enrichment failed for document X: Failed to generate content
```

**Investigation steps:**
1. შეამოწმე `GEMINI_API_KEY` valid-ია:
   ```
   GET https://generativelanguage.googleapis.com/v1beta/models?key=${GEMINI_API_KEY}
   ```
2. შეამოწმე model name: `ops/config/ingestion/.env.dev` → `INGESTION_ENRICHMENT_CHAT_MODEL=gemini-2.5-flash-lite`
3. Logs-ში `Failed to generate content` — Spring AI ბრუნებს ამ string-ს when API returns 400/429/503
4. თუ quota: `INGESTION_ENRICHMENT_ENABLED=false` → crawl პირველი, enrichment შემდეგ
5. თუ model name invalid: შეცვალე `ops/config/ingestion/.env.dev`-ში `INGESTION_ENRICHMENT_CHAT_MODEL=gemini-2.0-flash`

**არ შეეხო enrichment-ის კოდ-ლოგიკას** — მხოლოდ config.

---

## 3. PHASE B — DB VERIFICATION (post-crawl)

ingestion-service-ის ლოგებიდან გამოიმუშავე corpus crawl completion:
```
[orchestrator] corpus geostat-portal run <UUID> finished: completed (...)
```

შემდეგ გაუშვი verification queries PostgreSQL-ზე (JDBC connection: `jdbc:postgresql://127.0.0.1:5432/geostat`, schema: `ingestion`):

### B-1: Basic Counts

```sql
-- Documents per corpus
SELECT c.name AS corpus, COUNT(d.id) AS doc_count
FROM ingestion.document d
JOIN ingestion.corpus c ON c.id = d.corpus_id
GROUP BY c.name
ORDER BY c.name;

-- Chunks per corpus
SELECT c.name AS corpus, COUNT(ch.id) AS chunk_count, AVG(ch.token_count) AS avg_tokens
FROM ingestion.chunk ch
JOIN ingestion.document d ON d.id = ch.document_id
JOIN ingestion.corpus c ON c.id = d.corpus_id
GROUP BY c.name
ORDER BY c.name;

-- Vector index rows
SELECT c.name AS corpus, COUNT(vi.id) AS vector_index_count
FROM ingestion.vector_index vi
JOIN ingestion.chunk ch ON ch.id = vi.chunk_id
JOIN ingestion.document d ON d.id = ch.document_id
JOIN ingestion.corpus c ON c.id = d.corpus_id
GROUP BY c.name
ORDER BY c.name;
```

**Expected (minimum acceptable):**
- `geostat-portal`: docs ≥ 50, chunks ≥ 500
- `agriculture-ge`: docs = 0 (SPA, Playwright disabled) — normal
- vector_index count = chunk count (1:1 mapping) — თუ indexing enabled + Gemini works

### B-2: Orphan Check

```sql
-- Chunks with no parent document (orphaned)
SELECT COUNT(*) AS orphaned_chunks
FROM ingestion.chunk ch
WHERE NOT EXISTS (SELECT 1 FROM ingestion.document d WHERE d.id = ch.document_id);

-- Vector index rows with no parent chunk (orphaned)
SELECT COUNT(*) AS orphaned_vector_index
FROM ingestion.vector_index vi
WHERE NOT EXISTS (SELECT 1 FROM ingestion.chunk ch WHERE ch.id = vi.chunk_id);
```

**Expected:** BOTH must be `0`.

### B-3: Chunk Sequence Integrity

```sql
-- Documents with non-sequential chunks (gaps in sequence_no)
SELECT d.id, d.url, MIN(ch.sequence_no) AS min_seq, MAX(ch.sequence_no) AS max_seq, COUNT(*) AS chunk_count,
       CASE WHEN MAX(ch.sequence_no) - MIN(ch.sequence_no) + 1 = COUNT(*) THEN 'OK' ELSE 'GAP' END AS seq_status
FROM ingestion.document d
JOIN ingestion.chunk ch ON ch.document_id = d.id
GROUP BY d.id, d.url
HAVING CASE WHEN MAX(ch.sequence_no) - MIN(ch.sequence_no) + 1 = COUNT(*) THEN 'OK' ELSE 'GAP' END = 'GAP'
LIMIT 20;
```

**Expected:** 0 rows (no gaps).

### B-4: Stale Crawl Runs

```sql
-- Runs stuck in running/pending (should have been recovered by recoverStaleRuns @PostConstruct)
SELECT corpus_id, status, COUNT(*) AS cnt
FROM ingestion.crawl_run
WHERE status IN ('running', 'pending')
GROUP BY corpus_id, status;
```

**Expected:** 0 rows.  
If not 0 → `recoverStaleRuns()` didn't fire. Check `CrawlJobService` `@PostConstruct` annotation.

### B-5: Frontier Health

```sql
-- URL frontier queue per status per corpus
SELECT cr.corpus_id, uf.status, COUNT(*) AS cnt
FROM ingestion.url_frontier uf
JOIN ingestion.crawl_run cr ON cr.id = uf.crawl_run_id
WHERE cr.status = 'completed'
GROUP BY cr.corpus_id, uf.status
ORDER BY cr.corpus_id, uf.status;
```

**Expected:** `done` dominates, `failed` minimal (0-5%).  
If `failed` > 10% → investigate frontier failure patterns.

### B-6: Embedding Status

```sql
-- Chunks by embedding status per corpus
SELECT c.name AS corpus, ch.embedding_status, COUNT(*) AS cnt
FROM ingestion.chunk ch
JOIN ingestion.document d ON d.id = ch.document_id
JOIN ingestion.corpus c ON c.id = d.corpus_id
GROUP BY c.name, ch.embedding_status
ORDER BY c.name, ch.embedding_status;
```

**Expected:** `embedded` = all chunks (if Gemini works). If Gemini quota failed → `pending` or `null`.

### B-7: Document Quality

```sql
-- Documents with no chunks (quality reject or empty parse)
SELECT d.url, d.status, d.language
FROM ingestion.document d
WHERE NOT EXISTS (SELECT 1 FROM ingestion.chunk ch WHERE ch.document_id = d.id)
LIMIT 20;

-- Language distribution
SELECT language, COUNT(*) AS cnt
FROM ingestion.document
GROUP BY language
ORDER BY cnt DESC;
```

---

## 4. PHASE C — SERVICES STARTUP & VERIFICATION

### C-1: Backend Service (port 8080)

```powershell
# Run from project root:
Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue
Get-Content "ops/config/backend/.env.dev" | Where-Object { $_ -match "=" -and $_ -notmatch "^#" } |
    ForEach-Object { $k, $v = $_ -split "=", 2; Set-Item -Path "Env:$k" -Value $v }
cd apps/backend
.\gradlew.bat bootRun
```

**Expected startup log:**
```
Started ChatApplication in ... seconds
```

**Health check:**
```
GET http://localhost:8080/actuator/health → {"status":"UP"}
```

**Common failures:**
- `JAVA_HOME invalid` → `Remove-Item Env:JAVA_HOME` first
- `Port 8080 in use` → `netstat -ano | findstr ":8080"` → `Stop-Process -Id <PID>`
- `Flyway validation failed` → check `apps/backend/src/main/resources/db/migration/` checksums

### C-2: Retrieval Service (port 8094)

```powershell
# Run from project root:
Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue
Get-Content "ops/config/retrieval/.env.dev" | Where-Object { $_ -match "=" -and $_ -notmatch "^#" } |
    ForEach-Object { $k, $v = $_ -split "=", 2; Set-Item -Path "Env:$k" -Value $v }
cd apps/retrieval-service
.\gradlew.bat bootRun
```

**Health check:**
```
GET http://localhost:8094/actuator/health → {"status":"UP"}
```

### C-3: Ingestion Service (already running)

```powershell
# If restart needed:
Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue
$env:INGESTION_PARSE_CONFIG_DIR = "C:/Users/Test-User/CursorProjects/geostat-chat-ai/ops/config/corpus"
$env:SPRING_FLYWAY_VALIDATE_ON_MIGRATE = "false"
Get-Content "ops/config/ingestion/.env.dev" | Where-Object { $_ -match "=" -and $_ -notmatch "^#" } |
    ForEach-Object { $k, $v = $_ -split "=", 2; Set-Item -Path "Env:$k" -Value $v }
cd apps/ingestion-service
.\gradlew.bat bootRun
```

---

## 5. PHASE D — END-TO-END PIPELINE VERIFICATION

### D-1: Retrieval Test

```bash
POST http://localhost:8094/api/v1/retrieval/search
Content-Type: application/json

{
  "text": "საქართველოს მოსახლეობა",
  "maxChunks": 5,
  "locale": "ka"
}
```

**Expected:** JSON array with `text`, `score`, `documentId`, `url` fields — non-empty.

```bash
POST http://localhost:8094/api/v1/retrieval/search
Content-Type: application/json

{
  "text": "GDP growth Georgia",
  "maxChunks": 5,
  "locale": "en"
}
```

**Acceptance:**
- Results non-empty
- `score` > 0.5 for top result
- `url` points to valid geostat.ge domain

### D-2: Backend Chat Test

```bash
POST http://localhost:8080/api/v1/chat
Content-Type: application/json

{
  "message": "საქართველოს მოსახლეობა რამდენია?",
  "locale": "ka"
}
```

**Expected:** JSON with `answer`, `citations` fields. Citations must contain `url` from geostat.ge.

```bash
POST http://localhost:8080/api/v1/chat
Content-Type: application/json

{
  "message": "What is the GDP of Georgia?",
  "locale": "en"
}
```

**Failure modes to check:**
- `answer` is null or empty → retrieval returned 0 chunks → check Phase B + C
- `citations` empty → response routing or link builder issue
- 500 error → check backend logs for stack trace

### D-3: Query Understanding Test

```bash
POST http://localhost:8080/api/v1/query/analyze
Content-Type: application/json

{
  "query": "მოსახლეობა"
}
```

**Expected:**
```json
{
  "normalizedQuery": "მოსახლეობა",
  "intent": "STATISTICS",
  "entities": [...],
  "expandedTerms": [...]
}
```

---

## 6. PHASE E — LAYER 0 DATABASE IMPROVEMENTS (from QUALITY-PIPELINE-PLAN)

> **Reference:** `docs/plan/quality-pipeline/03-layers-0-to-5-execution.md`

### E-1: GIN Trigram Index for Cluster Matching (L0-01)

**Problem:** `JdbcDerivedCatalogReader.queryClusterIds()` does full table scan on `mv_topic_keywords`.

**Fix — migration:**
```sql
-- File: apps/ingestion-service/src/main/resources/db/migration/V39__catalog_gin_index.sql
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_mv_topic_keywords_keyword_trgm
    ON ingestion.mv_topic_keywords USING gin (keyword gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_curation_override_target
    ON ingestion.curation_override (target);
```

**Migration numbering:** Check `apps/ingestion-service/src/main/resources/db/migration/` for latest V number, use next available.  
**Current latest:** V38 — use V39.

**Code fix:** `apps/backend/src/main/java/com/geostat/chat/infrastructure/catalog/JdbcDerivedCatalogReader.java`  
Find `queryClusterIds()` SQL: replace `position(lower(tk.keyword) IN ?) > 0` with:
```sql
lower(tk.keyword) ILIKE '%' || lower(?) || '%'
```
(ILIKE uses GIN trigram index, `position()` does not.)

**Acceptance:**
```sql
EXPLAIN ANALYZE SELECT ... FROM ingestion.mv_topic_keywords WHERE lower(keyword) ILIKE '%მოსახლეობა%';
```
Must show `Index Scan using idx_mv_topic_keywords_keyword_trgm`.

### E-2: Document Full-Text Search Index (L0-02 prerequisite)

```sql
-- File: apps/ingestion-service/src/main/resources/db/migration/V40__document_fts_index.sql
CREATE INDEX IF NOT EXISTS idx_document_cleaned_text_fts
    ON ingestion.document USING gin (to_tsvector('simple', COALESCE(cleaned_text, '')));
```

This supports keyword search in `ChunkKeywordSearch`.

---

## 7. PHASE F — SILENT ERROR AUDIT

> These are hidden failures that produce no exception but corrupt data silently.

### F-1: `@Transactional(REQUIRES_NEW)` catch-block interaction

**File:** `apps/ingestion-service/src/main/java/com/geostat/ingestion/index/ChunkVectorIndexer.java`

**Check:** `indexDocument` catches `RuntimeException` inside `REQUIRES_NEW` transaction.  
If a DB failure occurs → transaction is marked rollback-only → catch returns 0 → **the transaction committed with partial data or not at all**.

**Verify:** Run crawl → check logs for `vector indexing failed for document` — if present, the document has no vector index row.  
If found → DB check:
```sql
SELECT d.id, d.url, COUNT(vi.id) AS vi_count
FROM ingestion.document d
JOIN ingestion.chunk ch ON ch.document_id = d.id
LEFT JOIN ingestion.vector_index vi ON vi.chunk_id = ch.id
WHERE vi.id IS NULL
GROUP BY d.id, d.url
LIMIT 20;
```
Re-index documents with 0 vector index rows via:
```
POST http://localhost:8093/admin/index/{corpusName}
```

### F-2: CorpusConfigurationLoader Silent Fallback

**After A-1 fix:** Watch for any corpus policy that still falls back.  
Log pattern to watch:
```
WARN CorpusConfigurationLoader : Failed to load corpus policy ... — using permissive fallback
```
**If seen:** Fix that corpus's policy YAML for the same unknown-field pattern.

### F-3: Missing `network:` in `agriculture-ge-policy.yaml`

**Check:** `ops/config/corpus/agriculture-ge-policy.yaml` — verify it also doesn't have `CrawlSectionYaml` unknown fields.

### F-4: Enrichment Retry Gap

If enrichment fails → document has no `summary`, no `entities`, no `page_kind`.  
These fields power `ChatResultFactory` and `ResponseRouter` — LOW confidence response for every query.

**DB Check:**
```sql
SELECT
    COUNT(*) FILTER (WHERE summary IS NOT NULL) AS has_summary,
    COUNT(*) FILTER (WHERE summary IS NULL) AS no_summary,
    COUNT(*) TOTAL
FROM ingestion.document
WHERE status = 'active';
```

**If `no_summary` > 20%:** Enrich manually:
```
POST http://localhost:8093/admin/enrich/{corpusName}
```

---

## 8. PHASE G — QUALITY GATES CHECKLIST

> Pass ALL before declaring pipeline complete.

| Gate | Check | How to Verify |
|---|---|---|
| G-01 | No permissive policy fallback on startup | Logs: no "using permissive fallback" for any corpus |
| G-02 | documents.count ≥ 50 for geostat-portal | Phase B-1 SQL |
| G-03 | orphaned_chunks = 0 | Phase B-2 SQL |
| G-04 | orphaned_vector_index = 0 | Phase B-2 SQL |
| G-05 | chunk sequence gaps = 0 | Phase B-3 SQL |
| G-06 | stale crawl_run = 0 | Phase B-4 SQL |
| G-07 | frontier failed < 10% | Phase B-5 SQL |
| G-08 | embedding_status = 'embedded' for ≥ 80% chunks | Phase B-6 SQL |
| G-09 | retrieval search returns results | Phase D-1 |
| G-10 | chat answer non-empty with citations | Phase D-2 |
| G-11 | no duplicate key errors in ingestion logs | grep logs |
| G-12 | no "No EntityManager" in ingestion logs | grep logs |
| G-13 | GIN index active on mv_topic_keywords | EXPLAIN ANALYZE |
| G-14 | vector_index count = chunk count | B-1 SQL |

---

## 9. ARCHITECTURE CONSTRAINTS — ვერ გადახვალ

### Layer violations (instant STOP)

- `domain/` პაკეტი არ შეიცავს Spring annotations-ს (`@Component`, `@Service`, etc.)
- `application/` არ import-ავს `infrastructure/` კლასებს
- Repository interfaces (`*Repository`) არ გადის `application/` layer-ს — ports-ის გავლა სავალდებულო
- `@Value` domain/application layer-ში — NO
- `new SomeInfraClass()` application/domain layer-ში — NO

### Transaction rules

- `@Transactional` მხოლოდ `public` მეთოდებზე (Spring AOP proxy)
- Lazy proxy-ები `@Transactional` boundary-ს გარეთ — NO (`Hibernate.initialize()` boundary-ში)
- Bulk operations → `@Modifying @Query(JPQL)` — derived delete-ი (`deleteBy...`) მხოლოდ სადაც cascade/lifecycle callbacks საჭიროა

### YAML config rules

- Hardcoded URLs, domain names, corpus identifiers კოდში — NO
- ყველა corpus-specific config → `ops/config/corpus/*.yaml`
- ახალი corpus = ახალი YAML, კოდის ცვლილება — NO

### Silent error rules

- `catch (Exception e) { return null; }` — NEVER without logging
- All catch blocks → `log.warn` minimum with document/URL context
- Failed enrichment → `WARN` with reason; never swallowed

### Migrations

- Flyway migration numbers: sequential, no gaps
- Production-deployed migrations — NEVER modify checksum or content
- New migration = new file, new V-number
- `apps/ingestion-service` migrations: `V1..V38` used → next is `V39`
- `apps/backend` migrations: check `apps/backend/src/main/resources/db/migration/`

---

## 10. PIPELINE EXECUTION ORDER

Execute strictly in this order. Do NOT skip phases.

```
Phase A  →  Phase B  →  Phase C  →  Phase D  →  Phase E  →  Phase F  →  Phase G
(Bugs)     (DB verify)  (Services)  (E2E test)  (DB index)  (Silent)   (Gates)
```

**A-1 is prerequisite for everything** — geostat-portal policy fallback means all crawls since startup used permissive policy (no excludePatterns). After A-1 fix, restart ingestion-service and re-crawl:

```
POST http://localhost:8093/admin/crawl/geostat-portal
```

Monitor until:
```
[orchestrator] corpus geostat-portal run <UUID> finished: completed
```

Only then proceed to Phase B.

---

## 11. KEY FILE MAP

| Concern | File |
|---|---|
| Corpus policy loading | `apps/ingestion-service/.../parse/profile/CorpusConfigurationLoader.java` |
| Corpus policies | `ops/config/corpus/*-policy.yaml` |
| Corpus parse profiles | `ops/config/corpus/*-parse.yaml` |
| Chunk bulk delete | `apps/ingestion-service/.../repository/ChunkRepository.java` |
| Vector index bulk delete | `apps/ingestion-service/.../repository/VectorIndexRepository.java` |
| Vector indexer (REQUIRES_NEW) | `apps/ingestion-service/.../index/ChunkVectorIndexer.java` |
| Crawl orchestration + locking | `apps/ingestion-service/.../crawl/runner/CrawlRunStore.java` |
| Enrichment pipeline | `apps/ingestion-service/.../enrichment/runner/EnrichmentRunExecutor.java` |
| DB migrations (ingestion) | `apps/ingestion-service/src/main/resources/db/migration/` |
| DB migrations (backend) | `apps/backend/src/main/resources/db/migration/` |
| Retrieval service | `apps/retrieval-service/.../search/QdrantRetrievalService.java` |
| Chat service | `apps/backend/.../application/chat/ChatService.java` |
| Response routing | `apps/backend/.../application/retrieval/ResponseRouter.java` |
| Quality pipeline plan | `docs/plan/quality-pipeline/` (10 files) |
| Ingestion data model | `docs/plan/INGESTION-DATA-MODEL.md` |
| Infra config (dev) | `ops/config/ingestion/.env.dev` |
| Infra config (backend dev) | `ops/config/backend/.env.dev` |

---

## 12. HOW TO START AFTER READING THIS DOC

1. Read rules/skills (Section 0)
2. Check current ingestion-service logs for "permissive fallback" → confirm BUG-1
3. Fix A-1 (CrawlSectionYaml + geostat-portal-policy.yaml)
4. Restart ingestion-service (command in Section 4 C-3)
5. Trigger re-crawl: `POST http://localhost:8093/admin/crawl/geostat-portal`
6. While crawling, investigate enrichment Gemini failures (A-2)
7. Wait for crawl completion → run all Phase B SQL queries
8. Start backend + retrieval (C-1, C-2)
9. Run Phase D E2E tests
10. Apply Phase E migrations
11. Check Phase F silent errors
12. Run Phase G quality gate checklist — ALL must pass
