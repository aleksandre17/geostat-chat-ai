# Documentation

სწრაფი დაწყება: [README](../README.md) (repo root).

| Doc | Topic |
|-----|--------|
| **[PROJECT-OVERVIEW.md](PROJECT-OVERVIEW.md)** | **სრული overview** — vision, pipeline, სტრუქტურა, done/remaining (GitHub) |
| **[plan/PROJECT-PLAN.md](plan/PROJECT-PLAN.md)** | **პროექტის გეგმა** — დამტკიცებული ფაზები, სტატუსები, backlog |
| **[plan/README.md](plan/README.md)** | როგორ ვამტკიცებთ / ვამატებთ გზად-გზა |
| **[ARCHITECTURE-B-SERVICES.md](ARCHITECTURE-B-SERVICES.md)** | Architecture B — ცალკე deployable სერვისები |
| **[ROOT-LAYOUT.md](ROOT-LAYOUT.md)** | **Root სტრუქტურა** — `apps/` + `kits/` + `ops/` (ADR 008) |
| **[MIGRATION-MAP.md](MIGRATION-MAP.md)** | **გადაწყობის რუკა** — სად იყო → სად არის ახლა |
| **[KITS-PACKAGE.md](KITS-PACKAGE.md)** | **`kits/geostat-kit` პაკეტის ჩატვირთვა** (submodule / copy) |
| **[kits/geostat-kit/docs/ADOPTION.md](../kits/geostat-kit/docs/ADOPTION.md)** | **v1.0.0 — გავრცელება**, რა გავაკეთეთ, adoption |
| **[CONFIG.md](CONFIG.md)** | **კონფიგის რუკა** — secrets, Spring, Vite, `tools/` / `scripts/` / `geostat-kit` |
| **[CI.md](CI.md)** | **`ops/ci/integration-stack.sh`** — manifest-driven compose smoke |
| **Kit maturity** | [kits/geostat-kit/docs/MATURITY.md](../kits/geostat-kit/docs/MATURITY.md) — 100% checklist |
| **New project** | [kits/geostat-kit/docs/STARTER.md](../kits/geostat-kit/docs/STARTER.md) |
| **დევ რეჟიმები** | [kits/geostat-kit/docs/DEV-MODES.md](../kits/geostat-kit/docs/DEV-MODES.md) — ლოკალური / Docker / remote |
| **Run and Debug** | [kits/geostat-kit/docs/LOCAL-DEBUG.md](../kits/geostat-kit/docs/LOCAL-DEBUG.md) — `geostat vscode-gen` |
| **[GEOSTAT-INIT.md](GEOSTAT-INIT.md)** | **`geostat init`** — სრული bootstrap (scaffold + seed + compose-gen) |
| [GEOSTAT-KIT-SETUP.md](GEOSTAT-KIT-SETUP.md) | სრული ops + deploy სტრუქტურა |
| [ARCHITECTURE.md](ARCHITECTURE.md) | ფენები, deploy, CLI, **Golden paths** |
| [ENV.md](ENV.md) | env ცვლადების ხელწერა |
| [ENVIRONMENT.md](ENVIRONMENT.md) | პრაქტიკა: legacy, worker, nginx, embed |
| [COMPOSE.md](COMPOSE.md) | Docker Compose სტრუქტურა |
| [MULTI-MODULE.md](MULTI-MODULE.md) | Backend მრავალმოდულიანი deploy |
| [MONOREPO.md](MONOREPO.md) | ოფციური git root-ის გაერთიანება |
| [CONTRIBUTING.md](CONTRIBUTING.md) | PR-ის წინ სია |
| [adr/](adr/) | Architecture Decision Records |

სხვა:

- [ops/config/README.md](../ops/config/README.md)
- [ops/cli/README.md](../ops/cli/README.md)
- [tools/README.md](../tools/README.md)
- [kits/geostat-kit/README.md](../kits/geostat-kit/README.md)
- **[kits/geostat-kit/docs/ADOPTION-LINE.md](../kits/geostat-kit/docs/ADOPTION-LINE.md)** — სრული ops პაკეტის კონფიგურაციის ხაზი
- **[kits/geostat-kit/docs/GOLDEN-PATHS.md](../kits/geostat-kit/docs/GOLDEN-PATHS.md)** — golden paths, Linux-ზე მუშაობა, watch vs deploy watch
- **[kits/geostat-kit/docs/REMOTE-DEV-DOCKERFILE-FLOW.md](../kits/geostat-kit/docs/REMOTE-DEV-DOCKERFILE-FLOW.md)** — `fe dev bootstrap/watch` + `apps/frontend/src/Dockerfile`
- [FRONTEND-DEPLOY-LAYOUTS.md](FRONTEND-DEPLOY-LAYOUTS.md) — UI deploy ვარიანტები, სიმულაცია, ხარვეზები
- [FE-WATCH.md](FE-WATCH.md) — `fe deploy watch` vs `fe dev watch` (არ აურიოთ)
- [DEV-REMOTE.md](DEV-REMOTE.md) — Windows → Linux SPA dev (rsync + compose, Vite/Angular)
- [FRONTEND-LAYOUT-SIMULATION-FULL.md](FRONTEND-LAYOUT-SIMULATION-FULL.md) — ყველა ვარიანტის სრული ხე + Docker start/update
