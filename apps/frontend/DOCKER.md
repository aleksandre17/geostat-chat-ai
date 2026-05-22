# Frontend — Docker Compose

## ფაილები

| ფაილი | გარემო |
|--------|--------|
| `docker-compose.yml` | ბაზა (build, image) |
| `docker-compose.override.yml` | **dev** (ავტომატურად) |
| `docker-compose.prod.yml` | **prod** (overlay) |

Env: `ops/config/frontend/.env.dev` | `.env.prod` | `.env.deploy` + `ops/config/deploy.env`

## ბრძანებები

```powershell
cd frontend

# Dev (Vite in container, port DEPLOY_HOST_PORT)
.\tools\geostat.ps1 fe compose up -d

# Prod image (nginx)
.\tools\geostat.ps1 fe compose -Prod up -d --build

# Validate
.\tools\geostat.ps1 fe compose config
.\tools\geostat.ps1 fe compose -Prod config
```

## ცვლადები

| Key | ფაილი |
|-----|--------|
| `VITE_API_URL` | `.env.dev` / `.env.prod` |
| `DEPLOY_SERVER` | `ops/config/deploy.env` |
| `DEPLOY_HOST_PORT`, `DEPLOY_PATH` | `.env.deploy` |

## Deploy

`ps1/deploy.ps1` — ცალკე ლოგიკა (local/dist/remote/sync). Remote compose იყენებს `HOST_PORT` export-ს სერვერზე.
