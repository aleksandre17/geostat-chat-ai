# Layer 7 — Chat / Response Layer: Remaining Work

> Session 2026-05-26: L7-01…L7-11 implemented (hardcodes, budget, grounding, pipeline dedup).
> **Session 2026-05-27:** R7-A..R7-J all confirmed implemented (A,B,C,D,E,F,H,I,J). All items in this plan are complete. R7-G (Rabin-Karp) remains FUTURE — only when max-chunks > 50.
> This file contains **only what remains**. Junior: follow phases R7-A → R7-F in order.

---

## Status Map

| Component | Status | Remaining |
|---|---|---|
| `StreamIntroExtractor` | ✅ Done | — |
| `ResponseGroundingEnforcer` | ✅ Done | — |
| `PromptBuilder` | ✅ Done | — |
| `PromptBudgetTrimmer` | ✅ Done | — |
| `SessionTurnRecorder` | ✅ Done (→ @Component) | — |
| `ExplanationGroundingVerifier` | ✅ Done (→ @Component) | O(N²) → future R7-G |
| `AiResponseParser` | ✅ Done | — |
| `ResponseSanitizer` | ✅ Done | — |
| `ChatResultFactory` | ✅ Done | — |
| `ChatService` | ✅ Done (buildPipeline, maxRag, double-trim) | constructor → R7-F |
| `ClarificationService` | 🔴 BUG | double history trim — R7-A |
| `SmallTalkHandler` | 🔴 HIGH | all hardcoded — R7-B |
| `TopicDetector` | ⚠️ MEDIUM | 3 violations — R7-C |
| `ChatPipelineContext` | ⚠️ MEDIUM | nullable → sealed — R7-D |
| `PromptCatalog.uiString()` | ⚠️ MINOR | String keys → enum — R7-E |
| `ChatService` constructor | ⚠️ MEDIUM | 27 params → decompose — R7-F |

---

## R7-A — `ClarificationService`: redundant history trim (BUG — 1 line)

**File:** `apps/backend/src/main/java/com/geostat/chat/application/chat/ClarificationService.java`

**Line 63 — current:**
```java
messages.addAll(HistoryBudgetTrimmer.trim(ctx.history(), aiChatProperties.maxHistoryMessages()));
```

`ctx.history()` is already trimmed in `ChatService.buildContext()`. The second trim silently
drops messages that were intentionally kept in context. This is the same bug fixed in
`ChatService.buildGeminiPrompt()` in the 2026-05-26 session.

**Fix (replace line 63 with):**
```java
messages.addAll(ctx.history());
```

**Verify:** `HistoryBudgetTrimmer.trim` is called in `ClarificationService` 0 times.

---

## R7-B — `SmallTalkHandler`: 6 violations (HIGH)

**Files to change:**
- `apps/backend/src/main/java/com/geostat/chat/application/chat/SmallTalkHandler.java`
- `apps/backend/src/main/java/com/geostat/chat/infrastructure/query/HeuristicIntentClassifier.java`
- `apps/backend/src/main/java/com/geostat/chat/application/chat/QueryRouter.java`
- `apps/backend/src/main/java/com/geostat/chat/infrastructure/config/QueryUnderstandingConfiguration.java`

**Files to create:**
- `apps/backend/src/main/resources/catalog/small-talk.yaml`
- `apps/backend/src/main/java/com/geostat/chat/infrastructure/catalog/SmallTalkCatalogLoader.java`
- `apps/backend/src/main/java/com/geostat/chat/application/util/KeywordMatcher.java`

---

### B-1 — Create `src/main/resources/catalog/small-talk.yaml`

