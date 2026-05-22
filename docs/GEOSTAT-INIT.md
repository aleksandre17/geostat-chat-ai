# geostat init — სრული პროექტის bootstrap

**ერთი ბრძანება** ახალი repo-სთვის (ან ops-ის დამატებისთვის): ჩაიყრება სტრუქტურა შაბლონებიდან, შეიქმნება env ფაილები examples-დან, გენერირდება compose — დარჩება **მხოლოდ შევსება** secrets-ში და (სურვილისამებ) `catalog.json`-ის მორგება.

პაკეტის ტექნიკური დეტალი: [../kits/geostat-kit/toolkit/init/README.md](../kits/geostat-kit/toolkit/init/README.md)

---

## როდის გამოიყენო

| სიტუაცია | ბრძანება |
|----------|----------|
| ახალი პროექტი + `kits/geostat-kit` უკვე არის | `.\tools\geostat.ps1 init` |
| მხოლოდ ხე, compose-ის გარეშე | `init -SkipComposeGen` |
| მხოლოდ API catalog (worker-ის გარეშე) | `init -MinimalCatalog` |
| არსებული env-ების გადაწერა examples-დან | `init -ForceExamples` |

**არ სჭირდება** `init` თუ ეს repo (`geostat-chat-ai`) უკვე მოწყობილია — გამოიყენე ყოველდღიური `fe` / `be` / `compose-gen`.

---

## გაშვება

```powershell
# repo root (სადაც არის ან სადაც გინდა geostat.ops.json)
.\tools\geostat.ps1 init
```

```bash
./tools/geostat.sh init
```

`geostat.ops.json` **არ არის საჭირო** init-ის წინ — ბრძანება თვითონ პოულობს root-ს (cwd ზემოთ) ან იყენებს `kits/geostat-kit`-ის მშობელ დირექტორიას.

---

## რას აკეთებს (7 ნაბიჯი)

```text
geostat init
    │
    ├─ 1. apply-scaffold     tools/, ops/config/*.example, infra/, deploy/, ops.config*, geostat.ops.json
    ├─ 2. catalog            catalog.full.json → ops/compose/catalog.json (default: full stack)
    ├─ 3. seed secrets       .example → deploy.env, .env.dev, .env.prod, .env.deploy, nginx.env
    ├─ 4. .gitignore         scaffold წესების დამატება root .gitignore-ში
    ├─ 5. compose-gen        docker-compose*.yml, apps/backend/ops.modules
    ├─ 6. nginx-gen          თუ manifest-ში არის frontend/nginx.conf.template
    └─ 7. checklist          რა ჯერ უნდა შეივსოს (DEPLOY_SERVER, API keys, …)
```

### რა ჩაიყრება (პროექტის root)

| Path | წყარო |
|------|--------|
| `tools/geostat.ps1`, `geostat.sh` | scaffold |
| `geostat.ops.json` | scaffold (თუ არ არსებობს) |
| `ops/config/` | examples + `.gitignore` |
| `ops/compose/catalog.json` | `catalog.full.json` ან `catalog.minimal.json` |
| `ops/compose/stack/` | README + `.gitkeep` → შემდეგ generated YAML |
| `apps/frontend/`, `apps/backend/` | `ops.config.*`, `.env.example`, `logs/.gitignore` |
| `ops/ci/` | integration-stack.sh (პროექტის CI) |

### რა **არ** ჩაიყრება

- `frontend/src/`, `backend/src/` — აპლიკაციის კოდი
- რეალური API keys (მხოლოდ placeholder examples)
- SSH private keys (მხოლოდ `ops/config/ssh/*.example`)

---

## სახელები — აბსტრაქტული, არა ბრენდი

| რა | სად | მაგალითი |
|----|-----|----------|
| **მოდულის id** | `geostat.ops.json` → `modules.*` | `frontend`, `backend` (ლოგიკური; შეგიძლია გადაარქვა) |
| **Docker სერვისი / container** | `ops/config/deploy.env` → `COMPOSE_*` | `<repo-slug>-api`, `<repo-slug>-app` |
| **ქსელი** | `deploy.env` → `DOCKER_NETWORK` | `<repo-slug>-net` |
| **გენერაცია** | `compose-gen` | `catalog.json` templates: `{api_service}`, `{app_service}` — placeholder-ები |

`geostat init` repo ფოლდერის სახელიდან (slug) ავსებს `deploy.env`-ში `COMPOSE_*`-ს (მაგ. `my-app-api`), არა ფიქსირებული ბრენდი.

Kit scaffold-ში აკრძალულია პროექტ-სპეციფიკური სახელები (ტესტი: `kits/geostat-kit/tests/test_scaffold_abstract_names.py`).

