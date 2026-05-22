# Backend deploy - full layout simulation

> Dry-run from `ops/config/` + `geostat.ops.json`. Regenerate: `geostat layout --backend -Markdown -OutFile docs/BACKEND-LAYOUT-SIMULATION-FULL.md`

## Resolved paths (this project)

| Key | Value |
|-----|-------|
| SSH | `administrator@192.168.1.199` |
| DEPLOY_PATH | `/home/administrator/geostat/backend` |
| DEPLOY_LAYOUT | `structured` |
| API runtime (R*) | `/home/administrator/geostat/backend/runtime/geostat-chat-ai-api` |
| API workspace (W*) | `/home/administrator/geostat/backend/workspace/geostat-chat-ai-api` |
| Legacy flat | `/home/administrator/geostat/backend/geostat-chat-ai-api` |
| Local repo | `C:\Users\Test-User\CursorProjects\geostat-chat-ai\backend` |
| Container (API) | `geostat-chat-ai-api` |

### Layout glossary

| Term | Meaning |
|------|---------|
| **`DEPLOY_LAYOUT=structured`** | Server uses `runtime/` (JAR deploy) and `workspace/` (be dev) under `DEPLOY_PATH` |
| **`flat`** | Legacy: service dirs directly under `DEPLOY_PATH` (e.g. `backend/geostat-chat-ai-api/`) |
| **flat → runtime** | Migration moves old flat dirs into `runtime/{container}/` — see [BACKEND-DEPLOY-LAYOUTS.md](./BACKEND-DEPLOY-LAYOUTS.md) |

Full documentation: [BACKEND-DEPLOY-LAYOUTS.md](./BACKEND-DEPLOY-LAYOUTS.md), [BE-DEPLOY-WATCH.md](./BE-DEPLOY-WATCH.md), [BACKEND-DEV-REMOTE.md](./BACKEND-DEV-REMOTE.md).

---

## L0 - Local dev (no SSH)

| | |
|---|---|
| Command | `./gradlew bootRun  |  geostat be compose up --build` |
| Host | (localhost) |
| Root | `C:\Users\Test-User\CursorProjects\geostat-chat-ai\backend` |
| Path kind | repo |

### Directory tree / flow

```
+-- apps/backend/
    |-- src/  (Spring Boot API)
    |-- shared/
    |-- worker/  (optional module)
    |-- build.gradle.kts
    +-- docker-compose.dev.yml  (local Docker)
```

- Golden path for daily dev on same machine as JVM.

---

## R1 - be deploy --dev (JAR -> runtime/)

| | |
|---|---|
| Command | `geostat be deploy geostat-chat-ai-api --dev` |
| Host | administrator@192.168.1.199 |
| Root | `/home/administrator/geostat/backend/runtime/geostat-chat-ai-api` |
| Path kind | runtime |

### Directory tree / flow

```
+-- /home/administrator/geostat/backend/runtime/geostat-chat-ai-api/
    |-- Dockerfile  (from src/Dockerfile, COPY app.jar)
    |-- app.jar  (built on Windows)
    |-- .env.dev
    |-- docker-compose.dev.yml  (generated on server)
    |-- logs/
    +-- info.log
```

- Structured layout requires ops/config/backend/.env.deploy.

---

## R2 - be deploy --prod (JAR + versions/)

| | |
|---|---|
| Command | `geostat be deploy geostat-chat-ai-api --prod` |
| Host | administrator@192.168.1.199 |
| Root | `/home/administrator/geostat/backend/runtime/geostat-chat-ai-api` |
| Path kind | runtime |

### Directory tree / flow

```
+-- /home/administrator/geostat/backend/runtime/geostat-chat-ai-api/
    |-- app.jar
    |-- .env.prod
    |-- docker-compose.prod.yml
    |-- versions/app-*.jar
    +-- logs/
```

- Rollback uses versions/ on failed healthcheck.

---

## R3 - be deploy watch (Gradle loop -> runtime/)

| | |
|---|---|
| Command | `geostat be deploy watch geostat-chat-ai-api --dev` |
| Host | administrator@192.168.1.199 |
| Root | `/home/administrator/geostat/backend/runtime/geostat-chat-ai-api` |
| Path kind | runtime |

### Directory tree / flow

```
Windows: src/, shared/, worker/  ->  gradlew bootJar
  ->  scp app.jar  ->  /home/administrator/geostat/backend/runtime/geostat-chat-ai-api
  ->  docker compose up --build
```

- Not be dev watch. Requires prior R1 deploy.

---

## W1 - be dev bootstrap (rsync -> workspace/)

| | |
|---|---|
| Command | `geostat be dev bootstrap geostat-chat-ai-api` |
| Host | administrator@192.168.1.199 |
| Root | `/home/administrator/geostat/backend/workspace/geostat-chat-ai-api` |
| Path kind | workspace |

### Directory tree / flow

```
+-- /home/administrator/geostat/backend/workspace/geostat-chat-ai-api/
    |-- gradlew, build.gradle.kts, src/, shared/, worker/
    |-- src/Dockerfile.dev.remote
    |-- .env.dev, google-credentials.json
    |-- docker-compose.workspace.yml  (bootRun in container)
    +-- volume .:/app + gradle cache
```

- DEPLOY_LAYOUT=structured required. Spring DevTools restarts on rsync.

---

## W2 - be dev watch (rsync only; DevTools reload)

| | |
|---|---|
| Command | `geostat be dev watch geostat-chat-ai-api  # add --restart to force compose restart` |
| Host | administrator@192.168.1.199 |
| Root | `/home/administrator/geostat/backend/workspace/geostat-chat-ai-api` |
| Path kind | workspace |

### Directory tree / flow

```
Poll backend/src, shared/, worker/
  â†’  rsync to /home/administrator/geostat/backend/workspace/geostat-chat-ai-api
  â†’  DevTools triggers bootRun restart (default)
  â†’  optional --restart for Dockerfile/gradle changes
```


---

## R1w - be deploy worker --dev

| | |
|---|---|
| Command | `geostat be deploy geostat-chat-ai-worker --dev` |
| Host | administrator@192.168.1.199 |
| Root | `/home/administrator/geostat/backend/runtime/geostat-chat-ai-worker` |
| Path kind | runtime |

### Directory tree / flow

```
+-- /home/administrator/geostat/backend/runtime/geostat-chat-ai-worker/  (worker/Dockerfile, app.jar)
```


---

## MIG - Migration flat -> structured

| | |
|---|---|
| Command | `bash kits/geostat-kit/toolkit/deploy/migrate-backend-layout.sh --dry-run` |
| Host | administrator@192.168.1.199 |
| Root | `/home/administrator/geostat/backend` |
| Path kind | migrate |

### Directory tree / flow

```
Legacy: /home/administrator/geostat/backend/geostat-chat-ai-api
  â†’  mv to /home/administrator/geostat/backend/runtime/geostat-chat-ai-api
New dev trees only under workspace/
```

- Run without --dry-run to apply on server.

---