```yaml
version: 1

groups:
  - id: greeting
    patterns: ["გამარჯობა", "სალამი", "hello", "hi", "hey", "გაუმარჯოს", "მოგესალმები"]
    maxLength: 40
    response:
      ka: "გამარჯობა. მე საქსტატის ვირტუალური ასისტენტი ვარ. რაში შემიძლია დაგეხმაროთ?"
      en: "Hello. I'm GeoStat's virtual assistant. How can I help you?"
  - id: gratitude
    patterns: ["მადლობა", "გმადლობთ", "thank", "thanks", "დიდი მადლობა"]
    maxLength: 40
    response:
      ka: "არაფრის. თუ სხვა რამეში დაგჭირდებათ დახმარება, მითხარით."
      en: "You're welcome. Let me know if you need anything else."
  - id: well-being
    patterns: ["როგორ ხარ", "რა ხდება", "how are you", "what's up", "რას აკეთებ"]
    maxLength: 0
    response:
      ka: "კარგად, მადლობა. რაში შემიძლია დაგეხმაროთ?"
      en: "Doing well, thanks. What can I help you with?"
  - id: identity
    patterns: ["ვინ ხარ", "რა ხარ", "who are you", "what are you", "რა შეგიძლია", "what can you do", "რაში მეხმარები"]
    maxLength: 0
    response:
      ka: "მე საქსტატის ვირტუალური ასისტენტი ვარ. შემიძლია დაგეხმაროთ სტატისტიკური ინფორმაციის მოძიებაში — მოსახლეობა, ეკონომიკა, დასაქმება, ვაჭრობა და სხვა."
      en: "I'm GeoStat's virtual assistant. I can help you find statistical information — population, economy, employment, trade, and more."
  - id: creator
    patterns: ["ვინ შეგქმნა", "ვინ გაკეთა", "ვინ დაგწერა", "ვინ შექმნა", "ვინ აგაწყო", "შემქმნელ", "დეველოპერ", "who created", "who made you", "who built you"]
    maxLength: 0
    response:
      ka: "მე შევიქმენი საქსტატში (საქართველოს სტატისტიკის ეროვნული სამსახური). მთავარი დეველოპერი — გუგა გოგუა."
      en: "I was created at GeoStat (National Statistics Office of Georgia). Lead developer — Guga Gogua."
  - id: farewell
    patterns: ["ნახვამდის", "მშვიდობით", "bye", "goodbye", "see you"]
    maxLength: 30
    response:
      ka: "ნახვამდის. წარმატებები."
      en: "Goodbye. Take care."
  - id: help
    patterns: ["დამეხმარე", "help", "დახმარება", "არ ვიცი", "რა ვკითხო"]
    maxLength: 0
    response:
      ka: "შეგიძლიათ იკითხოთ მაგალითად: მოსახლეობის სტატისტიკა, ინფლაციის მონაცემები, დასაქმება, ტურიზმი, საგარეო ვაჭრობა."
      en: "You can ask about: population statistics, inflation data, employment, tourism, external trade."

clarification:
  ka: "ვერ დავადგინე, კონკრეტულად რა გაინტერესებთ. გთხოვთ, გადაუფორმეთ კითხვა ან დააკონკრეტეთ - მაგალითად: \"მოსახლეობა\", \"ინფლაცია\", \"დასაქმება\", \"ვაჭრობა\", \"ტურიზმი\"."
  en: "I wasn't able to identify what you're looking for. Could you clarify or rephrase? For example: \"population\", \"inflation\", \"employment\", \"trade\", \"tourism\"."

portalList:
  portalKeywords: ["პორტალ", "portal", "portals"]
  calculatorKeywords: ["კალკულატორებ", "calculators", "ინტერაქტიულ ინსტრუმენტ", "interactive tool"]
  listRequestKeywords: ["რა პორტალ", "all portal", "რა კალკულატორ"]
  excludeKeywords: ["cpi", "სამომხმარებლო", "ინდექსაცი", "პერსონალურ ინფლაცი", "გადახდ", "გადასახად", "mytaxes", "ავტომობილ", "მანქან", "ბავშვ", "მოზარდ", "youth"]
  maxGenericLength: 35
```

> **Note on naming:** use camelCase keys in YAML (`maxLength`, `portalList`, etc.).
> Jackson's `findAndRegisterModules()` maps camelCase fields to camelCase YAML keys directly
> without needing `@JsonProperty`. This is simpler than kebab-case which requires annotations.

---

### B-2 — Create `KeywordMatcher`

**File:** `apps/backend/src/main/java/com/geostat/chat/application/util/KeywordMatcher.java`

> Create the package folder `application/util` if it doesn't exist.

```java
package com.geostat.chat.application.util;

import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Shared keyword matching utility for SmallTalkHandler, QueryRouter, HeuristicIntentClassifier.
 * Latin keywords use word-boundary matching to prevent "hi" from matching "history".
 * Georgian keywords use substring matching (Georgian chars are outside ASCII \w).
 */
@Component
public class KeywordMatcher {

    public boolean containsAny(String text, List<String> keywords) {
        if (text == null || keywords == null) return false;
        for (String kw : keywords) {
            String kwLower = kw.toLowerCase();
            boolean isLatin = kwLower.chars().allMatch(c -> c < 128);
            if (isLatin) {
                if (Pattern.compile("\\b" + Pattern.quote(kwLower) + "\\b").matcher(text).find()) {
                    return true;
                }
            } else {
                if (text.contains(kwLower)) return true;
            }
        }
        return false;
    }

    /** Varargs overload for inline call sites that don't yet use a list. */
    public boolean containsAny(String text, String... keywords) {
        return containsAny(text, List.of(keywords));
    }
}
```

---

### B-3 — Create `SmallTalkCatalogLoader`

**File:** `apps/backend/src/main/java/com/geostat/chat/infrastructure/catalog/SmallTalkCatalogLoader.java`

```java
package com.geostat.chat.infrastructure.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Loads {@code catalog/small-talk.yaml} at startup. */
@Component
public class SmallTalkCatalogLoader {

    /** One localized string — ka + en. */
    public record LocalizedString(String ka, String en) {}

    /** One response group (greeting, farewell, …). maxLength=0 means unlimited. */
    public record SmallTalkGroup(
            String id,
            List<String> patterns,
            int maxLength,
            LocalizedString response) {}

    /** Config for portal-list detection. */
    public record PortalListConfig(
            List<String> portalKeywords,
            List<String> calculatorKeywords,
            List<String> listRequestKeywords,
            List<String> excludeKeywords,
            int maxGenericLength) {}

    /** Root YAML structure. */
    public record SmallTalkRoot(
            int version,
            List<SmallTalkGroup> groups,
            LocalizedString clarification,
            PortalListConfig portalList) {}

    private SmallTalkRoot root;

    @PostConstruct
    void load() throws IOException {
        ClassPathResource resource = new ClassPathResource("catalog/small-talk.yaml");
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory()).findAndRegisterModules();
        root = mapper.readValue(resource.getInputStream(), SmallTalkRoot.class);
    }

    public SmallTalkRoot get() {
        return root;
    }
}
```

