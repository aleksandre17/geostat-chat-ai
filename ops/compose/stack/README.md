# Full-stack Compose (API + UI)



ორკესტრაცია **მხოლოდ აქ**. Build context და compose მოდულებში (`apps/frontend/`, `apps/backend/`) **არ ირღვევა**.



## ფაილები



| ფაილი | გარემო |

|--------|--------|

| `docker-compose.yml` | **dev** — Vite + Spring dev |

| `docker-compose.prod.yml` | **prod** — nginx + Spring prod (standalone) |



## Env



იტვირთება ავტომატურად:



- `ops/config/backend/.env.dev` | `.env.prod`

- `ops/config/frontend/.env.dev` | `.env.prod`

- `ops/config/deploy.env`



## გაშვება



```powershell

# repo root

.\tools\geostat.ps1 stack up -d --build

```



Prod:



```powershell

.\tools\geostat.ps1 stack -Prod up -d --build

```



## URLs (dev)



| სერვისი | მისამართი |

|---------|----------|

| UI | http://localhost:5177 (ან `DEPLOY_HOST_PORT`) |

| API | http://localhost:8090 (ან `API_PORT`) |



ბრაუზერი იყენებს `VITE_API_URL` — ნაგულისხმევი `http://localhost:8090` (`ops/config/frontend/.env.dev`).



## ქსელი



ორივე სერვისი: ქსელი `DOCKER_NETWORK` from `ops/config/deploy.env` (generated compose).



## მოდულური compose (ცალკე)



| მოდული | ბრძანება |

|--------|----------|

| მხოლოდ UI | `.\tools\geostat.ps1 fe compose up -d` |

| მხოლოდ API | `.\tools\geostat.ps1 be compose up --build` |



იხილეთ [../../docs/COMPOSE.md](../../docs/COMPOSE.md)

