# geostat-kit — პაკეტის ჩატვირთვა (`kits/geostat-kit`)

`geostat-kit` **არ არის** `packages/`-ში — ის **გადატანადი toolkit**ა, რომელიც პროექტში ჯდება **`kits/geostat-kit/`**-ში (v2 layout). აპლიკაცია ცალკეა: `apps/`.

**რა გავაკეთეთ და როგორ გავრცელდეს (v1.0.0):** [../kits/geostat-kit/docs/ADOPTION.md](../kits/geostat-kit/docs/ADOPTION.md)

## სწრაფი წესი

| | |
|--|--|
| **პაკეტი** | `kits/geostat-kit/` — submodule, copy, ან vendor zip |
| **კონტრაქტი** | root `geostat.ops.json` → `"package": "kits/geostat-kit"` |
| **პროექტის env** | `ops/config/` (არა პაკეტის შიგნით) |
| **CLI** | `tools/geostat.ps1` → `ops/cli/` → `kits/geostat-kit/cli/` |

---

## 1. Git submodule (რეკომენდებული)

```bash
cd your-app
git submodule add https://github.com/YOUR_USER/geostat-kit.git kits/geostat-kit
git submodule update --init --recursive
```

შემდეგ root-ზე:

```powershell
.\tools\geostat.ps1 init    # თუ ops ხე ჯერ არ გაქვს
# ან ხელით: დააკოპირე scaffold/geostat.ops.json და შეცვალე package path
```

`geostat.ops.json`:

```json
{
  "version": 2,
  "package": "kits/geostat-kit",
  "secrets": "ops/config",
  ...
}
```

---

## 2. Copy / vendor (submodule-ის გარეშე)

```powershell
# მაგალითი
xcopy /E /I C:\path\to\geostat-kit kits\geostat-kit
```

ან unzip release → `kits/geostat-kit/`.  
**არ ჩაწერო** `deploy.env`, API keys, generated compose პაკეტის შიგნით.

---

## 3. ამ monorepo-ში (geostat-chat-ai)

პაკეტი **ცალკე რეპოდან** ჯდება consumer-ში — არა `apps/`-ის ნაწილი.

| | |
|--|--|
| **GitHub (source of truth)** | https://github.com/aleksandre17/geostat-kit |
| **ამ პროექტში** | `kits/geostat-kit/` |
| **მარკერი** | [../kits/README.md](../kits/README.md) |
| **manifest** | `"package": "kits/geostat-kit"` in `geostat.ops.json` |
| **IDE** | workspace root: `geostat-kit (package · GitHub)` |

```text
geostat-chat-ai/kits/geostat-kit/   ← clone / submodule (არა copy-paste apps-ში)
```

განახლება:

```bash
cd kits/geostat-kit && git pull   # თუ submodule-ია
# ან ხელით ჩაანაცვლე vendor-ის ახალი ვერსია
```

ტესტი პაკეტზე:

```powershell
cd kits\geostat-kit
$env:PYTHONPATH = (Get-Location).Path
py -3 -m pytest tests -q
```

---

## 4. რა **არ** უნდა გააკეთო

| არასწორი | სწორი |
|----------|--------|
| `kits/geostat-kit/` | `kits/geostat-kit/` |
| secrets პაკეტის შიგნით | `ops/config/` პროექტის root-ზე |
| პაკეტში `docker-compose*.yml` პროექტისთვის | `compose-gen` → `apps/*`, `ops/compose/stack/` |
| deploy ლოგიკა `scripts/`-ში | `kits/geostat-kit/toolkit/deploy/` + `geostat` CLI |

---

## 5. შემდეგი ნაბიჯები

1. [ADOPTION-LINE.md](../kits/geostat-kit/docs/ADOPTION-LINE.md) — სრული კონფიგ ხაზი  
2. [GEOSTAT-INIT.md](GEOSTAT-INIT.md) — `geostat init`  
3. [MIGRATION-MAP.md](MIGRATION-MAP.md) — ძველი path → ახალი  

---

## ვერსია

პაკეტის ვერსია: [kits/geostat-kit/VERSION](../kits/geostat-kit/VERSION)
