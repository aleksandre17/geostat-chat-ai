# Query Understanding & Retrieval Layer — Gap Analysis

> **Senior directive — read every line before touching any file.**
> This document covers Layer 3 (Query Understanding Pipeline) and Layer 4 (Retrieval/Response Routing).
> These two layers directly determine what the user sees. Bad ingestion = bad data.
> Bad query understanding = good data never found.
>
> Findings are from reading actual source code. Every bug below is confirmed, not assumed.

> **Session 2026-05-27:** Phases Q-ARCH, Q-B, Q-C, Q-D, Q-E, Q-F, Q-A implemented.
> **Session 2026-05-27 (cont.):** GAP-ARCH-Q-02 Fix 2 ✅ — `QueryRouter` keyword lists extracted to `catalog/route-keywords.yaml`; wired as `@Bean` in `QueryUnderstandingConfiguration`; `@Component` removed.
> Remaining: Section 14 (ScoredClusterMatcher — major future feature).

---

## Table of Contents

1. [CRITICAL FINDING — Pipeline is Disabled in Production](#1-critical-finding--pipeline-is-disabled-in-production)
2. [Layer 3 — Query Understanding Bugs](#2-layer-3--query-understanding-bugs)
3. [Layer 4 — Retrieval & Confidence Bugs](#3-layer-4--retrieval--confidence-bugs)
4. [Architecture Observation — Two Parallel Intent Systems](#4-architecture-observation--two-parallel-intent-systems)
5. [Phase Q-A — Enable Pipeline + Fix Blockers](#5-phase-q-a--enable-pipeline--fix-blockers)
6. [Phase Q-B — Heuristic Classifier Fixes](#6-phase-q-b--heuristic-classifier-fixes)
7. [Phase Q-C — Entity Extractor Expansion](#7-phase-q-c--entity-extractor-expansion)
8. [Phase Q-D — Terminology YAML Expansion](#8-phase-q-d--terminology-yaml-expansion)
9. [Phase Q-E — Retrieval Confidence Calibration](#9-phase-q-e--retrieval-confidence-calibration)
10. [Execution Order](#10-execution-order)
11. [Acceptance Criteria](#11-acceptance-criteria)

---

## 1. CRITICAL FINDING — Pipeline is Disabled in Production

### What was found in ChatService.java (lines 263–269):

```java
// ChatService.buildContext():
if (queryUnderstandingProperties.isEnabled()) {
    AnalyzedQuery analyzed = queryUnderstandingPipeline.analyze(trimmed, locale);
    retrievalQuery = analyzed.retrievalText();
    intent = queryIntentMapper.toChatIntent(analyzed.intent());
} else {
    retrievalQuery = spellFixer.fix(trimmed, locale);  // ← ONLY this runs
}
```

### What QueryUnderstandingProperties default values are (lines 9–16):

```java
private boolean enabled           = false;  // ← MASTER SWITCH IS OFF
private boolean geminiIntentEnabled = false;
private boolean spellFixEnabled   = false;
private boolean geminiEntityEnabled = false;
private boolean llmExpandEnabled  = false;
```

### Impact:

```
User types: "რა იყო მშპ 2023 წელს?"
              ↓
SpellFixer.fix() → "რა იყო მშპ 2023 წელს?" (maybe nothing fixed)
              ↓
retrievalQuery = "რა იყო მშპ 2023 წელს?"  ← raw message sent to Qdrant
              ↓
Qdrant embeds raw text → finds chunks about "რა", "იყო", "მშპ", "2023"

What does NOT run:
  ✗ Intent classification      (LATEST? FACTUAL? COMPARE?)
  ✗ Entity extraction          (entities: [YEAR=2023, INDICATOR=GDP])
  ✗ Query expansion            ("მთლიანი შიდა პროდუქტი", "gross domestic product")
  ✗ retrievalText enrichment   (query + entities + expansions concatenated)
```

**The entire query understanding pipeline is a dead code path in production.**

### Fix (Phase Q-A — one line):

In `application-custom.yml`:
```yaml
geostat:
  chat:
    query-understanding:
      enabled: ${QUERY_UNDERSTANDING_ENABLED:true}   # ← change default from false to true
```

But: enabling it before fixing the bugs below will cause misclassification. Fix bugs first.
Priority order: Q-B → Q-C → Q-D → then Q-A (enable).

---

## 2. Layer 3 — Query Understanding Bugs

### BUG-Q-01 — "უმუკველწვერა" is not a Georgian word (entity extractor typo)

**File:** `HeuristicQueryEntityExtractor.java` line 20

```java
// CURRENT (BROKEN):
Pattern.compile("\\b(gdp|cpi|inflation|unemployment|export|import|fdi|mshp|მშპ|ინფლაცია|უმუკველწვერა|ექსპორტი|იმპორტი)\\b",
    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
```

**"უმუკველწვერა"** is not a real Georgian word. The correct Georgian word for unemployment is
**"უმუშევრობა"** (umushevroba). No Georgian user will ever type "უმუკველწვერა".
→ Unemployment-related queries in Georgian are never entity-enriched.

Also in `normalizeIndicator()` line 45:
```java
case "უმუკველწვერა" -> "UNEMPLOYMENT";  // ← dead code, never matches
```

**Fix:**
```java
// AFTER — correct Georgian:
Pattern.compile(
    "\\b(gdp|cpi|inflation|unemployment|export|import|fdi|mshp|" +
    "მშპ|ინფლაცია|უმუშევრობა|უმუშეველ|ექსპორტი|იმპორტი)\\b",
    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

// In normalizeIndicator():
case "უმუშევრობა", "უმუშეველ", "unemployment" -> "UNEMPLOYMENT";
```

---

### BUG-Q-02 — Year keywords hardcoded to 2024/2025, misses 2026+

**File:** `HeuristicIntentClassifier.java` lines 33–40

```java
// CURRENT:
if (containsAny(lower, "latest", "recent", "2024", "2025", "ბოლო", "ახალი მონაცემ")) {
    return QueryIntentKind.LATEST;
}
```

If user types "2026 წლის მონაცემები" → falls through to LOOKUP, not LATEST.
The year REGEX in entity extractor (`20[0-3]\d`) already handles years up to 2039.
The heuristic intent classifier should not hardcode specific years.

**Fix:**
```java
// AFTER — remove hardcoded years, use only semantic cues:
if (containsAny(lower, "latest", "recent", "ბოლო", "ახალი მონაცემ", "მიმდინარე", "current")) {
    return QueryIntentKind.LATEST;
}
// Year-specific intent is now handled by entity extractor (YEAR entity)
// which already matches 1990-2039 with regex.
```

---

### BUG-Q-03 — " და " (AND) triggers COMPARE for any compound query

**File:** `HeuristicIntentClassifier.java` line 15

```java
// CURRENT:
if (containsAny(lower, "compare", "versus", "vs", "შედარ", " და ")) {
    return QueryIntentKind.COMPARE;
}
```

**" და "** (Georgian "and") is extremely common. Examples that wrongly become COMPARE:
- "მოსახლეობა და შრომის ბაზარი" → COMPARE (wrong — should be LOOKUP/FACTUAL)
- "ექსპორტი და იმპორტი" → COMPARE (actually could be correct)
- "სოფლის მეურნეობა და გარემო" → COMPARE (wrong)

**Fix:** Remove `" და "` from COMPARE triggers. Add a real comparison marker instead:

```java
// AFTER — require explicit comparison language:
if (containsAny(lower, "compare", "versus", "vs", "შედარ",
        "განსხვავება", "მეტია თუ ნაკლები", "vs.", "difference between")) {
    return QueryIntentKind.COMPARE;
}
```

---

### BUG-Q-04 — "open" triggers NAVIGATION, catches "open data" queries

**File:** `HeuristicIntentClassifier.java` lines 43–61

```java
// CURRENT — includes "open":
if (containsAny(lower, "show me", "where can i find", ..., "open", "go to", ...)) {
    return QueryIntentKind.NAVIGATION;
}
```

"open data" → NAVIGATION (wrong — should be LOOKUP)
"open statistics" → NAVIGATION (wrong)
"open source" → NAVIGATION (wrong)

**Fix:** Remove `"open"` as a standalone navigation trigger:

```java
// AFTER — remove standalone "open":
if (containsAny(lower, "show me", "where can i find", "where to find",
        "give me", "download", "find", "go to", "navigate", "portal",
        "open portal", "open page",    // ← keep compound forms only
        "მაჩვენე", "სად ვნახო", "სად არის", "პორტალი", "გადავიდე", "გახსენი")) {
    return QueryIntentKind.NAVIGATION;
}
```

---

### GAP-Q-01 — No STATISTICAL intent in QueryIntentKind

**File:** `QueryIntentKind.java`

```java
public enum QueryIntentKind {
    FACTUAL, LOOKUP, COMPARE, DEFINITION, LATEST, NAVIGATION, SMALLTALK
    // ← no STATISTICAL / DATA_REQUEST type
}
```

GeoStat is a statistics agency. A query like "population by region 2023" should be classified
as a statistical data request — with different retrieval behavior (prioritize data tables,
structured content) vs. FACTUAL which expects prose explanation.

**Fix:** Add STATISTICAL intent:

```java
// QueryIntentKind.java:
public enum QueryIntentKind {
    FACTUAL,       // "what is GDP?"
    STATISTICAL,   // "show me population by region 2023" ← NEW
    LOOKUP,        // generic lookup
    COMPARE,       // "compare GDP 2022 vs 2023"
    DEFINITION,    // "define inflation"
    LATEST,        // "latest data on exports"
    NAVIGATION,    // "where to find the portal"
    SMALLTALK      // "hello", "thanks"
}
```

Add to `HeuristicIntentClassifier` before NAVIGATION check:
```java
// Add STATISTICAL check (before NAVIGATION):
if (containsAny(lower,
        "მონაცემ", "სტატისტიკ", "ინდიკატ", "მაჩვენებ",
        "data", "statistics", "indicator", "figure", "number",
        "show", "table", "chart") && !containsAny(lower, "portal", "პორტალი")) {
    return QueryIntentKind.STATISTICAL;
}
```

Also update `QueryIntentMapper` to map STATISTICAL to the correct `QueryIntent` in ChatService.

---

### GAP-Q-02 — Terminology YAML has only 11 entries for a statistical agency

**File:** `apps/backend/src/main/resources/catalog/terminology-overlay.yaml`

Current entries cover: GDP/მშპ, CPI/inflation, FDI, SNA, unemployment, export, import,
SDG, statistics, director. That is roughly 5% of GeoStat's statistical domain.

Missing (examples):
```
population / მოსახლეობა / census / აღწერა
agriculture / სოფლის მეურნეობა / crop / მოსავლიანობა
education / განათლება / school / სკოლა / university
healthcare / ჯანდაცვა / hospital / სიკვდილიანობა
environment / გარემო / emissions / ემისია
energy / ენერგეტიკა / electricity / ელექტროენერგია
construction / მშენებლობა / housing / საცხოვრებელი
tourism / ტურიზმი / visitors / ვიზიტორი
wages / ხელფასი / salary / შემოსავალი
poverty / სიღარიბე / social / სოციალური
gender / გენდერი / women / ქალი
```

See Phase Q-D below for expansion instructions.

---

### GAP-Q-03 — HeuristicQueryEntityExtractor covers only 6 of ~200 statistical indicators

**File:** `HeuristicQueryEntityExtractor.java` line 20

The INDICATOR regex covers: GDP, CPI, inflation, unemployment, export, import, FDI.
GeoStat has indicators across 20+ domains. Population figures, agricultural yields,
construction permits, energy consumption, tourism arrivals — none are extracted.

When entity extraction misses these: `composeRetrievalText` has no additional terms →
retrieval query = normalized query only → lower recall.

See Phase Q-C below for expansion instructions.

---

## 3. Layer 4 — Retrieval & Confidence Bugs

### BUG-R-01 — Confidence thresholds not calibrated to embedding model

**File:** `DefaultConfidenceAssessor.java` lines 12–14

```java
private static final float HIGH_THRESHOLD   = 0.75f;
private static final float MEDIUM_THRESHOLD = 0.55f;
private static final float LOW_THRESHOLD    = 0.35f;
private static final float GAP_THRESHOLD    = 0.05f;
```

**Critical context from `application-custom.yml`:**

```yaml
embedding:
  provider: ${EMBEDDING_PROVIDER:hash-v1}   # ← DEFAULT IS hash-v1
```

With `hash-v1` (hash-based embedding, not semantic):
- Cosine similarity scores are uniformly ~0.0 (random hashes have near-zero cosine similarity)
- All queries → topScore ≤ 0.35 → `RetrievalConfidence.NONE`
- `ResponseRouter` → `REFUSE_SUGGEST_TOPICS`
- **System refuses every statistical query when hash-v1 embedding is active**

With Gemini embedding (768d):
- Typical relevant content scores: 0.65–0.90
- The thresholds (0.75/0.55/0.35) were calibrated for Gemini-scale cosine similarity
- But they are hardcoded constants — no way to reconfigure without code change

**Fix:** Make thresholds configurable via properties:

```java
// DefaultConfidenceAssessor.java — inject from properties:
@ConfigurationProperties(prefix = "geostat.chat.retrieval.confidence")
public record ConfidenceThresholds(
    float high,   // default: 0.75
    float medium, // default: 0.55
    float low,    // default: 0.35
    float gap     // default: 0.05
) {
    public static ConfidenceThresholds defaults() {
        return new ConfidenceThresholds(0.75f, 0.55f, 0.35f, 0.05f);
    }
}
```

In `application-custom.yml`:
```yaml
geostat:
  chat:
    retrieval:
      confidence:
        high:   ${CONFIDENCE_HIGH:0.75}
        medium: ${CONFIDENCE_MEDIUM:0.55}
        low:    ${CONFIDENCE_LOW:0.35}
        gap:    ${CONFIDENCE_GAP:0.05}
```

When switching from hash-v1 to Gemini embeddings — update the env vars, no code change.

---

### BUG-R-02 — GAP_THRESHOLD (0.05) too strict with clustered results

**File:** `DefaultConfidenceAssessor.java` lines 27–28

```java
if (topScore > HIGH_THRESHOLD && gap > GAP_THRESHOLD) {
    return RetrievalConfidence.HIGH;
}
```

If top result scores 0.82 and second result scores 0.79:
- topScore = 0.82 > 0.75 ✓
- gap = 0.03 < 0.05 ✗ → **classified as MEDIUM, not HIGH**

After cross-encoder reranking (retrieval-service), results in a good corpus cluster close
together in score space. Strict gap requirement demotes well-grounded answers to MEDIUM
→ `ResponseRouter` → `ANSWER_WITH_SUGGESTIONS` instead of `ANSWER_WITH_CITATIONS`.

**Fix:** Remove gap requirement for HIGH. Use only topScore:

```java
// AFTER:
if (topScore > HIGH_THRESHOLD) {
    return RetrievalConfidence.HIGH;
}
```

Or lower gap threshold: `GAP_THRESHOLD = 0.02f`.

---

### BUG-R-03 — CatalogRagLinkMerger.trimLeadingPartialWord always removes first word

**File:** `CatalogRagLinkMerger.java` lines 149–162

```java
static String trimLeadingPartialWord(String text) {
    if (text == null || text.isEmpty()) { return text; }
    int firstSpace = text.indexOf(' ');
    if (firstSpace < 0) { return text; }
    int rest = text.length() - (firstSpace + 1);
    if (rest < 24) { return text; }
    return text.substring(firstSpace + 1);  // ← removes FIRST WORD always
}
```

**Intent:** skip partial leading words when a chunk starts mid-sentence from chunking.
**Reality:** removes the first word even from well-formed sentence starts:
- "მოსახლეობა 2023 წელს გაიზარდა..." → "2023 წელს გაიზარდა..."
- "სოფლის მეურნეობის სექტორი..." → "მეურნეობის სექტორი..."

**Fix:** Only remove first word if it looks like a partial word (no vowels, or very short,
or ends mid-word):

```java
static String trimLeadingPartialWord(String text) {
    if (text == null || text.isEmpty()) { return text; }
    int firstSpace = text.indexOf(' ');
    if (firstSpace < 0) { return text; }

    String firstWord = text.substring(0, firstSpace);
    // Only trim if firstWord looks like a partial (≤ 3 chars or no Georgian vowel)
    boolean looksPartial = firstWord.length() <= 3
        || !firstWord.chars().anyMatch(c ->
            "აეიოუ".indexOf(c) >= 0 || "aeiouAEIOU".indexOf(c) >= 0);
    if (!looksPartial) {
        return text;  // well-formed word — keep it
    }
    int rest = text.length() - (firstSpace + 1);
    if (rest < 24) { return text; }
    return text.substring(firstSpace + 1);
}
```

---

### GAP-R-01 — looksLikeProse filters statistical/numerical content from snippets

**File:** `CatalogRagLinkMerger.java` line 211–217

```java
static boolean looksLikeProse(String text) {
    if (text == null || text.length() < 20) { return false; }
    long letters = text.chars().filter(Character::isLetter).count();
    return letters >= 20;   // ← needs 20 letter characters
}
```

A statistical snippet like "2023 - 3,245,000. 2022 - 3,198,000." has letters in the year
labels and category names but may have < 20 letters total.

**Fix:** Treat text as usable if it has any prose-like content OR is a data list:

```java
static boolean looksLikeProse(String text) {
    if (text == null || text.length() < 10) { return false; }
    long letters = text.chars().filter(Character::isLetter).count();
    // prose: 20+ letters; or statistical data: has digits AND some letters (for labels)
    if (letters >= 20) { return true; }
    long digits = text.chars().filter(Character::isDigit).count();
    return digits >= 4 && letters >= 4;  // e.g. "2023: 1.2 მლნ"
}
```

---

## 4. Architecture Observation — Two Parallel Intent Systems

**This is a design inconsistency. Document it, then decide on unification.**

### System A: QueryIntentKind (query pipeline)

```
QueryUnderstandingPipeline → IntentClassifier → QueryIntentKind
{FACTUAL, LOOKUP, COMPARE, DEFINITION, LATEST, NAVIGATION, SMALLTALK}
```

### System B: QueryIntent (ChatService routing)

```
QueryRouter → QueryIntent
{CONCEPT, DATA_REQUEST, NAVIGATE, CLARIFY}
```

### How they connect (ChatService lines 261–266):

```java
// With pipeline enabled:
intent = queryIntentMapper.toChatIntent(analyzed.intent());  // A → B mapping

// Without pipeline:
intent = queryRouter.route(trimmed, trimmed.toLowerCase());  // direct B classification
```

### Impact:

When pipeline is disabled (current default), the ChatService uses `QueryRouter` directly.
When enabled, it uses `QueryIntentMapper` to translate from A to B.

The two systems can produce different results for the same input:
```
"download data" →
  QueryRouter (system B): NAVIGATE
  HeuristicIntentClassifier (system A): NAVIGATION → mapped to B?
```

### Decision to add to the plan:

**Do not unify now.** The two-level design is intentional:
- `QueryIntentKind` is for retrieval optimization (should I search broadly or narrowly?)
- `QueryIntent` is for response routing (should I answer, navigate, or clarify?)
These are different concerns. The mapping layer (`QueryIntentMapper`) is correct.

**What to fix:** ensure `QueryIntentMapper` handles the new `STATISTICAL` intent correctly
when it is added (Phase Q-B).

---

## 5. Phase Q-A — Enable Pipeline + Fix Blockers

> **Do this LAST — after Q-B, Q-C, Q-D are done.**
> Enabling pipeline before fixing bugs causes production misclassification.

### Step Q-A-1: Enable pipeline in application-custom.yml

```yaml
geostat:
  chat:
    query-understanding:
      enabled: ${QUERY_UNDERSTANDING_ENABLED:true}   # change default false → true
      gemini-intent-enabled: ${GEMINI_INTENT_ENABLED:false}   # keep off until eval
      spell-fix-enabled: ${SPELL_FIX_ENABLED:false}           # keep off until eval
      gemini-entity-enabled: ${GEMINI_ENTITY_ENABLED:false}
      llm-expand-enabled: ${LLM_EXPAND_ENABLED:false}
```

Heuristic intent + heuristic entity + terminology expansion run without Gemini cost.
Gemini variants stay disabled until eval baseline confirms improvement.

### Step Q-A-2: Verify ChatService uses pipeline when enabled

After enabling, run the `QueryAnalyzeController` endpoint to verify pipeline output:
```
POST /api/v1/query/analyze
{"message": "რა იყო მშპ 2023 წელს?", "locale": "ka"}
```

Expected response:
```json
{
  "original": "რა იყო მშპ 2023 წელს?",
  "normalized": "რა იყო მშპ 2023 წელს?",
  "intent": "FACTUAL",
  "entities": [{"type": "YEAR", "value": "2023"}, {"type": "INDICATOR", "value": "GDP"}],
  "expansions": ["მთლიანი შიდა პროდუქტი", "gross domestic product gdp"],
  "retrievalText": "რა იყო მშპ 2023 წელს? GDP მთლიანი შიდა პროდუქტი gross domestic product gdp"
}
```

---

## 6. Phase Q-B — Heuristic Classifier Fixes

File: `apps/backend/src/main/java/com/geostat/chat/infrastructure/query/HeuristicIntentClassifier.java`

Apply all 4 fixes in sequence. Final state of the file's `classify()` method:

```java
@Override
public QueryIntentKind classify(String message, String normalized, String locale) {
    if (message == null || message.isBlank()) {
        return QueryIntentKind.LOOKUP;
    }
    String lower = normalized == null ? message.toLowerCase() : normalized.toLowerCase();

    // SMALLTALK — check first (fastest exit for non-data queries)
    if (containsAny(lower, "hello", "hi", "thanks", "thank you",
            "გამარჯობა", "მადლობა", "როგორ ხარ")) {
        return QueryIntentKind.SMALLTALK;
    }

    // COMPARE — explicit comparison language only (removed " და ")
    if (containsAny(lower, "compare", "versus", "vs.", "შედარ",
            "განსხვავება", "difference between", "მეტია თუ ნაკლები")) {
        return QueryIntentKind.COMPARE;
    }

    // DEFINITION — before FACTUAL to avoid overlap
    if (containsAny(lower, "definition", "განმარტება", "meaning")) {
        return QueryIntentKind.DEFINITION;
    }

    // FACTUAL — "what is" type questions
    if (containsAny(lower, "what is", "what does", "define", "explain",
            "meaning of", "რა არის", "რას ნიშნავს")) {
        return QueryIntentKind.FACTUAL;
    }

    // LATEST — semantic cues only (no hardcoded years)
    if (containsAny(lower, "latest", "recent", "ბოლო", "ახალი მონაცემ",
            "მიმდინარე", "current")) {
        return QueryIntentKind.LATEST;
    }

    // STATISTICAL — data/statistics queries (before NAVIGATION to avoid "show data" → NAV)
    if (containsAny(lower, "მონაცემ", "სტატისტიკ", "ინდიკატ", "მაჩვენებ",
            "data", "statistics", "indicator", "figure", "number", "table", "chart")
        && !containsAny(lower, "portal", "პორტალი")) {
        return QueryIntentKind.STATISTICAL;
    }

    // NAVIGATION — explicit navigation intent (removed standalone "open", "find")
    if (containsAny(lower, "show me", "where can i find", "where to find",
            "give me", "download", "go to", "navigate", "portal",
            "open portal", "open page",
            "მაჩვენე", "სად ვნახო", "სად არის", "პორტალი", "გადავიდე", "გახსენი")) {
        return QueryIntentKind.NAVIGATION;
    }

    return QueryIntentKind.LOOKUP;
}
```

After adding `STATISTICAL` to `QueryIntentKind`, also update `QueryIntentMapper`:

File: `apps/backend/src/main/java/com/geostat/chat/application/query/QueryIntentMapper.java`

```java
// Add mapping for STATISTICAL:
case STATISTICAL -> QueryIntent.DATA_REQUEST;
```

---

## 7. Phase Q-C — Entity Extractor Expansion

File: `apps/backend/src/main/java/com/geostat/chat/infrastructure/query/HeuristicQueryEntityExtractor.java`

**Step C-1: Fix "უმუკველწვერა" typo and expand INDICATOR pattern:**

```java
// AFTER — corrected and expanded INDICATOR pattern:
private static final Pattern INDICATOR = Pattern.compile(
    "\\b(gdp|cpi|inflation|unemployment|export|import|fdi|" +
    // Georgian indicators:
    "მშპ|mshp|ინფლაცია|უმუშევრობა|უმუშეველ|ექსპორტი|იმპორტი|" +
    // Additional statistical terms:
    "მოსახლეობა|population|სოფლ|სამრეწ|მშენებ|ტურიზ|" +
    "wages|ხელფასი|სიღარიბე|poverty|energy|ენერგეტ)\\b",
    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
```

**Step C-2: Fix normalizeIndicator() and add new mappings:**

```java
private static String normalizeIndicator(String value) {
    return switch (value.toLowerCase(Locale.ROOT)) {
        case "mshp", "მშპ" -> "GDP";
        case "cpi", "ინფლაცია", "inflation" -> "CPI";
        case "უმუშევრობა", "უმუშეველ", "unemployment" -> "UNEMPLOYMENT";
        case "ექსპორტი", "export" -> "EXPORT";
        case "იმპორტი", "import" -> "IMPORT";
        case "მოსახლეობა", "population" -> "POPULATION";
        case "ხელფასი", "wages" -> "WAGES";
        case "სიღარიბე", "poverty" -> "POVERTY";
        default -> value.toUpperCase(Locale.ROOT);
    };
}
```

---

## 8. Phase Q-D — Terminology YAML Expansion

File: `apps/backend/src/main/resources/catalog/terminology-overlay.yaml`

Current: 11 entries. Required: ~40 entries covering GeoStat's statistical domain.
The YAML comment says "≤40 entries" — fill up to the limit:

```yaml
# Existing 11 entries stay unchanged. Add below:

  - triggers: ["მოსახლეობ", "population", "census", "აღწერ"]
    ka: "მოსახლეობა დემოგრაფია"
    en: "population demographics census"

  - triggers: ["სოფლ", "agriculture", "harvest", "მოსავლ"]
    ka: "სოფლის მეურნეობა მოსავლიანობა"
    en: "agriculture harvest crops"

  - triggers: ["განათლებ", "education", "school", "სკოლ", "university"]
    ka: "განათლება სკოლა"
    en: "education school university"

  - triggers: ["ჯანდაცვ", "health", "hospital", "სიკვდილ", "mortality"]
    ka: "ჯანდაცვა სიკვდილიანობა"
    en: "healthcare hospital mortality"

  - triggers: ["გარემო", "environment", "emission", "ემისი"]
    ka: "გარემო ემისია"
    en: "environment emissions climate"

  - triggers: ["ენერგეტ", "energy", "electricity", "ელექტრ"]
    ka: "ენერგეტიკა ელექტროენერგია"
    en: "energy electricity power"

  - triggers: ["მშენებ", "construction", "housing", "საცხოვრ"]
    ka: "მშენებლობა საცხოვრებელი"
    en: "construction housing buildings"

  - triggers: ["ტურიზ", "tourism", "visitor", "ვიზიტ"]
    ka: "ტურიზმი ვიზიტორები"
    en: "tourism visitors travel"

  - triggers: ["ხელფასი", "wages", "salary", "შემოსავ", "income"]
    ka: "ხელფასი შემოსავალი"
    en: "wages salary income earnings"

  - triggers: ["სიღარიბ", "poverty", "social", "სოციალ"]
    ka: "სიღარიბე სოციალური"
    en: "poverty social assistance"

  - triggers: ["გენდ", "gender", "women", "ქალ", "men", "კაც"]
    ka: "გენდერი ქალი კაცი"
    en: "gender women men equality"

  - triggers: ["სამრეწ", "industry", "manufacture", "წარმოებ"]
    ka: "სამრეწველო წარმოება"
    en: "industry manufacturing production"

  - triggers: ["ვაჭრობ", "trade", "commerce", "retail"]
    ka: "სავაჭრო ბრუნვა"
    en: "trade commerce retail"

  - triggers: ["მიგრაცი", "migration", "emigration", "emigrant"]
    ka: "მიგრაცია ემიგრაცია"
    en: "migration emigration immigration"

  - triggers: ["ბიუჯეტ", "budget", "fiscal", "ფისკალ"]
    ka: "სახელმწიფო ბიუჯეტი"
    en: "budget fiscal expenditure revenue"
```

---

## 9. Phase Q-E — Retrieval Confidence Calibration

### Step E-1: Make thresholds configurable

File: `apps/backend/src/main/java/com/geostat/chat/application/retrieval/DefaultConfidenceAssessor.java`

```java
// Add @ConfigurationProperties support:

@Component
public class DefaultConfidenceAssessor implements RetrievalConfidenceAssessor {

    private final float highThreshold;
    private final float mediumThreshold;
    private final float lowThreshold;
    private final float gapThreshold;

    public DefaultConfidenceAssessor(
            @Value("${geostat.chat.retrieval.confidence.high:0.75}") float high,
            @Value("${geostat.chat.retrieval.confidence.medium:0.55}") float medium,
            @Value("${geostat.chat.retrieval.confidence.low:0.35}") float low,
            @Value("${geostat.chat.retrieval.confidence.gap:0.02}") float gap) {
        this.highThreshold   = high;
        this.mediumThreshold = medium;
        this.lowThreshold    = low;
        this.gapThreshold    = gap;
    }

    @Override
    public RetrievalConfidence assess(List<RetrievedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) { return RetrievalConfidence.NONE; }
        double topScore    = chunks.get(0).score();
        double secondScore = chunks.size() > 1 ? chunks.get(1).score() : 0.0;
        double gap         = topScore - secondScore;

        if (topScore > highThreshold && gap > gapThreshold) { return RetrievalConfidence.HIGH; }
        if (topScore > mediumThreshold)                      { return RetrievalConfidence.MEDIUM; }
        if (topScore > lowThreshold)                         { return RetrievalConfidence.LOW; }
        return RetrievalConfidence.NONE;
    }
}
```

Add to `application-custom.yml`:
```yaml
geostat:
  chat:
    retrieval:
      confidence:
        high:   ${CONFIDENCE_HIGH:0.75}
        medium: ${CONFIDENCE_MEDIUM:0.55}
        low:    ${CONFIDENCE_LOW:0.35}
        gap:    ${CONFIDENCE_GAP:0.02}   # lowered from 0.05 → 0.02
```

### Step E-2: Apply looksLikeProse fix and trimLeadingPartialWord fix

Apply BUG-R-03 and GAP-R-01 fixes from Section 3.

---

## 10. Execution Order

```
IMPORTANT: Enable pipeline (Q-A) LAST, after all bug fixes are deployed.
Enabling before fixes = production misclassification.

Q-B → Q-C → Q-D → Q-E → Q-A

Step-by-step:

Phase Q-B (heuristic classifier — no tests affected):
  B-1: Fix HeuristicIntentClassifier:
       - Remove " და " from COMPARE triggers
       - Remove standalone "open" from NAVIGATION triggers
       - Remove hardcoded "2024", "2025" from LATEST triggers
       - Add STATISTICAL intent check
  B-2: Add STATISTICAL to QueryIntentKind enum
  B-3: Update QueryIntentMapper with STATISTICAL → DATA_REQUEST mapping
  ↓ verify: unit tests in HeuristicIntentClassifierTest pass

Phase Q-C (entity extractor):
  C-1: Fix "უმუკველწვერა" → "უმუშევრობა" in INDICATOR pattern
  C-2: Expand INDICATOR pattern with new domains
  C-3: Update normalizeIndicator() switch
  ↓ verify: HeuristicQueryEntityExtractorTest: Georgian unemployment query extracts UNEMPLOYMENT entity

Phase Q-D (terminology YAML):
  D-1: Add 15 new entries to terminology-overlay.yaml
  ↓ verify: YamlTerminologyQueryExpander test: "მოსახლეობა" → expands to "population demographics census"

Phase Q-E (confidence calibration):
  E-1: Inject thresholds via @Value in DefaultConfidenceAssessor
  E-2: Set gap: 0.02 in application-custom.yml
  E-3: Apply trimLeadingPartialWord fix
  E-4: Apply looksLikeProse fix
  ↓ verify: DefaultConfidenceAssessorTest with boundary scores

Phase Q-A (ENABLE PIPELINE — last):
  A-1: Set QUERY_UNDERSTANDING_ENABLED default to true in application-custom.yml
  A-2: Test QueryAnalyzeController endpoint with sample queries
  A-3: Monitor logs for intent classification distribution
  A-4: Verify retrievalText is enriched vs. raw message
```

---

## 11. Acceptance Criteria

### Phase Q-B complete when:
- [ ] `"open data"` → intent = LOOKUP (not NAVIGATION)
- [ ] `"მოსახლეობა და სახლები"` → intent = LOOKUP (not COMPARE)
- [ ] `"show me population data"` → intent = STATISTICAL (not NAVIGATION)
- [ ] `QueryIntentKind.STATISTICAL` exists in enum
- [ ] `QueryIntentMapper` maps STATISTICAL to DATA_REQUEST

### Phase Q-C complete when:
- [ ] `HeuristicQueryEntityExtractor` extracts UNEMPLOYMENT entity from query "უმუშევრობა 2023"
- [ ] INDICATOR pattern no longer contains "უმუკველწვერა"
- [ ] `normalizeIndicator("population")` returns "POPULATION"

### Phase Q-D complete when:
- [ ] `terminology-overlay.yaml` has ≥ 25 entries
- [ ] Query "მოსახლეობა" produces expansion "population demographics census"
- [ ] Query "ტურიზმი" produces expansion "tourism visitors travel"

### Phase Q-E complete when:
- [ ] Confidence thresholds are set via `@Value`, not hardcoded
- [ ] `gap` default is 0.02 in application-custom.yml
- [ ] `"GeoStat page"` no longer appears in link snippets from well-formed sentence starts

### Phase Q-A complete when:
- [ ] `QueryAnalyzeController` returns enriched `retrievalText` for test queries
- [ ] `retrievalText` for "მშპ 2023" contains "GDP" and "მთლიანი შიდა პროდუქტი"
- [ ] Intent classification distribution logged and visible in application logs
- [ ] No regression in existing `ChatServiceTest` or `QueryAnalyzeControllerTest`

---

*Senior directive. Fix in Q-B → Q-C → Q-D → Q-E → Q-A order.*
*Do not enable pipeline before bug fixes are deployed and unit-tested.*

---

## 12. Architecture & Hardcode Gaps — Additional Layer (SOLID / OCP violations)

> **Self-critique of the plan above:** the bug fixes in Phases Q-B/C/D are correct but
> incomplete. They fix wrong values but leave the structural problem intact: keyword lists
> live in Java code, not in configuration. Every future addition requires a code change.
> This section addresses the architecture, not just the bugs.

---

### GAP-ARCH-Q-01 — HeuristicIntentClassifier has Java-hardcoded keyword lists (OCP violation)

**File:** `HeuristicIntentClassifier.java`

```java
// CURRENT — every new intent keyword requires a Java code change:
if (containsAny(lower, "compare", "versus", "vs", "შედარ", " და ")) {
    return QueryIntentKind.COMPARE;
}
```

**OCP violation:** every time a new keyword is needed (e.g. "contrast", "შეადარე"), the
class must be modified. The `YamlTerminologyQueryExpander` pattern already exists in the
codebase and solves this correctly — keywords are in YAML, Java is the engine.

**Required fix:** Extract keyword lists to `catalog/intent-keywords.yaml`.
Java class becomes a loader + matcher — it never changes when keywords change.

**New file:** `apps/backend/src/main/resources/catalog/intent-keywords.yaml`

```yaml
# Intent classification keywords for HeuristicIntentClassifier.
# Add keywords here — no Java code change needed.
intents:
  SMALLTALK:
    - "hello"
    - "hi"
    - "thanks"
    - "thank you"
    - "გამარჯობა"
    - "მადლობა"
    - "როგორ ხარ"

  COMPARE:
    - "compare"
    - "versus"
    - "vs."
    - "შედარ"
    - "განსხვავება"
    - "difference between"
    - "მეტია თუ ნაკლები"

  DEFINITION:
    - "definition"
    - "განმარტება"
    - "meaning"

  FACTUAL:
    - "what is"
    - "what does"
    - "define"
    - "explain"
    - "meaning of"
    - "რა არის"
    - "რას ნიშნავს"

  LATEST:
    - "latest"
    - "recent"
    - "ბოლო"
    - "ახალი მონაცემ"
    - "მიმდინარე"
    - "current"

  STATISTICAL:
    - "მონაცემ"
    - "სტატისტიკ"
    - "ინდიკატ"
    - "მაჩვენებ"
    - "data"
    - "statistics"
    - "indicator"
    - "figure"
    - "number"
    - "table"
    - "chart"

  NAVIGATION:
    - "show me"
    - "where can i find"
    - "where to find"
    - "give me"
    - "download"
    - "go to"
    - "navigate"
    - "portal"
    - "open portal"
    - "open page"
    - "მაჩვენე"
    - "სად ვნახო"
    - "სად არის"
    - "პორტალი"
    - "გადავიდე"
    - "გახსენი"
```

**Updated `HeuristicIntentClassifier.java` — becomes a YAML loader, no keyword definitions:**

```java
// HeuristicIntentClassifier becomes a pure engine:
public class HeuristicIntentClassifier implements IntentClassifier {

    private final Map<QueryIntentKind, List<String>> keywordMap;

    // Constructor loaded with YAML-sourced keywords:
    public HeuristicIntentClassifier(Map<QueryIntentKind, List<String>> keywordMap) {
        this.keywordMap = Map.copyOf(keywordMap);
    }

    @Override
    public QueryIntentKind classify(String message, String normalized, String locale) {
        if (message == null || message.isBlank()) { return QueryIntentKind.LOOKUP; }
        String lower = normalized != null ? normalized.toLowerCase() : message.toLowerCase();

        // Check intents in priority order:
        for (QueryIntentKind priority : PRIORITY_ORDER) {
            if (matchesAny(lower, keywordMap.getOrDefault(priority, List.of()))) {
                // STATISTICAL requires additional guard: not a portal query
                if (priority == QueryIntentKind.STATISTICAL
                        && matchesAny(lower, keywordMap.getOrDefault(QueryIntentKind.NAVIGATION, List.of()))) {
                    continue;
                }
                return priority;
            }
        }
        return QueryIntentKind.LOOKUP;
    }

    private static final List<QueryIntentKind> PRIORITY_ORDER = List.of(
        QueryIntentKind.SMALLTALK,
        QueryIntentKind.COMPARE,
        QueryIntentKind.DEFINITION,
        QueryIntentKind.FACTUAL,
        QueryIntentKind.LATEST,
        QueryIntentKind.STATISTICAL,
        QueryIntentKind.NAVIGATION
    );

    private static boolean matchesAny(String text, List<String> keywords) {
        for (String kw : keywords) {
            if (text.contains(kw.toLowerCase())) { return true; }
        }
        return false;
    }
}
```

**Updated `QueryUnderstandingConfiguration.java` — loads YAML and injects:**

```java
@Bean
HeuristicIntentClassifier heuristicIntentClassifier() throws IOException {
    ClassPathResource resource = new ClassPathResource("catalog/intent-keywords.yaml");
    ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
    IntentKeywordsFile file = yaml.readValue(resource.getInputStream(), IntentKeywordsFile.class);
    Map<QueryIntentKind, List<String>> map = new EnumMap<>(QueryIntentKind.class);
    if (file.intents() != null) {
        file.intents().forEach((key, values) ->
            map.put(QueryIntentKind.valueOf(key), values));
    }
    return new HeuristicIntentClassifier(map);
}

record IntentKeywordsFile(Map<String, List<String>> intents) {}
```

---

### GAP-ARCH-Q-02 — QueryRouter duplicates HeuristicIntentClassifier keyword logic (DRY + OCP)

**File:** `QueryRouter.java`

```java
// CURRENT — IDENTICAL containsAny pattern, parallel keyword list:
if (containsAny(lowerQuery, "what is", "what does", "define", "explain", "meaning of",
        "რა არის", "რას ნიშნავს", "განმარტება")) {
    return QueryIntent.CONCEPT;  // same as FACTUAL in HeuristicIntentClassifier
}
```

**And in `ChatService.buildContext()` (lines 261–268):**

```java
QueryIntent intent = queryRouter.route(trimmed, trimmed.toLowerCase()); // ← always called
if (queryUnderstandingProperties.isEnabled()) {
    ...
    intent = queryIntentMapper.toChatIntent(analyzed.intent()); // ← OVERRIDES queryRouter!
}
```

When pipeline is enabled, `queryRouter.route()` runs but its result is **immediately
discarded** (overridden by `queryIntentMapper`). The call is wasted.

**Two violations:**
1. `QueryRouter` and `HeuristicIntentClassifier` both define overlapping keyword sets → DRY
2. `QueryRouter` is called even when its result is discarded → dead code path

**Fix 1 — Move queryRouter to else branch in ChatService:**

```java
// apps/backend/src/main/java/com/geostat/chat/application/chat/ChatService.java
// In buildContext():

// BEFORE (lines 261–269):
QueryIntent intent = queryRouter.route(trimmed, trimmed.toLowerCase());
String retrievalQuery = trimmed;
if (queryUnderstandingProperties.isEnabled()) {
    AnalyzedQuery analyzed = queryUnderstandingPipeline.analyze(trimmed, locale);
    retrievalQuery = analyzed.retrievalText();
    intent = queryIntentMapper.toChatIntent(analyzed.intent());
} else {
    retrievalQuery = spellFixer.fix(trimmed, locale);
}

// AFTER — no wasted call:
QueryIntent intent;
String retrievalQuery;
if (queryUnderstandingProperties.isEnabled()) {
    AnalyzedQuery analyzed = queryUnderstandingPipeline.analyze(trimmed, locale);
    retrievalQuery = analyzed.retrievalText();
    intent = queryIntentMapper.toChatIntent(analyzed.intent());
} else {
    retrievalQuery = spellFixer.fix(trimmed, locale);
    intent = queryRouter.route(trimmed, trimmed.toLowerCase());  // only runs as fallback
}
```

**Fix 2 — Make QueryRouter YAML-driven (same pattern as HeuristicIntentClassifier fix).**

The `QueryRouter` keyword lists are a subset of the same domain. Long-term: consider
whether `QueryRouter` and `HeuristicIntentClassifier` can be unified. They serve different
purposes (`QueryIntent` vs `QueryIntentKind`) but the keyword matching logic is identical.

For now: apply the same YAML extraction pattern. Add a `route-keywords.yaml` or reuse
`intent-keywords.yaml` with a separate section.

---

### GAP-ARCH-Q-03 — HeuristicQueryEntityExtractor INDICATOR regex is a Java hardcode

**File:** `HeuristicQueryEntityExtractor.java` lines 19–20

```java
// CURRENT — INDICATOR regex and normalization map are Java hardcodes:
private static final Pattern INDICATOR = Pattern.compile(
    "\\b(gdp|cpi|...|მშპ|ინფლაცია|...)\\b", ...);
```

```java
private static String normalizeIndicator(String value) {
    return switch (value.toLowerCase()) {
        case "მშპ" -> "GDP";
        ...
    };
}
```

Every new indicator (population, wages, energy, ...) requires:
1. Modifying the regex pattern string
2. Adding a case to the switch statement

OCP violation. The `query-typo-corrections.yaml` and `terminology-overlay.yaml` patterns
already exist. Entity indicators should follow the same approach.

**New file:** `apps/backend/src/main/resources/catalog/entity-indicators.yaml`

```yaml
# Statistical entity indicators for HeuristicQueryEntityExtractor.
# Each entry: triggers (matched in query) → normalized form (used for retrieval).
indicators:
  - triggers: ["gdp", "მშპ", "mshp"]
    normalized: "GDP"
    type: "INDICATOR"

  - triggers: ["cpi", "ინფლაცია", "inflation"]
    normalized: "CPI"
    type: "INDICATOR"

  - triggers: ["unemployment", "უმუშევრობა", "უმუშეველ"]
    normalized: "UNEMPLOYMENT"
    type: "INDICATOR"

  - triggers: ["export", "ექსპორტი"]
    normalized: "EXPORT"
    type: "INDICATOR"

  - triggers: ["import", "იმპორტი"]
    normalized: "IMPORT"
    type: "INDICATOR"

  - triggers: ["fdi", "პირდაპირი ინვესტ"]
    normalized: "FDI"
    type: "INDICATOR"

  - triggers: ["population", "მოსახლეობ"]
    normalized: "POPULATION"
    type: "INDICATOR"

  - triggers: ["wages", "ხელფასი", "salary"]
    normalized: "WAGES"
    type: "INDICATOR"

  - triggers: ["poverty", "სიღარიბ"]
    normalized: "POVERTY"
    type: "INDICATOR"

  - triggers: ["tourism", "ტურიზ"]
    normalized: "TOURISM"
    type: "INDICATOR"
```

**Updated `HeuristicQueryEntityExtractor`** loads `entity-indicators.yaml` at startup
(same `@PostConstruct` + `ClassPathResource` pattern as `YamlTerminologyQueryExpander`).
The YEAR regex stays as-is — years are structurally regular, not domain-configurable.

---

### GAP-ARCH-Q-04 — DisplayBoilerplate markers are Java hardcodes

**File:** `DisplayBoilerplate.java` lines 6–21

```java
private static final String[] MARKERS = {
    "ვებგვერდის ადაპტ",
    "adapted version of the website",
    "united nations development program",
    ...
};
```

This class is `package-private final` and its markers are static constants. When a new
boilerplate phrase appears on the site, a developer must edit Java code.

**Existing pattern:** `ops/config/corpus/geostat-portal-parse.yaml` already has a boilerplate
section for the ingestion layer. The chat/display layer should have the same.

**New file:** `apps/backend/src/main/resources/catalog/display-boilerplate.yaml`

```yaml
# Boilerplate phrases filtered from RAG citation snippets.
# Add new phrases here — no Java code change needed.
markers:
  - "ვებგვერდის ადაპტ"
  - "adapted version of the website"
  - "united nations development program"
  - "გაეროს განვითარების პროგრამ"
  - "government of sweden"
  - "შვედეთის მთავრობ"
  - "საჯარო სამართლის იურიდიული პირი"
  - "official statistics of georgia"
  - "ცენტრალური ოფისი:"
  - "central office:"
  - "საქსტატის ოფიციალური ვებგვერდი"
  - "skip to content"
  - "უკან დაბრუნება"
  - "go back "
```

**Make `DisplayBoilerplate` a `@Component`** that loads the YAML at startup and replaces the
static array. The `isBoilerplate()` method stays the same — it uses the loaded list.

---

### GAP-ARCH-Q-05 — CatalogRagLinkMerger has 3 magic constants

**File:** `CatalogRagLinkMerger.java` lines 21–23

```java
static final int MAX_TOTAL   = 8;    // hardcoded total link limit
public static final int MAX_RAG = 4; // hardcoded RAG link limit
private static final int SNIPPET_MAX = 240; // hardcoded snippet length
```

These values have business significance:
- `MAX_TOTAL` = how many links the user sees per response
- `MAX_RAG` = how many RAG citations vs catalog links
- `SNIPPET_MAX` = link description length in characters

**Fix:** Inject via `@Value`:

```java
@Component
public class CatalogRagLinkMerger {

    private final int maxTotal;
    private final int maxRag;
    private final int snippetMax;
    private final PresentationStyleCatalog presentationStyles;

    public CatalogRagLinkMerger(
            PresentationStyleCatalog presentationStyles,
            @Value("${geostat.chat.links.max-total:8}") int maxTotal,
            @Value("${geostat.chat.links.max-rag:4}") int maxRag,
            @Value("${geostat.chat.links.snippet-max:240}") int snippetMax) {
        this.presentationStyles = presentationStyles;
        this.maxTotal    = maxTotal;
        this.maxRag      = maxRag;
        this.snippetMax  = snippetMax;
    }
    // Use this.maxTotal instead of MAX_TOTAL throughout
}
```

Add to `application-custom.yml`:
```yaml
geostat:
  chat:
    links:
      max-total:   ${LINKS_MAX_TOTAL:8}
      max-rag:     ${LINKS_MAX_RAG:4}
      snippet-max: ${LINKS_SNIPPET_MAX:240}
```

Note: `MAX_RAG` is referenced as `CatalogRagLinkMerger.MAX_RAG` in `ChatService.mergedLinks()`.
After the change, extract the constant to a property or use the injected value through the
`merge()` method signature (already supported — `merge(catalogLinks, ragChunks, isGeorgian, maxRag)`).

---

### GAP-ARCH-Q-06 — containsAny() method duplicated in 3 classes

**Files:**
- `HeuristicIntentClassifier.java` line 76
- `QueryRouter.java` line 30
- (Pattern repeated in SmallTalkHandler likely as well)

Identical private static method:
```java
private static boolean containsAny(String text, String... keywords) {
    for (String keyword : keywords) {
        if (text.contains(keyword.toLowerCase())) { return true; }
    }
    return false;
}
```

**Fix:** Extract to a shared utility in the `domain.query` package or `application.chat` package:

```java
// New file: apps/backend/src/main/java/com/geostat/chat/domain/query/KeywordMatcher.java
package com.geostat.chat.domain.query;

public final class KeywordMatcher {

    private KeywordMatcher() {}

    public static boolean containsAny(String text, List<String> keywords) {
        if (text == null || keywords == null) { return false; }
        for (String keyword : keywords) {
            if (keyword != null && text.contains(keyword.toLowerCase())) { return true; }
        }
        return false;
    }
}
```

Replace all private `containsAny` duplicates with `KeywordMatcher.containsAny(...)`.

---

### Revised Execution Order (full, including architecture fixes)

```
Architecture fixes FIRST (no behavior change, low risk):

Phase Q-ARCH (prerequisite — extract utilities):
  ARCH-1: Create KeywordMatcher.java (domain utility, no tests broken)
  ARCH-2: Replace containsAny() in HeuristicIntentClassifier + QueryRouter

Phase Q-B (heuristic keyword bugs + YAML extraction):
  B-1: Create catalog/intent-keywords.yaml with corrected keyword lists
  B-2: Update HeuristicIntentClassifier to load from YAML (ARCH pattern)
  B-3: Update QueryUnderstandingConfiguration to inject keywords map
  B-4: Add STATISTICAL to QueryIntentKind enum
  B-5: Update QueryIntentMapper with STATISTICAL → DATA_REQUEST
  ↓ verify: HeuristicIntentClassifierTest — "open data" → LOOKUP, etc.

Phase Q-C (entity extractor + YAML extraction):
  C-1: Create catalog/entity-indicators.yaml
  C-2: Update HeuristicQueryEntityExtractor to load from YAML
  C-3: Fix Georgian typo "უმუკველწვერა" → "უმუშევრობა" in YAML
  ↓ verify: entity extraction test

Phase Q-D (terminology YAML expansion):
  D-1: Add 15 new entries to terminology-overlay.yaml
  ↓ verify: expansion test

Phase Q-E (confidence calibration + merger fixes):
  E-1: Make DefaultConfidenceAssessor thresholds configurable
  E-2: Fix trimLeadingPartialWord
  E-3: Fix looksLikeProse
  E-4: Make CatalogRagLinkMerger constants @Value-injected (GAP-ARCH-Q-05)
  ↓ verify: DefaultConfidenceAssessorTest, CatalogRagLinkMergerTest

Phase Q-F (display boilerplate + QueryRouter cleanup):
  F-1: Create catalog/display-boilerplate.yaml
  F-2: Update DisplayBoilerplate to @Component + YAML loader
  F-3: Fix ChatService.buildContext() — move queryRouter to else branch (GAP-ARCH-Q-02)
  ↓ verify: ChatService test — queryRouter not called when pipeline enabled

Phase Q-A (ENABLE PIPELINE — always last):
  A-1: Set QUERY_UNDERSTANDING_ENABLED default to true
  A-2: Test via QueryAnalyzeController endpoint
  A-3: Verify retrievalText is enriched
```

---

### Additional Acceptance Criteria (architecture)

- [ ] `HeuristicIntentClassifier` has zero keyword strings in Java source (all in YAML)
- [ ] `QueryRouter` has zero keyword strings in Java source (all in YAML)
- [ ] `HeuristicQueryEntityExtractor` has zero indicator strings in Java (all in YAML)
- [ ] `DisplayBoilerplate` has zero hardcoded marker strings (all in YAML)
- [ ] `CatalogRagLinkMerger` has zero hardcoded int constants (all `@Value`)
- [ ] `containsAny()` private method exists in exactly ONE place (`KeywordMatcher`)
- [ ] `ChatService.buildContext()` does not call `queryRouter.route()` when pipeline is enabled
- [ ] Adding a new intent keyword requires ONLY editing a YAML file, zero Java changes

---

## 14. Keyword Matching — Architecture Redesign (Response Quality Foundation)

> **This is the most critical section.** The chat response quality is directly determined
> by cluster matching quality. The current `position(keyword IN query)` substring approach
> has four structural failures: Georgian inflection, stopword noise, Gemini keywords unused,
> no scoring confidence gate. This section replaces the ad-hoc approach with a principled,
> layered architecture.

---

### 14.1 — Current State: 4 Structural Failures

```
Current flow:
  user query (raw string)
       ↓
  JdbcDerivedCatalogReader.matchClusters(query, language, limit)
       ↓
  SELECT ... WHERE position(lower(tk.keyword) IN ?) > 0   ← exact substring only
       ↓
  ANY match → cluster included (no minimum confidence)
       ↓
  links returned to chat → response built
```

| Failure | Root Cause | Effect on Response |
|---------|-----------|-------------------|
| **Georgian inflection** | "უმუშევართა" ≠ "უმუშევრობა" — same concept, different grammatical case | User asks about unemployment → cluster not found → empty response |
| **Stopword noise** | YAKE extracts "ის", "და", "ამ", "ან" — common 2-letter tokens that exist in almost every document | Query "სად ვნახო ინფლაცია" → every cluster matches on "ვნახო" or "სად" → irrelevant clusters returned |
| **Gemini keywords unused** | `mv_topic_keywords` aggregates `document.keywords` (YAKE), but `topic_cluster.keywords` (Gemini semantic labels) are never queried | The richest signal (LLM-generated cluster semantics) is discarded |
| **No confidence gate** | Any `position() > 0` match = cluster included, regardless of how weakly it matched | 1 occurrence of "2024" in a cluster → that cluster matches every year-related query → noise |

---

### 14.2 — Target Architecture

```
User Query
     ↓
[APPLICATION] QueryUnderstandingPipeline
     └── AnalyzedQuery {
           normalizedText: "gdp 2024"
           intent:         STATISTICAL
           entities:       [Entity("GDP", INDICATOR), Entity("2024", YEAR)]
           expansions:     ["მშპ", "gross domestic product", "gdp growth"]
           retrievalText:  "gdp მშპ gross domestic product 2024"
         }
     ↓
[APPLICATION] ScoredClusterMatcher  (NEW — replaces direct JdbcDerivedCatalogReader call)
     │
     ├── Signal A: KeywordSignal     → mv_topic_keywords (TF-IDF-weighted, rank ≤ 30)
     ├── Signal B: LabelSignal       → label_ka / label_en substring
     ├── Signal C: EntitySignal      → entities[] normalized → keyword lookup (inflection-safe)
     └── Signal D: ExpansionSignal   → expansions[] → keyword lookup (synonym coverage)
     │
     ├── Score fusion: weightedSum(A, B, C, D)
     ├── Minimum threshold gate:   score < MIN_SCORE → excluded
     ├── Diversity filter:         deduplicate clusters with same label prefix
     └── Output: List<ScoredCluster> (id, score, matchReason)
     ↓
[INFRASTRUCTURE] DerivedCatalogReader.findPortalLinks() + findSpecificLinks()
     ↓
[APPLICATION] CatalogRagLinkMerger → final ranked links
```

**The critical design principle:** `ScoredClusterMatcher` is Application Layer — it knows
about `AnalyzedQuery` (domain object) and calls `DerivedCatalogReader` (port). It never
knows about JDBC or SQL. Infrastructure stays isolated.

---

### 14.3 — V22 Migration: Improved `mv_topic_keywords`

The MV must be rebuilt to:
1. Include **Gemini cluster keywords** alongside YAKE document keywords
2. Compute **TF-IDF-like score** (`doc_frequency / across_clusters`) per keyword
3. Filter tokens shorter than 3 characters (eliminates "ის", "და", "ან")

**File:** `apps/ingestion-service/src/main/resources/db/migration/V22__improved_topic_keywords_mv.sql`

```sql
-- V22 — Replace mv_topic_keywords with multi-source TF-IDF variant.
-- Includes both YAKE (document-level) and Gemini (cluster-level) keywords.
-- Score = doc_frequency / across_clusters (discriminative power).

DROP MATERIALIZED VIEW IF EXISTS ingestion.mv_topic_keywords CASCADE;

CREATE MATERIALIZED VIEW ingestion.mv_topic_keywords AS
WITH
-- Source 1: YAKE keywords from individual documents (frequency-based)
yake_kw AS (
    SELECT d.topic_cluster_id,
           d.language,
           lower(kw) AS kw
    FROM   ingestion.document d
    JOIN   ingestion.topic_cluster tc ON tc.id = d.topic_cluster_id AND tc.approved = true,
           unnest(d.keywords) AS kw
    WHERE  d.topic_cluster_id IS NOT NULL
      AND  length(kw) >= 3
),

-- Source 2: Gemini semantic keywords from cluster (stored in topic_cluster.keywords)
-- Gemini keywords are language-agnostic — include for both ka and en
gemini_kw AS (
    SELECT tc.id  AS topic_cluster_id,
           'ka'   AS language,
           lower(kw) AS kw
    FROM   ingestion.topic_cluster tc, unnest(tc.keywords) AS kw
    WHERE  tc.approved = true AND length(kw) >= 3
    UNION ALL
    SELECT tc.id, 'en', lower(kw)
    FROM   ingestion.topic_cluster tc, unnest(tc.keywords) AS kw
    WHERE  tc.approved = true AND length(kw) >= 3
),

-- Combine: Gemini keywords added twice → higher doc_frequency in aggregation
-- This is the "Gemini boost" without a separate weight column
all_kw AS (
    SELECT * FROM yake_kw
    UNION ALL
    SELECT * FROM gemini_kw
    UNION ALL
    SELECT * FROM gemini_kw   -- second copy = 2x weight in COUNT
),

-- Aggregate: how many times does this keyword appear per cluster?
per_cluster AS (
    SELECT topic_cluster_id, language, kw,
           COUNT(*) AS doc_frequency
    FROM   all_kw
    GROUP BY topic_cluster_id, language, kw
),

-- IDF denominator: how many clusters share this keyword?
per_keyword_scope AS (
    SELECT language, kw,
           COUNT(DISTINCT topic_cluster_id) AS across_clusters
    FROM   per_cluster
    GROUP BY language, kw
)

SELECT
    pc.topic_cluster_id,
    pc.language,
    pc.kw                                                                    AS keyword,
    pc.doc_frequency,
    pks.across_clusters,
    -- TF-IDF proxy: intra-cluster frequency vs cross-cluster spread
    pc.doc_frequency::float / GREATEST(1.0, pks.across_clusters::float)     AS tfidf_score,
    ROW_NUMBER() OVER (
        PARTITION BY pc.topic_cluster_id, pc.language
        ORDER BY pc.doc_frequency::float / GREATEST(1.0, pks.across_clusters::float) DESC
    )                                                                        AS rank
FROM per_cluster pc
JOIN per_keyword_scope pks ON pks.language = pc.language AND pks.kw = pc.kw;

-- Index for fast lookup by cluster + language + rank
CREATE UNIQUE INDEX idx_mv_topic_keywords_unique
    ON ingestion.mv_topic_keywords (topic_cluster_id, language, rank);

-- Index for keyword-based search (the query path)
CREATE INDEX idx_mv_topic_keywords_kw
    ON ingestion.mv_topic_keywords (language, keyword, tfidf_score DESC);
```

**Comparison: old vs new MV**

| | Old | New |
|--|-----|-----|
| Sources | `document.keywords` (YAKE only) | YAKE + Gemini (2x weighted) |
| Short token filter | None | `length >= 3` |
| Scoring column | `doc_frequency` (raw count) | `tfidf_score` = freq / spread |
| Rank ordering | by doc_frequency | by tfidf_score |
| Discriminative power | Low — "საქართველო" ranks high | High — rare cluster-specific terms rank high |

---

### 14.4 — New Port: `ClusterMatchPort`

**File:** `apps/backend/src/main/java/com/geostat/chat/domain/catalog/ClusterMatchPort.java`

```java
package com.geostat.chat.domain.catalog;

import com.geostat.chat.domain.query.AnalyzedQuery;
import java.util.List;

/**
 * Port: application layer asks for ranked, scored cluster matches.
 * Infrastructure implements the multi-signal SQL + Java scoring.
 */
public interface ClusterMatchPort {

    /**
     * Returns clusters ranked by multi-signal relevance score.
     * Only clusters above the minimum configured threshold are returned.
     *
     * @param query    fully analyzed query (normalized, entities, expansions)
     * @param language "ka" or "en"
     * @param limit    maximum results to return
     */
    List<ScoredCluster> match(AnalyzedQuery query, String language, int limit);
}
```

**File:** `apps/backend/src/main/java/com/geostat/chat/domain/catalog/ScoredCluster.java`

```java
package com.geostat.chat.domain.catalog;

import java.util.UUID;

/**
 * A topic cluster matched to the current query, with score and reason for observability.
 */
public record ScoredCluster(
        UUID   clusterId,
        double score,
        String matchReason   // e.g. "entity:GDP keyword:gdp", "label:inflation"
) {}
```

---

### 14.5 — Application Layer: `ScoredClusterMatcher`

**File:** `apps/backend/src/main/java/com/geostat/chat/application/catalog/ScoredClusterMatcher.java`

```java
package com.geostat.chat.application.catalog;

import com.geostat.chat.domain.catalog.ClusterMatchPort;
import com.geostat.chat.domain.catalog.DerivedCatalogReader;
import com.geostat.chat.domain.catalog.DerivedClusterMatch;
import com.geostat.chat.domain.catalog.ScoredCluster;
import com.geostat.chat.domain.query.AnalyzedQuery;
import com.geostat.chat.domain.query.QueryEntity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Application-layer orchestrator for multi-signal cluster scoring.
 *
 * Signals (configurable weights, sum to 1.0):
 *   A — keyword match (YAKE + Gemini, TF-IDF weighted from MV)
 *   B — cluster label match
 *   C — entity-based match (inflection-safe via normalized entity forms)
 *   D — terminology expansion match (synonym coverage)
 *
 * A cluster must exceed MIN_SCORE to be included. This prevents noise
 * from weak partial matches.
 */
@Component
public class ScoredClusterMatcher {

    private final ClusterMatchPort clusterMatchPort;

    private final double minScore;
    private final int    maxDiversityPerPrefix;   // max clusters per label-prefix group

    public ScoredClusterMatcher(
            ClusterMatchPort clusterMatchPort,
            @Value("${geostat.chat.cluster.min-score:0.10}") double minScore,
            @Value("${geostat.chat.cluster.max-diversity-per-prefix:2}") int maxDiversityPerPrefix) {
        this.clusterMatchPort       = clusterMatchPort;
        this.minScore               = minScore;
        this.maxDiversityPerPrefix  = maxDiversityPerPrefix;
    }

    public DerivedClusterMatch match(AnalyzedQuery query, String language, int limit) {
        // Delegate multi-signal scoring to infrastructure (SQL + Java scoring)
        List<ScoredCluster> scored = clusterMatchPort.match(query, language, limit * 2);

        // Application-level: filter by minimum confidence gate
        List<ScoredCluster> confident = scored.stream()
                .filter(c -> c.score() >= minScore)
                .toList();

        // Application-level: diversity filter (deduplicate by label prefix)
        List<UUID> diverse = applyDiversity(confident, limit);

        return new DerivedClusterMatch(diverse);
    }

    /**
     * Limits clusters per label-prefix group to prevent response dominated
     * by one topic (e.g. 4 inflation clusters when query mentions both GDP and inflation).
     */
    private List<UUID> applyDiversity(List<ScoredCluster> scored, int limit) {
        Map<String, Integer> prefixCount = new LinkedHashMap<>();
        List<UUID> result = new ArrayList<>();
        for (ScoredCluster cluster : scored) {
            if (result.size() >= limit) break;
            String prefix = cluster.matchReason().toLowerCase().substring(
                    0, Math.min(cluster.matchReason().length(), 20));
            int count = prefixCount.getOrDefault(prefix, 0);
            if (count < maxDiversityPerPrefix) {
                result.add(cluster.clusterId());
                prefixCount.put(prefix, count + 1);
            }
        }
        return result;
    }
}
```

---

### 14.6 — Infrastructure: `JdbcClusterMatchAdapter` (replaces `JdbcDerivedCatalogReader.matchClusters`)

**File:** `apps/backend/src/main/java/com/geostat/chat/infrastructure/catalog/JdbcClusterMatchAdapter.java`

This adapter implements `ClusterMatchPort`. It executes the multi-signal SQL query and
applies the entity + expansion signals in Java (because normalized entity forms come from
the domain layer, not from SQL).

```java
package com.geostat.chat.infrastructure.catalog;

import com.geostat.chat.domain.catalog.ClusterMatchPort;
import com.geostat.chat.domain.catalog.ScoredCluster;
import com.geostat.chat.domain.query.AnalyzedQuery;
import com.geostat.chat.domain.query.QueryEntity;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "geostat.chat.catalog", name = "source", havingValue = "derived")
public class JdbcClusterMatchAdapter implements ClusterMatchPort {

    private final JdbcTemplate jdbcTemplate;

    // Signal weights — must sum to 1.0
    private final double wKeyword;    // Signal A: YAKE/Gemini keyword TF-IDF match
    private final double wLabel;      // Signal B: cluster label match
    private final double wEntity;     // Signal C: entity-normalized match
    private final double wExpansion;  // Signal D: synonym expansion match
    private final int    maxRank;     // only consider top-N keywords per cluster in MV

    public JdbcClusterMatchAdapter(
            JdbcTemplate catalogJdbcTemplate,
            @Value("${geostat.chat.cluster.weight.keyword:0.35}")   double wKeyword,
            @Value("${geostat.chat.cluster.weight.label:0.25}")     double wLabel,
            @Value("${geostat.chat.cluster.weight.entity:0.30}")    double wEntity,
            @Value("${geostat.chat.cluster.weight.expansion:0.10}") double wExpansion,
            @Value("${geostat.chat.cluster.mv-keyword-rank:30}")    int    maxRank) {
        this.jdbcTemplate = catalogJdbcTemplate;
        this.wKeyword     = wKeyword;
        this.wLabel       = wLabel;
        this.wEntity      = wEntity;
        this.wExpansion   = wExpansion;
        this.maxRank      = maxRank;
    }

    @Override
    public List<ScoredCluster> match(AnalyzedQuery query, String language, int limit) {
        String normalized = query.normalizedText() == null ? "" : query.normalizedText().strip().toLowerCase();
        if (normalized.length() < 3) return List.of();

        // Collect all match texts: normalized query + entity forms + expansions
        List<String> entityForms    = extractEntityForms(query);
        List<String> expansionForms = query.expansions() == null ? List.of() : query.expansions();

        // Step 1: SQL signals A + B → raw cluster candidates
        Map<UUID, Double[]> signalScores = querySignalsAB(normalized, language, maxRank, limit * 3);

        // Step 2: Java signals C + D → entity and expansion scores
        applySignalC(signalScores, entityForms, language, limit * 3);
        applySignalD(signalScores, expansionForms, language, limit * 3);

        // Step 3: fuse signals into final score
        return signalScores.entrySet().stream()
                .map(e -> {
                    Double[] s = e.getValue();
                    double final_score = wKeyword * normalize(s[0])
                                      + wLabel    * normalize(s[1])
                                      + wEntity   * normalize(s[2])
                                      + wExpansion* normalize(s[3]);
                    String reason = buildReason(s);
                    return new ScoredCluster(e.getKey(), final_score, reason);
                })
                .filter(c -> c.score() > 0)
                .sorted(Comparator.comparingDouble(ScoredCluster::score).reversed())
                .limit(limit)
                .toList();
    }

    private Map<UUID, Double[]> querySignalsAB(String query, String language, int maxRank, int limit) {
        Map<UUID, Double[]> result = new LinkedHashMap<>();
        jdbcTemplate.query(
            """
            SELECT cluster_id, keyword_score, label_score
            FROM (
                SELECT tc.id AS cluster_id,
                       SUM(tk.tfidf_score) AS keyword_score,
                       0.0 AS label_score
                FROM ingestion.topic_cluster tc
                JOIN ingestion.mv_topic_keywords tk
                  ON tk.topic_cluster_id = tc.id AND tk.language = ?
                WHERE tc.approved = true
                  AND tk.rank <= ?
                  AND position(tk.keyword IN ?) > 0
                GROUP BY tc.id

                UNION ALL

                SELECT tc.id AS cluster_id,
                       0.0   AS keyword_score,
                       1.0   AS label_score
                FROM ingestion.topic_cluster tc
                WHERE tc.approved = true
                  AND (position(lower(tc.label_ka) IN ?) > 0
                    OR position(lower(tc.label_en) IN ?) > 0)
            ) signals
            GROUP BY cluster_id, keyword_score, label_score
            ORDER BY keyword_score + label_score DESC
            LIMIT ?
            """,
            rs -> {
                UUID id = rs.getObject("cluster_id", UUID.class);
                Double[] scores = result.computeIfAbsent(id, k -> new Double[]{0.0, 0.0, 0.0, 0.0});
                scores[0] += rs.getDouble("keyword_score");  // Signal A
                scores[1] += rs.getDouble("label_score");    // Signal B
            },
            language, maxRank, query, query, query, limit);
        return result;
    }

    /** Signal C: entity-normalized forms — inflection-safe match via JdbcTemplate. */
    private void applySignalC(Map<UUID, Double[]> scores, List<String> entityForms,
                               String language, int limit) {
        if (entityForms.isEmpty()) return;
        for (String form : entityForms) {
            if (form == null || form.length() < 3) continue;
            jdbcTemplate.query(
                """
                SELECT tc.id AS cluster_id, SUM(tk.tfidf_score) AS score
                FROM ingestion.topic_cluster tc
                JOIN ingestion.mv_topic_keywords tk
                  ON tk.topic_cluster_id = tc.id AND tk.language = ?
                WHERE tc.approved = true AND tk.rank <= 50
                  AND position(? IN tk.keyword) > 0
                GROUP BY tc.id
                ORDER BY SUM(tk.tfidf_score) DESC
                LIMIT ?
                """,
                rs -> {
                    UUID id = rs.getObject("cluster_id", UUID.class);
                    Double[] s = scores.computeIfAbsent(id, k -> new Double[]{0.0, 0.0, 0.0, 0.0});
                    s[2] += rs.getDouble("score");
                },
                language, form.toLowerCase(), limit);
        }
    }

    /** Signal D: synonym expansion match. */
    private void applySignalD(Map<UUID, Double[]> scores, List<String> expansions,
                               String language, int limit) {
        if (expansions.isEmpty()) return;
        for (String exp : expansions) {
            if (exp == null || exp.length() < 3) continue;
            jdbcTemplate.query(
                """
                SELECT tc.id AS cluster_id, SUM(tk.tfidf_score) AS score
                FROM ingestion.topic_cluster tc
                JOIN ingestion.mv_topic_keywords tk
                  ON tk.topic_cluster_id = tc.id AND tk.language = ?
                WHERE tc.approved = true AND tk.rank <= 30
                  AND position(? IN tk.keyword) > 0
                GROUP BY tc.id
                LIMIT ?
                """,
                rs -> {
                    UUID id = rs.getObject("cluster_id", UUID.class);
                    Double[] s = scores.computeIfAbsent(id, k -> new Double[]{0.0, 0.0, 0.0, 0.0});
                    s[3] += rs.getDouble("score");
                },
                language, exp.toLowerCase(), limit);
        }
    }

    private static List<String> extractEntityForms(AnalyzedQuery query) {
        if (query.entities() == null) return List.of();
        List<String> forms = new ArrayList<>();
        for (QueryEntity entity : query.entities()) {
            if (entity.normalized() != null) {
                forms.add(entity.normalized().toLowerCase());
            }
            if (entity.raw() != null && !entity.raw().equalsIgnoreCase(entity.normalized())) {
                forms.add(entity.raw().toLowerCase());
            }
        }
        return forms;
    }

    private static double normalize(Double raw) {
        if (raw == null || raw <= 0) return 0.0;
        return Math.min(1.0, raw / 5.0);   // saturate at 5 TF-IDF points = score 1.0
    }

    private static String buildReason(Double[] signals) {
        StringBuilder sb = new StringBuilder();
        if (signals[0] > 0) sb.append("kw:").append(String.format("%.2f", signals[0])).append(" ");
        if (signals[1] > 0) sb.append("label ");
        if (signals[2] > 0) sb.append("entity:").append(String.format("%.2f", signals[2])).append(" ");
        if (signals[3] > 0) sb.append("expansion:").append(String.format("%.2f", signals[3]));
        return sb.toString().trim();
    }
}
```

---

### 14.7 — Georgian Stopword Filter at Ingestion

**Problem:** YAKE extracts "ის", "და", "ამ", "ან", "ან", "ში" from Georgian text. These are
grammatical particles/suffixes — they appear in every document and pollute keyword matching.

**Fix:** Filter stopwords **before storing** in `document.keywords`.

**New file:** `apps/ingestion-service/src/main/resources/catalog/georgian-stopwords.yaml`

```yaml
# Georgian stopwords — filtered from YAKE keyword output before persistence.
# These are grammatical particles, conjunctions, and function words.
stopwords:
  ka:
    - "ის"
    - "და"
    - "ან"
    - "ამ"
    - "ამ"
    - "ასევე"
    - "რომ"
    - "რომ"
    - "შემდეგ"
    - "წინ"
    - "კი"
    - "ხომ"
    - "ხოლო"
    - "მაგრამ"
    - "თუ"
    - "ასე"
    - "ისე"
    - "ვინ"
    - "რა"
    - "სად"
    - "როდ"
    - "ისინი"
    - "მას"
    - "ჩვენ"
    - "მათ"
    - "ჩვენი"
    - "მათი"
    - "ამ"
    - "ის"
    - "ში"
    - "ზე"
    - "ს"
  en:
    - "the"
    - "and"
    - "or"
    - "in"
    - "of"
    - "to"
    - "a"
    - "an"
    - "is"
    - "it"
    - "its"
    - "at"
    - "on"
    - "for"
    - "as"
    - "by"
    - "be"
    - "are"
    - "was"
    - "with"
    - "this"
    - "that"
    - "from"
    - "which"
```

**Apply filter in `KeywordEnrichmentService.persistKeywords()`:**

```java
// KeywordEnrichmentService.java
private void persistKeywords(DocumentEntity document, List<String> keywords) {
    List<String> filtered = keywords.stream()
            .filter(kw -> !stopwords.contains(kw.toLowerCase()))
            .filter(kw -> kw.length() >= 3)          // min 3 chars
            .filter(kw -> !isAllDigits(kw))           // skip pure numbers
            .collect(Collectors.toList());
    document.setKeywords(filtered.toArray(String[]::new));
}

private static boolean isAllDigits(String s) {
    return s.chars().allMatch(Character::isDigit);
}
```

The `stopwords` set is loaded from `georgian-stopwords.yaml` at startup
(same pattern as `YamlTerminologyQueryExpander`).

Note: numbers like "2024" are filtered at document level — a year alone is not a useful
document keyword. For retrieval, year is handled by `Entity("2024", YEAR)` from the
query understanding pipeline, not from YAKE.

---

### 14.8 — RetrievalContextService Fix: Use `retrievalText`, Not Raw Query

**Current critical bug:**

```java
// RetrievalContextService.java
public List<RetrievedChunk> retrieve(String userMessage, String locale) {
    return search(userMessage, locale, properties.maxChunks());  // ← raw user text!
}
```

When the pipeline is enabled, the `retrievalText` from `AnalyzedQuery` is enriched with
expanded synonyms and normalized terms. But `RetrievalContextService` always receives the
raw `userMessage` — it never benefits from the pipeline.

**Fix:** Pass `retrievalText` to `RetrievalContextService`:

```java
// ChatService.java — in retrieveChunks() or equivalent
// BEFORE:
List<RetrievedChunk> chunks = retrievalContextService.retrieve(trimmed, locale);

// AFTER:
String vectorQuery = queryUnderstandingProperties.isEnabled()
        ? analyzed.retrievalText()   // enriched: "gdp მშპ gross domestic product 2024"
        : spellFixer.fix(trimmed, locale);
List<RetrievedChunk> chunks = retrievalContextService.retrieve(vectorQuery, locale);
```

This single fix improves Qdrant semantic search quality significantly — the embedding is
computed on the enriched text, not the raw (possibly misspelled, uninflected) user text.

---

### 14.9 — Qdrant Payload: Include Document Keywords

**Current state:** `QdrantVectorStore.buildPoint()` includes `navBreadcrumb`, `publishedAt`,
`embeddingModel` in payload, but NOT `document.keywords`.

**Why this matters:** Spring AI's `QdrantVectorStore` supports Qdrant's hybrid sparse+dense
search. If keywords are in the payload, BM25 keyword boosting can be applied. Without
keywords in payload, BM25 boost is impossible.

**Fix in `QdrantVectorStore.buildPoint()`:**

```java
// Add to payload map in buildPoint():
.put("keywords", String.join(" ", chunk.getDocument().getKeywords()))
// chunk.getDocument().getKeywords() returns String[] — join with space for BM25 text field
```

Then configure Qdrant collection to index the `keywords` payload field:

```java
// QdrantCollectionManager.java — in createCollection():
// Add payload index for BM25 on keywords field:
qdrantClient.createPayloadIndexAsync(
    collectionName,
    "keywords",
    PayloadSchemaType.Text,    // ← enables full-text BM25 on this field
    null, true, null
).get();
```

---

### 14.10 — Configuration Properties (`application-custom.yml`)

All scoring parameters must be configurable without code change:

```yaml
geostat:
  chat:
    cluster:
      min-score:                  ${CLUSTER_MIN_SCORE:0.10}       # minimum confidence gate
      max-diversity-per-prefix:   ${CLUSTER_MAX_DIVERSITY:2}       # max clusters per label group
      mv-keyword-rank:            ${CLUSTER_MV_KW_RANK:30}         # top-N keywords per cluster in MV
      weight:
        keyword:                  ${CLUSTER_W_KEYWORD:0.35}        # Signal A: YAKE/Gemini keyword match
        label:                    ${CLUSTER_W_LABEL:0.25}          # Signal B: label match
        entity:                   ${CLUSTER_W_ENTITY:0.30}         # Signal C: entity-normalized match
        expansion:                ${CLUSTER_W_EXPANSION:0.10}      # Signal D: synonym expansion
```

---

### 14.11 — Execution Order (Junior: strict sequence)

```
Phase KW-A — MV rebuild (infrastructure, no app change):
  A-1: Write V22__improved_topic_keywords_mv.sql
  A-2: Deploy migration — verify MV has tfidf_score column
  A-3: Verify rank distribution: SELECT rank, COUNT(*) FROM mv_topic_keywords GROUP BY rank LIMIT 10
  ↓ expected: top ranks have discriminative keywords (GDP, unemployment), not "და", "ის"

Phase KW-B — Stopword filter at ingestion:
  B-1: Create georgian-stopwords.yaml in ingestion-service resources
  B-2: Load stopwords in KeywordEnrichmentService (same YAML pattern as terminology)
  B-3: Apply filter in persistKeywords()
  B-4: Re-run KeywordEnrichmentService for existing documents (backfill)
  ↓ verify: document.keywords no longer contains stopwords

Phase KW-C — New port + adapter (backend chat):
  C-1: Create ClusterMatchPort interface (domain)
  C-2: Create ScoredCluster record (domain)
  C-3: Create JdbcClusterMatchAdapter (infrastructure, implements ClusterMatchPort)
  C-4: Create ScoredClusterMatcher (application)
  C-5: Add config properties to application-custom.yml
  ↓ verify: ScoredClusterMatcherTest — GDP query returns GDP cluster, not every cluster

Phase KW-D — Wire ScoredClusterMatcher into ChatService:
  D-1: Replace direct DerivedCatalogReader.matchClusters() call with ScoredClusterMatcher.match()
  D-2: Pass AnalyzedQuery (not raw string) to ScoredClusterMatcher
  D-3: Fix RetrievalContextService — pass retrievalText to Qdrant (section 14.8)
  ↓ verify: ChatServiceTest — pipeline enriched text reaches Qdrant

Phase KW-E — Qdrant keyword payload:
  E-1: Add keywords to QdrantVectorStore.buildPoint() payload
  E-2: Add keywords payload index in QdrantCollectionManager
  ↓ verify: inspect Qdrant payload for a point — keywords field present
```

---

### 14.12 — Acceptance Criteria

- [ ] `mv_topic_keywords` has `tfidf_score` and `across_clusters` columns
- [ ] `mv_topic_keywords` includes Gemini cluster keywords (with 2x weight)
- [ ] No keyword shorter than 3 characters appears in `document.keywords`
- [ ] Georgian stopwords ("ის", "და", "ამ") absent from `document.keywords`
- [ ] `ScoredClusterMatcher` returns 0 clusters for query "a" (too short)
- [ ] Query "უმუშეველ" (inflected) matches unemployment cluster via entity signal
- [ ] Query "GDP growth rate" returns GDP cluster with entity signal active
- [ ] Query "სად ვნახო ინფლაცია" does NOT return clusters on "ვნახო" alone
- [ ] `min-score` threshold filters out clusters with only 1 weak keyword match
- [ ] `maxDiversityPerPrefix` prevents 4 inflation clusters dominating a mixed query
- [ ] Qdrant point payload contains `keywords` field (non-empty `String`)
- [ ] `RetrievalContextService` receives `retrievalText`, not raw user message

---

## 14.13 — Errata: Corrections to Section 14 Code (Self-Audit)

> **Critical.** The code in sections 14.4–14.6 contains 7 defects discovered in self-audit
> against the real codebase. This section provides exact corrections. Junior must apply
> these INSTEAD OF the original snippets above.

---

### Correction 1 — `Entity` field names (fixes sections 14.5, 14.6)

Section 14 incorrectly references `QueryEntity` with `entity.normalized()` and `entity.raw()`.

Real class (from `libs/platform-contracts`):
```java
// libs/platform-contracts/.../enrichment/Entity.java
public record Entity(String type, String value, String normalizedForm, double confidence) {}
```

And `AnalyzedQuery.entities()` returns `List<Entity>`, not `List<QueryEntity>`.

**Correct field usage everywhere in JdbcClusterMatchAdapter:**
```java
// WRONG (section 14.6):
for (QueryEntity entity : query.entities()) {
    forms.add(entity.normalized().toLowerCase());
    if (!entity.raw().equalsIgnoreCase(entity.normalized())) forms.add(entity.raw().toLowerCase());
}

// CORRECT:
for (Entity entity : query.entities()) {
    if (entity.normalizedForm() != null) forms.add(entity.normalizedForm().toLowerCase());
    if (entity.value() != null
            && !entity.value().equalsIgnoreCase(entity.normalizedForm())) {
        forms.add(entity.value().toLowerCase());
    }
}
```

---

### Correction 2 — `query.normalized()`, not `query.normalizedText()` (fixes section 14.6)

```java
// AnalyzedQuery record fields: original, spellFixed, normalized, retrievalText, ...
// WRONG:   query.normalizedText()
// CORRECT: query.normalized()

// In JdbcClusterMatchAdapter.match():
String normalized = query.normalized() == null ? "" : query.normalized().strip().toLowerCase();
```

---

### Correction 3 — Add `loadClusterLabels` to `DerivedCatalogReader` port

`ScoredClusterMatcher` needs cluster labels for diversity grouping and for constructing
`DerivedClusterMatch`. The port must expose this method.

**Updated port:**
```java
// apps/backend/src/main/java/com/geostat/chat/domain/catalog/DerivedCatalogReader.java
public interface DerivedCatalogReader {

    DerivedClusterMatch matchClusters(String query, String language, int limit);

    // NEW — load labels for a known set of cluster IDs (used by ScoredClusterMatcher)
    List<DerivedTopicCluster> loadClusterLabels(List<UUID> clusterIds);

    List<DerivedCatalogLink> findPortalLinks(List<UUID> topicClusterIds, String language);
    List<DerivedCatalogLink> findSpecificLinks(List<UUID> topicClusterIds, String language, int maxRank);
    List<DerivedCatalogLink> findTopPortalLinks(String language, int limit);
}
```

**Add implementation in `JdbcDerivedCatalogReader`** — the private `loadClusterLabels` method
already exists (line 134). Just expose it via the interface (rename to public, add `@Override`):

```java
@Override
public List<DerivedTopicCluster> loadClusterLabels(List<UUID> clusterIds) {
    if (clusterIds == null || clusterIds.isEmpty()) return List.of();
    // existing SQL from JdbcDerivedCatalogReader lines 134–168 — unchanged
    ...
}
```

---

### Correction 4 — `ScoredCluster` must carry label for diversity (fixes section 14.5)

The `applyDiversity()` method in Section 14.5 groups clusters by `matchReason` prefix — this
is **wrong**. Diversity must group by cluster topic label (e.g., "Inflation" vs "GDP").

**Updated `ScoredCluster` record:**
```java
// apps/backend/src/main/java/com/geostat/chat/domain/catalog/ScoredCluster.java
package com.geostat.chat.domain.catalog;

import java.util.UUID;

public record ScoredCluster(
        UUID   clusterId,
        double score,
        String labelKa,       // ← ADD: used for diversity grouping
        String labelEn,       // ← ADD: used for diversity grouping
        String matchReason
) {}
```

**Updated `JdbcClusterMatchAdapter.match()`** — after score fusion, load labels for top
candidates from DB before returning, so `ScoredCluster` carries label:

```java
@Override
public List<ScoredCluster> match(AnalyzedQuery query, String language, int limit) {
    ...
    // After signal fusion, load labels for top candidates (single SQL round-trip):
    List<UUID> topIds = fused.stream().map(Map.Entry::getKey).limit((long) limit * 2).toList();
    Map<UUID, DerivedTopicCluster> labelMap = loadLabelsAsMap(topIds);

    return fused.stream()
            .map(e -> {
                DerivedTopicCluster label = labelMap.get(e.getKey());
                return new ScoredCluster(
                        e.getKey(),
                        computeScore(e.getValue()),
                        label != null ? label.labelKa() : "",
                        label != null ? label.labelEn() : "",
                        buildReason(e.getValue()));
            })
            .sorted(Comparator.comparingDouble(ScoredCluster::score).reversed())
            .limit(limit)
            .toList();
}

// Single SQL to load labels for candidate IDs:
private Map<UUID, DerivedTopicCluster> loadLabelsAsMap(List<UUID> ids) {
    if (ids.isEmpty()) return Map.of();
    Map<UUID, DerivedTopicCluster> result = new LinkedHashMap<>();
    jdbcTemplate.query(
        "SELECT id, label_ka, label_en FROM ingestion.topic_cluster WHERE id = ANY(?)",
        ps -> {
            UUID[] arr = ids.toArray(UUID[]::new);
            ps.setArray(1, ps.getConnection().createArrayOf("uuid", arr));
        },
        rs -> {
            UUID id = rs.getObject("id", UUID.class);
            result.put(id, new DerivedTopicCluster(id,
                    rs.getString("label_ka"), rs.getString("label_en")));
        });
    return result;
}
```

**Corrected `applyDiversity()` in `ScoredClusterMatcher`** — groups by label, not matchReason:

```java
private List<DerivedTopicCluster> applyDiversityAndLoadLabels(
        List<ScoredCluster> scored,
        DerivedCatalogReader catalogReader,
        int limit) {

    // Collect top IDs respecting diversity:
    Map<String, Integer> labelGroupCount = new LinkedHashMap<>();
    List<UUID> selectedIds = new ArrayList<>();

    for (ScoredCluster cluster : scored) {
        if (selectedIds.size() >= limit) break;
        // Group by first 3 words of label_ka (or label_en as fallback)
        String groupKey = labelGroupKey(cluster.labelKa(), cluster.labelEn());
        int count = labelGroupCount.getOrDefault(groupKey, 0);
        if (count < maxDiversityPerPrefix) {
            selectedIds.add(cluster.clusterId());
            labelGroupCount.put(groupKey, count + 1);
        }
    }

    // Load full labels for selected IDs (preserving order)
    return catalogReader.loadClusterLabels(selectedIds);
}

private static String labelGroupKey(String labelKa, String labelEn) {
    String label = (labelKa != null && !labelKa.isBlank()) ? labelKa : labelEn;
    if (label == null || label.isBlank()) return "";
    String[] words = label.toLowerCase().split("\\s+");
    // Group by first 2 words — "gdp growth" and "gdp rate" → same group
    return words.length >= 2 ? words[0] + " " + words[1] : words[0];
}
```

**Updated `ScoredClusterMatcher.match()` — correct return type chain:**

```java
// CORRECTED full method:
public DerivedClusterMatch match(AnalyzedQuery query, String language, int limit) {
    List<ScoredCluster> scored = clusterMatchPort.match(query, language, limit * 2);

    List<ScoredCluster> confident = scored.stream()
            .filter(c -> c.score() >= minScore)
            .toList();

    // Load labels + apply diversity in one step:
    List<DerivedTopicCluster> diverse =
            applyDiversityAndLoadLabels(confident, catalogReader, limit);

    return new DerivedClusterMatch(diverse);  // ← correct constructor
}
```

`ScoredClusterMatcher` now also holds `DerivedCatalogReader catalogReader` injected via constructor.

---

### Correction 5 — Batch Signal C and D (N+1 → single query each)

Signal C (entity forms) and Signal D (expansion forms) must not make N separate SQL calls.
Use a single batched query with a `TEXT[]` array parameter:

```java
// Replaces applySignalC() + applySignalD() in JdbcClusterMatchAdapter:

private void applyBatchedFormSignal(Map<UUID, Double[]> scores,
                                     List<String> forms,
                                     String language,
                                     int signalIndex,    // 2 = Signal C, 3 = Signal D
                                     int limitCandidates) {
    List<String> valid = forms.stream()
            .filter(f -> f != null && f.length() >= 3)
            .map(String::toLowerCase)
            .distinct()
            .toList();
    if (valid.isEmpty()) return;

    // Build dynamic SQL: position(? IN tk.keyword) > 0 for each form
    // Use single array cross-join: CROSS JOIN unnest(CAST(? AS text[])) AS t(form)
    jdbcTemplate.query(
        """
        SELECT tc.id AS cluster_id, SUM(tk.tfidf_score) AS score
        FROM ingestion.topic_cluster tc
        JOIN ingestion.mv_topic_keywords tk
          ON tk.topic_cluster_id = tc.id AND tk.language = ?
        CROSS JOIN unnest(CAST(? AS text[])) AS t(form)
        WHERE tc.approved = true
          AND tk.rank <= ?
          AND position(t.form IN tk.keyword) > 0
        GROUP BY tc.id
        ORDER BY SUM(tk.tfidf_score) DESC
        LIMIT ?
        """,
        ps -> {
            ps.setString(1, language);
            Array formsArray = ps.getConnection().createArrayOf("text", valid.toArray());
            ps.setArray(2, formsArray);
            ps.setInt(3, maxRank);
            ps.setInt(4, limitCandidates);
        },
        rs -> {
            UUID id = rs.getObject("cluster_id", UUID.class);
            Double[] s = scores.computeIfAbsent(id, k -> new Double[]{0.0, 0.0, 0.0, 0.0});
            s[signalIndex] += rs.getDouble("score");
        });
}
```

Calls become:
```java
// Signal C: entity forms (inflection-safe)
applyBatchedFormSignal(signalScores, entityForms, language, 2, limit * 2);

// Signal D: expansion forms (synonym coverage)
applyBatchedFormSignal(signalScores, expansionForms, language, 3, limit * 2);
```

---

### Correction 6 — Remove all remaining hardcoded values

Every magic number must be `@Value`-injected:

```java
// JdbcClusterMatchAdapter — full corrected constructor:
public JdbcClusterMatchAdapter(
        JdbcTemplate catalogJdbcTemplate,
        @Value("${geostat.chat.cluster.weight.keyword:0.35}")      double wKeyword,
        @Value("${geostat.chat.cluster.weight.label:0.25}")        double wLabel,
        @Value("${geostat.chat.cluster.weight.entity:0.30}")       double wEntity,
        @Value("${geostat.chat.cluster.weight.expansion:0.10}")    double wExpansion,
        @Value("${geostat.chat.cluster.mv-keyword-rank:30}")       int    maxRank,
        @Value("${geostat.chat.cluster.score.saturation:5.0}")     double scoreSaturation,
        @Value("${geostat.chat.cluster.candidate-multiplier:2}")   int    candidateMultiplier) {
    this.jdbcTemplate          = catalogJdbcTemplate;
    this.wKeyword              = wKeyword;
    this.wLabel                = wLabel;
    this.wEntity               = wEntity;
    this.wExpansion            = wExpansion;
    this.maxRank               = maxRank;
    this.scoreSaturation       = scoreSaturation;
    this.candidateMultiplier   = candidateMultiplier;
}

// normalize() uses injected saturation:
private double normalize(Double raw) {
    if (raw == null || raw <= 0) return 0.0;
    return Math.min(1.0, raw / scoreSaturation);
}
```

Updated `application-custom.yml`:
```yaml
geostat:
  chat:
    cluster:
      min-score:                  ${CLUSTER_MIN_SCORE:0.10}
      max-diversity-per-prefix:   ${CLUSTER_MAX_DIVERSITY:2}
      mv-keyword-rank:            ${CLUSTER_MV_KW_RANK:30}
      candidate-multiplier:       ${CLUSTER_CANDIDATE_MULT:2}
      score:
        saturation:               ${CLUSTER_SCORE_SAT:5.0}
      weight:
        keyword:                  ${CLUSTER_W_KEYWORD:0.35}
        label:                    ${CLUSTER_W_LABEL:0.25}
        entity:                   ${CLUSTER_W_ENTITY:0.30}
        expansion:                ${CLUSTER_W_EXPANSION:0.10}
```

---

### Corrected Acceptance Criteria (replaces section 14.12 list)

- [ ] `Entity.normalizedForm()` and `Entity.value()` used (not `QueryEntity`)
- [ ] `query.normalized()` used (not `query.normalizedText()`)
- [ ] `DerivedCatalogReader` port has `loadClusterLabels(List<UUID>)` method
- [ ] `JdbcDerivedCatalogReader` implements `loadClusterLabels()` (exposes existing private method)
- [ ] `ScoredCluster` record has `labelKa` and `labelEn` fields
- [ ] `applyDiversity()` groups by `labelGroupKey(labelKa, labelEn)`, not matchReason prefix
- [ ] Signal C and D make exactly **one** JDBC call each (batch via `unnest(CAST(? AS text[]))`)
- [ ] `/ 5.0` saturation constant replaced by `@Value` injected field
- [ ] `rank <= 50` / `rank <= 30` replaced by `maxRank` configurable field
- [ ] `limit * 2` / `limit * 3` replaced by `candidateMultiplier` configurable field
- [ ] All 7 defects from this section resolved before implementing section 14.4–14.9

---

## 15. SmallTalkHandler + TopicDetector — Layer 6 Close (Audit)

---

### 15.1 — SmallTalkHandler: 6 Violations

#### Violation 1 — `containsAny()` 3rd Duplicate (DRY)

**File:** `SmallTalkHandler.java` line 85

Identical private method already exists in `QueryRouter` and `HeuristicIntentClassifier`.
Section 12 defines `KeywordMatcher` as the single shared utility.

```java
// DELETE the private containsAny() from SmallTalkHandler entirely.
// REPLACE all calls with: KeywordMatcher.containsAny(text, List.of(keywords))
```

---

#### Violation 2 — Keyword lists hardcoded (OCP violation)

7 `if` blocks each with inline `String...` keyword arrays. Adding "barev" or "xaçmuroba"
requires modifying Java code. Same pattern fixed for `HeuristicIntentClassifier` and
`QueryRouter` in Sections 11/12.

**New file:** `apps/backend/src/main/resources/catalog/small-talk.yaml`

```yaml
# Small-talk keyword triggers and response keys.
# Extend this file — no Java code change needed.
entries:
  - id: greeting
    triggers:
      - "გამარჯობა"
      - "სალამი"
      - "გაუმარჯოს"
      - "მოგესალმები"
      - "hello"
      - "hi"
      - "hey"
    maxLength: 40          # only match if message.length() < maxLength (0 = no limit)
    responseKey: greeting  # → references chat-prompts.yaml small-talk section

  - id: thanks
    triggers:
      - "მადლობა"
      - "გმადლობთ"
      - "დიდი მადლობა"
      - "thank"
      - "thanks"
    maxLength: 40
    responseKey: thanks

  - id: how_are_you
    triggers:
      - "როგორ ხარ"
      - "რა ხდება"
      - "how are you"
      - "what's up"
      - "რას აკეთებ"
    maxLength: 0
    responseKey: how_are_you

  - id: who_are_you
    triggers:
      - "ვინ ხარ"
      - "რა ხარ"
      - "who are you"
      - "what are you"
      - "რა შეგიძლია"
      - "what can you do"
      - "რაში მეხმარები"
    maxLength: 0
    responseKey: who_are_you

  - id: who_created
    triggers:
      - "ვინ შეგქმნა"
      - "ვინ გაკეთა"
      - "ვინ დაგწერა"
      - "ვინ შექმნა"
      - "შემქმნელ"
      - "დეველოპერ"
      - "who created"
      - "who made you"
      - "who built you"
    maxLength: 0
    responseKey: who_created

  - id: goodbye
    triggers:
      - "ნახვამდის"
      - "მშვიდობით"
      - "bye"
      - "goodbye"
      - "see you"
    maxLength: 30
    responseKey: goodbye

  - id: help
    triggers:
      - "დამეხმარე"
      - "help"
      - "დახმარება"
      - "არ ვიცი"
      - "რა ვკითხო"
    maxLength: 0
    responseKey: help

portal_list:
  triggers:
    - "პორტალ"
    - "portal"
    - "portals"
    - "კალკულატორებ"
    - "calculators"
    - "ინტერაქტიულ ინსტრუმენტ"
    - "interactive tool"
  list_triggers:
    - "რა პორტალ"
    - "all portal"
    - "რა კალკულატორ"
  # These keywords make the query specific (not a portal list request)
  specific_exceptions:
    - "cpi"
    - "სამომხმარებლო"
    - "ინდექსაცი"
    - "პერსონალურ ინფლაცი"
    - "გადახდ"
    - "გადასახად"
    - "mytaxes"
    - "ავტომობილ"
    - "მანქან"
    - "ბავშვ"
    - "მოზარდ"
    - "youth"
  maxLength: 35   # short generic queries only
```

---

#### Violation 3 — Response strings hardcoded in Java (14 strings, 2 languages)

`PromptCatalog` is already YAML-driven (backed by `chat-prompts.yaml`). Small-talk responses
belong in the same YAML.

**Add to `apps/backend/src/main/resources/prompts/chat-prompts.yaml`:**

```yaml
# Existing prompts...

small-talk:
  greeting:
    ka: "გამარჯობა. მე საქსტატის ვირტუალური ასისტენტი ვარ. რაში შემიძლია დაგეხმაროთ?"
    en: "Hello. I'm GeoStat's virtual assistant. How can I help you?"
  thanks:
    ka: "არაფრის. თუ სხვა რამეში დაგჭირდებათ დახმარება, მითხარით."
    en: "You're welcome. Let me know if you need anything else."
  how_are_you:
    ka: "კარგად, მადლობა. რაში შემიძლია დაგეხმაროთ?"
    en: "Doing well, thanks. What can I help you with?"
  who_are_you:
    ka: "მე საქსტატის ვირტუალური ასისტენტი ვარ. შემიძლია დაგეხმაროთ სტატისტიკური ინფორმაციის მოძიებაში — მოსახლეობა, ეკონომიკა, დასაქმება, ვაჭრობა და სხვა."
    en: "I'm GeoStat's virtual assistant. I can help you find statistical information — population, economy, employment, trade, and more."
  who_created:
    ka: "მე შევიქმენი საქსტატში (საქართველოს სტატისტიკის ეროვნული სამსახური). მთავარი დეველოპერი — გუგა გოგუა (https://www.linkedin.com/in/guga-gogua-418a902a2/)."
    en: "I was created at GeoStat (National Statistics Office of Georgia). Lead developer — Guga Gogua (https://www.linkedin.com/in/guga-gogua-418a902a2/)."
  goodbye:
    ka: "ნახვამდის. წარმატებები."
    en: "Goodbye. Take care."
  help:
    ka: "შეგიძლიათ იკითხოთ მაგალითად: მოსახლეობის სტატისტიკა, ინფლაციის მონაცემები, დასაქმება, ტურიზმი, საგარეო ვაჭრობა."
    en: "You can ask about: population statistics, inflation data, employment, tourism, external trade."
  clarification:
    ka: "ვერ დავადგინე, კონკრეტულად რა გაინტერესებთ. გთხოვთ, გადაუფორმეთ კითხვა ან დააკონკრეტეთ — მაგალითად: \"მოსახლეობა\", \"ინფლაცია\", \"დასაქმება\", \"ვაჭრობა\", \"ტურიზმი\"."
    en: "I wasn't able to identify what you're looking for. Could you clarify or rephrase? For example: \"population\", \"inflation\", \"employment\", \"trade\", \"tourism\"."
```

**Updated `PromptCatalog` interface** — add small-talk method:

```java
// domain/prompt/PromptCatalog.java — add:
String smallTalkResponse(String responseKey, boolean georgian);
String clarificationText(boolean georgian);
```

**`SmallTalkHandler` becomes a pure engine** — zero strings, zero keyword lists in Java:

```java
@Component
public class SmallTalkHandler {

    private final SmallTalkConfig config;    // loaded from small-talk.yaml
    private final PromptCatalog promptCatalog;

    public SmallTalkHandler(SmallTalkConfig config, PromptCatalog promptCatalog) {
        this.config = config;
        this.promptCatalog = promptCatalog;
    }

    public String handle(String message, boolean isGeorgian) {
        String lower = message.toLowerCase();
        for (SmallTalkEntry entry : config.entries()) {
            if (entry.maxLength() > 0 && message.length() >= entry.maxLength()) continue;
            if (KeywordMatcher.containsAny(lower, entry.triggers())) {
                return promptCatalog.smallTalkResponse(entry.responseKey(), isGeorgian);
            }
        }
        return null;
    }

    public String clarificationRequest(boolean isGeorgian) {
        return promptCatalog.clarificationText(isGeorgian);
    }

    public boolean isPortalListQuery(String lowerQuery) {
        PortalListConfig pl = config.portalList();
        if (KeywordMatcher.containsAny(lowerQuery, pl.specificExceptions())) return false;
        boolean isListReq  = KeywordMatcher.containsAny(lowerQuery, pl.listTriggers());
        boolean hasKw      = KeywordMatcher.containsAny(lowerQuery, pl.triggers());
        boolean isShort    = pl.maxLength() > 0 && lowerQuery.length() < pl.maxLength();
        return isListReq || (hasKw && isShort);
    }
}
```

---

#### Violations 4–6 — Summary (covered by fixes above)

| Violation | Fix |
|-----------|-----|
| `clarificationRequest()` returns hardcoded string | → `promptCatalog.clarificationText(isGeorgian)` |
| `< 40`, `< 30`, `< 35` magic lengths | → `entry.maxLength()` from YAML |
| `isPortalListQuery()` dual hardcoded keyword lists | → `small-talk.yaml` `portal_list` section |

---

### 15.2 — TopicDetector: 3 Issues

#### Issue 1 — `MAX_TOPICS`, `MAX_CONTEXT_USER_TURNS` static hardcodes

```java
// CURRENT:
private static final int MAX_TOPICS = 3;
private static final int MAX_CONTEXT_USER_TURNS = 2;

// CORRECT — inject via constructor:
public TopicDetector(
        ChatClient chatClient,
        TopicCatalog topicCatalog,
        PromptCatalog promptCatalog,
        AiChatOptionsFactory chatOptionsFactory,
        @Value("${geostat.chat.topic-detector.max-topics:3}") int maxTopics,
        @Value("${geostat.chat.topic-detector.max-context-turns:2}") int maxContextTurns) {
    ...
    this.maxTopics       = maxTopics;
    this.maxContextTurns = maxContextTurns;
}
```

Add to `application-custom.yml`:
```yaml
geostat:
  chat:
    topic-detector:
      max-topics:        ${TOPIC_DETECTOR_MAX_TOPICS:3}
      max-context-turns: ${TOPIC_DETECTOR_MAX_TURNS:2}
```

---

#### Issue 2 — `Topic.valueOf(cleaned)` is brittle

If Gemini returns "ECONOMIC_ACTIVITY" (valid) → works.
If Gemini returns "ECONOMIC ACTIVITY" → `cleaned` = "ECONOMICACTIVITY" → `valueOf` throws → catch returns GENERAL.
But: `catch (Exception)` is a code smell for control flow — it hides real errors.

```java
// CURRENT (brittle — exception as control flow):
return Topic.valueOf(cleaned);

// CORRECT (explicit, zero exceptions):
return Arrays.stream(Topic.values())
        .filter(t -> t.name().equals(cleaned))
        .findFirst()
        .orElseGet(() -> {
            log.debug("AI returned unknown topic '{}', defaulting to GENERAL", cleaned);
            return Topic.GENERAL;
        });
```

---

#### Issue 3 — `recentUserContext()` iterates full history every call

```java
// Current: iterates ALL history messages, then subLists
for (Message message : history) {    // ← O(N) scan of full deque
    if (message instanceof UserMessage ...) recent.add(um);
```

For long conversations (100+ turns), this scans everything. For context, only the last 4
messages (2 turns × 2) are needed.

```java
// CORRECT — take from tail, avoid full scan:
private List<Message> recentUserContext(Deque<Message> history) {
    if (history == null || history.isEmpty()) return List.of();
    int needed = maxContextTurns * 2;
    List<Message> tail = new ArrayList<>(needed + 1);
    Iterator<Message> desc = ((ArrayDeque<Message>) history).descendingIterator();
    while (desc.hasNext() && tail.size() < needed) {
        tail.add(0, desc.next());   // prepend to maintain order
    }
    return tail;
}
```

Note: requires `history` to be `ArrayDeque<Message>` (already is, based on `ChatContext`).

---

### 15.3 — Acceptance Criteria

**SmallTalkHandler:**
- [ ] Zero keyword strings in Java source (all in `small-talk.yaml`)
- [ ] Zero response strings in Java source (all in `chat-prompts.yaml` small-talk section)
- [ ] `containsAny()` removed — uses `KeywordMatcher.containsAny()`
- [ ] `clarificationRequest()` delegates to `promptCatalog.clarificationText()`
- [ ] `isPortalListQuery()` driven by `portal_list` section in `small-talk.yaml`
- [ ] Length thresholds come from YAML `maxLength` field

**TopicDetector:**
- [ ] `MAX_TOPICS` → `@Value("${geostat.chat.topic-detector.max-topics:3}")`
- [ ] `MAX_CONTEXT_USER_TURNS` → `@Value("${geostat.chat.topic-detector.max-context-turns:2}")`
- [ ] `Topic.valueOf()` replaced with safe `Arrays.stream().filter().findFirst().orElse()` lookup
- [ ] `recentUserContext()` uses descending iterator, not full-scan + subList

---

### Layer 6 — CLOSED

All retrieval + query-understanding components audited:

| Component | Status |
|-----------|--------|
| HeuristicIntentClassifier | ✅ Section 11 |
| QueryRouter | ✅ Section 12 |
| HeuristicQueryEntityExtractor | ✅ Section 11 |
| terminology-overlay.yaml | ✅ Section 11 |
| DefaultConfidenceAssessor | ✅ Section 5 |
| CatalogRagLinkMerger | ✅ Section 12 |
| DisplayBoilerplate | ✅ Section 12 |
| mv_topic_keywords + keyword pipeline | ✅ Section 14 |
| SmallTalkHandler | ✅ Section 15 |
| TopicDetector | ✅ Section 15 |
| ClarificationService | ✅ Clean — no action |
