# ADR 008: Root layout consolidation (3-plane model)

## Status

Accepted (Phase 1 implemented in geostat-chat-ai)

## Context

Repo root has many sibling folders (`frontend`, `backend`, `secrets`, `infra`, `deploy`, `tools`, `packages`, `scripts`, `docs`, `logs`, `shared`, …). Each has a valid role, but **cognitive load** is high: newcomers cannot tell “app” vs “ops” vs “generated” at a glance.

Requirements:

- Keep **full ops functionality** (`geostat-kit`, drivers, deploy, compose-gen, secrets contract).
- Keep **module paths** stable enough for Gradle/npm and CI during migration.
- Avoid hiding secrets inside the reusable package.
- Support **other repos** adopting the same kit via `geostat.ops.json` path overrides.

## Decision

Adopt a **3-plane root model** (target layout). Migration is **phased**; until Phase 3 completes, legacy paths remain valid via `geostat.ops.json`.

### Target root (4 top-level items + manifest)

```text
<repo>/
├── apps/                      # Application plane
│   ├── apps/frontend/
│   └── apps/backend/
├── kits/                      # Reusable toolkits (submodule/vendor)
│   └── geostat-kit/           # was kits/geostat-kit/
├── ops/                       # This project’s operations only
│   ├── config/                # was ops/config/
│   ├── compose/
│   │   ├── catalog.json
│   │   └── stack/             # GENERATED full-stack compose
│   ├── cli/                   # geostat.ps1
│   └── ci/
├── docs/
├── tools/                     # shim → ops/cli
└── geostat.ops.json           # v2
```

**Not** `ops/kit/` — kits and ops are siblings (see discussion in PR / ROOT-LAYOUT.md).

### Plane rules

| Plane | Contains | Must not contain |
|-------|----------|------------------|
| **apps/** | Source, Dockerfiles, `ops.config.*`, module-generated `docker-compose*.yml` | Production secrets |
| **ops/** | Kit, env contract, catalog, stack compose output, CLI entry, project CI | Application business logic |
| **docs/** | Human docs, ADRs | Generated compose |

### What merges vs stays separate

| Merge | Rationale |
|-------|-----------|
| `ops/compose` + `ops/compose/stack` → `ops/compose/{catalog,stack}` | One “compose” story: source catalog + generated stack |
| `tools` + `ops/ci` → `ops/cli` + `ops/ci` | All automation under ops |
| `secrets` → `ops/config` | Single config tree (name `config` = plane; values still gitignored) |

| Keep separate | Rationale |
|---------------|-----------|
| `apps/frontend` vs `apps/backend` | Different stacks, drivers, deploy paths |
| `ops/kit` vs `ops/config` | Reusable package vs project secrets |
| Module `docker-compose*.yml` under each app | Local dev paths (`../../ops/config`) differ from stack compose |

### Manifest mapping (v2 example)

```json
{
  "version": 2,
  "package": "ops/kit",
  "secrets": "ops/config",
  "compose": {
    "catalog": "ops/compose/catalog.json",
    "syncModules": "apps/backend/ops.modules"
  },
  "stack": { "composeDir": "ops/compose/stack" },
  "modules": {
    "backend": { "path": "apps/backend", "secretsModule": "backend", ... },
    "frontend": { "path": "apps/frontend", "secretsModule": "frontend", ... }
  }
}
```

`geostat-kit` already resolves paths from the manifest — **no hardcoded root folder names** in drivers.

## Immediate cleanup (Phase 0 — no manifest change)

| Item | Action |
|------|--------|
| Root `shared/` (empty) | Remove; Gradle library stays `apps/backend/shared/` |
| Root `logs/` (if empty) | Remove; use `apps/backend/logs`, `apps/frontend/logs` |
| Root `bash.exe.stackdump` | Delete; add to `.gitignore` |

## Phased migration

| Phase | Change | Risk |
|-------|--------|------|
| **0** | Delete unused root dirs; document 3-plane in ARCHITECTURE | Low |
| **1** | `ops/compose/stack` ← move `ops/compose/stack`; `ops/compose/catalog` ← `ops/compose` | Medium — update catalog paths in targets, docs, CI |
| **2** | `ops/cli`, `ops/ci` ← `tools`, `ops/ci`; shim: root `tools/geostat.ps1` → delegate | Low if shim kept |
| **3** | `ops/config` ← `secrets`; `apps/*` ← `frontend`,`backend` | High — Spring imports, Vite env paths, SSH docs |
| **4** | `ops/kit` ← `kits/geostat-kit`; update submodule docs | Medium |

Each phase: one PR, `compose-gen`, `run-kit-tests.sh`, CI green. Shims at old paths optional for one release.

## Alternatives considered

### A. Flatten everything into `packages/`

Rejected: blurs vendored kit with project secrets and app code.

### B. Single `docker/` tree for all compose

Rejected: module compose must live next to Dockerfiles; stack compose is a different target set.

### C. Keep current layout, only document

Valid short-term; does not reduce root folder count.

## Consequences

### Positive

- Root shows **apps | ops | docs** — senior-readable structure.
- Onboarding: “secrets and deploy live under `ops/`”.
- `geostat init` scaffold can emit 3-plane tree for new repos.

### Negative

- Migration cost: hundreds of path references (docs, catalog `fmt` paths, Spring `../secrets`).
- Existing clones need one-time path update or shims.
- `packages/` convention familiar to Node users — `ops/kit` needs documenting for submodule path.

## Non-goals

- Merging frontend and backend into one app folder.
- Moving kit secrets or catalog into `apps/`.
- Changing Docker **service names** (still `COMPOSE_*` in config).

## References

- [CONFIG.md](../CONFIG.md)
- [GEOSTAT-INIT.md](../GEOSTAT-INIT.md)
- [006-geostat-kit-package.md](006-geostat-kit-package.md)
- [MONOREPO.md](../MONOREPO.md)