GCP / სპეციფიკური ინტეგრაციები — optional profile: [gcp-credentials.md](../kits/geostat-kit/scaffold/ops/config/profiles/gcp-credentials.md).

---

## Catalog შაბლონები

| ფაილი scaffold-ში | `init` default | შიგთავა |
|---------------------|----------------|---------|
| `ops/compose/catalog.full.json` | **კი** (სრული stack) | API + worker + frontend + `ops/compose/stack` — როგორც ამ monorepo-ში |
| `ops/compose/catalog.minimal.json` | `init -MinimalCatalog` | მხოლოდ backend API dev |

თუ `ops/compose/catalog.json` უკვე არსებობს, init **არ გადაწერს** (გარდა `-ForceExamples`-ისა).

---

## Seed — რა env ფაილები იქმნება

| წყარო | სამიზნე (თუ არ არსებობს) |
|--------|-------------------------|
| `ops/config/deploy.env.example` | `ops/config/deploy.env` |
| `ops/config/frontend/.env.example` | `.env.dev`, `.env.prod` |
| `ops/config/frontend/.env.deploy.example` | `.env.deploy` |
| `ops/config/frontend/nginx.env.example` | `nginx.env` |
| `ops/config/backend/.env.example` | `.env.dev`, `.env.prod` |
| `ops/config/backend/.env.deploy.example` | `.env.deploy` |
| `google-credentials.json.example` | `google-credentials.json` |

არსებული `deploy.env`, `.env.dev`, SSH keys — **არ იშლება** (უსაფრთხო merge).

---

## Flags

| Flag | მოქმედება |
|------|-----------|
| `-MinimalCatalog` | `catalog.minimal.json` ნაცვლად full stack-ისა |
| `-SkipComposeGen` | არ გაუშვას `compose/build.py` |
| `-SkipSeed` | არ შექმნას env ფაილები examples-დან |
| `-SkipNginxGen` | გამოტოვოს nginx-gen |
| `-SkipGitIgnore` | არ შეცვალოს `.gitignore` |
| `-ForceExamples` | გადაწეროს არსებული scaffold/env/catalog |

---

## Init-ის შემდეგ (შევსება)

1. **`ops/config/deploy.env`** — `DEPLOY_SERVER`, `DEPLOY_PROJECT`
2. **`ops/config/backend/.env.*`** — `GEMINI_API_KEY`, `GCP_PROJECT_ID`, …
3. **`ops/config/frontend/.env.prod`** — `VITE_API_URL` production-ისთვის
4. **`ops/config/backend/google-credentials.json`** — რეალური GCP JSON (არა example)
5. **SSH** — [ops/config/ssh/README.md](../ops/config/ssh/README.md)
6. სურვილისამებ: **`ops/compose/catalog.json`** — სერვისების სახელები, worker on/off
7. თუ catalog შეცვალე: `.\tools\geostat.ps1 compose-gen`

```powershell
.\tools\geostat.ps1 stack up -d --build    # ლოკალური სრული stack
.\tools\geostat.ps1 fe check
.\tools\geostat.ps1 be check
.\tools\geostat.ps1 layout                 # path-ების შემოწმება
```

---

## ხელით vs init

| ხელით (ძველი) | `geostat init` |
|---------------|----------------|
| `apply-scaffold.ps1` | ნაბიჯი 1 (ავტომატური) |
| `copy *.example` × N | ნაბიჯი 3 seed |
| `compose-gen` | ნაბიჯი 5 |
| ცალკე checklist | ნაბიჯი 7 ტერმინალში |

თუ გინდა მხოლოდ ფოლდერების ხე compose-ის გარეშე:

```powershell
powershell -File kits\geostat-kit\scaffold\apply-scaffold.ps1
```

---

## გადმოწერა სხვა პროექტში

1. დააყენე `kits/geostat-kit` (copy ან git submodule)
2. repo root-ში: `.\tools\geostat.ps1 init` (შექმნის `tools/` თუ არ არსებობს)
3. დაამატე აპის კოდი `apps/frontend/`, `apps/backend/`
4. შეავსე secrets + გაუშვი `compose-gen` თუ catalog შეცვალე

სრული adoption: [kits/geostat-kit/docs/ADOPTION-LINE.md](../kits/geostat-kit/docs/ADOPTION-LINE.md)

---

## Related

| Doc | Topic |
|-----|--------|
| [CONFIG.md](CONFIG.md) | სად რა კონფიგი ცხოვრობს |
| [GEOSTAT-KIT-SETUP.md](GEOSTAT-KIT-SETUP.md) | ops + deploy სტრუქტურა |
| [COMPOSE.md](COMPOSE.md) | catalog → generated compose |
| [ops/config/README.md](../ops/config/README.md) | env ფაილების ხე |
| [scaffold/README.md](../kits/geostat-kit/scaffold/README.md) | scaffold ხის სია |
