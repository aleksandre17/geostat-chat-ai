# Infra compose — Postgres, Redis, Qdrant, RabbitMQ

არა Java აპები — მხოლოდ official Docker images.  
**Driver:** `kits/geostat-kit/toolkit/infra/Invoke-Infra.ps1` (`geostat infra …`).  
გეგმა: [docs/plan/INFRA-DATA-STORES.md](../../docs/plan/INFRA-DATA-STORES.md) · [DOCKER-ECOSYSTEM.md](../../docs/plan/DOCKER-ECOSYSTEM.md)

## Setup (ერთხელ)

```powershell
copy ops\config\infra\.env.example ops\config\infra\.env.dev
copy ops\config\infra\.env.deploy.example ops\config\infra\.env.deploy
# დაარედაქტირე POSTGRES_PASSWORD
```

`ops/config/deploy.env` — `DEPLOY_SERVER`, optional `DOCKER_NETWORK=geostat-chat-ai-net`.

## ბრძანებები

| ბრძანება | რა |
|----------|-----|
| `.\tools\geostat.ps1 infra prereqs` | SSH: network + docker check (kit) |
| `.\tools\geostat.ps1 infra local up` | ლოკალური Docker (laptop) |
| `.\tools\geostat.ps1 infra local down` | გაჩერება |
| `.\tools\geostat.ps1 infra remote sync` | rsync compose → სერვერი |
| `.\tools\geostat.ps1 infra remote up` | sync + compose up სერვერზე |
| `.\tools\geostat.ps1 infra remote down` | compose down (მხოლოდ ამ `INFRA_SLUG`) |
| `.\tools\geostat.ps1 infra remote purge -Confirm` | down -v — volumes ამ პროექტისთვის |
| `.\tools\geostat.ps1 infra remote up -Prod` | prod overlay |
| `.\tools\geostat.ps1 infra tunnel` | ssh -L only for `stack.infra.services` (see infra-catalog) |

## სერვერის ხე

გლობალური root: `/home/administrator/geostat/` (`DEPLOY_PROJECT=geostat`).  
**ყოველ consumer repo-ს საკუთარი ინფრა:** `infra/<INFRA_SLUG>/` (D-11).

```text
/home/administrator/geostat/
  frontend/ | backend/     ← shared bases (ყველა პროექტი)
  infra/
    geostat-chat-ai/      ← THIS repo (DEPLOY_PATH)
      .env.runtime
      compose/
        docker-compose.yml
        docker-compose.prod.yml
```

`ops/config/infra/.env.deploy` — `INFRA_SLUG=geostat-chat-ai`,  
`DEPLOY_PATH=/home/administrator/geostat/infra/geostat-chat-ai`.

**რომელი store გაეშვას:** მხოლოდ `geostat.ops.json` → `stack.infra.services` (მაგ. `["postgres"]`). Compose: `docker-compose.base.yml` + `services/*.yml`. Kit: `kits/geostat-kit/docs/PACKAGE-PRINCIPLES.md`.

სრული ახსნა: [docs/plan/SERVER-DEPLOY-LAYOUT.md](../../docs/plan/SERVER-DEPLOY-LAYOUT.md).  
**არა** shared `geostat/infra/compose/` ყველასთვის. **არა** JAR.

### მეორე პროექტი იგივე სერვერზე

სხვა repo-ში: სხვა `INFRA_SLUG`, `DEPLOY_PATH`, `INFRA_PREFIX`, `DOCKER_NETWORK`, პორტები (მაგ. 5433, 6380, 6335).

## ქსელი

ყველა სერვისი: `geostat-chat-ai-net` (იგივე რაც `ops/compose/stack`).  
App compose — `external: true` (P6).

## პორტები (host bind)

მხოლოდ `127.0.0.1` — hybrid tunnel/LAN, არა საჯარო ინტერნეტი.

| სერვისი | პორტი |
|---------|-------|
| Postgres | 5432 |
| Redis | 6379 |
| Qdrant HTTP | 6333 |
| RabbitMQ AMQP | 5672 |
