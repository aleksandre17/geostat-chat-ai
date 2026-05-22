# Frontend deploy - full layout simulation

> Dry-run from `ops/config/` + `geostat.ops.json`. Regenerate: geostat layout --frontend -Markdown -OutFile docs/FRONTEND-LAYOUT-SIMULATION-FULL.md

## Resolved paths (this project)

| Key | Path |
|-----|------|
| SSH | `administrator@192.168.1.199` |
| DEPLOY_PATH (base) | `/home/administrator/geostat/frontend` |
| Static (B1/B2/B3) | `/home/administrator/geostat/frontend/static/geostat-chat-ai-app` |
| Compose dev (D/C1) | `/home/administrator/geostat/frontend/compose/dev/geostat-chat-ai-app` |
| Compose prod (C2) | `/home/administrator/geostat/frontend/compose/prod/geostat-chat-ai-app` |
| Staging archives | `/home/administrator/geostat/deploy-staging` |
| Legacy flat | `/home/administrator/geostat/frontend/geostat-chat-ai-app` |
| Local repo UI | `C:\Users\Test-User\CursorProjects\geostat-chat-ai\frontend` |
| Container name | `geostat-chat-ai-app` |

---

## A1 - Vite dev server (host, no Docker)

| | |
|---|---|
| Command | `cd frontend && npm run dev` |
| Host | (localhost) |
| Root | `C:\Users\Test-User\CursorProjects\geostat-chat-ai\frontend` |
| Env | ops/config/frontend/.env.dev |
| Docker | none - Node on host :5173 (vite default) or vite.config |
| Runtime | Hot reload; VITE_API_URL from .env.dev |

### Directory tree

```
+-- C:\Users\Test-User\CursorProjects\geostat-chat-ai
    |-- C:\Users\Test-User\CursorProjects\geostat-chat-ai\secrets
    |   |-- deploy.env  # DEPLOY_SERVER, DEPLOY_PROJECT
    |   +-- apps/frontend/
    |       |-- .env.deploy  # DEPLOY_PATH, DEPLOY_LAYOUT, DEPLOY_HOST_PORT
    |       |-- .env.dev  # VITE_API_URL, dev profile
    |       |-- .env.prod  # prod profile
    |       +-- nginx.env  # optional, nginx-gen
    |-- C:\Users\Test-User\CursorProjects\geostat-chat-ai\frontend
    |   |-- src/
    |   |   |-- main.jsx
    |   |   |-- index.css
    |   |   |-- config/
    |   |   |   +-- api.js
    |   |   |-- components/
    |   |   |   +-- chatbot/
    |   |   |       |-- ChatWidget.jsx
    |   |   |       |-- ChatMessage.jsx
    |   |   |       |-- VoiceInputButton.jsx
    |   |   |       +-- logo.svg
    |   |   |-- hooks/
    |   |   |   +-- useChatTiers.jsx
    |   |   |-- i18n/
    |   |   |   |-- translations.js
    |   |   |   +-- LanguageContext.jsx
    |   |   +-- Dockerfile  # targets: deps, development, builder, production
    |   |-- public/
    |   |-- package.json
    |   |-- package-lock.json
    |   |-- vite.config.js  # if present
    |   |-- nginx.conf.template
    |   |-- nginx.conf  # generated: geostat nginx-gen
    |   |-- docker-compose.yml  # GENERATED
    |   |-- docker-compose.override.yml  # dev: volume .:/app, develop.watch
    |   |-- docker-compose.prod.yml  # prod overlay
    |   |-- ops.config.ps1
    |   +-- logs/
    |       |-- deploy-*.log
    |       |-- watch-*.log
    |       +-- dev-*.log
    +-- node_modules/  # local npm ci, not on server
```

### Docker - start (bootstrap)
- 1. (optional) cd frontend && npm ci
- 2. npm run dev  â†’  vite --mode dev
- 3. Browser â†’ http://localhost:5173 (or configured port)

