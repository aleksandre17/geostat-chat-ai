# Backend: `deploy watch` vs `dev watch`

Two different commands — the **`deploy`** vs **`dev`** prefix is intentional.

Layout: [BACKEND-DEPLOY-LAYOUTS.md](./BACKEND-DEPLOY-LAYOUTS.md) — `runtime/` vs `workspace/`, `structured` vs `flat`.

## Quick table

| Command | What it does | Server path | On each save |
|---------|--------------|-------------|--------------|
| **`geostat be deploy watch`** | Staging **JAR** loop | `{DEPLOY_PATH}/runtime/{container}/` | `./gradlew bootJar` (Windows) → scp `app.jar` → `compose up --build` |
| **`geostat be dev watch`** | **Remote Gradle** dev | `{DEPLOY_PATH}/workspace/{container}/` | rsync only (DevTools reload in bootRun) |

## When to use which

| Goal | Command |
|------|---------|
| Windows edit, Linux runs **JAR image** (like staging) | `be deploy` once, then **`be deploy watch`** |
| Windows edit, Linux **compiles in container** | `be dev bootstrap` then **`be dev watch`** |
| One-shot JAR to server | `be deploy api --dev` |
| One-shot source push | `be dev sync` |

## Prerequisites

- **`deploy watch`:** `DEPLOY_LAYOUT=structured`, `ops/config/backend/.env.deploy`, runtime already deployed:

  ```bash
  ./tools/geostat.sh be deploy geostat-chat-ai-api --dev
  ```

- **`dev watch`:** `be dev bootstrap`, rsync (Git for Windows), `structured` layout.

Default debounce: **8000 ms** (Gradle slower than `fe deploy watch` ~3s).

```bash
./tools/geostat.sh be deploy watch geostat-chat-ai-api --dev --debounce-ms 10000
./tools/geostat.sh be deploy watch --no-initial   # skip first cycle
```

## Why `compose up --build` (not only restart)?

Prod `Dockerfile` on server is `COPY app.jar` — ახალი jar host-ზე მოითხოვს image rebuild. Watch cycle ამიტომ აკეთებს `up --build`, არა მხოლოდ `restart`.

## CLI aliases

| Invocation | Resolves to |
|------------|-------------|
| `geostat be watch` | `be dev watch` (CLI hint) |
| `geostat be deploy watch` | JAR loop on **`runtime/`** |

## Related paths (structured)

```text
{DEPLOY_PATH}/runtime/geostat-chat-ai-api/    ← deploy watch
{DEPLOY_PATH}/workspace/geostat-chat-ai-api/  ← dev watch (NOT here)
```

Legacy **flat** (without `.env.deploy`): `{DEPLOY_PATH}/geostat-chat-ai-api/` — migrate: [BACKEND-DEPLOY-LAYOUTS.md#migration-checklist](./BACKEND-DEPLOY-LAYOUTS.md#migration-checklist).

See also: [BACKEND-DEV-REMOTE.md](./BACKEND-DEV-REMOTE.md), [kits/geostat-kit/docs/REMOTE-DEV-JAR-FLOW.md](../kits/geostat-kit/docs/REMOTE-DEV-JAR-FLOW.md)
