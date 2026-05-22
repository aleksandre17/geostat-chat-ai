# ADR-009: Architecture B (separate deployables)

- **Status:** Accepted
- **Date:** 2026-05-21
- **Plan:** [docs/plan/PROJECT-PLAN.md](../plan/PROJECT-PLAN.md) — Phase 1 done (skeleton)

## Context

Geostat chatbot should answer from crawled website content (RAG). Design notes in `Desktop/projects-files/` describe crawler4j, Jsoup, embeddings, Qdrant, Spring Boot, React.

Existing `apps/backend` is a monolithic chat API (Spring AI + Gemini + topics). Ops are manifest-driven via `geostat-kit`.

## Decision

Adopt **architecture B**: separate deployable services with shared contracts, not a single merged backend folder for all concerns.

| Service | Path | Role |
|---------|------|------|
| chat-api | `apps/backend` | BFF, chat, speech, Gemini orchestration |
| retrieval | `apps/retrieval-service` | Vector search / RAG retrieval |
| ingestion | `apps/ingestion-service` | Crawl, parse, chunk, embed, index |
| ui | `apps/frontend` | React |

Shared API types: `libs/platform-contracts` (Java library, **not** under `apps/`).

Communication:

- UI → chat-api (sync HTTP)
- chat-api → retrieval (sync HTTP, future)
- ingestion → index (async events, future) — **not** chat-api → ingestion on user path

Legacy `apps/backend/worker` Gradle module remains for existing compose catalog placeholder; **ingestion-service** is the RAG worker, not that module.

## Consequences

- **Positive:** Clear boundaries, SOLID ports, independent scale/deploy, aligns with RAG pipeline.
- **Negative:** More compose/manifest work, network contracts, operational surface.
- **Skeleton first:** Phase 1 delivered health + stub APIs only; logic in plan phases 2–4.

## Alternatives considered

- **Modular monolith** inside one Spring Boot app — rejected for target B; may be used temporarily inside one repo with multiple Gradle projects.
- **Moving `libs/` into `apps/`** — rejected; shared contracts are not deployables ([plan/BACKLOG.md](../plan/BACKLOG.md) B-08).

## References

- [ARCHITECTURE-B-SERVICES.md](../ARCHITECTURE-B-SERVICES.md)
- [docs/plan/approved/README.md](../plan/approved/README.md)
