# Project CI (`ops/ci/`)



ეს არის **consumer repo**-ის CI სკრიპტები — არა `kits/geostat-kit` პაკეტის ნაწილი.



## `integration-stack.sh`



Infra (postgres, qdrant, rabbitmq) + full stack health matrix.



```bash

bash ops/ci/integration-stack.sh

# optional RAG crawl after stack:

RUN_RAG_SMOKE=1 bash ops/ci/integration-stack.sh

```



## `rag-pipeline-smoke.sh` (P5-02)



Ingestion + retrieval უნდა უკვე მუშაობდეს (hybrid ან stack).



```bash

bash ops/ci/rag-pipeline-smoke.sh

```



| Env | Default |

|-----|---------|

| `INGESTION_URL` | `http://127.0.0.1:8093` |

| `RETRIEVAL_URL` | `http://127.0.0.1:8092` |

| `SMOKE_SEED_URL` | `https://www.geostat.ge/ka` |

| `SMOKE_JOB_TIMEOUT` | `600` |

| `SMOKE_INDEX_WAIT` | `120` |



Manifest: `geostat.ops.json` → `ci.ragSmoke`, `ci.integration`.



## `corpus-quality-audit.sh` (OPS-02 / P3-03b)



Corpus metrics beyond smoke crawl: empty-body rate, chunk/vector coverage, Playwright/reindex recommendations.



```bash

bash ops/ci/corpus-quality-audit.sh

AUDIT_STRICT=1 bash ops/ci/corpus-quality-audit.sh

```



| Env | Default |

|-----|---------|

| `INGESTION_URL` | `http://127.0.0.1:8093` |

| `AUDIT_CORPUS` | `geostat-portal` |

| `AUDIT_STRICT` | `0` |



API: `GET /api/v1/ingestion/corpora/{name}/quality`. Manifest: `ci.corpusQualityAudit`.



Prod (SSH deploy host):



```bash

bash ops/ci/corpus-quality-audit-prod.sh

```



## `chat-catalog-rag-smoke.ps1` (P5-03)



Retrieval + chat-api unified `items` (catalog + RAG `source` links). Hybrid ④ ან stack.



```powershell
.\ops\ci\chat-catalog-rag-smoke.ps1
.\ops\ci\chat-catalog-rag-smoke.ps1 -RequireSourceLink
```



Prereq: indexed corpus (`rag-pipeline-smoke` ან crawl); `RETRIEVAL_ENABLED=true` on chat-api.



Manifest: `geostat.ops.json` → `ci.chatCatalogRagSmoke`.



## `chat-derived-catalog-smoke.ps1` (RAG-U02 cutover)



Chat-api **`GEOSTAT_CHAT_CATALOG_SOURCE=derived`** — verifies catalog link cards from `mv_portal_link` / `mv_specific_link`.



```powershell
# After cutover prep + chat-api restart with derived env
.\ops\ci\chat-derived-catalog-smoke.ps1
.\ops\ci\chat-derived-catalog-smoke.ps1 -SkipReadiness
```



Prereq: enrichment + aggregation ON, MVs refreshed (`rag-derivation-cutover-prep.ps1`); `GEOSTAT_CHAT_CATALOG_JDBC_URL` on chat-api.



Manifest: `ci.chatDerivedCatalogSmoke`.



## `rag-eval-smoke.ps1` (RAG-L08)



Golden queries from `ingestion.evaluation_query` (fallback hardcoded if API down).



```powershell
.\ops\ci\rag-eval-smoke.ps1
.\ops\ci\rag-eval-smoke.ps1 -Strict
```



Manifest: `ci.ragEvalSmoke` (quick pass rate). Full harness: `ci.ragEvalHarness`.



## `run-eval.py` (RAG-U12)



Full golden-set harness — hit@1, hit@5, MRR, NDCG@10, mean latency; baseline regression gate.



```powershell
.\ops\ci\rag-eval-harness.ps1
.\ops\ci\rag-eval-harness.ps1 -WriteBaseline   # owner: after verified good run
.\ops\ci\rag-eval-harness.ps1 -DryRun
.\ops\ci\rag-eval-harness.ps1 -SkipMinQueries  # fallback 4-query smoke DB down
```



See [ops/eval/README.md](../eval/README.md) · spec [RAG-DERIVATION-ARCHITECTURE §9](../plan/RAG-DERIVATION-ARCHITECTURE.md).



## `rag-eval-gate.ps1` (RAG-U12 P1-15)



Health + golden set + derivation-readiness + lifecycle sync + full harness.



```powershell
.\ops\ci\rag-eval-gate.ps1
.\ops\ci\rag-eval-gate.ps1 -WriteBaseline
.\ops\ci\rag-eval-gate.ps1 -AllowNotReady   # YAML baseline when enrichment OFF
```



Manifest: `ci.ragEvalGate`.



## `rag-derivation-cutover-prep.ps1` (Phase 8 section 13)



API orchestration before eval gate: authority → remine → approve clusters → catalog refresh → lifecycle sync.



```powershell
# Requires INGESTION_ENRICHMENT_ENABLED + INGESTION_AGGREGATION_ENABLED
.\ops\ci\rag-derivation-cutover-prep.ps1 -ApproveAllClusters
.\ops\ci\rag-derivation-cutover-prep.ps1 -RunEvalGate -WriteBaseline
```



Manifest: `ci.ragDerivationCutoverPrep` · readiness: `GET …/derivation-readiness`.



## `rag-locale-pipeline.ps1` (RAG-L01…L09)



Dual seed crawl (`/ka` + `/en`), reindex, eval smoke, optional bilingual chat.



```powershell
.\ops\ci\rag-locale-pipeline.ps1 -MaxPagesPerSeed 5 -Strict
```



Prereq: hybrid tunnel + ingestion + retrieval (+ chat-api for chat leg). Manifest: `ci.ragLocalePipeline`.



## `rag-full-corpus-crawl.ps1` (OPS-02)



Production **full-site** policy (no dev 25-page cap). Dual seed + reindex; continuation runs in ingestion logs.



```powershell
.\ops\ci\rag-full-corpus-crawl.ps1
.\ops\ci\rag-full-corpus-crawl.ps1 -Strict
```



After crawl audit: `POST …/corpora/geostat-portal/playwright-refetch` when quality recommends P3-03b (`INGESTION_PLAYWRIGHT_ENABLED=true`).



## `hybrid-boot-app.ps1` (P0-infra-08 → P0-kit-12 delegate)



Hybrid ④ — Windows host apps + `ops/config/<module>/.env.dev`.  
**Canonical:** `tools\geostat.cmd hybrid boot fe|be|ret|ing` (ან `geostat fe run`).



```powershell
.\ops\ci\hybrid-boot-app.ps1 -Service ing   # delegates to geostat hybrid boot ing
tools\geostat.cmd hybrid boot be
tools\geostat.cmd fe run
```



Prereq: `geostat infra tunnel` (infra ports on localhost).



## დოკუმენტაცია



- [docs/CI.md](../../docs/CI.md)

- [docs/CONFIG.md](../../docs/CONFIG.md)

