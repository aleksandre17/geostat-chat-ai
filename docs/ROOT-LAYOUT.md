# Root layout — apps + kits + ops (active)

## Target structure (implemented)

```text
geostat-chat-ai/
├── apps/
│   ├── apps/frontend/
│   └── apps/backend/
├── kits/
│   └── geostat-kit/          # reusable toolkit (submodule)
├── ops/
│   ├── config/               # secrets (env, SSH) — was ops/config/
│   ├── compose/
│   │   ├── catalog.json      # source — was ops/compose/
│   │   └── stack/            # GENERATED — was ops/compose/stack/
│   ├── cli/                  # geostat.ps1 — was tools/
│   └── ci/                   # was ops/ci/
├── docs/
├── tools/                    # shim → ops/cli/geostat.ps1
└── geostat.ops.json          # version 2 paths
```

## CLI entry

`tools/geostat.ps1` — thin shim to `ops/cli/geostat.ps1` (ხელისთვის ჩვეულებრივი path).

## Manifest (`geostat.ops.json` v2)

- `package`: `kits/geostat-kit`
- `secrets`: `ops/config`
- `compose.catalog`: `ops/compose/catalog.json`
- `stack.composeDir`: `ops/compose/stack`
- `modules.*.path`: `apps/frontend`, `apps/backend`

## Commands

```powershell
.\tools\geostat.ps1 compose-gen    # or ops\cli\geostat.ps1
.\tools\geostat.ps1 stack up -d --build
```

ADR: [adr/008-root-layout-consolidation.md](adr/008-root-layout-consolidation.md)  
სრული ცხრილი (ძველი → ახალი): [MIGRATION-MAP.md](MIGRATION-MAP.md)
