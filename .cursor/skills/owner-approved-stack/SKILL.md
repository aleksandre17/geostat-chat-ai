---
name: owner-approved-stack
description: >-
  Approved product stack from docs/plan and ADRs: when to use established OSS
  libraries (crawler4j, Jsoup, Flyway, Qdrant) vs custom code vs kits/geostat-kit.
  Clean Architecture adapters. Use for ingestion, retrieval, RAG, dependencies,
  or when choosing build.gradle libraries.
---

# Owner approved stack & libraries

The owner expects **senior bar**: Clean Architecture, SOLID, **best practical choice** — including **established packages** where the plan already approved them. Do not reimplement what a proven library does well.

**Related rules:** `zero-gap-architecture.mdc`, `max-capability-collaboration.mdc`, `plan-automation-gate.mdc` · Index: `.cursor/rules/README.md`

## Sources of truth (read before picking tech)

| Source | What it defines |
|--------|-----------------|
| [docs/plan/PROJECT-PLAN.md](../../docs/plan/PROJECT-PLAN.md) | Phases, P*-tasks, status |
| [docs/plan/SOURCE-RAG-DESIGN-PROJECTS-FILES.md](../../docs/plan/SOURCE-RAG-DESIGN-PROJECTS-FILES.md) | RAG pipeline, **Q-01…Q-17** decisions |
| [docs/plan/INFRA-DATA-STORES.md](../../docs/plan/INFRA-DATA-STORES.md) | Postgres / Redis / Qdrant roles |
| [docs/plan/INGESTION-DATA-MODEL.md](../../docs/plan/INGESTION-DATA-MODEL.md) | Schema `ingestion.*` ownership |
| [docs/adr/](../../docs/adr/) | Architecture decisions (e.g. ADR-009 Architecture B) |
| [docs/plan/approved/](../../docs/plan/approved/) | Locked choices |

If plan names a library and implementation uses a hand-rolled substitute → **flag it** and align (see `.cursor/rules/plan-automation-gate.mdc`).

## Three kinds of “package” (do not confuse)

| Kind | Location | Use when | Examples |
|------|----------|----------|----------|
| **Reusable ops kit** | `kits/<name>/` | CLI, compose-gen, manifest drivers; other repos clone it | `geostat-kit` |
| **Maven/Gradle dependency** | `build.gradle.kts` | Domain capability approved in plan; battle-tested OSS | [crawler4j](https://github.com/yasserg/crawler4j), Jsoup, Flyway, Spring Boot starters |
| **App/domain code** | `apps/*`, `libs/platform-contracts` | Business rules, ports, PG models, orchestration | `CrawlJobService`, JPA entities, HTTP adapters |

**Rule:** `kits/` = ops framework. **Not** a replacement for Maven libs like crawler4j.

## Library-first policy (mandatory)

1. **Check plan / Q-* IDs** before writing fetch, crawl, embed, vector search, migrations.
2. If plan **approved** a library → **use it** behind an **adapter** (infrastructure layer), not copy its behavior ad hoc — **when a logical milestone requires it** (see below).
3. **Custom code** is OK for what we own by design: Postgres pipeline state, manifest wiring, API contracts.
4. **Stopgap** is OK while the milestone is not reached; do not block pipeline progress solely to wire a library early.
5. When two options exist (A library vs B library) and plan is **open** → propose with rationale; do not silently pick.

### When to wire crawler4j (logical milestones)

Integrate **at the first milestone that needs it**, not before:

| Milestone | Wire crawler4j? |
|-----------|-----------------|
| Basic fetch + PG frontier + link discovery | **Done** — `Crawler4jPageFetcher` |
| `respectRobotsTxt: true` enforced from corpus policy | **Done** — `RobotstxtServer` |
| Production politeness / user-agent standardization | **Done** — `CrawlFetchInfrastructure` |
| `excludePatterns` / `includePatterns` from policy | **Done** — `CorpusPolicy.isUrlAllowed` |
| Multi-threaded fetch workers | **Optional** — PG queue + thread pool may suffice first |

## Approved ingestion / RAG stack (current)

| Concern | Approved | Integration pattern |
|---------|----------|---------------------|
| Crawl fetch + robots + politeness | **crawler4j** (Q-06) | `PageFetcher`, `RobotstxtServer`, `CrawlConfig` — **not** full `CrawlController` queue |
| URL queue + audit | **Postgres** `url_frontier` | Owner / source of truth — crawler4j does **not** replace this |
| HTML parse + clean | **Jsoup** (Q-06, P3-02) | Domain service or adapter after fetch |
| Pipeline schema | **Flyway** + JPA `ingestion.*` | ingestion-service owner (P3-05) |
| Vectors | **Qdrant** (P4) | ingestion write, retrieval read |
| Async events | **RabbitMQ** (P5, B-01) | Spring AMQP adapter — **when P5 milestone**; self-host, not paid SaaS |
| Inter-service API | **platform-contracts** + HTTP | ports/adapters |

**Target crawl architecture (final):**

```text
POST /jobs → crawl_run + seed url_frontier (PG)
     → crawler4j PageFetcher + robots (infra adapter)
     → Jsoup clean (domain)
     → document / chunk (PG) → embed → Qdrant
     → retrieval-service → chat-api (CorpusContextFormatter, CatalogRagLinkMerger)
```

**Zero-gap (B-25):** chat-api **must not** live-crawl or BFS URLs that ingestion indexes (e.g. `/structure`). Clarification and RAG use **retrieval only**. Single seed + link discovery — no duplicate structure seeds. See `.cursor/rules/zero-gap-architecture.mdc`.

**Max capability:** ingestion runs **background** crawl; each document → **async index event** (RabbitMQ when enabled); prod uses full corpus policy, not smoke page limits. See `.cursor/rules/max-capability-collaboration.mdc`.

## Clean Architecture mapping (ingestion example)

| Layer | Contents |
|-------|----------|
| **Domain** | Corpus policy, crawl run lifecycle, chunk strategy (interfaces) |
| **Application** | `CrawlJobService`, orchestration |
| **Infrastructure** | JPA, `CrawlRunStore`, **Crawler4jPageFetcherAdapter**, Jsoup parser |
| **API** | `IngestionController`, `CorpusController` |

Dependencies point **inward**. Libraries sit in **infrastructure**, wired via Spring `@Configuration`.

## When to propose instead of implementing

- Plan says library X but integration is non-trivial (e.g. PG frontier + crawler4j) → implement **adapter**; if scope large, propose baseline in `PROJECT-PLAN` first.
- New dependency not in plan → BACKLOG or PLAN row + owner OK.
- Library unmaintained (crawler4j 4.4.0) → note risk; still use if plan approved unless owner picks alternative in Q-*.

## Verification

After adding/changing stack choices:

- Dependency in correct module `build.gradle.kts` only
- Plan row / Q-* still accurate
- No duplicate queue (PG + crawler4j storage both “owner”)
- Tests for adapter boundary where behavior matters

## Related rules & skills

- `.cursor/rules/owner-standards.mdc` — overall bar
- `.cursor/rules/plan-automation-gate.mdc` — propose before silent debt
- `.cursor/skills/owner-architecture` — layout & boundaries
- `.cursor/skills/owner-agent-conduct` — execute & verify
