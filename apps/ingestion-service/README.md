# ingestion-service

Crawl → parse → chunk → embed → index pipeline. **Postgres schema `ingestion`** (Flyway) — owner of crawl/pipeline tables.

## Run

```powershell
# Health only (no Postgres — CI default)
cd apps/ingestion-service
..\..\apps\backend\gradlew.bat bootRun

# With Postgres (hybrid): tunnel + ops/config/ingestion/.env.dev
# SPRING_PROFILES_ACTIVE=dev,hybrid
..\..\apps\backend\gradlew.bat bootRun
```

- Health: `http://localhost:8093/actuator/health`
- Corpora (db profile): `GET /api/v1/ingestion/corpora`
- Jobs (db profile): `POST /api/v1/ingestion/jobs`, `GET /api/v1/ingestion/jobs/{id}`

Example job:

```json
POST /api/v1/ingestion/jobs
{ "corpusName": "geostat-portal", "seedUrl": null, "fullRecrawl": false }
```

## Database

| Item | Value |
|------|--------|
| Schema | `ingestion` |
| Migrations | `src/main/resources/db/migration/` |
| Config | `ops/config/ingestion/.env.dev` + `ops/config/infra/` |
| Profiles | `hybrid` (host + tunnel), `docker` (compose DNS `postgres`) |

## Package layout (logic-based)

```text
com.geostat.ingestion/
├── api/                    HTTP controllers
├── config/                 Spring configuration
├── persistence/
│   ├── entity/             JPA entities
│   ├── model/              status enums
│   └── repository/         Spring Data
├── crawl/
│   ├── job/                crawl job API orchestration
│   ├── runner/             async run loop + PG store
│   ├── fetch/              crawler4j PageFetcher + robots; Jsoup parse
│   ├── frontier/           URL hash + link discovery
│   └── policy/             corpus policy from JSON
└── parse/                  HTML → clean text (P3-02)
└── chunk/                  text → DB chunks (P3-03)
    └── strategy/           chunking algorithms
```

See [docs/plan/INGESTION-DATA-MODEL.md](../../docs/plan/INGESTION-DATA-MODEL.md).