---

### B-4 — Rewrite `SmallTalkHandler`

**Replace the entire content of `SmallTalkHandler.java`:**

```java
package com.geostat.chat.application.chat;

import com.geostat.chat.application.util.KeywordMatcher;
import com.geostat.chat.infrastructure.catalog.SmallTalkCatalogLoader;
import com.geostat.chat.infrastructure.catalog.SmallTalkCatalogLoader.PortalListConfig;
import com.geostat.chat.infrastructure.catalog.SmallTalkCatalogLoader.SmallTalkGroup;
import com.geostat.chat.infrastructure.catalog.SmallTalkCatalogLoader.SmallTalkRoot;
import org.springframework.stereotype.Component;

/**
 * Handles small-talk detection and portal-list query detection.
 * All patterns and responses are driven by {@code catalog/small-talk.yaml}.
 * Returns {@code null} when the message is not small talk.
 */
@Component
public class SmallTalkHandler {

    private final SmallTalkCatalogLoader loader;
    private final KeywordMatcher keywordMatcher;

    public SmallTalkHandler(SmallTalkCatalogLoader loader, KeywordMatcher keywordMatcher) {
        this.loader         = loader;
        this.keywordMatcher = keywordMatcher;
    }

    public String handle(String message, boolean isGeorgian) {
        String lower = message.toLowerCase();
        for (SmallTalkGroup group : loader.get().groups()) {
            if (group.maxLength() > 0 && message.length() > group.maxLength()) continue;
            if (keywordMatcher.containsAny(lower, group.patterns())) {
                return isGeorgian ? group.response().ka() : group.response().en();
            }
        }
        return null;
    }

    public String clarificationRequest(boolean isGeorgian) {
        SmallTalkRoot root = loader.get();
        return isGeorgian ? root.clarification().ka() : root.clarification().en();
    }

    public boolean isPortalListQuery(String lowerQuery) {
        PortalListConfig cfg = loader.get().portalList();
        if (keywordMatcher.containsAny(lowerQuery, cfg.excludeKeywords())) return false;
        boolean hasPortal  = keywordMatcher.containsAny(lowerQuery, cfg.portalKeywords());
        boolean hasCalc    = keywordMatcher.containsAny(lowerQuery, cfg.calculatorKeywords());
        boolean isListReq  = keywordMatcher.containsAny(lowerQuery, cfg.listRequestKeywords());
        boolean isShortGeneric = (hasPortal || hasCalc) && lowerQuery.length() < cfg.maxGenericLength();
        return isListReq || isShortGeneric;
    }
}
```

---

### B-5 — Migrate `QueryRouter` to use `KeywordMatcher`

**File:** `apps/backend/src/main/java/com/geostat/chat/application/chat/QueryRouter.java`

`QueryRouter` is a `@Component` — inject `KeywordMatcher` via constructor.
Remove the private static `containsAny()` method. Replace all `containsAny(...)` call sites
with `keywordMatcher.containsAny(...)`.

```java
@Component
public class QueryRouter {

    private final KeywordMatcher keywordMatcher;

    public QueryRouter(KeywordMatcher keywordMatcher) {
        this.keywordMatcher = keywordMatcher;
    }

    public QueryIntent route(String message, String lowerQuery) {
        if (message == null || message.isBlank()) return QueryIntent.CLARIFY;
        if (keywordMatcher.containsAny(lowerQuery,
                "what is", "what does", "define", "explain", "meaning of",
                "რა არის", "რას ნიშნავს", "განმარტება")) {
            return QueryIntent.CONCEPT;
        }
        if (keywordMatcher.containsAny(lowerQuery,
                "show me", "where can i find", "give me", "download", "find",
                "მაჩვენე", "სად ვნახო", "მომეცი", "ჩამოტვირთ", "open", "go to", "navigate",
                "გადავიდე", "გახსენი")) {
            return QueryIntent.DATA_REQUEST;
        }
        if (keywordMatcher.containsAny(lowerQuery,
                "portal", "calculator", "tool", "statistics page", "website",
                "პორტალი", "კალკულატორი", "ინსტრუმენტი", "საიტი", "გვერდი")) {
            return QueryIntent.NAVIGATE;
        }
        return QueryIntent.CONCEPT;
    }
}
```

---

### B-6 — Migrate `HeuristicIntentClassifier` to use `KeywordMatcher`

**File:** `apps/backend/src/main/java/com/geostat/chat/infrastructure/query/HeuristicIntentClassifier.java`

`HeuristicIntentClassifier` is NOT a `@Component` — it is created via:
```java
// QueryUnderstandingConfiguration.java line 15:
@Bean
HeuristicIntentClassifier heuristicIntentClassifier() {
    return new HeuristicIntentClassifier();
}
```

**Step 1 — Add `KeywordMatcher` constructor parameter to `HeuristicIntentClassifier`:**

```java
public class HeuristicIntentClassifier implements IntentClassifier {

    private final KeywordMatcher keywordMatcher;

    public HeuristicIntentClassifier(KeywordMatcher keywordMatcher) {
        this.keywordMatcher = keywordMatcher;
    }

    @Override
    public QueryIntentKind classify(String message, String normalized, String locale) {
        if (message == null || message.isBlank()) return QueryIntentKind.LOOKUP;
        String lower = normalized == null ? message.toLowerCase() : normalized.toLowerCase();
        // replace all containsAny(...) with keywordMatcher.containsAny(...)
        // keep all keyword lists EXACTLY as-is — only the method changes
        ...
    }
    // Remove the private static containsAny() method
}
```

