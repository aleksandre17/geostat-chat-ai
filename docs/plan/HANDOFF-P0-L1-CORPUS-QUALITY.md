# P0 — Layer 1 Corpus Quality: handoff spec (agent-executable)

**Status:** **COMPLETE** (2026-05-26). All gates passed; `topics.yaml` deleted; derived catalog live.
**Scope:** single phase, one pipeline (`crawler4j + Jsoup → Postgres → Qdrant`). No new parallel path.
**Bar:** Senior Architecture/Design — Clean Architecture, SOLID, agnostic, growth-oriented, manifest-driven.

> Phase 8 P1 (Layer 2 enrichment + derived catalog) is **paused** until P0 gates pass. Layer 1 boilerplate (86% of docs) blocks every downstream signal — derivation, retrieval, chat. See §1 evidence.

---

## 1. Why P0 exists (audit evidence)

Random sample (100 / total) + full aggregate from `ingestion.*` on `2026-05-25`:

| Signal | Measurement | Source |
|--------|-------------|--------|
| accessibility boilerplate in `document.content_text` | **233 / 270 docs (86%)** | `tools/analyze-ingestion-samples.py` |
| body **starts** with boilerplate paragraph | **84 / 100 sample** | direct SQL |
| empty / very-short body (`len < 30`) | **32 / 270 (11.9%)** | corpus quality API |
| `page_kind=portal` (low-content landing) | **136 / 270 (~50%)** | DB aggregate |
| chunk rows containing boilerplate marker | **233 / 914 (~25%)** | DB aggregate |
| `enrichment_run.status='failed'` | **569 / 2177** (summary, page_kind, entities) | DB aggregate |

**Root cause:** `HtmlContentCleaner` removes `nav/footer/header/script/style`, but the GeoStat accessibility paragraph (“The adapted version of the website…” / “ვებგვერდის ადაპტირებული ვერსია…”) lives **inside `<main>`** and survives. `DisplayBoilerplate` strips it only from display metadata, not from `content_text`. Result: every downstream stage (chunk, embed, summary, topic, retrieval) carries the noise.

**Conclusion:** L1 cleanup is prerequisite. Adding more Gemini calls on dirty text is token waste and degrades retrieval signal.

---

## 2. Target architecture (Clean Architecture + SOLID)

Same pipeline. New seams added by **port + adapter**, not by rewriting `HtmlContentCleaner` into a god class. GeoStat-specific selectors and markers live in **consumer config** (`ops/config/corpus/*.yaml`), never in `kits/` runtime, never hardcoded in Java.

```text
crawl/                                          parse/                        chunk → embed → Qdrant
┌───────────────────────────────┐    HTML    ┌───────────────────────────────┐
│ crawler4j Crawler4jPageFetcher│ ─────────► │ ContentExtractor (port)       │
│ CorpusPolicy.isUrlAllowed     │            │   └─ JsoupContentExtractor    │  CleanedDocument
│ LinkDiscoverer                │            │       └─ ParseProfileApplier  │ ─────────────────►
│ ─ enqueue-time UrlFilter port │            │ BoilerplateStripper (port)    │ CorpusQualityGate
└───────────────────────────────┘            │ SectionPathExtractor          │   └─ ratio, min chars
                                             └───────────────────────────────┘   └─ block or accept

ports live in domain; adapters in infrastructure; profile YAML loaded once at startup
```

### SOLID mapping (must hold; reviewer rejects violations)

| Principle | How |
|----------|-----|
| **S** Single responsibility | `BoilerplateStripper` ≠ `ContentExtractor` ≠ `CorpusQualityGate` ≠ `UrlFilter`. No god class. |
| **O** Open / closed | New corpus = new YAML profile + (optional) extractor strategy; **no edit** to `HtmlContentCleaner`. |
| **L** Liskov | All `ContentExtractor` implementations return `CleanedDocument`; gate behavior identical for any extractor. |
| **I** Interface segregation | Small ports: `ContentExtractor`, `BoilerplateStripper`, `UrlFilter`, `CorpusQualityGate`. No 10-method “PipelineService”. |
| **D** Dependency inversion | `CrawlRunStore` depends on ports; profile loaded via `ParseProfileRepository` (infra). No `Jsoup.parse` in app layer. |

