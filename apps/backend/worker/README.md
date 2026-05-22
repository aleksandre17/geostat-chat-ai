# Worker module (Gradle submodule)

Secondary Spring Boot service in the **same repo** as chat-api (`apps/backend`).

**geostat-chat-ai:** embedded compose worker is **off** (`features.worker: false`). Background / ingest work is **`apps/ingestion-service`** (`geostat ing`, module `ingestion` in `geostat.ops.json`). See `docs/ARCHITECTURE-B-SERVICES.md`.

For other consumers that still use catalog `features.worker: true`:

- Health: `GET /actuator/health` (reports API reachability via `API_INTERNAL_URL`)
- Default port: `WORKER_PORT` (8091)
- Depends on API container health in compose

## Local

```bash
cd backend
./tools/geostat.sh be compose up --build
# API: :8090  Worker: :8091
```

## Deploy

```bash
./tools/geostat.sh be deploy geostat-chat-ai-worker --prod
# or all services:
./tools/geostat.sh be deploy all --prod
```

## Disable worker in compose output

Set `"worker": false` in `infra/compose/catalog.json` → `features`, then run `tools/geostat compose-gen`.