**Step 2 — Update `QueryUnderstandingConfiguration.heuristicIntentClassifier()` to inject `KeywordMatcher`:**

```java
// QueryUnderstandingConfiguration.java
// Add KeywordMatcher as a @Bean parameter:
@Bean
HeuristicIntentClassifier heuristicIntentClassifier(KeywordMatcher keywordMatcher) {
    return new HeuristicIntentClassifier(keywordMatcher);
}
```

**Important:** The keyword lists inside `HeuristicIntentClassifier` stay in Java for now —
they are part of the query-understanding layer and will be migrated to YAML in a separate task
(see `13-query-and-retrieval-layer-gaps.md` Section 12). Only the `containsAny()` method
is removed here and replaced by the shared `KeywordMatcher`.

---

### B-7 — Verify

```
grep -r "private.*boolean containsAny" apps/backend/src/main/java → 0 results
grep -r "\"გამარჯობა\"\|\"ნახვამდის\"\|\"ვერ დავადგინე\"" apps/backend/src/main/java → 0 results
```

---

## R7-C — `TopicDetector`: 3 violations (MEDIUM)

**File:** `apps/backend/src/main/java/com/geostat/chat/application/chat/TopicDetector.java`

### C-1 — Replace static finals with `@Value`

`application-custom.yml` already has these keys (added 2026-05-26):
```yaml
geostat:
  chat:
    topic-detector:
      max-topics:        3
      max-context-turns: 2
```

**Change the constructor:**

```java
// Remove:
private static final int MAX_TOPICS = 3;
private static final int MAX_CONTEXT_USER_TURNS = 2;

// Add instance fields:
private final int maxTopics;
private final int maxContextTurns;

// Update constructor signature (add two @Value params at the end):
public TopicDetector(
        ChatClient chatClient,
        TopicCatalog topicCatalog,
        PromptCatalog promptCatalog,
        AiChatOptionsFactory chatOptionsFactory,
        @Value("${geostat.chat.topic-detector.max-topics:3}") int maxTopics,
        @Value("${geostat.chat.topic-detector.max-context-turns:2}") int maxContextTurns) {
    this.chatClient         = chatClient;
    this.topicCatalog       = topicCatalog;
    this.promptCatalog      = promptCatalog;
    this.chatOptionsFactory = chatOptionsFactory;
    this.maxTopics          = maxTopics;
    this.maxContextTurns    = maxContextTurns;
    this.sortedRules        = buildSortedRules();
}
```

Replace `MAX_TOPICS` with `maxTopics` in `detectByRules()` line 96.
Replace `MAX_CONTEXT_USER_TURNS` with `maxContextTurns` in `recentUserContext()` line 131.

### C-2 — Replace brittle `Topic.valueOf()` with safe lookup

**Current `classifyWithAi()` lines 112–113:**
```java
String cleaned = raw.strip().toUpperCase().replaceAll("[^A-Z_]", "");
return Topic.valueOf(cleaned);  // ← throws IllegalArgumentException on unknown value
```

**Replace with:**
```java
String cleaned = raw.strip().toUpperCase().replaceAll("[^A-Z_]", "");
return Arrays.stream(Topic.values())
        .filter(t -> t.name().equals(cleaned))
        .findFirst()
        .orElse(Topic.GENERAL);
```

The outer `try/catch` is still needed for the `chatClient.prompt()` call — keep it.
Only the `IllegalArgumentException` from `valueOf` no longer needs catching.

### C-3 — Replace `recentUserContext()` deque traversal

**Current (line 119–134):** builds a full list of all messages then slices the tail.

**Replace the full method:**

```java
private List<Message> recentUserContext(Deque<Message> history) {
    if (history == null || history.isEmpty()) return List.of();
    int limit = maxContextTurns * 2;
    ArrayDeque<Message> collected = new ArrayDeque<>(limit);
    Iterator<Message> it = history.descendingIterator();
    while (it.hasNext() && collected.size() < limit) {
        collected.addFirst(it.next());
    }
    return new ArrayList<>(collected);
}
```

**Add imports:** `java.util.ArrayDeque`, `java.util.Iterator` (both likely already present via `java.util.*`).

---

## R7-D — `ChatPipelineContext` → sealed `PipelineResult` (MEDIUM)

### D-1 — Create `PipelineResult`

**New file:** `apps/backend/src/main/java/com/geostat/chat/domain/chat/PipelineResult.java`

```java
package com.geostat.chat.domain.chat;

import com.geostat.chat.application.chat.AiChatResult;
import com.geostat.chat.domain.catalog.CatalogTopicLabelResolver;
import com.geostat.chat.domain.catalog.LinkCard;
import com.geostat.chat.domain.catalog.Topic;
import com.geostat.platform.contracts.retrieval.RetrievedChunk;
import java.util.List;

/**
 * Result of the shared pipeline step in ChatService.
 * Compiler-enforced exhaustiveness via sealed type.
 */
public sealed interface PipelineResult
        permits PipelineResult.Ready, PipelineResult.NeedsClarification {

    record Ready(
            List<Topic> topics,
            CatalogTopicLabelResolver.Labels topicLabels,
            List<LinkCard> links,
            List<RetrievedChunk> ragChunks) implements PipelineResult {}

    record NeedsClarification(
            List<Topic> topics,
            CatalogTopicLabelResolver.Labels topicLabels,
            List<RetrievedChunk> ragChunks,
            AiChatResult result) implements PipelineResult {}
}
```

