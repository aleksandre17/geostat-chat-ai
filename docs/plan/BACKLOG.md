# Backlog — ჯერ არა დამტკიცებული



იდეები სანამ `PROJECT-PLAN.md`-ში **approved** არ გახდება. დამტკიცებისას: გადატანა PLAN-ში + `CHANGELOG-PLAN.md` + საჭიროებისას ADR.



| ID | იდეა | შენიშვნა |

|----|------|----------|

| B-01 | RabbitMQ ingestion → index events | async; self-host infra compose — [INFRA-DATA-STORES.md](INFRA-DATA-STORES.md) |

| B-02 | LangChain4j vs მხოლოდ Spring AI | აირჩიოთ ერთი primary stack |

| B-03 | Playwright dynamic pages | მხოლოდ თუ Jsoup არ სკამებს SPA |

| B-04 | Ollama local LLM | alternative to Gemini-only |

| B-05 | `apps/backend/worker` გაუქმება / გაერთიანება ingestion-თან | **done** (2026-05-22) — `features.worker: false`, manifest `ingestion` |

| B-06 | Rename module `backend` → `chat-api` in manifest | breaking CLI alias only |

| B-07 | Integration test: full RAG question → answer | CI compose + curl |

| B-08 | `libs/` გადატანა `apps/`-ში | **rejected** — libs root-ზე რჩება (2026-05-21) |

| B-09 | `kits/geostat-kit/docs/DEV-MODES.md` §④ Hybrid | plan დოკი მზადაა — kit დოკი pending |

| B-10 | `geostat infra` driver (remote up/down/tunnel) | **done** — [HYBRID-DEV-ARCHITECTURE.md](HYBRID-DEV-ARCHITECTURE.md) §7 |
| B-11 | Kit: `embeddedWorker` manifest-ში (`features.worker` deprecate) | [PACKAGE-PRINCIPLES.md](../../kits/geostat-kit/docs/PACKAGE-PRINCIPLES.md) |
| B-12 | Server prod migrate: flat → structured (`runtime/`, `static/`) | P6-migrate — ძველი `geostat-chat-api`/`app` კიდევ flat |
| B-13 | retrieval/ingestion `ops.config.sh` (optional dev overrides) | daily dev-ისთვის საჭიროებისამებრ |
| B-14 | Manifest → Spring profile YAML (`config-gen`) | **→ PLAN** ფაზა 0d, P0-kit-09…11 |



### გადაწყვეტილების კითხვები (სკრინშოტებიდან)



სრული აღწერა: [SOURCE-RAG-DESIGN-PROJECTS-FILES.md](SOURCE-RAG-DESIGN-PROJECTS-FILES.md) §9.



| ID | კითხვა | სტატუსი |

|----|--------|---------|

| Q-01 | Primary LLM: Gemini vs Ollama+Llama | open |

| Q-02 | Spring AI vs LangChain4j | open |

| Q-03 | Embeddings: `hash-v1` dev, **Gemini** prod, **Ollama** local free | **done** | `libs/embedding-adapters` |

| Q-04 | Qdrant vs Weaviate | open |

| Q-05 | Jsoup only vs +Playwright | open |

| Q-06 … Q-13 | იხ. SOURCE დოკი | open |



### ინფრა / Docker (Postgres / Redis / hybrid / network)

სრული: [INFRA-DATA-STORES.md](INFRA-DATA-STORES.md) · [HYBRID-DEV-ARCHITECTURE.md](HYBRID-DEV-ARCHITECTURE.md) · [DOCKER-ECOSYSTEM.md](DOCKER-ECOSYSTEM.md).

| ID | კითხვა | სტატუსი |
|----|--------|---------|
| Q-14 | Postgres: ერთი cluster vs DB per service | **closed** — schema `ingestion` in ingestion-service |
| Q-15 | Redis persistence dev/prod | open |
| Q-16 | Infra compose ცალკე vs მხოლოდ full stack | **closed** — D-08, [DOCKER-ECOSYSTEM.md](DOCKER-ECOSYSTEM.md) §3.2 |
| Q-17 | `apps/backend/worker` vs ingestion | **closed** (2026-05-22) — ingestion only |



---



## როგორ დავამატოთ



```markdown

| B-11 | ახალი იდეა | მოკლე ახსნა |

```



დამტკიცება → კოპირება `PROJECT-PLAN.md` ცხრილში `approved` სტატუსით.

