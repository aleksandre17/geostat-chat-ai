# chat-api (module id: `backend`)

This deployable is the **Chat / BFF** service in the Architecture B layout.

| | |
|---|---|
| **Role** | Orchestration, Gemini, topics, sessions, STT/TTS |
| **Port** | 8090 (`API_PORT`) |
| **Calls** | `retrieval-service` (future HTTP client), not `ingestion` directly |

Outbound port (future): `RetrievalClient` implementing `libs/platform-contracts` `RetrievalPort`.

New skeleton services: `apps/retrieval-service`, `apps/ingestion-service`.  
Architecture: [docs/ARCHITECTURE-B-SERVICES.md](../../docs/ARCHITECTURE-B-SERVICES.md)