### D-2 — Delete `ChatPipelineContext.java`

```
Delete: apps/backend/src/main/java/com/geostat/chat/application/chat/ChatPipelineContext.java
```

### D-3 — Update `ChatService.buildPipeline()`

**Current return type:** `ChatPipelineContext`
**New return type:** `PipelineResult`

```java
// Change method signature:
private PipelineResult buildPipeline(ChatContext ctx) {
    List<Topic> topics = topicDetector.detect(ctx.lowerQuery(), ctx.message(), ctx.history());
    CatalogResponseAssembler.Bundle catalog =
            catalogResponseAssembler.assemble(topics, ctx.retrievalQuery(), ctx.locale(), ctx.isGeorgian());
    CatalogTopicLabelResolver.Labels topicLabels = catalog.topicLabels();
    List<RetrievedChunk> ragChunks = retrievalContextService.retrieve(ctx.retrievalQuery(), ctx.locale());
    List<LinkCard> links = mergedLinks(catalog.links(), ctx, ragChunks);

    if (links.isEmpty()) {
        List<RetrievedChunk> corpus = ragChunks.isEmpty()
                ? retrievalContextService.retrieveForClarification(ctx.retrievalQuery(), ctx.locale())
                : ragChunks;
        return new PipelineResult.NeedsClarification(
                topics, topicLabels, corpus, clarificationService.generate(ctx, corpus));
    }
    return new PipelineResult.Ready(topics, topicLabels, links, ragChunks);
}
```

### D-4 — Update the two callers in `getChatResponse()` and `streamChatResponse()`

**Replace the `pipeline.needsClarification()` block with exhaustive switch:**

```java
// In getChatResponse():
PipelineResult pipe = buildPipeline(ctx);
return switch (pipe) {
    case PipelineResult.Ready r -> {
        AiChatResult result = generateAiResponse(ctx, r.topicLabels(), r.links(), r.ragChunks());
        yield respond(ctx, result.intro(), result.items(),
                r.topics(), r.topicLabels(), r.ragChunks(), ChatResponseKind.answer);
    }
    case PipelineResult.NeedsClarification nc ->
        respond(ctx, nc.result().intro(), nc.result().items(),
                nc.topics(), nc.topicLabels(), nc.ragChunks(), ChatResponseKind.clarification);
};

// In streamChatResponse() — same switch, but the Ready branch calls streaming AI:
case PipelineResult.Ready r -> {
    String systemPrompt = promptBuilder.build(r.topicLabels(), r.links(), ctx.isGeorgian(), r.ragChunks());
    Prompt prompt = buildGeminiPrompt(systemPrompt, ctx);
    // ... rest of streaming logic using r.links(), r.ragChunks(), r.topics(), r.topicLabels()
}
```

**Add import:** `com.geostat.chat.domain.chat.PipelineResult`

---

## R7-E — `PromptCatalog.uiString()`: String keys → `UiStringKey` enum (MINOR)

### E-1 — Create `UiStringKey` enum

**New file:** `apps/backend/src/main/java/com/geostat/chat/domain/prompt/UiStringKey.java`

```java
package com.geostat.chat.domain.prompt;

/**
 * Type-safe keys for {@link PromptCatalog#uiString(UiStringKey, boolean)}.
 * Each constant maps to a camelCase key in the {@code uiStrings} section of
 * {@code prompts/chat-prompts.yaml}.
 */
public enum UiStringKey {
    ERROR_INTRO,
    ERROR_SITE_MAP_TITLE,
    PORTAL_LIST_INTRO,
    SEE_RESOURCES,
    SEE_INFORMATION,
    LINKS_BELOW,
    SANITIZER_FALLBACK;

    /** Maps enum name to camelCase YAML key. ERROR_INTRO → "errorIntro". */
    public String yamlKey() {
        String[] parts = name().split("_");
        StringBuilder sb = new StringBuilder(parts[0].toLowerCase());
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            sb.append(Character.toUpperCase(part.charAt(0)));
            sb.append(part.substring(1).toLowerCase());
        }
        return sb.toString();
    }
}
```

### E-2 — Update `PromptCatalog` interface

**File:** `apps/backend/src/main/java/com/geostat/chat/domain/prompt/PromptCatalog.java`

```java
// Change signature from:
String uiString(String key, boolean isGeorgian);
// To:
String uiString(UiStringKey key, boolean isGeorgian);

// Remove ALL String UI_* constants (UI_ERROR_INTRO, UI_ERROR_SITE_MAP_TITLE, etc.)
```

### E-3 — Update `PromptCatalogLoader`

**File:** `apps/backend/src/main/java/com/geostat/chat/infrastructure/prompt/PromptCatalogLoader.java`

