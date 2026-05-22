# ADR 003: Multi-module backend on one Docker network

## Status

Accepted

## Context

Multiple Spring Boot modules (API, worker) must run in separate containers, deploy independently, and call each other over HTTP inside Docker.

## Decision

1. **Registry** — `apps/backend/ops.modules` maps compose service → Gradle path, Dockerfile, type (`boot` | `library`).
2. **Compose** — `ops/compose/catalog.json` with `features.worker`; `worker_*` templates share `{network_key}` and `depends_on` API health.
3. **Internal URLs** — `API_INTERNAL_URL=http://{api_container}:{API_PORT}` in env (not `localhost` from sibling containers).
4. **Server deploy** — per-service directory `<container_name>/`; `gen_server_compose.py` attaches `networks: external: true` to shared `DOCKER_NETWORK`.
5. **Gradle** — `:shared` library; `:worker` bootJar; `-PactiveModules` disables unrelated subprojects in task graph.

## Consequences

- Disable worker: set `"worker": false` in catalog `features` and run `compose-gen`.
- Legacy container names: override `COMPOSE_*_SERVICE` in `ops/config/deploy.env`.