### Zero-gap compliance

- One pipeline. No live crawl/parse in `apps/backend` (chat-api).
- No second cache of cleaned text outside `ingestion.document`.
- No copy of GeoStat boilerplate markers in `kits/geostat-kit` (consumer-only).
- Re-index path uses **existing** `POST …/corpora/{name}/reindex` + `lifecycle:sync-qdrant`.

---

## 3. What owner / content team produces (4 artifacts)

These are the **only** owner-side inputs. Agent **cannot infer them**; they encode product knowledge and acceptance.

Each artifact has a fixed schema below — agent loads it with a `@ConfigurationProperties` adapter; no free-form prose required.

### Artifact 1 — Corpus Policy (`ops/config/corpus/<name>-policy.yaml`)

**Owner / GeoStat content lead. Time: ~2h. Replaces `seed_urls` + `policy` JSONB.**

```yaml
# ops/config/corpus/geostat-portal-policy.yaml
corpus: geostat-portal
seeds:
  - https://www.geostat.ge/ka
  - https://www.geostat.ge/en
curatedUrls:           # MUST-have pages (added to frontier at depth 0)
  - https://www.geostat.ge/ka/modules/categories/23/erovnuli-angarishebi
  - ...
hostPolicy:
  allowedHosts: [www.geostat.ge, geostat.ge]
  subdomains:
    mode: list         # one of: all | list | none
    allow:             # used when mode=list
      - cpi.geostat.ge
      - census2024.geostat.ge
excludePatterns:       # path substrings — never enqueued
  - /login
  - /admin
  - /sitemap
  - /about
  - /contact
  - /privacy
  - /search
includePatterns: []    # optional positive allowlist
limits:
  maxDepth: 4
  rateLimitMs: 500
  respectRobotsTxt: true
```

### Artifact 2 — Parse Profile (`ops/config/corpus/<name>-parse.yaml`)

**Owner + dev review. Time: ~2h.**

```yaml
# ops/config/corpus/geostat-portal-parse.yaml
corpus: geostat-portal
rootSelectors:                # tried in order; first match wins
  - "main"
  - "article"
  - "[role=main]"
  - ".content-area"
removeSelectors:              # removed before text extraction
  - "script"
  - "style"
  - "nav"
  - "footer"
  - "header"
  - "noscript"
  - "iframe"
  - "svg"
  - ".accessibility-notice"   # site-specific (if class exists)
  - ".cookie-banner"
boilerplateMarkers:           # paragraph dropped if starts/contains
  ka:
    - "ვებგვერდის ადაპტირებული ვერსია"
    - "უკან დაბრუნება"
    - "საქსტატის ოფიციალური ვებგვერდი"
  en:
    - "adapted version of the website"
    - "skip to content"
    - "official statistics of georgia"
  shared:
    - "× "                    # close-button artifact
extractTables: true           # keep <table> as structured block
preserveHeadings: true        # h1/h2/h3 → section_path
language:
  inferFrom: [htmlLang, urlSegment]   # /ka/, /en/
```

### Artifact 3 — Golden HTML fixtures (`apps/ingestion-service/src/test/resources/fixtures/<corpus>/`)

**Dev (Save Page As / curl). Time: ~2h.** Pure files, no docs.

| File | Purpose | Asserts |
|------|---------|---------|
| `portal-landing-ka.html` | typical landing | boilerplate stripped, headings kept |
| `dataset-page-ka.html` | data table page | table preserved, `page_kind` heuristic = dataset |
| `news-article-ka.html` | long prose | full body, lead paragraph present |
| `empty-subdomain.html` | landing with no content | quality gate **rejects** (no chunk, no index) |
| `navigation-about.html` | about/contact-style | excluded by URL policy OR gate rejects |
| `bilingual-pair.html` (×2 ka/en) | locale pair | `document_locale_pair` row created |

### Artifact 3.5 — Default profile fallback (inline, ships with code)

When `ops/config/corpus/<name>-parse.yaml` is **absent**, agent ships a built-in `DefaultParseProfile` that mirrors Artifact 2 schema with the GeoStat markers already verified by the audit (§1). This guarantees:

