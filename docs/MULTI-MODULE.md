# Multi-module backend

Deploy and manage **several runnable Spring Boot modules** plus **shared libraries** with the same ops flow.

## Concepts

| Layer | Source of truth |
|-------|-----------------|
| **Runnable services** | `apps/backend/docker-compose.*.yml` → `services:` keys |
| **Gradle layout** | `apps/backend/ops.modules` → compose service → Gradle path |
| **Libraries** | `ops.modules` lines with `type=library` (not deployed alone) |

## `apps/backend/ops.modules`

```text
compose_service|gradle_module|type|dockerfile|enabled
geostat-chat-ai-api||boot|src/Dockerfile|yes
geostat-chat-ai-retrieval||boot|Dockerfile|yes
geostat-chat-ai-ingestion|worker|boot|Dockerfile|yes
|shared|library||yes
```

| Field | Meaning |
|-------|---------|
| `compose_service` | Key in generated `docker-compose.prod.yml` |
| `gradle_module` | Empty = root project; else subfolder (`worker`) |
| `type` | `boot` (deployable) or `library` |
| `dockerfile` | Relative to module dir |
| `enabled` | `no` skips registry entry |

## Commands

```bash
# List mapped modules
./tools/geostat.sh be modules

# Deploy one boot module
./tools/geostat.sh be deploy geostat-chat-ai-api --prod

# Deploy all compose services
./tools/geostat.sh be deploy all --prod

# Manage (server dirs = container names)
./tools/geostat.sh be manage geostat-chat-ai-api status --prod
./tools/geostat.sh be manage all restart --prod
```

## Gradle

- **Root API** — main app stays at `apps/backend/` (task `bootJar`).
- **`:shared`** — library; root depends on `project(":shared")`.
- **`:worker`** (optional) — uncomment in `settings.gradle.kts`, add boot app under `worker/`.

Build uses the correct task per service:

- Root → `./gradlew bootJar -PactiveModules=root`
- Worker → `./gradlew :worker:bootJar -PactiveModules=worker`

Interactive deploy asks which `findProject` / `project(":…")` deps to include (`gradle-modules.sh`).

## Worker role (ingestion, not embedded backend worker)

Architecture B: **`ingestion`** is the worker deployable (`apps/ingestion-service`, port 8093). Embedded `apps/backend/worker` is **off** (`features.worker: false`).

```powershell
.\tools\geostat.ps1 ing compose up --build
# deploy (when ready):
.\tools\geostat.ps1 mod ingestion deploy ...
```

To enable the legacy **embedded** sidecar in `apps/backend/worker` instead, set `"worker": true` in `ops/compose/catalog.json` and run `compose-gen` (not used in this repo).

## Add another boot module

1. Copy `worker/` pattern to `apps/backend/<name>/`.
2. `include("<name>")` in `settings.gradle.kts`.
3. Add line to `ops.modules`.
4. Add `<name>_dev` / `<name>_prod` templates in `catalog.json` + target `services`.
5. `compose-gen` and deploy.

## Server layout (structured — Phase 1)

Configure `ops/config/backend/.env.deploy` (`DEPLOY_LAYOUT=structured`). Each boot service:

```text
{DEPLOY_PATH}/runtime/<container_name>/
  app.jar
  Dockerfile
  .env.prod
  docker-compose.prod.yml
  logs/
  versions/          # prod only
```

See [BACKEND-DEPLOY-LAYOUTS.md](./BACKEND-DEPLOY-LAYOUTS.md) — `structured` (`runtime/` + `workspace/`) vs legacy `flat` (`{DEPLOY_PATH}/<container>/`). Migration: `kits/geostat-kit/toolkit/deploy/migrate-backend-layout.sh`.

Same Docker network (`DOCKER_NETWORK` in `ops/config/deploy.env`).

## Integration (library into boot module)

In `worker/build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":shared"))
}
```

Deploy only the **boot** service; Gradle builds dependencies automatically.
