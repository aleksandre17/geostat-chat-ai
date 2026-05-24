# Architecture B — separate deployables

Status: **implemented** (RAG pipeline, HTTP wiring, prod env B-16). See [docs/plan/PROJECT-PLAN.md](plan/PROJECT-PLAN.md).

## Services

| Service | Path | Module id | Port | Role |
|---------|------|-----------|------|------|
| **chat-api** | `apps/backend` | `chat-api` | 8090 | BFF: chat, Gemini, topics, speech, retrieval client |
| **retrieval** | `apps/retrieval-service` | `retrieval` | 8092 | Qdrant vector search |
| **ingestion** | `apps/ingestion-service` | `ingestion` | 8093 | Crawl / index pipeline |
| **ui** | `apps/frontend` | `frontend` | 5177 | React |

`apps/backend/worker` (Gradle submodule) = **removed** (2026-05-23). Worker role = manifest module **`ingestion`** (`features.worker: false`).

## Communication

```text
frontend ──HTTP──► chat-api (8090)
                      │
                      │ sync HTTP (RetrievalPort)
                      ▼
                 retrieval (8092) ──► Qdrant

ingestion (8093) ──RabbitMQ (async)──► document index listener
                 ──sync fallback──► ChunkVectorIndexer
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

Prod RAG (B-16): same `GEMINI_API_KEY` in backend + retrieval + ingestion; `EMBEDDING_PROVIDER=gemini`; chat `RETRIEVAL_ENABLED=true`.

## Compose

- **Full stack:** `geostat stack up -d --build` → chat-api + retrieval + ingestion + ui (`stack.composeModules`).
- **Remote prod:** `geostat stack-deploy --prod` → same modules, role order (api → worker → ui).
- **Per module:** `geostat ret compose up`, `geostat ing compose up`, `geostat be compose up`.
- **Infra:** `geostat infra local|remote` — postgres, redis, qdrant, rabbitmq per `stack.infra.services`.

Regenerate after catalog edits: `geostat compose-gen`.

## Package layout (chat-api)

Root package: **`com.geostat.chat`** (replaces legacy `Chatbot`).

```text
com.geostat.chat/
├── api/              REST + SSE + dto/
├── application/      chat, speech, retrieval, telemetry
├── domain/           catalog, chat, session (ports + model)
└── infrastructure/   config, gcp, session, retrieval, catalog
```

Single knowledge path for clarification/RAG: ingestion → Qdrant → retrieval (B-25). Removed: `StructureLookup`, live org-chart BFS.

## Catalog (B-24)

All topic detection rules, links, and metadata externalized:

| File | Content |
|------|---------|
| `topics.yaml` | Topic definitions, rules, portals, styles |
| `specific-links.yaml` | Keyword-triggered high-priority links |
| `catalog-meta.yaml` | All portals list, sectoral keywords, news-relevant topics |
| `news-categories.yaml` | Per-topic news category filters |

Loader: `YamlTopicCatalog` implements `TopicCatalog` port.

## Backlog (service improvements)

- **done:** B-06, B-21, B-22, B-23 package `com.geostat.chat` + layers, **B-24 catalog YAML** (2026-05-24)
- **approved:** P3-03b Playwright (trigger-only), P7-01 Ollama local gen, P0-kit-13 worker manifest
- **OPS-02:** `corpus-quality-audit` — data-driven reindex / P3-03b trigger
- **stack:** [ADR-010](adr/010-product-stack-benefit-gate.md) — benefit gate, Q-* closed

See also: [SOURCE-RAG-DESIGN-PROJECTS-FILES.md](plan/SOURCE-RAG-DESIGN-PROJECTS-FILES.md)