```java
// Change method signature:
String uiString(UiStringKey key, boolean isGeorgian) {
    if (root.uiStrings() == null) return key.name();
    LocalizedString entry = root.uiStrings().get(key.yamlKey());
    if (entry == null) return key.name();
    String value = isGeorgian ? entry.ka() : entry.en();
    return value != null ? value : key.name();
}
```

### E-4 — Update `YamlPromptCatalog`

**File:** `apps/backend/src/main/java/com/geostat/chat/infrastructure/prompt/YamlPromptCatalog.java`

```java
@Override
public String uiString(UiStringKey key, boolean isGeorgian) {
    return loader.uiString(key, isGeorgian);
}
```

### E-5 — Update all 5 call sites

Search: `promptCatalog.uiString(PromptCatalog.UI_`

Files to update (replace `PromptCatalog.UI_XXX` with `UiStringKey.XXX`):

| File | Old call | New call |
|---|---|---|
| `ChatResultFactory.java` | `PromptCatalog.UI_ERROR_INTRO` | `UiStringKey.ERROR_INTRO` |
| `ChatResultFactory.java` | `PromptCatalog.UI_ERROR_SITE_MAP_TITLE` | `UiStringKey.ERROR_SITE_MAP_TITLE` |
| `ChatService.java` | `PromptCatalog.UI_PORTAL_LIST_INTRO` | `UiStringKey.PORTAL_LIST_INTRO` |
| `AiResponseParser.java` | `PromptCatalog.UI_SEE_RESOURCES` | `UiStringKey.SEE_RESOURCES` |
| `AiResponseParser.java` | `PromptCatalog.UI_SEE_INFORMATION` | `UiStringKey.SEE_INFORMATION` |
| `AiResponseParser.java` | `PromptCatalog.UI_LINKS_BELOW` | `UiStringKey.LINKS_BELOW` |
| `ResponseSanitizer.java` | `PromptCatalog.UI_SANITIZER_FALLBACK` | `UiStringKey.SANITIZER_FALLBACK` |

**Add import** `com.geostat.chat.domain.prompt.UiStringKey` to each file.

**Verify:** `grep -r "UI_ERROR\|UI_PORTAL\|UI_SEE\|UI_LINKS\|UI_SANITIZER" apps/backend/src/main/java` → 0 results

---

## R7-F — `ChatService`: decompose constructor (MEDIUM — do last)

**File:** `apps/backend/src/main/java/com/geostat/chat/application/chat/ChatService.java`

Current: 27 constructor parameters, `@SuppressWarnings("java:S107")` suppressing the smell.
This is an SRP violation — `ChatService` owns retrieval, composition, encoding, history,
telemetry, query-understanding, language detection, and streaming concerns simultaneously.

### F-1 — Extract `ChatResponseComposer`

**New file:** `apps/backend/src/main/java/com/geostat/chat/application/chat/ChatResponseComposer.java`

Move these methods from `ChatService` into this component:
- `respond()` (private → package or public)
- `addToHistory()` (private → package)
- `generateAiResponse()` (private → package)
- `buildGeminiPrompt()` (private → package)

`ChatResponseComposer` takes as constructor dependencies:
```
AiResponseParser, ChatResultFactory, ResponseGroundingEnforcer, ResponseSanitizer,
PromptBuilder, ChatTelemetryService, SessionTurnRecorder, PromptCatalog,
ConversationHistory, AiChatOptionsFactory, AiChatProperties, ChatClient
```

### F-2 — Extract `ChatPipelineCoordinator`

**New file:** `apps/backend/src/main/java/com/geostat/chat/application/chat/ChatPipelineCoordinator.java`

Move these methods from `ChatService` into this component:
- `buildPipeline()` (private → package)
- `checkEarlyExit()` (private → package)
- `mergedLinks()` (private → package)
- `respondWithPortals()` (private → package)

`ChatPipelineCoordinator` takes as constructor dependencies:
```
TopicDetector, CatalogResponseAssembler, RetrievalContextService,
CatalogRagLinkMerger, ClarificationService, SmallTalkHandler,
Map<QueryIntent, Integer> maxRagByIntent (from @Value)
```

`respondWithPortals()` needs `ChatResponseComposer.respond()` — inject `ChatResponseComposer`
into `ChatPipelineCoordinator` as well, or extract `respondWithPortals()` back to `ChatService`.

> **Recommendation:** keep `respondWithPortals()` in `ChatService` since it uses both
> coordinators. Move only `buildPipeline()`, `checkEarlyExit()`, and `mergedLinks()`.

### F-3 — `ChatService` after decomposition

```java
@Service
public class ChatService {

    private final ChatPipelineCoordinator pipeline;
    private final ChatResponseComposer composer;
    private final ChatContextBuilder contextBuilder;   // wraps buildContext() logic
    private final ChatCompleteEncoder encoder;

    // getChatResponse() — 15 lines
    // streamChatResponse() — 25 lines
    // completeEvent() — 5 lines
}
```

> **Junior rule:** Do R7-F in ONE commit. Do not move methods one at a time across commits —
> the service will not compile in between. Extract both components in a single changeset,
> verify all tests pass, then commit.

---

## R7-H — `ClarificationService` → `SmallTalkHandler` coupling (MEDIUM)

**Problem:** `ClarificationService` (line 76) injects `SmallTalkHandler` solely to call
`smallTalkHandler.clarificationRequest(ctx.isGeorgian())` as a fallback string.
These are two completely different concerns:

