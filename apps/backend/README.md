# chat-api (`apps/backend`)

BFF for the GEOSTAT chat widget: Gemini orchestration, topics/catalog, speech, retrieval client.

| | |
|---|---|
| Module id | `chat-api` |
| Main class | `com.geostat.chat.ChatApplication` |
| Port | 8090 |
| Secrets | `ops/config/backend/` |

See [ARCHITECTURE-B-SERVICES.md](../../docs/ARCHITECTURE-B-SERVICES.md).

## Package layout (Clean Architecture)

```text
com.geostat.chat/
├── ChatApplication.java
├── api/                         REST + SSE adapters
│   ├── dto/                     wire types (ChatRequest, ChatResponse, …)
│   └── mapper/                  ChatApiMapper (DTO ↔ application)
├── application/                 use cases
│   ├── chat/                    ChatService, ChatResult, ChatResultFactory, …
│   ├── speech/
│   ├── retrieval/
│   └── telemetry/               RetrievalHit, ChatFeedbackCommand
├── domain/                      ports + model (no Spring)
│   ├── catalog/                 Topic, TopicCatalog (port), …
│   ├── chat/                    ChatContext
│   ├── prompt/                  PromptCatalog (port)
│   └── session/                 ConversationHistory (port)
└── infrastructure/              adapters out
    ├── config/                  Ai, AiChatProperties, CORS, routes
    ├── catalog/                 YamlTopicCatalog + YAML loaders (B-24)
    ├── prompt/                  YamlPromptCatalog + loader (B-27)
    ├── gcp/                     Speech client
    ├── session/                 Redis / Caffeine history
    ├── retrieval/               HTTP RetrievalPort
    └── web/                     JacksonChatCompleteEncoder (SSE)
```

Clarification and org-structure queries use **indexed corpus** (`CorpusContextFormatter` + retrieval) — no live BFS in chat-api (B-25).

**ChatResponse (R-01…R-06):** `intro`, `items[]` (citations with `sourceType`, `snippet`, `relevanceScore`), `responseType`, `grounded`, `sourceCount`, `turnId` — no `retrievalHits` in wire JSON (telemetry server-only).

Dependency rule: `api → application → domain`; `infrastructure` implements `domain` ports.

## Catalog data (B-24)

Topic/link data lives in YAML — edit without recompiling Java:

```text
src/main/resources/catalog/
├── topics.yaml
├── specific-links.yaml
├── catalog-meta.yaml      # portals, sectoral keywords, …
└── news-categories.yaml
```

Runtime: `YamlTopicCatalog` + loaders (`TopicCatalogLoader`, `SpecificLinkLoader`, …).

## Prompt data (B-27)

Gemini system prompts — same pattern as catalog:

```text
src/main/resources/prompts/
└── chat-prompts.yaml      # main, clarification, topicClassifier (KA/EN)
```

Runtime: `YamlPromptCatalog` + `PromptCatalogLoader`. AI options: `geostat.ai.chat.*` in `application-custom.yml` (temperatures, JSON schema, safety, history/token budgets). Grounding: `ExplanationGroundingVerifier` + `ResponseGroundingEnforcer`. Telemetry logs `promptVersion` + `promptHash`.

## Build & run

```powershell
cd apps/backend
.\gradlew.bat bootRun
..\..\tools\geostat.ps1 api dev watch
```

Dockerfiles at module root: `Dockerfile` (prod JAR), `Dockerfile.dev` (local compose build).

## Tests

```powershell
.\gradlew.bat test
```