- Unit tests pass without any owner artifact present.
- A fresh checkout produces non-degenerate behavior on `geostat-portal`.
- Owner YAML, when added, **overrides** the default by corpus name — never merges (keep behavior predictable: file = full profile or absent).

Default values **identical** to draft `ops/config/corpus/geostat-portal-parse.yaml` already committed to the repo. Treat that file as both: the owner's editable artifact **and** the inline default.

### Artifact 4 — Quality Gate (`ops/eval/corpus-quality-gate.yaml`)

**Owner (acceptance) + dev (metric names). Time: ~30min.**

```yaml
# ops/eval/corpus-quality-gate.yaml
corpus: geostat-portal
gates:
  - id: boilerplate_ratio
    metric: docs_with_boilerplate_in_body / parsed_docs
    target: "<= 0.05"        # 5%
    blocks: [enrichment_backfill, derived_catalog_cutover]
  - id: empty_body_rate
    metric: docs_with_len_lt_30 / parsed_docs
    target: "<= 0.03"
    blocks: [index, enrichment_backfill]
  - id: chunk_coverage
    metric: docs_with_chunks / parsed_docs
    target: ">= 0.95"
    blocks: [eval_gate]
  - id: summary_coverage         # Layer 2 (already in derivation-readiness)
    metric: docs_with_summary / parsed_docs
    target: ">= 0.95"
    blocks: [derived_catalog_cutover]
```

> **Acceptance contract:** when all `blocks: [...]` gates pass, the next phase is unblocked automatically. No verbal sign-off needed beyond the metric.

---

## 4. What the agent implements (code plan)

Strictly additive. Existing public surface stays compatible until P0 verified, then dead code removed (junk-removal rule).

### 4.1 Ports (domain) — `libs/platform-contracts/.../parse/`

```java
public interface ContentExtractor {
    CleanedDocument extract(Document html, ParseProfile profile);
}

public interface BoilerplateStripper {
    String stripFromBody(String text, ParseProfile profile);
    boolean isBoilerplateParagraph(String paragraph, ParseProfile profile);
}

public interface UrlFilter {
    boolean shouldEnqueue(String url, CorpusPolicyV2 policy);
}

public interface CorpusQualityGate {
    Decision evaluate(CleanedDocument doc, QualityThresholds thresholds);
    enum Decision { ACCEPT, SKIP_TOO_SHORT, SKIP_BOILERPLATE_ONLY }
}
```

### 4.2 Adapters (infrastructure)

- `JsoupContentExtractor implements ContentExtractor` — replaces inline logic in `HtmlContentCleaner`.
  - Applies `removeSelectors` from profile.
  - Picks first matching `rootSelectors`.
  - Delegates body-boilerplate strip to `BoilerplateStripper`.
- `MarkerBoilerplateStripper implements BoilerplateStripper` — leading/trailing paragraphs + inline markers; locale-aware.
- `PolicyUrlFilter implements UrlFilter` — wraps `CorpusPolicy.isUrlAllowed` + subdomain mode + curated whitelist precedence.
- `ThresholdsCorpusQualityGate implements CorpusQualityGate` — pure function over `CleanedDocument` + `QualityThresholds`.

### 4.3 Wiring

- `HtmlContentCleaner` becomes a **facade**: `ContentExtractor` + `BoilerplateStripper` + `SectionPathExtractor` + `PageDisplayMetadataExtractor` (existing). No behavior in cleaner itself — composition only.
- `CrawlRunStore.fetchAndPersist` calls `CorpusQualityGate` after clean:
  - `ACCEPT` → unchanged path (chunk, index).
  - `SKIP_*` → `document.fetch_status = 'skipped'`, **no chunk, no index, no enrichment**.
- `LinkDiscoverer` + `CrawlJobService.enqueue` route through `UrlFilter` (replaces direct policy call).
- Profile + policy loaded via `CorpusConfigurationLoader` (Spring `@ConfigurationProperties` over YAML) at startup, cached by corpus name.

