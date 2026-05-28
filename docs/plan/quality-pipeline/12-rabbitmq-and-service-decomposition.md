# RabbitMQ Hardening & Service Decomposition

> **Session 2026-05-27:** Phases R-A, R-B, R-C implemented.
> CurationOverrideService is a stub — full implementation pending (curation_override table not yet created).
> FeedbackScoreAggregator deprecated. Service decomposition section is documentation only — no split triggered.

> **Senior directive — read every line before touching any file.**
> This document covers:
> 1. RabbitMQ — what exists, what is broken, exact fixes with code
> 2. Service decomposition — current state, when to split further, what NOT to do now
>
> Fix in order. Do not skip BUG fixes and jump to features.

---

## Table of Contents

1. [Current RabbitMQ Architecture](#1-current-rabbitmq-architecture)
2. [What Is Already Working](#2-what-is-already-working)
3. [Bugs and Gaps — Must Fix](#3-bugs-and-gaps--must-fix)
4. [Phase R-A — Fix @ConditionalOnExpression Bug](#4-phase-r-a--fix-conditionalonexpression-bug)
5. [Phase R-B — Add DLQ + Manual Ack + Prefetch + Retry](#5-phase-r-b--add-dlq--manual-ack--prefetch--retry)
6. [Phase R-C — FeedbackScoreEvent via RabbitMQ (Phase D prerequisite)](#6-phase-r-c--feedbackscoreevent-via-rabbitmq-phase-d-prerequisite)
7. [Service Decomposition — Current State and Roadmap](#7-service-decomposition--current-state-and-roadmap)
8. [Execution Order](#8-execution-order)
9. [Acceptance Criteria](#9-acceptance-criteria)

---

## 1. Current RabbitMQ Architecture

### Topology (as-built)

```
CrawlRunStore.storePage(documentId, corpusId)
        ↓
DocumentPostPersistPipeline.afterDocumentPersisted(documentId, corpusId)
        ↓                                    ↓
DocumentIndexTrigger                 DocumentEnrichmentTrigger
(RabbitDocumentIndexPublisher)       (RabbitDocumentEnrichmentPublisher)
        ↓                                    ↓
   convertAndSend(exchange,            convertAndSend(exchange,
     routingKey="document.index",        enrichmentRoutingKey="document.parsed",
     DocumentIndexEvent)                 DocumentParsedEvent)
        ↓                                    ↓
TopicExchange "geostat.ingestion"   TopicExchange "geostat.ingestion"
        ↓                                    ↓
Queue "geostat.ingestion.document-index"    Queue "geostat.ingestion.document-parsed"
        ↓                                    ↓
DocumentIndexEventListener           DocumentEnrichmentListener
  → ChunkVectorIndexer                 → DocumentEnrichmentOrchestrator
      .indexDocument(id, corpusId)         .enrichDocument(id)
```

### Enabled/disabled by:

```yaml
# application-custom.yml — default is DISABLED:
geostat:
  ingestion:
    events:
      enabled: ${INGESTION_EVENTS_ENABLED:false}    # ← set to true to activate
    enrichment:
      enabled: ${INGESTION_ENRICHMENT_ENABLED:false} # ← also needed for listener
```

Both must be `true` for the async pipeline to work.

### Key files:

```
apps/ingestion-service/src/main/java/com/geostat/ingestion/events/
├── rabbit/
│   ├── RabbitMqConfiguration.java            ← exchange, queues, bindings, factory
│   ├── RabbitDocumentIndexPublisher.java      ← publishes DocumentIndexEvent
│   ├── RabbitDocumentEnrichmentPublisher.java ← publishes DocumentParsedEvent
│   ├── DocumentIndexEventListener.java        ← receives index events
│   └── DocumentEnrichmentListener.java        ← receives enrichment events
├── DocumentPostPersistPipeline.java           ← calls both triggers after save
├── DocumentIndexTrigger.java                  ← port interface
└── DocumentEnrichmentTrigger.java             ← port interface
```

---

## 2. What Is Already Working

The architecture is correct and mature. Port interfaces (`DocumentIndexTrigger`,
`DocumentEnrichmentTrigger`) exist — callers don't depend on RabbitMQ directly. If
`events.enabled=false`, a no-op or synchronous implementation is used instead. This is a
proper Strategy/Port pattern. Do not change the port interfaces.

The trigger flow after crawl is correct:
```
persist document → trigger index + enrichment → async via RabbitMQ
```

This means crawl is non-blocking: crawl-thread is not waiting for Gemini enrichment.

---

## 3. Bugs and Gaps — Must Fix

### BUG-RABBIT-01 — Two `@ConditionalOnProperty` on one class (CRITICAL)

**File:** `DocumentEnrichmentListener.java` lines 11–13

```java
// CURRENT (BROKEN):
@ConditionalOnProperty(prefix = "geostat.ingestion.enrichment", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "geostat.ingestion.events",     name = "enabled", havingValue = "true")
public class DocumentEnrichmentListener { ... }
```

**Why broken:** Java processes annotations in declaration order. Spring sees two
`@ConditionalOnProperty` annotations of the same type. Due to how Spring
`@AnnotationUtils.getRepeatableAnnotations` works with `@Conditional`, **only the last one is
effectively registered** as the condition. This means if `events.enabled=true` but
`enrichment.enabled=false`, the listener still starts — and calls
`enrichmentOrchestrator.enrichDocument()` which is inactive/null → `NullPointerException` or
silent no-op depending on how the orchestrator is conditionally wired.

**Fix:** Replace both annotations with a single `@ConditionalOnExpression`.
See Phase R-A below.

---

### BUG-RABBIT-02 — No Dead Letter Queue (CRITICAL)

**File:** `RabbitMqConfiguration.java` lines 68–79

```java
// CURRENT — no DLQ:
Queue indexQueue     = new Queue(events.indexQueue(), true);       // no DLX args
Queue enrichmentQueue = new Queue(events.enrichmentQueue(), true); // no DLX args
```

**Consequence:** If `ChunkVectorIndexer.indexDocument()` or
`DocumentEnrichmentOrchestrator.enrichDocument()` throw an exception:
- With auto-ack (current): message is consumed and acked before processing →
  **message is lost permanently**
- With manual-ack (after R-B fix): message is nacked → requeued forever → **poison message
  loops indefinitely**, blocking the queue

Without a DLQ, failed messages have nowhere safe to go.

**Fix:** Add DLX (dead letter exchange) and per-queue DLQ. See Phase R-B below.

---

### BUG-RABBIT-03 — Auto-ack mode (CRITICAL)

**File:** `RabbitMqConfiguration.java` lines 59–66

```java
// CURRENT — no acknowledgeMode set → defaults to AUTO:
SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
factory.setConnectionFactory(connectionFactory);
factory.setMessageConverter(converter);
// ← acknowledgeMode not set → AcknowledgeMode.AUTO
```

**Consequence:** Message is acked immediately when the listener method is invoked, before
any processing happens. If Gemini call throws a timeout: message already acked → enrichment
never retried → document stays unenriched forever.

**Fix:** `factory.setAcknowledgeMode(AcknowledgeMode.MANUAL)` + retry interceptor.
See Phase R-B below.

---

### BUG-RABBIT-04 — No prefetch count (PERFORMANCE BUG)

**File:** `RabbitMqConfiguration.java`

```java
// CURRENT — no setPrefetchCount() → unlimited
```

**Consequence:** If 4,000 unprocessed documents are in the enrichment queue, RabbitMQ sends
ALL 4,000 messages to the single consumer at once. The consumer holds all in memory, tries to
call Gemini 4,000 times concurrently → rate limit errors, memory spike, consumer crash.

**Fix:** `factory.setPrefetchCount(5)` — process at most 5 documents concurrently per
consumer instance. See Phase R-B below.

---

### BUG-RABBIT-05 — No retry with backoff

**File:** `RabbitMqConfiguration.java`

**Consequence:** Gemini API returns 429 (rate limit) → exception → with manual ack: message
nacked → immediately requeued → immediately retried → 429 again → loop at full speed →
burns Gemini quota.

**Fix:** `RetryInterceptorBuilder` with exponential backoff + max attempts before DLQ.
See Phase R-B below.

---

### GAP-RABBIT-01 — FeedbackScoreAggregator is poll-based, not event-driven

**File:** `FeedbackScoreAggregator.java`

```java
@Scheduled(cron = "0 0 3 * * *")  // runs at 3 AM nightly, polling chat schema
```

This is a nightly batch that reads `chat.*` tables directly. The correct architecture (Phase D
from `11-coupling-architecture-plan.md`) is: chat-service emits a `FeedbackScoreEvent`
whenever feedback is recorded → RabbitMQ → ingestion-service listener updates
`curation_override`.

**Prerequisite:** Phase D from file 11. Implement that first, then wire via RabbitMQ in
Phase R-C of this file.

---

## 4. Phase R-A — Fix @ConditionalOnExpression Bug

### Step R-A-1: Fix DocumentEnrichmentListener

File: `apps/ingestion-service/src/main/java/com/geostat/ingestion/events/rabbit/DocumentEnrichmentListener.java`

```java
// Add import:
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;

// BEFORE (remove both @ConditionalOnProperty annotations):
@ConditionalOnProperty(prefix = "geostat.ingestion.enrichment", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "geostat.ingestion.events",     name = "enabled", havingValue = "true")
public class DocumentEnrichmentListener {

// AFTER (replace with single @ConditionalOnExpression):
@ConditionalOnExpression(
    "${geostat.ingestion.enrichment.enabled:false} && ${geostat.ingestion.events.enabled:false}")
public class DocumentEnrichmentListener {
```

Keep everything else unchanged. Only change the class-level annotations.

### Step R-A-2: Verify RabbitDocumentEnrichmentPublisher (same bug potential)

File: `RabbitDocumentEnrichmentPublisher.java`

```java
// Check if this class also has two @ConditionalOnProperty:
@ConditionalOnProperty(prefix = "geostat.ingestion.enrichment", name = "enabled", ...)
@ConditionalOnProperty(prefix = "geostat.ingestion.events",     name = "enabled", ...)
```

If yes — apply the same `@ConditionalOnExpression` fix as above.

### Phase R-A acceptance criteria:

```powershell
# Verify no class has two @ConditionalOnProperty for these two prefixes:
rg "@ConditionalOnProperty" apps/ingestion-service/src/main/java/com/geostat/ingestion/events/ --type java -A 1
# Expected: no file shows the same annotation twice in a row
```

---

## 5. Phase R-B — Add DLQ + Manual Ack + Prefetch + Retry

### Step R-B-1: Add DLX exchange and per-queue DLQs to RabbitMqConfiguration

File: `apps/ingestion-service/src/main/java/com/geostat/ingestion/events/rabbit/RabbitMqConfiguration.java`

**Replace the entire `documentIndexTopology` bean:**

```java
// Add imports:
import java.util.Map;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;

// Replace documentIndexTopology bean:
@Bean
Declarables documentIndexTopology(IngestionProperties properties) {
    IngestionProperties.Events events = properties.events();

    // ── Dead-letter exchange (one shared DLX for all ingestion queues) ────────
    DirectExchange dlx = new DirectExchange("geostat.ingestion.dlx", true, false);

    // ── Index queue + DLQ ─────────────────────────────────────────────────────
    Map<String, Object> indexArgs = Map.of(
        "x-dead-letter-exchange",    "geostat.ingestion.dlx",
        "x-dead-letter-routing-key", "dead.index",
        "x-message-ttl",             86_400_000   // 24h — drop stale messages
    );
    TopicExchange exchange    = new TopicExchange(events.exchange(), true, false);
    Queue indexQueue          = new Queue(events.indexQueue(), true, false, false, indexArgs);
    Queue indexDlq            = new Queue(events.indexQueue() + ".dlq", true);
    Binding indexBinding      = BindingBuilder.bind(indexQueue).to(exchange).with(events.routingKey());
    Binding indexDlqBinding   = BindingBuilder.bind(indexDlq).to(dlx).with("dead.index");

    // ── Enrichment queue + DLQ ────────────────────────────────────────────────
    Map<String, Object> enrichArgs = Map.of(
        "x-dead-letter-exchange",    "geostat.ingestion.dlx",
        "x-dead-letter-routing-key", "dead.enrichment",
        "x-message-ttl",             86_400_000
    );
    Queue enrichmentQueue       = new Queue(events.enrichmentQueue(), true, false, false, enrichArgs);
    Queue enrichmentDlq         = new Queue(events.enrichmentQueue() + ".dlq", true);
    Binding enrichmentBinding   = BindingBuilder.bind(enrichmentQueue).to(exchange).with(events.enrichmentRoutingKey());
    Binding enrichmentDlqBinding= BindingBuilder.bind(enrichmentDlq).to(dlx).with("dead.enrichment");

    return new Declarables(
        exchange, dlx,
        indexQueue,      indexDlq,      indexBinding,      indexDlqBinding,
        enrichmentQueue, enrichmentDlq, enrichmentBinding, enrichmentDlqBinding
    );
}
```

---

### Step R-B-2: Configure factory with manual ack, prefetch, retry

**Replace the `rabbitListenerContainerFactory` bean in the same file:**

```java
@Bean
SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
        ConnectionFactory connectionFactory, JacksonJsonMessageConverter converter) {
    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
    factory.setConnectionFactory(connectionFactory);
    factory.setMessageConverter(converter);

    // Manual ack: message not acked until listener method returns successfully
    factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);

    // Max 5 unacked messages per consumer — prevents memory spike during backfill
    factory.setPrefetchCount(5);

    // Retry: 3 attempts, exponential backoff 1s → 2s → 4s, then → DLQ
    factory.setAdviceChain(
        RetryInterceptorBuilder.stateless()
            .maxAttempts(3)
            .backOffOptions(1_000L, 2.0, 8_000L)   // initial=1s, multiplier=2, max=8s
            .recoverer(new RejectAndDontRequeueRecoverer())  // after 3 fails → DLQ
            .build()
    );

    return factory;
}
```

---

### Step R-B-3: Update listeners to use Channel ack

Because we switched to `AcknowledgeMode.MANUAL`, the listeners must explicitly ack/nack.
However, with the `RetryInterceptorBuilder` + `RejectAndDontRequeueRecoverer` combination,
Spring AMQP handles the ack/nack internally via the retry advice. **You do NOT need to
inject `Channel` and call `channel.basicAck()` manually.**

The retry interceptor wraps the listener method:
- success → ack automatically
- failure after maxAttempts → nack + don't requeue → message goes to DLQ

So **no changes to `DocumentIndexEventListener` or `DocumentEnrichmentListener` method
bodies are needed** for ack behavior. The factory configuration handles it.

---

### Step R-B-4: Verify queue creation on startup

After deploying with the new configuration, check RabbitMQ management UI or run:

```bash
# If RabbitMQ management plugin is enabled:
curl -u guest:guest http://localhost:15672/api/queues/%2F | jq '.[].name'

# Expected queues:
# geostat.ingestion.document-index
# geostat.ingestion.document-index.dlq
# geostat.ingestion.document-parsed
# geostat.ingestion.document-parsed.dlq
```

---

### Step R-B-5: DLQ monitoring (ops item)

Add to `application-custom.yml` under `geostat.ingestion.events`:

```yaml
geostat:
  ingestion:
    events:
      enabled: ${INGESTION_EVENTS_ENABLED:false}
      exchange: geostat.ingestion
      index-queue: geostat.ingestion.document-index
      routing-key: document.index
      enrichment-queue: geostat.ingestion.document-parsed
      enrichment-routing-key: document.parsed
      # DLQ names are auto-derived: queue-name + ".dlq"
      # Monitor DLQs — if count > 0, investigate and replay:
      # rabbitmqctl list_queues name messages | grep dlq
```

**When DLQ has messages:** investigate the cause (usually Gemini timeout or DB connection),
fix the root issue, then replay DLQ messages by re-publishing them to the main exchange with
the correct routing key.

---

### Phase R-B acceptance criteria:

- [ ] RabbitMQ management UI shows 4 queues: main × 2 + dlq × 2
- [x] `factory.setAcknowledgeMode(AcknowledgeMode.MANUAL)` confirmed in config
- [x] `factory.setPrefetchCount(5)` confirmed in config
- [ ] Simulate failure: throw exception in `DocumentIndexEventListener.onDocumentIndex()`,
      verify message ends up in `.dlq` after 3 retries (check queue message count in UI)
- [ ] No message loss during normal enrichment run

---

## 6. Phase R-C — FeedbackScoreEvent via RabbitMQ (Phase D prerequisite)

> **Only implement this after Phase D from `11-coupling-architecture-plan.md` is done.**
> Phase D removes the direct `chat → ingestion` SQL coupling. This phase routes it via Rabbit.

### Current state (Phase D-complete):

```java
// After Phase D: FeedbackScoreAggregator uses ChatFeedbackReader with TODO comment
// Still polling-based. This phase makes it event-driven.
```

### Target architecture:

```
chat-service:
  User gives feedback (POST /api/v1/feedback)
        ↓
  FeedbackService.saveFeedback(turnId, rating)
        ↓
  RabbitTemplate.convertAndSend("geostat.chat", "feedback.score", FeedbackScoreEvent)

ingestion-service:
  Queue: "geostat.ingestion.feedback-score"
        ↓
  FeedbackScoreListener.onFeedbackScore(FeedbackScoreEvent)
        ↓
  CurationOverrideService.applyScoreBoost(documentId, delta)
        ↓
  INSERT/UPDATE ingestion.curation_override
```

### Step R-C-1: Define FeedbackScoreEvent in libs/platform-contracts

File: `libs/platform-contracts/src/main/java/com/geostat/platform/contracts/feedback/FeedbackScoreEvent.java`

```java
package com.geostat.platform.contracts.feedback;

import java.util.UUID;

/**
 * Published by chat-service when user feedback affects a document's relevance score.
 * Consumed by ingestion-service to update curation_override.
 */
public record FeedbackScoreEvent(
    UUID documentId,
    UUID corpusId,
    double scoreDelta,    // positive = helpful, negative = not helpful
    String feedbackType   // "positive" | "negative"
) {}
```

---

### Step R-C-2: Add feedback topology to RabbitMqConfiguration

```java
// In documentIndexTopology bean, add:

// ── Feedback score queue ───────────────────────────────────────────────────
// Exchange is in chat domain; ingestion binds a queue to receive events.
// For monolith (same broker): reuse geostat.ingestion exchange.
// For separate services: configure separate exchange name via properties.
Map<String, Object> feedbackArgs = Map.of(
    "x-dead-letter-exchange",    "geostat.ingestion.dlx",
    "x-dead-letter-routing-key", "dead.feedback-score",
    "x-message-ttl",             86_400_000
);
Queue feedbackQueue       = new Queue("geostat.ingestion.feedback-score", true, false, false, feedbackArgs);
Queue feedbackDlq         = new Queue("geostat.ingestion.feedback-score.dlq", true);
Binding feedbackBinding   = BindingBuilder.bind(feedbackQueue).to(exchange).with("feedback.score");
Binding feedbackDlqBinding= BindingBuilder.bind(feedbackDlq).to(dlx).with("dead.feedback-score");

// Add to Declarables(...) return value
```

---

### Step R-C-3: Create FeedbackScoreListener in ingestion-service

File: `apps/ingestion-service/src/main/java/com/geostat/ingestion/events/rabbit/FeedbackScoreListener.java`

```java
package com.geostat.ingestion.events.rabbit;

import com.geostat.ingestion.curation.CurationOverrideService;
import com.geostat.platform.contracts.feedback.FeedbackScoreEvent;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("db")
@ConditionalOnExpression(
    "${geostat.ingestion.feedback.score-boost-enabled:false} && ${geostat.ingestion.events.enabled:false}")
public class FeedbackScoreListener {

    private final CurationOverrideService curationOverrideService;

    public FeedbackScoreListener(CurationOverrideService curationOverrideService) {
        this.curationOverrideService = curationOverrideService;
    }

    @RabbitListener(queues = "geostat.ingestion.feedback-score")
    public void onFeedbackScore(FeedbackScoreEvent event) {
        curationOverrideService.applyScoreBoost(
            event.documentId(), event.corpusId(), event.scoreDelta());
    }
}
```

---

### Step R-C-4: Remove FeedbackScoreAggregator scheduled poll

After `FeedbackScoreListener` is live and verified:
1. Add `@Deprecated` to `FeedbackScoreAggregator.aggregateScoreBoost()` with a comment:
   ```java
   /**
    * @deprecated Replaced by FeedbackScoreListener (event-driven).
    * Remove after verifying FeedbackScoreListener processes all feedback correctly.
    */
   @Deprecated
   @Scheduled(cron = "0 0 3 * * *")
   public void aggregateScoreBoost() { ... }
   ```
2. In the next sprint: delete `FeedbackScoreAggregator` and `ChatFeedbackReader` entirely.

---

## 7. Service Decomposition — Current State and Roadmap

### What exists now

```
apps/
├── ingestion-service/    ← crawl + parse + chunk + embed + enrich + topic + authority
│                           RabbitMQ async pipeline inside
├── retrieval-service/    ← ✅ already split — HybridSearch, RRF, cross-encoder, MMR
│                           Redis + Caffeine cache, named vectors (body/title/summary)
└── backend/              ← chat: query understanding + response building + Gemini
```

### retrieval-service — already excellent

`retrieval-service` has a mature retrieval pipeline:
- `body` + `title` + `summary` named vector search (3 recall channels)
- RRF (Reciprocal Rank Fusion) — merges ranked lists
- Cross-encoder reranking — semantic relevance scoring
- MMR (Maximal Marginal Relevance) diversification
- HyDE (Hypothetical Document Embeddings) strategy
- MultiQuery strategy — expand query into N variants
- Redis + Caffeine cache backends — hot result caching

**Do NOT restructure `retrieval-service`. It is correctly designed.**

---

### Should ingestion-service be split further now?

**Answer: NO — not now.**

Current data: ~4,215 documents, one corpus. Splitting adds:
- Network calls between crawl/parse/enrichment workers
- Distributed transaction complexity (what if parse succeeds but enrichment-service is down?)
- More infrastructure to maintain (separate deployments, health checks, config)

RabbitMQ **already provides async decoupling** inside the monolith. The bottleneck is not
architectural coupling — it is data quality and Gemini API rate limits.

---

### When to split ingestion-service (triggers)

```
Split trigger 1 — crawl volume:
  When: crawl rate consistently > 10,000 pages/day for 2+ corpora
  Split: crawl-service (crawler4j) ←→ parse-service (Jsoup) separate deployments
  Signal: parse queue depth > 500 for > 1 hour

Split trigger 2 — enrichment throughput:
  When: enrichment queue depth > 5,000 messages (3+ days of backlog)
  Split: enrichment-service as separate consumer group
  Benefit: scale to N consumer replicas independently of crawl
  Signal: enrichment-queue.messages in RabbitMQ > 5,000

Split trigger 3 — multiple corpora with SLA:
  When: 3+ active corpora, each with different refresh frequency
  Split: per-corpus CrawlWorker pools with independent scheduling
  Signal: one corpus's crawl blocks another corpus's freshness

Split trigger 4 — backend bottleneck:
  When: > 500 concurrent chat users, query understanding latency > 300ms
  Split: query-service (intent + entity + expand) from chat-service (Gemini response)
  Signal: p99 latency on /api/v1/chat > 2s consistently
```

**None of these triggers are met today. Do not split.**

---

### RabbitMQ growth path (what to use it for in future)

```
Today (after this plan):
  crawl → index          (via RabbitMQ ✅)
  crawl → enrichment     (via RabbitMQ ✅)
  feedback → curation    (via RabbitMQ — Phase R-C)

Near future:
  enrichment complete → topic remine trigger
    enrichment-service publishes: DocumentEnrichedEvent
    topic-scheduler consumes: trigger SmileKMeans for corpus if threshold met
    → removes the scheduled cron for topic mining

  vector_index built → cache invalidation
    indexing publishes: VectorIndexReadyEvent
    retrieval-service consumes: invalidate Caffeine/Redis cache for corpus
    → guarantees retrieval cache stays fresh after re-indexing

Far future (only if services split):
  crawl-service → parse-service (document.crawled event)
  parse-service → enrichment-service (document.parsed event)
  enrichment-service → index-service (document.enriched event)
```

---

## 8. Execution Order

```
Phase R-A (no Rabbit restart needed — annotation fix only):
  R-A-1: Fix @ConditionalOnExpression on DocumentEnrichmentListener
  R-A-2: Check RabbitDocumentEnrichmentPublisher for same bug
  ↓ deploy, verify no NPE in logs when enrichment.enabled=false but events.enabled=true

Phase R-B (requires Rabbit restart to create new queues):
  R-B-1: Add DLX + DLQs to RabbitMqConfiguration.documentIndexTopology()
  R-B-2: Configure factory: AcknowledgeMode.MANUAL, prefetchCount=5, RetryInterceptor
  R-B-3: No listener code changes needed (retry advice handles ack/nack)
  R-B-4: Deploy, verify 4 queues visible in RabbitMQ management UI
  R-B-5: Simulate failure, confirm message → DLQ after 3 retries
  ↓ DONE — RabbitMQ is now production-safe

Phase R-C (only after Phase D from file 11 is complete):
  R-C-1: Create FeedbackScoreEvent in libs/platform-contracts
  R-C-2: Add feedback queue to documentIndexTopology
  R-C-3: Create FeedbackScoreListener
  R-C-4: Deprecate FeedbackScoreAggregator.aggregateScoreBoost()
  ↓ DONE — feedback loop is event-driven, no cross-schema SQL

Service decomposition: NO ACTION NOW.
  Watch the split triggers defined in Section 7.
  Revisit when trigger 1 or 2 is met.
```

---

## 9. Acceptance Criteria

### Phase R-A complete when:
- [x] `DocumentEnrichmentListener` has `@ConditionalOnExpression` (not two `@ConditionalOnProperty`)
- [ ] Starting app with `enrichment.enabled=false, events.enabled=true` does not log any NPE
      related to enrichment beans being null

### Phase R-B complete when:
- [ ] RabbitMQ shows queues: `document-index`, `document-index.dlq`, `document-parsed`,
      `document-parsed.dlq`
- [x] `factory.setAcknowledgeMode(AcknowledgeMode.MANUAL)` in config
- [x] `factory.setPrefetchCount(5)` in config
- [ ] Simulated listener exception → message in `.dlq` after 3 attempts (not looping)
- [ ] Full enrichment backfill run completes without memory spike

### Phase R-C complete when:
- [x] `FeedbackScoreEvent` class exists in `libs/platform-contracts`
- [x] `FeedbackScoreListener` receives events and calls `CurationOverrideService`
- [x] `FeedbackScoreAggregator` annotated `@Deprecated`
- [ ] No `UPDATE ingestion.document SET score_boost` in any non-deprecated code

---

*Senior directive. Fix in phase order. R-A and R-B are production-safety fixes — urgent.*
*R-C and service decomposition are architectural improvements — do after R-A and R-B verified.*
