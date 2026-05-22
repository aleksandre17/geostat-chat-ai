# kits/ — გარე პაკეტები (არა პროდუქტის კოდი)

`apps/` — **ეს პროექტის** აპლიკაცია (frontend, backend, ingestion, retrieval).  
`kits/` — **ცალკე დაწერილი**, GitHub-ზე ცალკე გამოქვეყნებული ops პაკეტები, რომლებსაც ეს consumer იყენებს.

## geostat-kit

| | |
|--|--|
| **რა არის** | Manifest-driven ops framework — CLI, compose-gen, infra tunnel, deploy drivers |
| **GitHub (ცალკე რეპო)** | https://github.com/aleksandre17/geostat-kit |
| **ამ პროექტში** | `kits/geostat-kit/` |
| **ვერსია (ლოკალურად)** | იხ. `geostat-kit/VERSION` და `git -C geostat-kit describe --tags` |
| **კონტრაქტი** | root `geostat.ops.json` → `"package": "kits/geostat-kit"` |

```text
geostat-kit (GitHub)              geostat-chat-ai (ეს repo)
      │                                    │
      │  submodule / vendor                │
      └──────────► kits/geostat-kit ◄──────┘
                         │
                   geostat.ops.json
                   ops/config/  apps/
```

## სად რა რჩება

| აქ (`kits/geostat-kit/`) | პროექტში (`geostat-chat-ai/`) |
|--------------------------|-------------------------------|
| CLI, drivers, compose-gen | `apps/*` — ბიზნეს ლოგიკა |
| scaffold ნიმუშები | `ops/config/` — secrets, env |
| პაკეტის ტესტები | `ops/compose/` — ამ პროექტის stack |
| | `tools/geostat.ps1` — thin shim → kit CLI |

## განახლება

```powershell
cd kits\geostat-kit
git pull origin main
# ან კონკრეტული tag: git checkout v1.1.0
```

სრული ინსტრუქცია: [../docs/KITS-PACKAGE.md](../docs/KITS-PACKAGE.md)
