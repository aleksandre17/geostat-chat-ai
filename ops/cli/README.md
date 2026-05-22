# geostat CLI

ერთადერთი შესვლა პროექტის ops-ში — ლოგიკა `kits/geostat-kit/`-ში.

**ახალი პროექტი / ops bootstrap:** `.\tools\geostat.ps1 init` — სრული შაბლონი + შევსების checklist. დოკი: [../docs/GEOSTAT-INIT.md](../docs/GEOSTAT-INIT.md).

```powershell
.\tools\geostat.ps1 help
.\tools\geostat.ps1 stack up -d --build
.\tools\geostat.ps1 be deploy all --prod
.\tools\geostat.ps1 be manage api logs errors --prod
.\tools\geostat.ps1 fe manage status
.\tools\geostat.ps1 fe deploy dist -Environment prod
.\tools\geostat.ps1 compose-gen
.\tools\geostat.ps1 mod backend deploy all --prod
.\tools\geostat.ps1 help
```

ახალი backend stack (მაგ. Node API): `modules.<id>.type` შეცვალე manifest-ში — იხ. `kits/geostat-kit/drivers/README.md`.

Linux/macOS (Git Bash):

```bash
./tools/geostat.sh be deploy all --prod
```

პროექტში რჩება: `ops/config/`, `geostat.ops.json`, `ops/compose/catalog.json`, `backend/ops.config.sh`, `frontend/ops.config.ps1`, `ops/ci/` (ინტეგრაცია).

სრული გზამკვლევი (გადმოტანა, CI, ახალი driver): [../kits/geostat-kit/docs/ADOPTION-LINE.md](../kits/geostat-kit/docs/ADOPTION-LINE.md).
