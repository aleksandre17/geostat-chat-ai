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
| 2026-05-22 | **P3-05 started:** ingestion-service Flyway schema `ingestion`, JPA, hybrid/docker profiles — [INGESTION-DATA-MODEL.md](INGESTION-DATA-MODEL.md) |
| 2026-05-22 | **Q-14 closed:** shared Postgres cluster, schema per service |
| 2026-05-22 | **P3-05 done:** ingestion JPA (6 tables), `POST/GET /jobs`, async crawl runner + Jsoup |
| 2026-05-22 | **P3-01 started:** PG `url_frontier` queue, link discovery from corpus policy |
| 2026-05-22 | **Skills/rules:** `owner-approved-stack` — plan libs (crawler4j, Jsoup), CA adapters; P3-01 = hybrid not optional |