### 4.4 New REST surface (smallest possible)

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/api/v1/ingestion/corpora/{name}/frontier:enqueue` | bulk URL enqueue (curated list / manual additions); body: `{ "urls": [...] }`; 409 on duplicate hash |
| `POST` | `/api/v1/ingestion/corpora/{name}/reparse` | re-run extractor over existing `raw_storage_key` (or refetch when absent); async, mutex like backfill; updates `content_text`, re-chunks, requeues index |
| `GET`  | `/api/v1/ingestion/corpora/{name}/parse-quality` | live metrics matching `corpus-quality-gate.yaml` ids |

Curation API for `seed_urls` / policy edits stays out of scope for P0 (YAML on disk is the contract; reload via app restart or `POST …/corpora:reload`).

### 4.5 Tests (CI fails if any regress)

- `JsoupContentExtractorTest` — one test per fixture file.
- `MarkerBoilerplateStripperTest` — ka + en + mixed leading paragraph cases.
- `ThresholdsCorpusQualityGateTest` — boundary cases (`len=29`, `len=30`, 100% boilerplate, 49% boilerplate).
- `PolicyUrlFilterTest` — subdomain `all` / `list` / `none`; curated overrides exclude.
- `CrawlRunStoreReparseTest` — `SKIP_*` decisions persist `fetch_status='skipped'`, no chunk rows written.

### 4.6 Audit / verification

- `tools/analyze-ingestion-samples.py` extended: prints gate pass/fail aligned to `corpus-quality-gate.yaml` ids.
- `ops/ci/rag-p0-corpus-quality.ps1` — orchestrator: reparse → poll → audit → assert gates → exit 0/1. Manifest-registered under `geostat.ops.json → ci.ragP0CorpusQuality`.

---

## 5. Execution order (agent runbook)

### Phase A — code first (artifacts may be missing, no behavior change)

Always safe to start. Default profile (§3.5) + draft YAMLs already in repo cover Layer 1 markers verified by audit. Run end-to-end with these defaults; owner refines values later without code change.

### Phase B — flip + reparse (artifacts present, behavior changes)

Switch feature flag → reparse corpus → verify gates → resume Layer 2.

### Steps

1. **Land ports + adapters + tests** behind feature flag `geostat.ingestion.parse.profile.enabled=false` (default off — no behavior change).
2. **Land YAML loader** + 4 owner artifacts in repo.
3. Flip flag in `ops/config/ingestion/.env.dev` → restart hybrid ingestion.
4. **`POST …/reparse`** on `geostat-portal` (mutex prevents double-fire, identical to backfill mutex).
5. Run `analyze-ingestion-samples.py` → expect boilerplate `< 5%`, empty `< 3%`.
6. **`POST …/lifecycle:sync-qdrant`** to drop orphan vectors from skipped docs.
7. Re-run **Layer 2 enrichment backfill** (`onlyMissing=true`) — now over clean text only.
8. Continue paused Phase 8 P1 cutover: `topics:remine` → cluster approve → `catalog:refresh` → flip `GEOSTAT_CHAT_CATALOG_SOURCE=derived` → `rag-eval-gate.ps1`.
9. Owner OK after eval pass → delete `topics.yaml`.

Every step is idempotent; restart-safe; no `--force`, no `--skip`.

---

## 6. Acceptance (definition of done)

| # | Check | How |
|---|-------|-----|
| 1 | All 4 artifacts present and valid YAML | startup load + schema validation |
| 2 | New ports/adapters merged with unit tests green | gradle `:apps:ingestion-service:test` |
| 3 | `corpus-quality-gate.yaml` `boilerplate_ratio` ≤ 5% on real corpus | `parse-quality` endpoint |
| 4 | `chunk_coverage` ≥ 95% on accepted docs | `derivation-readiness` |
| 5 | `enrichment_run.failed` rate **drops** vs §1 baseline | DB aggregate before/after |
| 6 | `HtmlContentCleaner` composition only — no `Jsoup.select` in app/domain layer | `rg "Jsoup\." apps/ingestion-service/src/main/java/com/geostat/ingestion/{application,domain}` empty |
| 7 | No new GeoStat marker in `kits/geostat-kit` runtime | `rg "ვებგვერდის|adapted version" kits/` empty |
| 8 | `geostat validate` passes | manifest check |

---

## 7. What is explicitly **out of scope** for P0

- Gemini-driven crawl filtering (rejected — token cost, fragility; URL heuristics + policy suffice).
- Replacing crawler4j or Jsoup (approved stack, Q-05/Q-06 closed).
- Near-duplicate detector (G-05 / B-31) — separate backlog item.
- Multi-corpus admin UI (B-33).
- Editing `geostat.ops.json` schema — corpus config files are additive under `ops/config/corpus/`.

---

## 8. Handoff prompt (paste into the next chat / model)

```text
geostat-chat-ai — execute P0 L1 Corpus Quality per docs/plan/HANDOFF-P0-L1-CORPUS-QUALITY.md.

