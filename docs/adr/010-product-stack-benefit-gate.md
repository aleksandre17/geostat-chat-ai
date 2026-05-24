# ADR-010: Product stack — benefit gate & closed Q-* decisions

- **Status:** Accepted
- **Date:** 2026-05-23
- **Plan:** [docs/plan/PROJECT-PLAN.md](../plan/PROJECT-PLAN.md) · [approved](../plan/approved/README.md) D-16…D-23

## Context

Design screenshots (`SOURCE-RAG-DESIGN-PROJECTS-FILES.md`) describe LangChain4j, Ollama, Playwright, Weaviate. The repo already runs **Architecture B** with Spring Boot 4, **Spring AI + Gemini**, crawler4j + Jsoup, Qdrant, RabbitMQ, Redis sessions (prod).

Owner policy (2026-05-23):

- **Maximum quality** — architecture comparable to successful production RAG systems.
- **One paid SaaS acceptable** — Gemini API for generation + prod embeddings.
- **Free-first elsewhere** — OSS/self-host when quality is sufficient (Ollama embed local, Qdrant, Postgres, RabbitMQ).
- **Benefit gate** — integrate a library or service **only** when it improves architecture, reliability, performance, or product outcomes. No stack tourism.

## Decision

### 1. Benefit gate (mandatory for future integrations)

Before adding any dependency or external service, document:

| Question | Must be “yes” for at least one |
|----------|--------------------------------|
| Does it reduce duplication or boundary leaks? | Architecture |
| Does it improve crawl/index/retrieval/chat quality measurably? | Product |
| Does it improve latency, throughput, or operability? | Performance / ops |
| Is there no lighter OSS/self-host alternative at same quality bar? | Cost / simplicity |

If none apply → **reject** (BACKLOG only, not PLAN).

### 2. Closed product stack (Q-01 … Q-05, Q-13)

| ID | Decision | Rationale |
|----|----------|-----------|
| **Q-01** | **Hybrid LLM:** Gemini generation in **prod**; optional **Ollama generation** in **local/hybrid profile only** (P7-01) | Prod quality + single paid API (D-16); free local dev without key |
| **Q-02** | **Spring AI primary**; **LangChain4j rejected** | Already integrated; dual AI frameworks violate SRP and increase maintenance |
| **Q-03** | *(already closed)* `libs/embedding-adapters`: hash-v1 dev, Gemini prod, Ollama embed optional | Adapter pattern done |
| **Q-04** | **Qdrant**; Weaviate rejected | P4 done, `libs/qdrant-client`, infra compose |
| **Q-05** | **Jsoup + crawler4j default**; Playwright **trigger-only** (P3-03b / B-03) | Playwright adds CI/ops cost; add only when corpus audit proves SPA gap |
| **Q-13** | **Single pipeline — corpus only** | StructureLookup removed (B-25); org/structure via ingestion seeds + RAG; `CorpusContextFormatter` for prompts |

### 3. Structure / clarification (Q-13 superseded)

- **Ingestion** — single seed `https://www.geostat.ge/ka`; `/structure` via **link discovery** (not duplicate seeds).
- **Retrieval + ClarificationService** — indexed passages via `CorpusContextFormatter`; no parallel live crawl in chat-api.
- **CatalogRagLinkMerger (B-15)** — unifies citation cards from catalog + RAG hits.
- **Target (B-26 / Q-09):** background crawl until frontier exhausted; async RabbitMQ index per document in prod; scheduler + auto-continue **done**.

### 4. Backlog disposition

| ID | Outcome |
|----|---------|
| B-02 | **Rejected** — merged into Q-02 (Spring AI) |
| B-03 | **Approved conditional** — P3-03b Playwright when corpus empty-body rate exceeds threshold |
| B-04 | **Approved** — P7-01 Ollama chat generation adapter (local profile only) |
| B-11 | **Approved** — P0-kit-13 deprecate `features.worker`; manifest-only worker modules |
| B-13 | **Cancelled** — `ops.config.sh` exists; `config-gen` + `ops/config/*/.env.dev` sufficient |

### 5. Infra (Q-15)

| ID | Decision |
|----|----------|
| **Q-15** | **Closed** — prod Redis AOF via `ops/compose/infra/services/redis.yml` (`REDIS_AOF:-yes`); dev may run without persistence |

## Consequences

- **Positive:** Single AI stack, clear ports/adapters, no duplicate frameworks; single knowledge pipeline (zero-gap); Playwright/Ollama gen only when justified.
- **Negative:** Ollama gen not yet implemented (P7-01).
- **Ops:** Owner must **rotate GEMINI_API_KEY** (exposed in chat); full corpus reindex is data-driven (telemetry B-19), not scheduled by default.

## Alternatives considered

| Alternative | Why rejected |
|-------------|--------------|
| LangChain4j primary | Rewrite cost; no product benefit over Spring AI |
| Ollama primary LLM in prod | Lower quality vs Gemini; conflicts with D-16 paid-quality bar for prod |
| Playwright by default | Heavy; most geostat.ge pages parse with Jsoup today |
| Weaviate | Migration cost; Qdrant already operational |
| Remove StructureLookup | **Done (B-25)** — clarification uses retrieval; structure via crawl link discovery |

## References

- [009-architecture-b-separate-deployables.md](009-architecture-b-separate-deployables.md)
- [docs/plan/approved/README.md](../plan/approved/README.md) — D-16, D-17, D-18…D-23
