# ADR 005: Java package naming (`Chatbot`)

## Status

Accepted — **no change** for ops; optional application refactor later.

## Context

Spring sources live under `package Chatbot` (historical naming). Deploy, compose, Docker, and manage tooling use:

- Compose service names (`COMPOSE_*` / repo slug)
- `apps/backend/ops.modules` (generated)
- Gradle module paths (`worker/`, root boot)

None of these reference Java package names.

## Decision

- **Ops and IaC** remain package-agnostic.
- **Renaming** `Chatbot` → e.g. `geostat.chatbot` is an in-app refactor only (imports, tests, docs) when the team chooses — not required for multi-module or legacy server alignment.

## Consequences

- New developers: see [ENVIRONMENT.md](../ENVIRONMENT.md) for deploy/env; ignore package name for scripts.
- If package is renamed later, update application docs only — not `deploy.sh` / `manage.sh`.