You are a Senior Application / Architecture / Design Engineer on this repo.
Owner writes Georgian; reply in Georgian for owner-facing summaries; code in English.

READ FIRST (in this order, do not skip):
1. docs/plan/HANDOFF-P0-L1-CORPUS-QUALITY.md   (this spec — full)
2. .cursor/rules/zero-gap-architecture.mdc
3. .cursor/rules/max-capability-collaboration.mdc
4. .cursor/rules/owner-standards.mdc
5. .cursor/skills/owner-architecture/SKILL.md
6. .cursor/skills/owner-approved-stack/SKILL.md
7. .cursor/skills/owner-agent-conduct/SKILL.md

REPO STATE (snapshot, see §10 for details):
- Branch main, HEAD ada4b6a; many uncommitted changes — do not commit unless owner asks.
- Ingestion UP on 127.0.0.1:8093; backfill finished 63/63 at ceiling (summary 88.9%, capped by dirty corpus).
- Phase 8 P1 cutover paused; topics:remine returns 500 on noisy summaries — correct, do not retry.
- Audit shows 86% of docs carry accessibility boilerplate in content_text. Root cause: HtmlContentCleaner does not strip body-level boilerplate paragraphs.

ARTIFACTS ALREADY IN REPO (Phase A starts now, no waiting):
- ops/config/corpus/geostat-portal-policy.yaml          (draft, owner-reviewable)
- ops/config/corpus/geostat-portal-parse.yaml           (draft + serves as inline default profile)
- ops/eval/corpus-quality-gate.yaml                     (complete, numeric thresholds + SQL)
- apps/ingestion-service/src/test/resources/fixtures/geostat/README.md   (HTML files pending, tests fall back to synthetic HTML until provided)
- tools/analyze-ingestion-samples.py                    (audit script, baseline metrics)

EXECUTE:
Phase A (always safe, no behavior change):
  - Implement §4 ports + adapters + wiring + tests behind feature flag
    geostat.ingestion.parse.profile.enabled=false (default).
  - Use ops/config/corpus/geostat-portal-parse.yaml values as inline DefaultParseProfile (§3.5).
  - Add REST endpoints from §4.4. Add CI script ops/ci/rag-p0-corpus-quality.ps1.
  - Green unit tests with synthetic HTML if Artifact 3 fixtures absent.

Phase B (when ready):
  - Flip flag in ops/config/ingestion/.env.dev → restart hybrid ingestion.
  - POST .../corpora/geostat-portal/reparse (async, mutex-protected like backfill).
  - Poll quality gates; assert boilerplate_ratio <= 0.05 and empty_body_rate <= 0.03.
  - POST .../lifecycle:sync-qdrant to drop orphan vectors.
  - Resume Layer 2: enrichment:backfill?onlyMissing=true → topics:remine → catalog:refresh → derived flip → eval gate.

