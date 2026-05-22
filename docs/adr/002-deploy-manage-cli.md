# ADR 002: Deploy, manage, and unified CLI

## Status

Accepted

## Context

Operations were split across frontend PowerShell, backend Bash, and ad-hoc server steps. We need one contract for local dev, remote deploy, and day-2 management.

## Decision

1. **Unified CLI** — `tools/geostat.ps1` / `tools/geostat.sh` expose:
   - `stack` — `kits/geostat-kit/toolkit/stack/compose.ps1`
   - `stack-deploy` — `kits/geostat-kit/toolkit/deploy/stack-remote.sh` via `tools/geostat.ps1`
   - `compose-gen` — `kits/geostat-kit/compose/build.py` + `ops/compose/catalog.json`
   - `infra` — `kits/geostat-kit/toolkit/infra/ensure-prereqs.sh`
   - `fe` / `be` — module drivers (`java-boot`, `node-vite`) via `tools/geostat.ps1`

2. **Compose catalog** — Single JSON catalog at `ops/compose/catalog.json`; generated files carry `# GENERATED` header. CI fails on drift.

3. **Manage contract** — `kits/geostat-kit/contracts/MANAGE-CONTRACT.md`. Frontend adds `reload` / `config` for static nginx dist; aliases `rm` → `undeploy`, `nuke` → `delete`.

4. **Backend deploy modules** — `geostat be deploy` → `drivers/java-boot/sh/deploy.sh` + `toolkit/deploy/*`; Gradle via `gradle-modules.sh`.

5. **Secrets** — `ops/config/deploy.env` + per-module `ops/config/{frontend,backend}/`; no hardcoded `SERVER` in scripts.

## Consequences

- VS Code tasks should prefer `tools/geostat` for discoverability.
- Root git is optional until [MONOREPO.md](../MONOREPO.md) migration; nested `.git` in modules remain valid.
- Registry/IaC/observability remain out of scope until a future ADR.
