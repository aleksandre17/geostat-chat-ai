# Environment — ცვლადების ხელწერა

**სრული რუკა (ფაილები + ვინ კითხულობს):** [CONFIG.md](CONFIG.md)

Frontend და backend **იგივე სახელებს** იყენებენ:

| ფაილი | Frontend | Backend |
|--------|----------|---------|
| `.env.dev` | Vite dev, Docker dev | Spring Docker dev |
| `.env.prod` | Vite prod build | Spring Docker prod |
| `.env.deploy` | DEPLOY_PATH, port | (სურვილისამებრ) |
| `deploy.env` (root) | `DEPLOY_SERVER`, `DEPLOY_PROJECT`, `DOCKER_NETWORK`, … | same |

### SSH keys

**Not in git.** Setup: [ops/config/ssh/README.md](../ops/config/ssh/README.md) (`~/.ssh` or `ops/config/ssh/id_ed25519`).

### `ops/config/deploy.env` (shared identity)

| Key | Purpose |
|-----|---------|
| `DEPLOY_SERVER` | SSH `user@host` (or Host alias from `ops/config/ssh/config`) |
| `DEPLOY_SSH_IDENTITY_FILE` | Optional path to private key (e.g. `ops/config/ssh/id_ed25519`) |
| `DEPLOY_SSH_CONFIG_FILE` | Optional `ssh -F` config (e.g. `ops/config/ssh/config`) |
| `DEPLOY_PROJECT` | Remote folder slug (default: repo folder name) |
| `DEPLOY_SERVER_BASE` | e.g. `/home/user` (else derived from SSH user) |
| `COMPOSE_PROJECT_NAME` | Docker compose `name:` (default: repo folder) |
| `DOCKER_NETWORK` | External network (default: `{slug}-net`) |
| *(service names)* | From `geostat.ops.json` `modules.*` — run `geostat compose-gen`; see `python kits/geostat-kit/lib/modules_cli.py compose-names` |
| `COMPOSE_API_SERVICE` / `COMPOSE_APP_SERVICE` / `COMPOSE_WORKER_SERVICE` | **Legacy only** — override primary api/ui/worker when merging `profiles/legacy-server.env.example` |
| `API_PORT` / `DEPLOY_HOST_PORT` | Local ports |
| `OPS_BUILD_TMP_PREFIX` | Local Gradle log prefix under `/tmp` |
| `OPS_MONOREPO_ROOT` | Override monorepo discovery |
| `WORKER_PORT` | Worker container port (default 8091) |
| `API_INTERNAL_URL` | Worker → API over Docker DNS (`http://<api-service>:8090`) |
| `COMPOSE_WORKER_SERVICE` | Legacy override for primary `worker` role module name |

```
ops/config/
├── deploy.env
├── apps/frontend/.env.{dev,prod,deploy}
└── apps/backend/.env.{dev,prod} + google-credentials.json
```

## სკრიპტები

| მოქმედება | ბრძანება |
|-----------|----------|
| **Full stack dev** | `.\tools\geostat.ps1 stack up -d --build` |
| **Full stack prod** | `.\tools\geostat.ps1 stack -Prod up -d --build` |
| Vite dev (host) | `cd frontend && npm run dev` |
| UI Docker only | `.\tools\geostat.ps1 fe compose up -d` |
| API Docker only | `.\tools\geostat.ps1 be compose up --build` |
| Deploy UI | `.\tools\geostat.ps1 fe deploy dist -Environment prod` |

დეტალი: [CONFIG.md](CONFIG.md) | [ops/config/README.md](../ops/config/README.md) | [ENVIRONMENT.md](ENVIRONMENT.md) | [COMPOSE.md](COMPOSE.md) | [ops/compose/README.md](../ops/compose/README.md)
