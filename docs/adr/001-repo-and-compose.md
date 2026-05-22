# ADR 001: Monorepo workspace and generated Compose

## Status

Accepted

## Context

Frontend and backend lived in separate git repos with duplicated compose and divergent deploy scripts.

## Decision

1. **Workspace root** `geostat-chat-ai/` holds secrets, stack compose, and shared tooling.
2. **Compose** is generated from `scripts/compose/build.py` (catalog in code) — do not edit generated YAML.
3. **Ops CLI** unified under `tools/geostat.ps1` / `geostat.sh`.
4. **Server deploy** uses `gen_server_compose.py` to rewrite `../ops/config/` paths to `./` on the server.
5. **Frontend prod** static deploy writes `dist/config.json` for runtime API URL without rebuild.

## Consequences

- Run `compose-gen` after catalog changes.
- Module repos may remain separate git roots until an explicit monorepo migration.
