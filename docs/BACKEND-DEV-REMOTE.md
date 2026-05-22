# Backend remote dev — Windows edit, Linux bootRun

For **Spring Boot / Gradle** using `java-boot` and **`DEPLOY_LAYOUT=structured`** (workspace paths).

Layout primer: [BACKEND-DEPLOY-LAYOUTS.md](./BACKEND-DEPLOY-LAYOUTS.md) — რას ნიშნავს `structured` vs `flat`, `workspace/` vs `runtime/`.

## Two loops (do not mix)

| Goal | Command | Server path |
|------|---------|-------------|
| **Daily dev on Linux** | `./gradlew bootRun` or `be compose up` | local `apps/backend/` |
| **Windows → Linux source** | `be dev bootstrap` → `be dev watch` | `{DEPLOY_PATH}/workspace/{container}/` |
| **Staging/prod JAR** | `be deploy` | `{DEPLOY_PATH}/runtime/{container}/` |
| **Auto JAR (local Gradle)** | `be deploy watch` | `runtime/` — [BE-DEPLOY-WATCH.md](./BE-DEPLOY-WATCH.md) |

`be dev` **არ მუშაობს** `DEPLOY_LAYOUT=flat`-ზე (სკრიპტი გააჩერებს) — საჭიროა `structured`.

## Prerequisites

- `ops/config/backend/.env.deploy`: `DEPLOY_LAYOUT=structured`, `DEPLOY_PATH`
- `ops/config/deploy.env`: `DEPLOY_SERVER`, `DEPLOY_PROJECT`
- `ops/config/backend/.env.dev`: ports, API keys
- **rsync**: Git for Windows (`usr\bin\rsync.exe`)
- **Spring DevTools** (repo-ში ჩართული) — `be dev watch` ნაგულისხმევად მხოლოდ rsync; reload კონტეინერში

## Commands

```bash
# Once: rsync apps/backend/ + bootRun on Linux
./tools/geostat.sh be dev bootstrap geostat-chat-ai-api

# Incremental sync only
./tools/geostat.sh be dev sync geostat-chat-ai-api

# Save on Windows → rsync (DevTools restarts bootRun)
./tools/geostat.sh be dev watch geostat-chat-ai-api

# After Dockerfile.dev.remote / build.gradle.kts / settings change
./tools/geostat.sh be dev bootstrap geostat-chat-ai-api
# or force container restart:
./tools/geostat.sh be dev watch geostat-chat-ai-api --restart
./tools/geostat.sh be dev restart geostat-chat-ai-api
```

Worker:

```bash
./tools/geostat.sh be dev bootstrap geostat-chat-ai-worker
```

## Server tree (`workspace/`)

```text
/home/.../geostat/backend/workspace/geostat-chat-ai-api/
  gradlew, build.gradle.kts, settings.gradle.kts
  src/, shared/, worker/
  src/Dockerfile.dev.remote
  .env.dev
  google-credentials.json
  docker-compose.workspace.yml   # generated on bootstrap
  .geostat-deploy.json
```

Volume `.:/app` + Gradle cache volume — compile **on Linux**, not on Windows.

## How it works

1. **rsync** — `apps/backend/` (excludes `.gradle/`, `build/`, `app.jar`, …)
2. **compose** — `docker-compose.workspace.yml`, `./gradlew bootRun` (or `:worker:bootRun`)
3. **watch** — debounced rsync; **DevTools** reloads app in container
4. **`--restart`** — ძალით `compose restart` (Dockerfile / Gradle ფაილების ცვლილების შემდეგ)

## vs `be deploy watch`

| | `be dev watch` | `be deploy watch` |
|--|----------------|-------------------|
| Path | `workspace/` | `runtime/` |
| Build | Gradle in container | Gradle on **Windows** |
| Artifact | live sources | `app.jar` + JRE image |

## Package

- `kits/geostat-kit/toolkit/deploy/dev-remote.sh`
- `kits/geostat-kit/drivers/java-boot/sh/dev.sh`

See also: [GOLDEN-PATHS-BACKEND.md](../kits/geostat-kit/docs/GOLDEN-PATHS-BACKEND.md), [REMOTE-DEV-JAR-FLOW.md](../kits/geostat-kit/docs/REMOTE-DEV-JAR-FLOW.md)
