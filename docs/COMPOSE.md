# Docker Compose — არქიტექტურა

## სტრუქტურა

```
geostat-chat-ai/
├── ops/config/                    # env — არა compose
├── tools/geostat.ps1           # ერთი CLI → kits/geostat-kit
├── kits/geostat-kit/       # compose-gen, drivers, stack toolkit
├── ops/compose/stack/             # FULL STACK (generated YAML)
├── apps/frontend/                   # მოდული UI + ops.config.ps1
└── apps/backend/                    # მოდული API + ops.config.sh
```

## Catalog და bootstrap

`ops/compose/catalog.json` — ერთადერთი წყარო compose-ისთვის. ახალი პროექტში: `geostat init` ჩააგდებს `catalog.full.json`-ს (ან `-MinimalCatalog`). დეტალი: [GEOSTAT-INIT.md](GEOSTAT-INIT.md).

## როდის რა გამოიყენოთ

| სცენარი | ბრძანება |
|---------|----------|
| **ორივე სერვისი (dev)** | `.\tools\geostat.ps1 stack up -d --build` |
| **ორივე (prod) local** | `.\tools\geostat.ps1 stack -Prod up -d --build` |
| მხოლოდ UI | `.\tools\geostat.ps1 fe compose up -d` |
| მხოლოდ API | `.\tools\geostat.ps1 be compose up --build` |
| UI deploy სერვერზე | `.\tools\geostat.ps1 fe deploy dist -Environment prod` |
| API deploy სერვერზე | `.\tools\geostat.ps1 be deploy all --prod` |

## პრინციპები

1. **Compose** მოდულის root-ში — `build.context` = Dockerfile.
2. **Stack compose** — `ops/compose/stack/`, paths `../../frontend`, `../../backend`.
3. **Secrets** — `ops/config/`; stack იტვირთავს ორივე მოდულის `.env.*`.
4. **Env ხელწერა** — [ENV.md](ENV.md): `.env.dev` / `.env.prod`.
5. **ქსელი** — `DOCKER_NETWORK` in `ops/config/deploy.env` (default: `{DEPLOY_PROJECT}-net`).

## Env ↔ Compose

```yaml
env_file:
  - ../../ops/config/backend/.env.dev
```

`${API_PORT}`, `${DEPLOY_HOST_PORT}` — `geostat stack` / `geostat fe|be compose` იტვირთავს env-ს.

---

დეტალი: [ops/compose/stack/README.md](../ops/compose/stack/README.md) | [tools/README.md](../tools/README.md) | [frontend/DOCKER.md](../frontend/DOCKER.md) | [backend/DOCKER.md](../backend/DOCKER.md)
