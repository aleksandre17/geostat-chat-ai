# Quality Pipeline Plan — Index

> **Purpose:** This folder contains the split version of the original `QUALITY-PIPELINE-PLAN.md`.
> All content is **preserved exactly** — no deletions, no modifications.
>
> **Original:** `docs/plan/QUALITY-PIPELINE-PLAN.md` (10249 lines, now a redirect stub)
> **Parts:** 13 files

---

## Files

| # | File | Content | Lines |
|---|------|---------|-------|
| 1 | [`01-overview.md`](./01-overview.md) | Overview, Audit Snapshot, Pipeline Diagram | 47 |
| 2 | [`02-layer-minus1-ingestion.md`](./02-layer-minus1-ingestion.md) | Layer -1 — ingestion-service: Crawl/Parse/Enrich (L-1-01..L-1-07) | 542 |
| 3 | [`03-layers-0-to-5-execution.md`](./03-layers-0-to-5-execution.md) | Layers 0–5: Database/Qdrant/Retrieval/Catalog/Query/Gemini + Execution Order | 282 |
| 4 | [`04-arch-backlog-initial.md`](./04-arch-backlog-initial.md) | Execution Order, Open Questions, Architecture Evolution Backlog (ARCH-01..ARCH-08) | 1123 |
| 5 | [`05-multi-corpus-arch.md`](./05-multi-corpus-arch.md) | Multi-Corpus / Page-Kind / Network Growth Architecture | 1095 |
| 6 | [`06-arch-decisions.md`](./06-arch-decisions.md) | YAML vs DB Architectural Decision + ADRs (AD-01..AD-03) | 306 |
| 7 | [`07-data-quality-defense-part1.md`](./07-data-quality-defense-part1.md) | Data Quality Defense + Gap Analysis Part 1: L-1-08..L-1-22 | 2292 |
| 8 | [`08-data-quality-defense-part2.md`](./08-data-quality-defense-part2.md) | Gap Analysis Part 2: L-1-23..L-1-30, CFG-01, PERF-01..06, BUG-CRAWL-01, BUG-DB-01, DB-ARCH-01..02, OPS-01, ARCH-09..10 | 2874 |
| 9 | [`09-embedding-qdrant-gaps.md`](./09-embedding-qdrant-gaps.md) | Embedding / Vector Index Gap Analysis: PERF-07..10, ARCH-11..13, QDRANT-01..03 | 1304 |
| 10 | [`10-cross-gap-backlog.md`](./10-cross-gap-backlog.md) | CROSS-GAP-01 (Qdrant vector cleanup), BACKLOG items, Final Audit Note | 384 |

---

## Navigation Guide

```
Start here:           01-overview.md              — big picture, audit numbers
Active fixes:         02-layer-minus1-ingestion.md — L-1-01..07 parser/crawl fixes
Pipeline layers:      03-layers-0-to-5-execution.md
Architecture backlog: 04-arch-backlog-initial.md   — ARCH-01..08
Multi-corpus/SPA:     05-multi-corpus-arch.md
YAML vs DB decision:  06-arch-decisions.md         — MUST READ before any DB work
Gap analysis (DB/Par) 07-data-quality-defense-part1.md  — L-1-08..L-1-22
Gap analysis (Crawl): 08-data-quality-defense-part2.md  — L-1-23..PERF-06
Embedding/Qdrant:     09-embedding-qdrant-gaps.md  — PERF-07..10, ARCH-11..13
Backlog:              10-cross-gap-backlog.md       — CROSS-GAP-01, BACKLOG
Coupling/Ownership:   11-coupling-architecture-plan.md — Phase A-E, MUST READ before DB refactor
RabbitMQ/Services:    12-rabbitmq-and-service-decomposition.md
Query+Retrieval:      13-query-and-retrieval-layer-gaps.md — CRITICAL: pipeline disabled, 4 bugs, confidence fix — DLQ, retry, prefetch, service split triggers
```

---

*Split by senior engineer directive. Content integrity verified.*
*Original total: 10249 lines. Zero content removed or modified.*