# Frontend: `deploy watch` vs `dev watch`

Two different commands — the **`deploy`** vs **`dev`** prefix is intentional.

## Quick table

| Command | What it does | Server path | On each save |
|---------|--------------|-------------|--------------|
| **`geostat fe deploy watch`** | Prod-like **static** UI | `.../frontend/static/{service}/` | `npm build` → upload `dist/` → nginx reload |
| **`geostat fe dev watch`** | **Remote dev** (Vite/Angular in Docker) | `.../frontend/compose/dev/{service}/` | **rsync** source only (no build) |

## When to use which

| Goal | Command |
|------|---------|
| Windows edit, Linux shows UI with hot reload (no dist build) | `fe dev bootstrap` then **`fe dev watch`** |
| Staging/prod static site on server, auto-rebuild on save | `fe deploy dist` once, then **`fe deploy watch`** |
| One-shot static update | `fe deploy sync` |
| One-shot source push | `fe dev sync` |

## Backward compatibility

| Old | New |
|-----|-----|
| `geostat fe watch` | **`geostat fe deploy watch`** (CLI redirects with a hint) |

## Prerequisites

- **deploy watch:** nginx container already up (`fe deploy dist`), `DEPLOY_SERVER` set.
- **dev watch:** dev container up (`fe dev bootstrap`), **rsync** on Windows (Git/WSL).

See also: [ARCHITECTURE.md](./ARCHITECTURE.md#golden-paths-only-team-policy), [DEV-REMOTE.md](./DEV-REMOTE.md), [FRONTEND-DEPLOY-LAYOUTS.md](./FRONTEND-DEPLOY-LAYOUTS.md).