- `ClarificationService` — generates AI clarification questions
- `SmallTalkHandler` — detects and responds to conversational small-talk

This is an **SRP + Clean Architecture violation**: `ClarificationService` must not depend on
`SmallTalkHandler` for a UI fallback string. The string belongs in `PromptCatalog`.

### H-1 — Add `CLARIFICATION_FALLBACK` to `UiStringKey`

**File:** `apps/backend/src/main/java/com/geostat/chat/domain/prompt/UiStringKey.java`

```java
public enum UiStringKey {
    ERROR_INTRO,
    ERROR_SITE_MAP_TITLE,
    PORTAL_LIST_INTRO,
    SEE_RESOURCES,
    SEE_INFORMATION,
    LINKS_BELOW,
    SANITIZER_FALLBACK,
    CLARIFICATION_FALLBACK;  // ← add this
    // ... yamlKey() unchanged ...
}
```

### H-2 — Add the string to `chat-prompts.yaml`

**File:** `apps/backend/src/main/resources/prompts/chat-prompts.yaml` — `uiStrings` section

```yaml
uiStrings:
  # ... existing keys ...
  clarificationFallback:
    ka: "შეგიძლიათ დააზუსტოთ კითხვა?"
    en: "Could you please clarify your question?"
```

### H-3 — Fix `ClarificationService`

**File:** `apps/backend/src/main/java/com/geostat/chat/application/chat/ClarificationService.java`

```java
// Before:
// private final SmallTalkHandler smallTalkHandler;
// constructor: ..., SmallTalkHandler smallTalkHandler, ...
// line 76: return AiChatResult.emptyIntro(smallTalkHandler.clarificationRequest(ctx.isGeorgian()));

// After:
// No SmallTalkHandler field or constructor parameter.
return AiChatResult.emptyIntro(promptCatalog.uiString(UiStringKey.CLARIFICATION_FALLBACK, ctx.isGeorgian()));
```

Remove `SmallTalkHandler` from `ClarificationService`'s constructor entirely.
`ClarificationService` already has `PromptCatalog` injected — no new dependency needed.

> **Verify:** `grep -n "SmallTalkHandler" ClarificationService.java` → 0 matches after fix.

---

## R7-I — Small-talk must NOT enter AI conversation history (MEDIUM)

**Problem:** `checkEarlyExit()` calls `respond()` which calls:

1. `addToHistory(ctx, result)` — adds the greeting exchange to the AI `Deque<Message>` history
2. `chatTelemetryService.recordTurn(...)` — optionally also records it

**Why this is wrong:** small-talk greetings ("გამარჯობა" / "hello") pollute the AI's conversation
context. In the next real question, the AI sees "User: hello / Assistant: hello …" as prior turns.
This dilutes the statistical topic signal and wastes the history budget.

### I-1 — Add `respondSmallTalk()` to `ChatService`

```java
/** Small-talk bypass: returns the result but does NOT add to AI message history. */
private ChatResult respondSmallTalk(ChatContext ctx, String intro,
                                    CatalogTopicLabelResolver.Labels labels) {
    ChatResult result = chatResultFactory.build(
            AiChatResult.emptyIntro(intro), List.of(), List.of(Topic.GENERAL),
            labels, List.of(), ctx, ChatResponseKind.smalltalk);
    // telemetry is still valid (for analytics) — keep recordTurn if desired
    // history is intentionally SKIPPED — small-talk must not pollute AI context
    return result;
}
```

### I-2 — Update `checkEarlyExit()` to call `respondSmallTalk()`

```java
private ChatResult checkEarlyExit(ChatContext ctx) {
    String smallTalk = smallTalkHandler.handle(ctx.userText(), ctx.isGeorgian());
    if (smallTalk != null) {
        CatalogTopicLabelResolver.Labels labels = generalLabels(ctx.isGeorgian());
        return respondSmallTalk(ctx, smallTalk, labels);
    }
    // ... portal list check ...
}
```

### I-3 — Add `generalLabels()` helper (avoids wasteful catalog assembly — see R7-J)

```java
private CatalogTopicLabelResolver.Labels generalLabels(boolean isGeorgian) {
    String label = isGeorgian ? "ზოგადი" : "General";
    return new CatalogTopicLabelResolver.Labels(label, List.of());
}
```

> **Acceptance:** after a "hello" → real question sequence, the AI system prompt and
> conversation history contain **no** greeting pair; only the real question is in context.

---

## R7-J — `resolveTopicLabels()` wasteful for GENERAL-only topics (MINOR)

**Problem:** `checkEarlyExit()` and portal-list path call:

```java
resolveTopicLabels(List.of(Topic.GENERAL), ctx)
```

`resolveTopicLabels()` delegates to `catalogResponseAssembler.assemble(...)` — a full catalog
assembly that may touch the database and do link resolution, only to then call `.topicLabels()`.
For `Topic.GENERAL` alone, the result is always a simple label string — no DB call needed.

**Fix:** See `generalLabels()` from R7-I above. Replace all `resolveTopicLabels(List.of(Topic.GENERAL), ctx)` call sites with `generalLabels(ctx.isGeorgian())`.

Call sites:
1. `checkEarlyExit()` — small-talk path
2. `respondWithPortals()` — portal-list path

```java
// Before:
resolveTopicLabels(List.of(Topic.GENERAL), ctx)

// After:
generalLabels(ctx.isGeorgian())
```