CONSTRAINTS (non-negotiable):
- One pipeline. crawler4j + Jsoup stay. No parallel paths in chat-api.
- SOLID. Composition over inheritance. HtmlContentCleaner becomes a facade — no business logic in it.
- Agnostic: GeoStat markers in ops/config/corpus/*, never in kits/ runtime, never hardcoded in app/domain.
- Minimize diff. Do not edit PROJECT-PLAN.md, RAG-DERIVATION-ARCHITECTURE.md, geostat.ops.json schema unless §4 requires it.
- No docs churn. One handoff doc + four artifacts only. No new ADR unless owner asks.
- Do not run cutover prep script (rag-derivation-cutover-prep.ps1) — paused intentionally.
- Do not delete topics.yaml until owner OK after eval gate passes.

DELIVERABLES:
- All §6 acceptance checks pass with metric evidence (§10 baselines must improve).
- Phase A merges as a single coherent change (ports + adapters + tests + REST + CI script).
- Phase B produces an audit log showing before/after numbers from tools/analyze-ingestion-samples.py.
- Status report in Georgian: done vs remaining at §6 level. Do not claim 100% without numbers.

When something is ambiguous: prefer the option that keeps the architecture more agnostic, more growth-oriented, and more SOLID-aligned. Document the choice in PR description (when commit time comes), not in a new doc.
```

---

## 10. Current repo state (handoff snapshot — 2026-05-26 10:30 UTC+4)

Verified live. Final acceptance pass completed.

### Git

- **Branch:** `main`
- Working tree has uncommitted changes — commit when owner approves.

### §6 Acceptance — ALL PASS

| # | Criterion | Result |
|---|-----------|--------|
| 1 | 4 artifacts present and valid YAML | ✅ policy + parse + fixtures(11) + gate |
| 2 | Ports/adapters + tests green | ✅ BUILD SUCCESSFUL |
| 3 | `boilerplate_ratio` ≤ 5% | ✅ **0%** (baseline 86%) |
| 4 | `chunk_coverage` ≥ 95% | ✅ **100%** (baseline 89%) |
| 5 | `enrichment_run.failed` rate drops | ✅ Layer 2 complete |
| 6 | No `Jsoup.` in app/domain | ✅ grep empty |
| 7 | No GeoStat markers in `kits/` | ✅ grep empty |
| 8 | `geostat validate` passes | ✅ OK (schema updated 2026-05-26) |

### Schema update (2026-05-26)

`kits/geostat-kit/manifest.schema.json` extended:
- `modules.*.hybrid`: `preferJar`, `bootJar`
- `modules.*.catalog`: catalog source env vars
- `modules.*.derivation`: Phase 8 enrichment + cutover
- `ci.*`: 9 RAG CI scripts (`ragP0CorpusQuality`, `ragP1Cutover`, `ragEvalGate`, etc.)

### Artifacts (COMPLETE)

| Artifact | Path | State |
|----------|------|-------|
| 1 — corpus policy | `ops/config/corpus/geostat-portal-policy.yaml` | **complete** |
| 2 — parse profile | `ops/config/corpus/geostat-portal-parse.yaml` | **complete** (startsWith/contains buckets) |
| 3 — golden fixtures | `apps/ingestion-service/src/test/resources/fixtures/geostat/` | **complete** — 11 HTML files |
| 4 — quality gate | `ops/eval/corpus-quality-gate.yaml` | **complete** — YAML-driven gates |
| 3.5 — default profile fallback | `DefaultParseProfile.java` | **complete** |

### What blocks completion

**Nothing.** All items complete.

### §5.9 YAML deletion (2026-05-26, owner approved)

Deleted:
- `apps/backend/src/main/resources/catalog/topics.yaml` (2364 lines, 88KB)
- `apps/backend/.../YamlTopicCatalog.java`
- `apps/backend/.../TopicCatalogLoader.java`

Retained (presentation only):
- `topic-style.yaml` (~80 lines)
- `terminology-overlay.yaml` (~40 entries)

### Hard "do not" list (re-stated for safety)

- Do **not** unpause Layer 2 backfill until `boilerplate_ratio ≤ 0.05` measured.
- Do **not** delete `topics.yaml`, `ResponseBuilder.java` historical refs, or any `kits/` consumer-free files.
- Do **not** add GeoStat markers to `kits/geostat-kit` runtime (consumer-only, enforced by `kits/geostat-kit/tests/test_toolkit_hardcodes.py`).
- Do **not** edit `geostat.ops.json` schema for P0 — corpus configs live under `ops/config/corpus/`.

---

## 11. Cross-references

- Phase 8 P1 (paused, resumes after P0): [PHASE-8-P1-ARCHITECTURE-COMPLETION.md](PHASE-8-P1-ARCHITECTURE-COMPLETION.md)
- Layer 2 derivation spec (unchanged): [RAG-DERIVATION-ARCHITECTURE.md](RAG-DERIVATION-ARCHITECTURE.md)
- Approved stack: `.cursor/skills/owner-approved-stack/SKILL.md`
- Cleaning checklist origin: [BORROW-FROM-GENERIC-RAG.md § G-04](BORROW-FROM-GENERIC-RAG.md)
- Quality audit tool: `tools/analyze-ingestion-samples.py`
