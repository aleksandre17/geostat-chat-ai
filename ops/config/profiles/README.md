# Profiles (project overrides)

Copy snippets from **`kits/geostat-kit/scaffold/profiles/`** into `ops/config/deploy.env`, then `.\tools\geostat.ps1 compose-gen`.

| File | Use |
|------|-----|
| [legacy-server.env.example](../../kits/geostat-kit/scaffold/profiles/legacy-server.env.example) | Existing `geostat-chat-api` / `geostat-net` |
| [worker-disabled.md](../../kits/geostat-kit/scaffold/profiles/worker-disabled.md) | Disable worker in catalog |

Canonical copy lives in the **package** scaffold for new projects.
