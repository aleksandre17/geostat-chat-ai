# Ingestion data model — Postgres schema `ingestion`

განახლება: **2026-05-22**  
Owner service: **`apps/ingestion-service`** · Q-14: shared cluster, schema per service.

## Ownership

| Store | Schema / collection | Writer |
|-------|---------------------|--------|
| Postgres pipeline state | `ingestion.*` | ingestion-service only |
| Qdrant vectors | per-corpus collection | ingestion write, retrieval read |

## Tables

| Table | Purpose |
|-------|---------|
| `corpus` | Crawl target registry (seed URLs, policy JSON — site-agnostic) |
| `crawl_run` | One pipeline execution |
| `url_frontier` | Per-run URL queue |
| `document` | Canonical page + cleaned text (source of truth) |
| `chunk` | Text segments for embedding |
| `vector_index` | Pointer chunk → Qdrant point |

Migrations: `apps/ingestion-service/src/main/resources/db/migration/`

## Default corpus

`geostat-portal` — seed `https://www.geostat.ge/ka` (V2 seed migration). Other sites = new `corpus` rows.

## Run with DB

```powershell
# 1. infra + tunnel
.\tools\geostat.ps1 infra remote up
.\tools\geostat.ps1 infra tunnel

# 2. ingestion (hybrid profile from ops/config/ingestion/.env.dev)
cd apps/ingestion-service
..\..\apps\backend\gradlew.bat bootRun
# GET http://localhost:8093/api/v1/ingestion/corpora
```

Without `db` profile (no Postgres): health-only skeleton — CI default.

## Related

- [INFRA-DATA-STORES.md](INFRA-DATA-STORES.md)
- [PROJECT-PLAN.md](PROJECT-PLAN.md) P3-05
