# chat-api (module id: `chat-api`)

Spring Boot BFF — chat, Gemini, topics, speech, retrieval client. Path: `apps/backend`. Secrets: `ops/config/backend/`. CLI: `geostat be` → module `chat-api`.

This deployable is the **Chat / BFF** service in the Architecture B layout.

| | |
|---|---|
| **Role** | Orchestration, Gemini, topics, sessions, STT/TTS, RAG client |
| **Port** | 8090 (`API_PORT`) |
| **Calls** | `retrieval-service` via HTTP (`HttpRetrievalClient` → `RetrievalPort`) — **not** `ingestion` on user path |

Outbound: `RetrievalContextService` → `libs/platform-contracts` `RetrievalPort` → retrieval `:8092`.

Prod docker: `RETRIEVAL_ENABLED=true`, `RETRIEVAL_BASE_URL=http://geostat-chat-ai-retrieval:8092` in `ops/config/backend/.env.prod`.

Sibling services: `apps/retrieval-service`, `apps/ingestion-service`.  
Architecture: [docs/ARCHITECTURE-B-SERVICES.md](../../docs/ARCHITECTURE-B-SERVICES.md)
