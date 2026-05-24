# გეგმის ცვლილებების ჟურნალი

| თარიღი | ცვლილება |
|--------|----------|
| 2026-05-21 | შეიქმნა `docs/plan/` (README, PROJECT-PLAN, BACKLOG, approved) |
| 2026-05-21 | ფაზა 1 **done**: B skeleton — retrieval, ingestion, platform-contracts |
| 2026-05-21 | ფაზა 7 **done**: `.cursor/skills`, `owner-standards` rule |
| 2026-05-21 | ADR-009 Accepted: Architecture B (separate deployables) |
| 2026-05-21 | **approved**: ფაზები 2–4 (chat→retrieval, ingestion pipeline, Qdrant) |
| 2026-05-21 | **rejected** (BACKLOG B-08): `libs/` → `apps/` — libs root-ზე რჩება |
| 2026-05-21 | დაემატა [SOURCE-RAG-DESIGN-PROJECTS-FILES.md](SOURCE-RAG-DESIGN-PROJECTS-FILES.md) — 8 სკრინშოტის დეტალური ამოღება + Q-01…Q-13 |
| 2026-05-21 | დაემატა [INFRA-DATA-STORES.md](INFRA-DATA-STORES.md) — Postgres/Redis/Qdrant, ფაზები, Q-14…Q-17 |
| 2026-05-21 | დაემატა [HYBRID-DEV-ARCHITECTURE.md](HYBRID-DEV-ARCHITECTURE.md) — **approved** D-01…D-06, ფაზა 0b, P0-infra-* |
| 2026-05-21 | დაემატა [DOCKER-ECOSYSTEM.md](DOCKER-ECOSYSTEM.md) — **approved** D-07…D-09, `geostat-chat-ai-net`, compose A/B, Q-16 closed |
| 2026-05-21 | **იმპლემენტაცია P0-infra:** `ops/compose/infra/` compose + `ops/config/infra/` + `geostat infra` (local/remote/tunnel) |
| 2026-05-21 | დაემატა [SERVER-DEPLOY-LAYOUT.md](SERVER-DEPLOY-LAYOUT.md) — **D-10** `geostat/{frontend,backend,infra}`, არა artifact, multi-project |
| 2026-05-22 | **ფაზა 0c done:** geostat-kit manifest audit #1–#7 (P0-kit-01…08) — compose-gen N-module, stack-deploy, CI health matrix |
| 2026-05-22 | **P6-01…03 done:** `ops/compose/stack/`, `ci.healthModules`, integration-stack manifest-driven |
| 2026-05-22 | **სერვერი:** infra გადავიდა `infra/geostat-chat-ai/`; წაშლილი ძველი `infra/compose/` + artifact dirs |
| 2026-05-22 | **Q-17 closed:** worker role = `ingestion`; `features.worker: false` |
| 2026-05-22 | **B-25 done:** StructureLookup წაშლილი; clarification/RAG — მხოლოდ ingestion pipeline; `zero-gap-architecture` rule + skills |
| 2026-05-22 | **R-01…R-06 done:** ChatResponse v2 — citations metadata, server-only telemetry, frontend grounded badge |
| 2026-05-22 | **P3-05 started:** ingestion-service Flyway schema `ingestion`, JPA, hybrid/docker profiles — [INGESTION-DATA-MODEL.md](INGESTION-DATA-MODEL.md) |
| 2026-05-22 | **Q-14 closed:** shared Postgres cluster, schema per service |
| 2026-05-22 | **P3-05 done:** ingestion JPA (6 tables), `POST/GET /jobs`, async crawl runner + Jsoup |
| 2026-05-22 | **P3-01 started:** PG `url_frontier` queue, link discovery from corpus policy |
| 2026-05-22 | **Skills/rules:** `owner-approved-stack` — plan libs (crawler4j, Jsoup), CA adapters; P3-01 = hybrid not optional |
| 2026-05-22 | **B-27 done:** Prompt YAML (`resources/prompts/`), `GoogleGenAiChatOptions` JSON schema, enriched session turns, clarification URL whitelist, stream intro extractor, grounded fix, `ops/ci/chat-prompt-smoke.ps1` |
| 2026-05-22 | **B-27 completion:** P6 `ExplanationGroundingVerifier`, H2 RAG excerpts in history, Gemini safety settings, prompt hash telemetry, `PromptBudgetTrimmer`, `ChatServiceTest`, OPS runbook |
| 2026-05-23 | **Hybrid ④ gap + kit upstream:** **P0-infra-08** consumer stopgap `ops/ci/hybrid-boot-app.ps1`; **P0-kit-12** `geostat hybrid boot` + `fe`/`be`/`ing`/`ret` `run` in `kits/geostat-kit` — [HYBRID-DEV-ARCHITECTURE.md](HYBRID-DEV-ARCHITECTURE.md) §6, **B-20** |
| 2026-05-23 | **P5-02 done:** `ops/ci/rag-pipeline-smoke.sh`; hybrid crawl→index→retrieval verified |
| 2026-05-23 | **P5-03 done:** `CatalogRagLinkMerger`, RAG prompt citations, `ops/ci/chat-catalog-rag-smoke.ps1` — **B-15** |
| 2026-05-23 | **P0-kit-09 baseline:** `geostat config-gen`, `application-custom.yml` split; ingestion golden + validate drift |
| 2026-05-22 | **P0-kit-09 retrieval:** `configGen.mode=simple` — qdrant-only `application.yml` + `application-custom.yml`; pytest 8/8 |
| 2026-05-22 | **P0-kit-09…11 done:** backend `env-profiles` (local/dev/prod), `API_PORT`, hybrid `springProfiles=local`; 3 modules validate |
| 2026-05-22 | **P0-infra-07 done:** `vscode_gen` hybrid compound + `geostat: infra tunnel` background preLaunchTask |
| 2026-05-23 | **B-16 / P2-02 done:** prod `RETRIEVAL_ENABLED`, docker DNS retrieval URL, gemini embeddings, RabbitMQ async; fixes: config-gen rabbit placeholders, `RabbitMqConfiguration` ConnectionFactory + RabbitAdmin |
| 2026-05-23 | **B-07 done:** `ops/ci/chat-rag-e2e-smoke.sh`; CI `integration-stack.sh` + `RUN_RAG_SMOKE=1`; manifest `ci.chatRagE2eSmoke` |
| 2026-05-23 | **B-17 done:** SSE `/api/chat/stream` (`ChatStreamController`, `ChatService.streamChatResponse`); frontend SSE + sync fallback |
| 2026-05-23 | **B-18 done:** `ConversationHistory` port; Redis adapter prod; Caffeine default local |
| 2026-05-23 | **B-19 done:** feedback API, retrieval hit telemetry, `turnId` on `ChatResponse`; `ops/ci/verify-services-prod.sh` |
| 2026-05-23 | **P6-migrate / B-12 done:** server structured layout verified; `geostat layout migrate --prod`; prod redeploy api+retrieval+ingestion; `verify-services-prod.sh` ALL PASSED; legacy `geostat-chat-api` container name coexists (not stopped) |
| 2026-05-23 | **Kit fix:** `stack-remote.sh` CRLF strip; `deploy.sh` UTF-8 BOM removed; `common.sh` service arg `\r` strip — fixes `stack-deploy` `all\r` error |
| 2026-05-23 | **Kit deploy preflight:** `check.sh` manifest port env + module Dockerfile; `stack-deploy` passes `@CliRest`; `config_gen --port-env`; ingestion shared gradlew |
| 2026-05-23 | **Kit stack-deploy:** java-boot-only `--prod`/`--skip-checks`; node-vite `-Environment` binding in `geostat.ps1`; `fe deploy dist` prod verified |
| 2026-05-23 | **B-06 done:** manifest module id `backend` → `chat-api`; aliases `be`/`chat` preserved; secrets path unchanged |
| 2026-05-23 | **B-21 done:** chat-api logic packages `session/`, `chat/`, `speech/`, `structure/`; removed flat `service/` |
| 2026-05-23 | **B-22 done:** `libs/qdrant-client` — shared `QdrantClients`, `VectorCollectionNaming`, `QdrantOperationException` |
| 2026-05-23 | **Worker cleanup:** removed `apps/backend/worker` Gradle submodule (`features.worker: false` since B-05) |
| 2026-05-23 | **Kit validate:** `manifest.schema.json` — `ci.chatRagE2eSmoke`, `ci.verifyServicesProd`; `driver_api` resolves CLI aliases for `path`/`type`/`caps` |
| 2026-05-23 | **Kit Windows:** `lib/geostat-python.ps1` — `py -3` launcher fallback; fixes `geostat validate`/`compose-gen` when Store `python` alias is broken |
| 2026-05-23 | **ADR-010 Accepted:** benefit gate (D-18); Q-01,02,04,05,13,15 closed; B-02 rejected; B-03/04/11 → PLAN; B-13 cancelled; approved D-19…D-24 |
| 2026-05-23 | **OPS-02 tooling:** `GET …/corpora/{name}/quality` + `ops/ci/corpus-quality-audit.sh` — empty-body / chunk / vector metrics; P3-03b trigger |
| 2026-05-24 | **RAG gaps closed:** L07 semantic rerank, Redis retrieval cache, P3-03b Playwright refetch API, B-28 archive port, B-30 `chat.*` telemetry (profile `telemetry-db`), OPS-02 `rag-full-corpus-crawl.ps1` |
| 2026-05-24 | **RAG polish:** incremental freshness (`freshness-refresh`), S3/MinIO archive adapter, V7 prod eval set, QueryRouter RAG-first, `hybrid-jar-boot.ps1`, catalog `urlEn` YAML |
| 2026-05-24 | **Closure:** kit JAR hybrid boot, catalog urlEn gen, chat broad smoke, eval MinPassRate, full corpus crawl started, Qdrant-locale/Playwright closed in plan |
| 2026-05-24 | **RAG-L11 done:** display metadata layer — V8 `document.display_description`, `PageDisplayMetadataExtractor`, Qdrant `pageDescription`, locale-aware citation snippets |
| 2026-05-24 | **Decision narrative:** [`transcripts/2026-05-24-derivation-architecture-decision.md`](transcripts/2026-05-24-derivation-architecture-decision.md) — owner→agent dialogue arc (initial state → senior review gaps → owner derivation insight → refined L1..L5 architecture → keyword nuance → adopted vs rejected variants → lessons for junior agents). Complement to ADR-011 (the *why* / *journey*; ADR captures the *what* / decision). |
| 2026-05-24 | **ADR-011 Accepted + Phase 8 approved:** RAG **derivation architecture** — corpus is single source of truth; YAML catalog deprecated. Approved: D-25 (derivation adopted), D-26 (curation overlay ≤50 rows, TTL 90d, reason mandatory). Spec: [RAG-DERIVATION-ARCHITECTURE.md](RAG-DERIVATION-ARCHITECTURE.md) — full L1..L5 architecture, V9..V12 migrations, 8 derivers (RAG-U01a..h), 3 materialized views (mv_portal_link, mv_specific_link, mv_topic_keywords), curation overlay (`ingestion.curation_override`), query understanding pipeline (RAG-U07: SymSpell + Normalizer + Gemini IntentClassifier + EntityExtractor + QueryExpander), multi-vector Qdrant migration v1→v2 (RAG-U08), HyDE+multi-query (RAG-U09), hybrid retrieval + RRF fusion + MMR (RAG-U10), confidence tier + ResponseRouter (RAG-U11), eval harness 150–300 golden + CI gate (RAG-U12), feedback score_boost (RAG-U13), caching tier (RAG-U14). Free packages adopted: JGraphT, Smile, YAKE Java port, SymSpell. Rejected: original RAG-U01 raw YAML→DB migration (superseded), RAG-U03 SourceComposer (merged into U10), RAG-U04 hybrid keyword topic classifier (replaced by U07c IntentClassifier), RAG-U06 public catalog API (dropped), Python sidecars (deferred). RAG-U15 knowledge graph (Apache AGE) deferred to P4+. PROJECT-PLAN.md Phase 8 row + RAG-U01a..h, U02, U05, U07..U15 detailed rows added; approved/README.md D-25, D-26; BACKLOG.md rejected variants archived; ADR-011 doc; ADR-README index updated. |
| 2026-05-23 | **P0-kit-13 done:** `modules.*.compose.embeddedWorker`; `effective_compose_features`; catalog `features.worker` deprecated |
