# დამტკიცებული



მოკლე ინდექსი — სრული ცხრილი: [PROJECT-PLAN.md](../PROJECT-PLAN.md).



## არქიტექტურა



- **Architecture B** — ცალკე deployables: chat-api, retrieval, ingestion ([ADR-009](../../adr/009-architecture-b-separate-deployables.md))

- **libs/** root-ზე — `platform-contracts`, არა `apps/libs/` (2026-05-21)

- **Secrets** — `ops/config/<module>/`; compose კოდთან `apps/*`



## Ops



- **geostat-kit** manifest-driven ([ADR-006](../../adr/006-geostat-kit-package.md))

- **4-plane** — `apps/`, `kits/`, `ops/`, `docs/`



## Dev — Hybrid (2026-05-21)



სრული დოკი: [HYBRID-DEV-ARCHITECTURE.md](../HYBRID-DEV-ARCHITECTURE.md) · ინფრა: [INFRA-DATA-STORES.md](../INFRA-DATA-STORES.md)



| ID | გადაწყვეტილება |

|----|----------------|

| **D-01** | რეჟიმი **④ Hybrid**: apps Windows-ზე (bootRun/F5), Postgres/Redis/Qdrant remote Linux Docker |

| **D-02** | Infra = `ops/compose/infra` + `geostat infra remote up/down` + `geostat infra tunnel` |

| **D-03** | კავშირი infra-თან: `INFRA_HOST` + პორტები; dev-ში SSH tunnel → `127.0.0.1` default |

| **D-04** | Peer სერვისები: `RETRIEVAL_BASE_URL`, `VITE_API_URL` და ა.შ. — localhost vs SERVER |

| **D-05** | Spring profile `hybrid` (chat, retrieval, ingestion როცა DB/Redis ჩაერთვება) |

| **D-06** | P3 Postgres + P4 Qdrant + P5 RabbitMQ (+ Redis cache) — remote infra compose | **approved** |

## ბიუჯეტი / stack (2026-05-22)

| ID | გადაწყვეტილება |
|----|----------------|
| **D-16** | **Paid SaaS = Gemini API only** (generation + prod embeddings default) |
| **D-17** | **Free-first elsewhere** — OSS/self-host/local (Ollama embed, Qdrant, RabbitMQ, Postgres); adapter pattern |

## Docker ეკოსისტემა (2026-05-21)

სრული დოკი: [DOCKER-ECOSYSTEM.md](../DOCKER-ECOSYSTEM.md)

| ID | გადაწყვეტილება |
|----|----------------|
| **D-07** | ერთი network ყველა compose-ში: `geostat-chat-ai-net` |
| **D-08** | P6: infra compose ცალკე + apps `external: true` (ვარიანტი B); ერთი stack compose optional (A) |
| **D-09** | Env/Spring profiles `docker` (შიდა DNS) vs `hybrid` (`INFRA_HOST` / localhost peers) |

## სერვერის ხე (2026-05-21)

[SERVER-DEPLOY-LAYOUT.md](../SERVER-DEPLOY-LAYOUT.md)

| ID | გადაწყვეტილება |
|----|----------------|
| **D-10** | Root `/home/.../geostat/`; siblings `frontend/`, `backend/`, `infra/`; **არა** `backend/infra`; infra **არა** JAR artifact |

## geostat-kit N-module ops (2026-05-22)

| ID | გადაწყვეტილება |
|----|----------------|
| **D-12** | Manifest = single source: compose service names, stack deploy steps, CI health URLs — არა hardcoded fe/be/worker |
| **D-13** | `stack.composeModules`: backend + retrieval + ingestion + frontend; `features.worker: false` |
| **D-14** | Infra consumer path: `infra/<INFRA_SLUG>/` (არა shared `infra/compose/`) |
| **D-15** | CI `healthModules`: Java services only; UI skip in Docker integration |

## შემდეგი დამტკიცებული ფაზები (იმპლემენტაცია pending)

- **0b + 0c:** done — infra slug + kit audit
- P2: chat-api → retrieval HTTP

- P3: ingestion pipeline + Postgres (hybrid)  

- P4: Qdrant + retrieval search  