### Docker - update (inner loop)
- Save src/** â†’ Vite HMR instant (no Docker, no geostat)

### Gaps / notes
- Port may differ from DEPLOY_HOST_PORT=5177 used in Docker modes.

### Recommendation
- Fastest inner loop when API is reachable from Windows.

---

## A2 - Docker compose - dev (module)

| | |
|---|---|
| Command | `.\tools\geostat.ps1 fe compose up -d` |
| Host | (localhost) |
| Root | `C:\Users\Test-User\CursorProjects\geostat-chat-ai\frontend` |
| Env | ops/config/frontend/.env.dev; ops/config/deploy.env |
| Docker | image geostat-chat-ai-app, target development, -p :5177 |
| Runtime | Vite in container; compose develop.watch on ./src |

### Directory tree

```
+-- C:\Users\Test-User\CursorProjects\geostat-chat-ai\frontend
    |-- C:\Users\Test-User\CursorProjects\geostat-chat-ai\frontend
    |   |-- src/
    |   |   |-- main.jsx
    |   |   |-- index.css
    |   |   |-- config/
    |   |   |   +-- api.js
    |   |   |-- components/
    |   |   |   +-- chatbot/
    |   |   |       |-- ChatWidget.jsx
    |   |   |       |-- ChatMessage.jsx
    |   |   |       |-- VoiceInputButton.jsx
    |   |   |       +-- logo.svg
    |   |   |-- hooks/
    |   |   |   +-- useChatTiers.jsx
    |   |   |-- i18n/
    |   |   |   |-- translations.js
    |   |   |   +-- LanguageContext.jsx
    |   |   +-- Dockerfile  # targets: deps, development, builder, production
    |   |-- public/
    |   |-- package.json
    |   |-- package-lock.json
    |   |-- vite.config.js  # if present
    |   |-- nginx.conf.template
    |   |-- nginx.conf  # generated: geostat nginx-gen
    |   |-- docker-compose.yml  # GENERATED
    |   |-- docker-compose.override.yml  # dev: volume .:/app, develop.watch
    |   |-- docker-compose.prod.yml  # prod overlay
    |   |-- ops.config.ps1
    |   +-- logs/
    |       |-- deploy-*.log
    |       |-- watch-*.log
    |       +-- dev-*.log
    +-- (Docker)
        |-- container: geostat-chat-ai-app
        |-- mount: C:\Users\Test-User\CursorProjects\geostat-chat-ai\frontend -> /app
        |-- mount: anonymous /app/node_modules
        +-- CMD: npm run dev -- --host 0.0.0.0
```

### Docker - start (bootstrap)
- 1. geostat compose-gen (if needed)
- 2. cd frontend; docker compose -f docker-compose.yml -f docker-compose.override.yml up -d --build
- 3. Container: development stage, port 5177:5177

### Docker - update (inner loop)
- Edit host frontend/src/** â†’ volume reflects immediately
- compose develop.watch: sync src/ â†’ /app/src in container
- package.json change â†’ watch action rebuild image

### Gaps / notes
- GENERATED yaml - run geostat compose-gen after catalog change.

### Recommendation
- Parity test before D1 remote dev.

---

## A3 - Docker compose - prod overlay (local smoke)

| | |
|---|---|
| Command | `.\tools\geostat.ps1 fe compose -Prod up -d --build` |
| Host | (localhost) |
| Root | `C:\Users\Test-User\CursorProjects\geostat-chat-ai\frontend` |
| Env | ops/config/frontend/.env.prod; ops/config/deploy.env |
| Docker | target production (nginx in image), build-arg VITE_API_URL |
| Runtime | dist inside image at /usr/share/nginx/html |

### Directory tree

```
+-- C:\Users\Test-User\CursorProjects\geostat-chat-ai\frontend
    |-- docker-compose.yml
    |-- docker-compose.prod.yml
    +-- (image interior)
        |-- usr/share/nginx/html/
        |   |-- index.html
        |   +-- assets/...
        +-- etc/nginx/conf.d/default.conf
```

### Docker - start (bootstrap)
- 1. docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
- 2. Stages: deps â†’ builder (npm run build) â†’ production (nginx)

### Docker - update (inner loop)
- Code change â†’ must rebuild image (--build); no host dist/ sync

### Gaps / notes
- Not the same tree as B1 (host-built dist on server).

### Recommendation
- Smoke-test Dockerfile production stage.

---

## A4 - Full stack - API + UI (ops/compose/stack)

| | |
|---|---|
| Command | `.\tools\geostat.ps1 stack up -d --build` |
| Host | (localhost) |
| Root | `C:\Users\Test-User\CursorProjects\geostat-chat-ai\deploy\compose` |
| Env | ops/config/frontend/.env.dev; ops/config/backend/.env.dev; ops/config/deploy.env |
| Docker | ops/compose/stack/docker-compose.yml + network geostat-net |
| Runtime | UI depends_on API health |

### Directory tree

```
+-- C:\Users\Test-User\CursorProjects\geostat-chat-ai
    |-- ops/compose/stack/
    |   |-- docker-compose.yml  # build context ../../backend, ../../frontend
    |   +-- docker-compose.prod.yml
    |-- apps/backend/
    |   |-- docker-compose.dev.yml
    |   |-- src/...
    |   +-- build/libs/*.jar
    +-- C:\Users\Test-User\CursorProjects\geostat-chat-ai\frontend
        |-- src/
        |   |-- main.jsx
        |   |-- index.css
        |   |-- config/
        |   |   +-- api.js
        |   |-- components/
        |   |   +-- chatbot/
        |   |       |-- ChatWidget.jsx
        |   |       |-- ChatMessage.jsx
        |   |       |-- VoiceInputButton.jsx
        |   |       +-- logo.svg
        |   |-- hooks/
        |   |   +-- useChatTiers.jsx
        |   |-- i18n/
        |   |   |-- translations.js
        |   |   +-- LanguageContext.jsx
        |   +-- Dockerfile  # targets: deps, development, builder, production
        |-- public/
        |-- package.json
        |-- package-lock.json
        |-- vite.config.js  # if present
        |-- nginx.conf.template
        |-- nginx.conf  # generated: geostat nginx-gen
        |-- docker-compose.yml  # GENERATED
        |-- docker-compose.override.yml  # dev: volume .:/app, develop.watch
        |-- docker-compose.prod.yml  # prod overlay
        |-- ops.config.ps1
        +-- logs/
            |-- deploy-*.log
            |-- watch-*.log
            +-- dev-*.log
```

### Docker - start (bootstrap)
- 1. geostat stack up -d --build
- 2. Backend container(s) + frontend geostat-chat-ai-app on shared network

### Docker - update (inner loop)
- Module rebuild via compose; not SSH module paths

### Gaps / notes
- Different roots than server static/compose trees.

### Recommendation
- Integration / E2E on laptop.

---

## A5 - deploy local - production image on laptop

| | |
|---|---|
| Command | `.\tools\geostat.ps1 fe deploy local -Environment dev|prod` |
| Host | (localhost) |
| Root | `C:\Users\Test-User\CursorProjects\geostat-chat-ai\frontend` |
| Env | ops/config/frontend/.env.{profile}; VITE_API_URL, DEPLOY_HOST_PORT |
| Docker | docker build --target production; docker run -p HOST_PORT:80 |
| Runtime | Container name = folder leaf (frontend) - not geostat-chat-ai-app |

### Directory tree

```
+-- (local Docker only)
    |-- image: frontend  # tag from folder name
    |-- container: frontend
    |-- port: DEPLOY_HOST_PORT:80
    +-- C:\Users\Test-User\CursorProjects\geostat-chat-ai\frontend\logs
        +-- deploy-*-local-*.log
```

### Docker - start (bootstrap)
- 1. geostat nginx-gen
- 2. docker build --target production --build-arg VITE_API_URL=... -t frontend
- 3. docker run -d --name frontend -p 5177:80 frontend

### Docker - update (inner loop)
- Re-run deploy local â†’ stop/rm, rebuild image, run again

### Gaps / notes
- Container name differs from compose (geostat-chat-ai-app).

### Recommendation
- Rare; use A2 or B1 instead.

---

## B1 - deploy dist (dev or prod profile)

| | |
|---|---|
| Command | `.\tools\geostat.ps1 fe deploy dist -Environment dev|prod` |
| Host | administrator@192.168.1.199 |
| Root | `/home/administrator/geostat/frontend/static/geostat-chat-ai-app` |
| Env | ops/config/frontend/.env.{profile}; .env.deploy; server: .env.runtime.json |
| Docker | nginx:1.27-alpine; volumes dist + nginx.conf; -p HOST_PORT:80 |
| Runtime | Static SPA; container geostat-chat-ai-app |

### Directory tree

```
+-- administrator@192.168.1.199
    |-- /home/administrator/geostat/deploy-staging
    |   +-- (none for dist)  # tar.gz from deploy remote; removed after extract
    |-- /home/administrator/geostat/frontend/static/geostat-chat-ai-app
    |   |-- .geostat-deploy.json  # manifest: kind=static, deployMode
    |   |-- .env.runtime.json  # VITE_API_URL, environment, deployedAt
    |   |-- nginx.conf  # from nginx-gen / module root
    |   +-- dist/
    |       |-- index.html
    |       |-- assets/
    |       |   |-- index-[hash].js
    |       |   |-- index-[hash].css
    |       |   +-- ...  # chunks, fonts, images
    |       +-- config.json  # VITE_API_URL runtime
    +-- (Docker)
        |-- container: geostat-chat-ai-app
        |-- volume: /home/administrator/geostat/frontend/static/geostat-chat-ai-app/dist -> /usr/share/nginx/html:ro
        +-- volume: /home/administrator/geostat/frontend/static/geostat-chat-ai-app/nginx.conf -> /etc/nginx/conf.d/default.conf:ro
```

### Docker - start (bootstrap)
- 1. Windows: npm run build or build:dev (profile)
- 2. Write frontend/dist/config.json
- 3. scp -r dist/ + nginx.conf + .env.runtime.json -> /home/administrator/geostat/frontend/static/geostat-chat-ai-app/
- 4. ssh: docker stop/rm geostat-chat-ai-app; docker run -d -p 5177:80 -v .../dist -v .../nginx.conf nginx:1.27-alpine

### Docker - update (inner loop)
- Full redeploy dist â†’ rebuild npm, re-scp entire dist/, recreate container

### Gaps / notes
- Legacy flat path may exist: /home/administrator/geostat/frontend/geostat-chat-ai-app

### Recommendation
- Default production/staging static UI on server.

---

## B2 - deploy sync (+ nginx reload)

| | |
|---|---|
| Command | `.\tools\geostat.ps1 fe deploy sync -Environment dev|prod` |
| Host | administrator@192.168.1.199 |
| Root | `/home/administrator/geostat/frontend/static/geostat-chat-ai-app` |
| Env | same as B1 |
| Docker | same container as B1; nginx -s reload or restart |
| Runtime | Invoke-FeStaticPublishCycle: build + scp + reload |

### Directory tree

```
+-- /home/administrator/geostat/frontend/static/geostat-chat-ai-app
    |-- .geostat-deploy.json  # manifest: kind=static, deployMode
    |-- .env.runtime.json  # VITE_API_URL, environment, deployedAt
    |-- nginx.conf  # from nginx-gen / module root
    +-- dist/
        |-- index.html
        |-- assets/
        |   |-- index-[hash].js
        |   |-- index-[hash].css
        |   +-- ...  # chunks, fonts, images
        +-- config.json  # VITE_API_URL runtime
```

### Docker - start (bootstrap)
- (assumes B1 container already running)

### Docker - update (inner loop)
- 1. Windows: npm build (profile)
- 2. scp dist/, nginx.conf, .env.runtime.json
- 3. ssh: docker exec geostat-chat-ai-app nginx -s reload || docker restart

### Recommendation
- Quick prod-like UI patch without recreating container.

---

## B3 - deploy watch - static dist loop (NOT dev watch)

| | |
|---|---|
| Command | `.\tools\geostat.ps1 fe deploy watch -Environment dev|prod` |
| Host | administrator@192.168.1.199 |
| Root | `/home/administrator/geostat/frontend/static/geostat-chat-ai-app` |
| Env | same as B1 |
| Docker | same as B2 on each debounced save |
| Runtime | FileSystemWatcher on src/ â†’ repeat B2 cycle |

### Directory tree

```
+-- Windows watch roots
    |-- C:\Users\Test-User\CursorProjects\geostat-chat-ai\frontend\src\
    |   |-- components/chatbot/*.jsx
    |   +-- main.jsx
    +-- â†’ npm build â†’ scp -> /home/administrator/geostat/frontend/static/geostat-chat-ai-app/dist/
```

### Docker - start (bootstrap)
- Requires prior B1 dist deploy (container up)

### Docker - update (inner loop)
- Each save (3s debounce): npm build + scp dist + nginx reload

### Gaps / notes
- Heavy - use D2 for source-only remote dev.

### Recommendation
- Prod-like auto-publish from Windows.

---

## C1 - deploy remote dev (tar + server build)

| | |
|---|---|
| Command | `.\tools\geostat.ps1 fe deploy remote -Environment dev` |
| Host | administrator@192.168.1.199 |
| Root | `/home/administrator/geostat/frontend/compose/dev/geostat-chat-ai-app` |
| Env | server: .env.dev (uploaded bundle); deploy.env |
| Docker | compose up -d --build on server |
| Runtime | Heavy; prefer D1 rsync for daily loop |

### Directory tree

```
+-- administrator@192.168.1.199
    |-- /home/administrator/geostat/deploy-staging
    |   +-- geostat-chat-ai-app_deploy_dev.tar.gz  # tar.gz from deploy remote; removed after extract
    +-- /home/administrator/geostat/frontend/compose/dev/geostat-chat-ai-app
        |-- .geostat-deploy.json  # kind=compose-dev
        |-- .env.dev  # merged secrets upload
        |-- docker-compose.yml
        |-- docker-compose.override.yml
        |-- package.json
        |-- package-lock.json
        |-- src/
        |   |-- main.jsx
        |   |-- components/chatbot/...
        |   +-- Dockerfile
        |-- public/
        +-- node_modules/  # inside container volume, not rsynced from Windows
```

### Docker - start (bootstrap)
- 1. tar.gz apps/frontend/ (excl node_modules, dist, .git)
- 2. scp archive -> /home/administrator/geostat/deploy-staging/
- 3. ssh: tar -xzf -> /home/administrator/geostat/frontend/compose/dev/geostat-chat-ai-app; scp .env.dev
- 4. docker compose -f docker-compose.yml -f docker-compose.override.yml up -d --build

### Docker - update (inner loop)
- Each remote deploy: full archive again + --build (slow)

### Gaps / notes
- Large footprint; duplicates D1 path on repeat.

### Recommendation
- One-shot bootstrap alternative to D1 if rsync unavailable.

---

## D1 - dev bootstrap (rsync + compose up --build)

| | |
|---|---|
| Command | `.\tools\geostat.ps1 fe dev bootstrap -Environment dev` |
| Host | administrator@192.168.1.199 |
| Root | `/home/administrator/geostat/frontend/compose/dev/geostat-chat-ai-app` |
| Env | server: .env.dev; ops/config/frontend/.env.dev |
| Docker | target development; -p 5177:5177; volume /home/administrator/geostat/frontend/compose/dev/geostat-chat-ai-app:/app |
| Runtime | npm run dev --host 0.0.0.0 in container |

### Directory tree

```
+-- administrator@192.168.1.199
    |-- /home/administrator/geostat/frontend/compose/dev/geostat-chat-ai-app
    |   |-- .geostat-deploy.json  # kind=compose-dev
    |   |-- .env.dev  # merged secrets upload
    |   |-- docker-compose.yml
    |   |-- docker-compose.override.yml
    |   |-- package.json
    |   |-- package-lock.json
    |   |-- src/
    |   |   |-- main.jsx
    |   |   |-- components/chatbot/...
    |   |   +-- Dockerfile
    |   |-- public/
    |   +-- node_modules/  # inside container volume, not rsynced from Windows
    +-- (Docker)
        |-- container: geostat-chat-ai-app
        |-- mount: /home/administrator/geostat/frontend/compose/dev/geostat-chat-ai-app -> /app
        +-- develop.watch: src -> /app/src
```

### Docker - start (bootstrap)
- 1. rsync -avz --delete apps/frontend/ -> /home/administrator/geostat/frontend/compose/dev/geostat-chat-ai-app/ (excl node_modules, dist)
- 2. Publish-RemoteEnvFiles -> .env.dev
- 3. ssh cd /home/administrator/geostat/frontend/compose/dev/geostat-chat-ai-app; docker compose up -d --build

### Docker - update (inner loop)
- Re-bootstrap after Dockerfile/package.json: fe dev bootstrap
- Daily edits: use D2/D3 instead (no --build)

### Gaps / notes
- Requires rsync (Git/WSL).

### Recommendation
- Best: Windows edit, Linux Vite in Docker.

---

## D2 - dev watch (debounced rsync only)

| | |
|---|---|
| Command | `.\tools\geostat.ps1 fe dev watch -Environment dev` |
| Host | administrator@192.168.1.199 |
| Root | `/home/administrator/geostat/frontend/compose/dev/geostat-chat-ai-app` |
| Env |  |
| Docker | container keeps running; Vite HMR + volume |
| Runtime | No npm build; no docker restart on jsx/css |

### Directory tree

```
+-- sync flow
    |-- C:\Users\Test-User\CursorProjects\geostat-chat-ai\frontend
    |   |-- src/components/chatbot/...
    |   |-- angular.json  # Angular projects
    |   +-- projects/  # Nx/monorepo UI
    |-- rsync -> /home/administrator/geostat/frontend/compose/dev/geostat-chat-ai-app
    +-- container Vite picks up /app changes
```

### Docker - start (bootstrap)
- After D1 bootstrap

### Docker - update (inner loop)
- Save -> debounce 1.5s -> rsync delta only
- compose develop.watch may also sync src inside container

### Recommendation
- Primary inner loop for Windows->Linux UI dev.

---

## D3 - dev sync (one-shot rsync)

| | |
|---|---|
| Command | `.\tools\geostat.ps1 fe dev sync` |
| Host | administrator@192.168.1.199 |
| Root | `/home/administrator/geostat/frontend/compose/dev/geostat-chat-ai-app` |
| Env |  |
| Docker | no restart |
| Runtime | Same rsync as D2 single shot |

### Directory tree

```
+-- /home/administrator/geostat/frontend/compose/dev/geostat-chat-ai-app
    |-- .geostat-deploy.json  # kind=compose-dev
    |-- .env.dev  # merged secrets upload
    |-- docker-compose.yml
    |-- docker-compose.override.yml
    |-- package.json
    |-- package-lock.json
    |-- src/
    |   |-- main.jsx
    |   |-- components/chatbot/...
    |   +-- Dockerfile
    |-- public/
    +-- node_modules/  # inside container volume, not rsynced from Windows
```

### Docker - update (inner loop)
- One rsync -avz --delete; container unchanged

### Recommendation
- Manual push without watch process.

---

## D4 - dev restart (compose restart)

| | |
|---|---|
| Command | `.\tools\geostat.ps1 fe dev restart -Environment dev` |
| Host | administrator@192.168.1.199 |
| Root | `/home/administrator/geostat/frontend/compose/dev/geostat-chat-ai-app` |
| Env |  |
| Docker | docker compose restart (no rebuild) |
| Runtime | After env change without full bootstrap |

### Directory tree

```
+-- /home/administrator/geostat/frontend/compose/dev/geostat-chat-ai-app
    |-- .geostat-deploy.json  # kind=compose-dev
    |-- .env.dev  # merged secrets upload
    |-- docker-compose.yml
    |-- docker-compose.override.yml
    |-- package.json
    |-- package-lock.json
    |-- src/
    |   |-- main.jsx
    |   |-- components/chatbot/...
    |   +-- Dockerfile
    |-- public/
    +-- node_modules/  # inside container volume, not rsynced from Windows
```

### Docker - update (inner loop)
- ssh: docker compose restart in /home/administrator/geostat/frontend/compose/dev/geostat-chat-ai-app

### Recommendation
- Lighter than bootstrap; no image rebuild.

---

## C2 - deploy remote prod

| | |
|---|---|
| Command | `.\tools\geostat.ps1 fe deploy remote -Environment prod` |
| Host | administrator@192.168.1.199 |
| Root | `/home/administrator/geostat/frontend/compose/prod/geostat-chat-ai-app` |
| Env | server: .env.prod; deploy.env |
| Docker | compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build |
| Runtime | production target in container on server |

### Directory tree

```
+-- /home/administrator/geostat/frontend/compose/prod/geostat-chat-ai-app
    |-- .geostat-deploy.json
    |-- .env.prod
    |-- docker-compose.yml
    |-- docker-compose.prod.yml
    |-- src/
    +-- dist/  # built inside image on server
```

### Docker - start (bootstrap)
- Same as C1 but path compose/prod/ and prod overlay

### Docker - update (inner loop)
- Full tar + --build each time

### Gaps / notes
- Prefer B1 dist for production UI.

### Recommendation
- Optional server-side prod compose.

---

## E1 - stack-deploy (geostat.ops.json steps)

| | |
|---|---|
| Command | `.\tools\geostat.ps1 stack-deploy --prod` |
| Host | administrator@192.168.1.199 |
| Root | `/home/administrator/geostat` |
| Env | backend secrets; frontend .env.prod; deploy.env |
| Docker | be deploy all + fe deploy dist |
| Runtime | B1 for UI + backend SSH deploy trees |

### Directory tree

```
+-- /home/administrator/geostat
    |-- apps/backend/
    |   +-- <service>/
    |       |-- app.jar
    |       |-- .env.prod
    |       +-- docker-compose.prod.yml
    |-- apps/frontend/
    |   +-- /home/administrator/geostat/frontend/static/geostat-chat-ai-app
    |       |-- .geostat-deploy.json  # manifest: kind=static, deployMode
    |       |-- .env.runtime.json  # VITE_API_URL, environment, deployedAt
    |       |-- nginx.conf  # from nginx-gen / module root
    |       +-- dist/
    |           |-- index.html
    |           |-- assets/
    |           |   |-- index-[hash].js
    |           |   |-- index-[hash].css
    |           |   +-- ...  # chunks, fonts, images
    |           +-- config.json  # VITE_API_URL runtime
    +-- deploy-staging/
```

### Docker - start (bootstrap)
- 1. geostat be deploy all (backend modules)
- 2. geostat fe deploy dist -Environment prod

### Docker - update (inner loop)
- Re-run stack-deploy; not incremental

### Recommendation
- Production full stack push.

---

## L0 - Flat layout (DEPLOY_LAYOUT=flat)

| | |
|---|---|
| Command | `(old) deploy dist before structured paths` |
| Host | administrator@192.168.1.199 |
| Root | `/home/administrator/geostat/frontend/geostat-chat-ai-app` |
| Env | .env.deploy with DEPLOY_LAYOUT=flat |
| Docker | same nginx pattern under flat path |
| Runtime | Deprecated vs structured |

### Directory tree

```
+-- /home/administrator/geostat/frontend/geostat-chat-ai-app
    |-- dist/
    +-- nginx.conf
```

### Docker - start (bootstrap)
- Same commands but Set-DeployPathForMode uses flat/{container}

### Docker - update (inner loop)
- Migrate to structured static/ after one B1 deploy

### Gaps / notes
- Two paths may coexist until old dir removed.

### Recommendation
- Do not mix flat and structured on same host.

---

## Architecture summary
- LOCAL: repo apps/frontend/ + ops/config/ + generated compose; logs under frontend/logs/.
- SERVER STATIC (recommended prod): {base}/static/{service}/dist + nginx.conf  - no source, no node_modules.
- SERVER COMPOSE (dev/staging): {base}/compose/{service}/  - full source; isolate from static.
- PATH: clarify DEPLOY_PATH  - base directory OR full service path; avoid silent /{container_name} append.
- CI: npm build + optional dist layout dry-run; integration via stack not fe deploy.
- stack-deploy: uses dist (B1)  - align docs and ADOPTION-LINE with chosen prod mode.

| Use case | Mode | Server | Update mechanism |
|----------|------|--------|------------------|
| Daily dev (local) | A1 / A2 | No | HMR / compose watch |
| Windows edit, Linux UI | D1 then D2 | Yes | rsync only |
| CI / build artifact | npm run build | No | N/A |
| Prod UI (static) | B1 dist | Yes | B2 or B3 |
| Full stack prod | E1 stack-deploy | Yes | re-deploy |
| Server compose UI | C1/C2 tar | Yes | full tar+build |
| Legacy host | L0 flat | Yes | migrate to static/ |
