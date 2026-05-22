# CI — integration და compose smoke

პროექტის CI ცალკდება ორ ფენად:

| ფენა | სად | როლი |
|------|-----|------|
| **პაკეტი** | `kits/geostat-kit/ci/` | generic: `prepare-integration-env`, `wait-health` |
| **პროექტი** | `ops/ci/` | რა compose ავიდეხოთ, რომელი მოდული — **manifest-ით** |

`geostat.ops.json` → `ci`:

```json
"ci": {
  "integration": "ops/ci/integration-stack.sh",
  "prepareEnv": "kits/geostat-kit/ci/prepare-integration-env.sh",
  "waitHealth": "kits/geostat-kit/ci/wait-health.sh"
}
```

## `ops/ci/integration-stack.sh` (manifest-driven)

სკრიპტი **არ იყენებს** hardcoded `apps/backend` ან `ops/config/backend`:

1. `source kits/geostat-kit/lib/project.sh` + `env.sh`
2. `API_MOD=$(geostat_module_id_for_role api)` — პირველი `modules.*` სად `role: api`
3. `BE=$(geostat_module_path "$API_MOD")` — მაგ. `apps/backend` ან `services/api`
4. `SECRETS_API=$(geostat_secrets_dir_for_module "$API_MOD")` — მაგ. `ops/config/backend`
5. `prepare-integration-env` + `compose-gen`
6. `docker compose` მოდულის root-ში, env: `$SECRETS_API/.env.dev` + `ops/config/deploy.env`
7. `wait-health` — API (+ worker თუ compose-ში განსაზღვრულია)

### ლოკალურად გაშვება

```bash
export GEOSTAT_PROJECT_ROOT="$(pwd)"   # optional — სკრიპტი თავად პოულობს root-ს
bash ops/ci/integration-stack.sh
```

Windows (Git Bash):

```powershell
& "${env:ProgramFiles}\Git\bin\bash.exe" ops/ci/integration-stack.sh
```

### მოთხოვნები

- Docker + Docker Compose
- `python3` (ან `py -3`) — `compose/build.py`
- `bash` + `curl` — health wait
- `ops/config/<api-secretsModule>/.env.dev` — `geostat init` / `ci_prepare` seed-ის შემდეგ

### სხვა API მოდული (მაგ. `services/api`)

`geostat.ops.json`:

```json
"modules": {
  "api": {
    "role": "api",
    "type": "java-boot",
    "path": "services/api",
    "secretsModule": "api"
  }
}
```

იგივე `integration-stack.sh` — ცვლილება სკრიპტში არ სჭირდება.

## რეკომენდებული pipeline (სრული)

| ნაბიჯი | ბრძანება |
|--------|----------|
| Catalog drift | `python3 kits/geostat-kit/compose/build.py` + `git diff` on `**/docker-compose*.yml` |
| Kit tests | `cd kits/geostat-kit && PYTHONPATH=. pytest tests -q` |
| Frontend build | `cd apps/frontend && npm ci && npm run build` (ან `modules.<ui>.path`) |
| Backend build | `cd apps/backend && ./gradlew build` (ან `modules.<api>.path`) |
| Integration | `bash ops/ci/integration-stack.sh` |
| Ops smoke | `bash kits/geostat-kit/scripts/module-ops-smoke.sh` |

UI/backend path-ები წაიკითხე manifest-იდან (`modules.frontend.path`, `modules.backend.path`) — CI build job-ებში.

## GitHub Actions მაგალითი

```yaml
name: ci
on: [push, pull_request]

jobs:
  integration-compose:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-python@v5
        with:
          python-version: "3.12"
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "21"
      - name: Seed ops/config for CI
        run: bash kits/geostat-kit/ci/prepare-integration-env.sh
      - name: Compose-gen
        run: python3 kits/geostat-kit/compose/build.py
      - name: Integration stack smoke
        run: bash ops/ci/integration-stack.sh
```

## პაკეტის helper-ები

| Script | რა აკეთებს |
|--------|------------|
| `kits/geostat-kit/ci/prepare-integration-env.sh` | `lib/ci_prepare.py` — ყველა `modules.*` seed `.example` → working copies |
| `kits/geostat-kit/ci/wait-health.sh` | `curl` loop until body matches pattern |

დეტალები: [kits/geostat-kit/ci/README.md](../kits/geostat-kit/ci/README.md), [PACKAGE-ARCHITECTURE.md](../kits/geostat-kit/docs/PACKAGE-ARCHITECTURE.md).

## დაკავშირებული

- [CONFIG.md](CONFIG.md) — env / paths რუკა
- [GEOSTAT-INIT.md](GEOSTAT-INIT.md) — პირველი bootstrap
- [ops/ci/README.md](../ops/ci/README.md) —  reference
