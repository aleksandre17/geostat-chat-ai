# ADR 005: Java package naming (`Chatbot`)

## Status

**Superseded** — migrated to `com.geostat.chat` (B-23, 2026-05-24).

## Context

Spring sources originally lived under `package Chatbot` (historical naming). Deploy, compose, Docker, and manage tooling use:

- Compose service names (`COMPOSE_*` / repo slug)
- `apps/backend/ops.modules` (generated)
- Gradle module paths

None of these reference Java package names.

## Decision (original)

- **Ops and IaC** remain package-agnostic.
- **Renaming** `Chatbot` → product package is an in-app refactor only — not required for multi-module or legacy server alignment.

## Supersession (2026-05-24)

- Application package is now **`com.geostat.chat`** with Clean Architecture layers (`api`, `application`, `domain`, `infrastructure`).
- Ops unchanged: same compose service names, `geostat be deploy`, manifest module id `chat-api`.

## Consequences

- New developers: see [ENVIRONMENT.md](../ENVIRONMENT.md) and [apps/backend/README.md](../../apps/backend/README.md).
- Loggers / prod config use `com.geostat.chat`, not `Chatbot`.
