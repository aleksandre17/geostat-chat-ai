# Environment & operations (practice, not application code)

How to align **your machine and servers** with this repo without hardcoding hosts or legacy names in source.

## Quick checklist

| Step | Action |
|------|--------|
| 1 | `copy ops\config\deploy.env.example ops\config\deploy.env` — set **real** `DEPLOY_SERVER` (never commit) |
| 2 | Copy `ops/config/frontend/.env.*`, `ops/config/backend/.env.*` from examples |
| 3 | Legacy server? → [kits/geostat-kit/scaffold/profiles/legacy-server.env.example](../kits/geostat-kit/scaffold/profiles/legacy-server.env.example) into `deploy.env` |
| 4 | `.\tools\geostat.ps1 compose-gen` |
| 5 | `copy ops\config\frontend\nginx.env.example ops\config\frontend\nginx.env` — set CSP parents → `nginx-gen` |
| 6 | Embed hosts: use `?chat_src=` on [example.html](../frontend/public/embed/example.html) |

Details: [CONFIG.md](CONFIG.md) | [kits/geostat-kit/README.md](../kits/geostat-kit/README.md) | [ops/config/README.md](../ops/config/README.md) | [ENV.md](ENV.md) | [ops/compose/README.md](../ops/compose/README.md)

---

## `ops/config/deploy.env`

| Key | In repo? | Notes |
|-----|----------|--------|
| `DEPLOY_SERVER` | **No** (gitignored) | Required for remote deploy/manage |
| `DEPLOY_PROJECT` | Example only | Remote folder slug (e.g. `geostat`) |
| `COMPOSE_*` / `DOCKER_NETWORK` | Example + profiles | Override when server already uses legacy names |

Default compose names come from the **repo folder name**. Legacy containers (`geostat-chat-api`, `geostat-net`) need profile overrides — then `compose-gen` regenerates all `docker-compose*.yml` and `apps/backend/ops.modules`.

---

## Legacy server names

1. Merge [legacy-server.env.example](../kits/geostat-kit/scaffold/profiles/legacy-server.env.example) into `deploy.env`.
2. `.\tools\geostat.ps1 compose-gen`
3. Set `API_INTERNAL_URL=http://<api-service-name>:8090` in `ops/config/backend/.env.prod` (match compose-gen / `compose-names` — e.g. `geostat-chat-ai-api`).

Remote directories on disk (`DEPLOY_PROJECT`) do **not** have to match container names.

---

## Worker optional

Set in `ops/compose/catalog.json`:

```json
"features": { "worker": false }
```

Then `compose-gen`. See [worker-disabled.md](../kits/geostat-kit/scaffold/profiles/worker-disabled.md).

---

## Nginx CSP (`frame-ancestors`)

| File | Role |
|------|------|
| `frontend/nginx.conf.template` | Source template |
| `ops/config/frontend/nginx.env` | Your allowed parent origins (gitignored) |
| `frontend/nginx.conf` | Generated — used by Docker + `deploy.ps1 dist` |

```powershell
.\tools\geostat.ps1 nginx-gen
# or before deploy:
.\tools\geostat.ps1 fe deploy dist
```

---

## Embed widget URLs

| File | Role |
|------|------|
| `public/embed/widget.js` | Generic; `chat-src` attribute |
| `public/embed/example.html` | Demo; reads `?chat_src=` (no fixed IP in repo) |
| `ops/config/frontend/embed.env.example` | Documented defaults for your team |

Host sites should set:

```html
<geostat-chat-widget chat-src="https://your-chat-host:5177" lang="ka"></geostat-chat-widget>
```

---

## Java package `com.geostat.chat`

Application code under `apps/backend/src/main/java/com/geostat/chat/`. **Ops scripts do not depend on package names** — only compose service names, Gradle modules, and `ops.modules`. Legacy `Chatbot` package was migrated in B-23 (see [ADR 005](adr/005-java-package-naming.md)).
