# Backend — Docker Compose

## ფაილები

| ფაილი | როლი |
|--------|------|
| `docker-compose.dev.yml` | **dev** — სრული სერვისი (Dockerfile.dev) |
| `docker-compose.prod.yml` | **prod** — სრული სერვისი (deploy-ზე თვითიყოფა) |

Env: `ops/config/backend/.env.dev` | `.env.prod` (+ `ops/config/deploy.env`). Map: [../../../docs/CONFIG.md](../../../docs/CONFIG.md)

## ბრძანებები (Git Bash / WSL)

```bash
cd backend

# Dev API
./tools/geostat.sh be compose up --build

# Prod-like local
./tools/geostat.sh be compose -Prod up -d --build

# Validate
./tools/geostat.sh be compose config
```

## ცვლადები

| Key | ფაილი |
|-----|--------|
| `API_PORT`, `GEMINI_*`, `GCP_*`, … | `.env.dev` / `.env.prod` |
| `DEPLOY_SERVER` | `ops/config/deploy.env` |
| GCP key file | `ops/config/backend/google-credentials.json` |

## Deploy

`tools/geostat.ps1 be deploy` — ატვირთავს `docker-compose.<env>.yml` სერვერზე (სრული ფაილი, include-ის გარეშე).
