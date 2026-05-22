# Project CI (`ops/ci/`)

ეს არის **consumer repo**-ის CI სკრიპტები — არა `kits/geostat-kit` პაკეტის ნაწილი. პაკეტის generic helper-ები: `kits/geostat-kit/ci/`.

## `integration-stack.sh`

Docker Compose smoke: აწეხმებს API (+ worker თუ compose-შია) ლოკალურად CI-ში.

**ყველა path manifest-იდან** (`geostat.ops.json`):

| რა | საიდან |
|----|--------|
| API მოდული | `modules.*` სად `role: api` (ან `type: java-boot`) |
| App dir | `modules.<id>.path` → მაგ. `apps/backend` |
| Env files | `modules.<id>.secretsModule` → `ops/config/<folder>/.env.dev` |
| Kit prepare | `ci.prepareEnv` → `kits/geostat-kit/ci/prepare-integration-env.sh` |
| Health wait | `ci.waitHealth` → `kits/geostat-kit/ci/wait-health.sh` |

### ლოკალურად

```bash
# Linux / Git Bash (Docker running)
bash ops/ci/integration-stack.sh
```

```powershell
# Windows — Git Bash რეკომენდებული
& "C:\Program Files\Git\bin\bash.exe" ops/ci/integration-stack.sh
```

### GitHub Actions (მაგალითი)

```yaml
integration-compose:
  runs-on: ubuntu-latest
  steps:
    - uses: actions/checkout@v4
    - uses: actions/setup-java@v4
      with:
        distribution: temurin
        java-version: "21"
    - run: bash kits/geostat-kit/ci/prepare-integration-env.sh
    - run: python3 kits/geostat-kit/compose/build.py
    - run: bash ops/ci/integration-stack.sh
```

### გარემოს ცვლადები

| Variable | Default | აღწერა |
|----------|---------|--------|
| `API_PORT` | `8090` | API health URL პორტი |
| `WORKER_PORT` | `8091` | Worker actuator (თუ სერვისი არსებობს) |
| `COMPOSE_FILE` | `docker-compose.dev.yml` | Compose ფაილი მოდულის root-ში |

### სხვა მოდულის გამოყენება

თუ API არაა `backend` id, manifest-ში დააყენე `"role": "api"` — სკრიპტი თავად იპოვის მოდულს. UI-სთვის ცალკე job: `npm run build` under `modules.<ui>.path`.

## `setup-root-git.ps1` (optional)

ერთჯერადი `git init` root-ზე — იხ. `docs/MONOREPO.md`.

## დოკუმენტაცია

- [docs/CI.md](../../docs/CI.md) — სრული CI ხაზი
- [docs/CONFIG.md](../../docs/CONFIG.md) — კონფიგის რუკა
- [kits/geostat-kit/docs/PACKAGE-ARCHITECTURE.md](../../kits/geostat-kit/docs/PACKAGE-ARCHITECTURE.md) — პაკეტის ზღვები