> **Verify:** `grep -n "resolveTopicLabels.*GENERAL" ChatService.java` → 0 matches after fix.

---

## R7-G — O(N²) grounding → Rabin-Karp (FUTURE)

**When:** only when `geostat.retrieval.max-chunks` > 50 in any deployed profile.

**File:** `ExplanationGroundingVerifier.java` — `containsPassagePhrase()`

Current: nested loops (lengths × offsets) over 180-char window = ~12,960 comparisons per chunk.
At 20 chunks: ~260k ops/request. Acceptable today.

At scale: replace with Apache Commons Text `StringMatcherFactory` or implement Rabin-Karp
rolling hash (O(N+M) per chunk). Do not implement until max-chunks actually exceeds 50.

---

## Execution Order Summary

```
R7-A  (1 line, do now)   ClarificationService — remove redundant trim
R7-B  (HIGH — 3 new files + 3 refactors)
      B-1  small-talk.yaml
      B-2  KeywordMatcher  (application/util)
      B-3  SmallTalkCatalogLoader  (infrastructure/catalog)
      B-4  Rewrite SmallTalkHandler
      B-5  Migrate QueryRouter → KeywordMatcher
      B-6  Migrate HeuristicIntentClassifier → KeywordMatcher + update Configuration
      B-7  Verify grep
R7-C  (MEDIUM — TopicDetector)
      C-1  @Value fields
      C-2  safe Topic.valueOf()
      C-3  descendingIterator()
R7-D  (MEDIUM — sealed PipelineResult)
      D-1  Create PipelineResult.java  (domain/chat)
      D-2  Delete ChatPipelineContext.java
      D-3  Update buildPipeline() return type
      D-4  Update switch in getChatResponse() and streamChatResponse()
R7-E  (MINOR — enum UiStringKey)
      E-1  Create UiStringKey.java  (domain/prompt)
      E-2  Update PromptCatalog interface
      E-3  Update PromptCatalogLoader
      E-4  Update YamlPromptCatalog
      E-5  Update 7 call sites (table above)
R7-F  (MEDIUM — ChatService decompose, do last)
      F-1  Extract ChatResponseComposer
      F-2  Extract ChatPipelineCoordinator
      F-3  ChatService → thin orchestrator, remove @SuppressWarnings
R7-H  (MEDIUM — ClarificationService SRP fix)
      H-1  Add CLARIFICATION_FALLBACK to UiStringKey  (do together with R7-E)
      H-2  Add clarificationFallback to chat-prompts.yaml
      H-3  Remove SmallTalkHandler from ClarificationService, use PromptCatalog
R7-I  (MEDIUM — small-talk must not pollute AI history)
      I-1  Add respondSmallTalk() to ChatService
      I-2  Update checkEarlyExit() to call respondSmallTalk()
      I-3  Add generalLabels() helper
R7-J  (MINOR — remove wasteful resolveTopicLabels for GENERAL)
      J-1  Replace resolveTopicLabels(GENERAL) with generalLabels() in 2 call sites
R7-G  FUTURE — only when max-chunks > 50
```

> **Order note:** Do R7-H at the same time as R7-E (both touch `UiStringKey`). Do R7-I and R7-J
> together in `ChatService` since `generalLabels()` is needed by both.

---

## Acceptance Criteria

- [ ] `SmallTalkHandler` — zero hardcoded string literals, no `containsAny()` method
- [ ] `KeywordMatcher` — only place `containsAny()` exists in the codebase
- [ ] `catalog/small-talk.yaml` — loads at startup, all groups have ka+en responses
- [ ] `HeuristicIntentClassifier` — uses `KeywordMatcher`; no private `containsAny()`
- [ ] `QueryRouter` — uses `KeywordMatcher`; no private `containsAny()`
- [ ] `TopicDetector` — no `static final int`; `@Value` fields; safe stream `Topic` lookup
- [ ] `ClarificationService` line 63 — `ctx.history()` directly (no `HistoryBudgetTrimmer`)
- [ ] `ClarificationService` — zero `SmallTalkHandler` references; uses `PromptCatalog.uiString()`
- [ ] `PipelineResult` sealed interface exists in `domain/chat`; `ChatPipelineContext` deleted
- [ ] `ChatService` uses exhaustive `switch (pipe)` in both sync and stream paths
- [ ] `UiStringKey` enum exists in `domain/prompt`; no `String UI_*` constants on `PromptCatalog`
- [ ] `UiStringKey` includes `CLARIFICATION_FALLBACK`; `chat-prompts.yaml` has the key
- [ ] `PromptCatalog.uiString()` accepts `UiStringKey`; compiler catches any unknown key
- [ ] `ChatService.checkEarlyExit()` calls `respondSmallTalk()` — small-talk does NOT call `addToHistory()`
- [ ] `ChatService` has no `resolveTopicLabels(List.of(Topic.GENERAL), ...)` calls; uses `generalLabels()`
- [ ] `ChatService` `@SuppressWarnings("java:S107")` removed; constructor params ≤ 8
- [ ] `grep -rn "private.*containsAny\|\"გამარჯობა\"\|\"ნახვამდის\"\|UI_ERROR\|UI_PORTAL" apps/backend/src/main/java` → 0 matches
- [ ] All existing unit tests pass after each phase
