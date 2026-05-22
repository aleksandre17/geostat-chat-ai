# Architecture B — separate deployables (skeleton)

Status: **skeleton only** (health + contract stubs). Business logic comes in later phases.

**Living plan:** [docs/plan/PROJECT-PLAN.md](plan/PROJECT-PLAN.md) — what is approved, in progress, and next.

## Services

| Service | Path | Module id | Port | Role |
|---------|------|-----------|------|------|
| **chat-api** | `apps/backend` | `backend` | 8090 | BFF: chat, Gemini, topics, speech |
| **retrieval** | `apps/retrieval-service` | `retrieval` | 8092 | RAG search → chunks |
| **ingestion** | `apps/ingestion-service` | `ingestion` | 8093 | Crawl / index pipeline |
| **ui** | `apps/frontend` | `frontend` | 5177 | React |

`apps/backend/worker` (Gradle submodule) = optional **embedded** worker in older single-repo stacks. **This project** sets `ops/compose/catalog.json` → `"features": { "worker": false }` and uses manifest module **`ingestion`** (`apps/ingestion-service`, port 8093) for the worker role in compose.

## Communication (target)

```text
frontend ──HTTP──► chat-api (8090)
                      │
                      │ sync HTTP
                      ▼
                 retrieval (8092) ──► Qdrant (future)

ingestion (8093) ──async (P5: RabbitMQ)──► index consumers (future)
```

- **chat-api** must not call **ingestion** on user request path.
- **ingestion** must not block **chat-api**.

## Shared contracts

`libs/platform-contracts` — DTOs + ports only (no Spring):

- `RetrievalPort`, `RetrievalQuery`, `RetrievedChunk`
- `IngestionJobRequest`, `IngestionJobStatus`

Each service is an independent Gradle build (`includeBuild` contracts).

## Secrets

| Module | Config |
|--------|--------|
| chat-api | `ops/config/backend/` |
| retrieval | `ops/config/retrieval/` |
| ingestion | `ops/config/ingestion/` |
| ui | `ops/config/frontend/` |

## Run locally (skeleton)

```powershell
cd apps/retrieval-service
..\..\apps\backend\gradlew.bat bootRun   # or install wrapper per service later

cd apps/ingestion-service
..\..\apps\backend\gradlew.bat -p . bootRun
```

Or from each service directory after `gradle wrapper` is added.

## Compose

- **Full stack:** `geostat stack up -d --build` → chat-api + retrieval + ingestion + ui (`stack.composeModules`).
- **Remote prod:** `geostat stack-deploy --prod` → same modules, role order (api → worker → ui); no hand-maintained `stackDeploy.steps` required.
- **Per module:** `geostat ret compose up`, `geostat ing compose up`, `geostat be compose up` (API only).
- **Infra:** `geostat infra local|remote` — postgres, redis, qdrant per consumer `stack.infra.services`.

Regenerate after catalog edits: `geostat compose-gen`.

## Next implementation phases

1. Wire **chat-api** → `RetrievalClient` HTTP adapter (contracts).
2. **ingestion**: crawler4j + Jsoup + chunk + embed (projects-files pipeline).
3. **retrieval**: Qdrant + search API implementation.
4. Async bus (**RabbitMQ**, P5) ingestion → index events.
5. Stack compose-gen + CI health matrix — **done** (2026-05-22, ფაზა 0c).

See also: `C:\Users\Test-User\Desktop\projects-files\` (RAG design screenshots).
