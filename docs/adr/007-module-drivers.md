# ADR 007: Module drivers by stack type

## Status

Accepted (amended — extensible multi-stack)

## Context

After extracting `geostat-kit`, deploy/manage scripts still lived under per-module `scripts/`. Teams need extension points: Java API today, Node API tomorrow, other languages, **without** coupling driver choice to folder names `backend` / `frontend`.

## Decision

1. **`drivers/<type>/`** in the package — `java-boot`, `node-vite`, future `node-api`, `go-fiber`, …
2. **`geostat.ops.json`** — each module has required **`type`** + **`path`** (module id is arbitrary: `backend`, `bff`, `gateway`).
3. **`drivers/registry.json`** — type → `runtime`, `roles` (hint), `commands` (capabilities).
4. **Resolution** — `lib/driver_api.py` (single source); Bash/PowerShell wrappers; **no default** `backend`→java-boot inference without manifest.
5. **CLI**
   - `geostat mod <moduleId> <command> …` — always valid
   - `fe` / `be` — optional `cli.aliases` → module id
   - Subcommands validated against registry `commands` for that module’s type
6. **`stackDeploy.steps`** — manifest-driven full-stack remote deploy (not hardcoded be/fe).

## Naming

| Concept | Example | Meaning |
|---------|---------|---------|
| Module id | `backend`, `frontend`, `bff` | Project key in manifest |
| Driver type | `java-boot`, `node-api` | Stack toolchain in package |
| Role | `api`, `ui` | Documented in registry; not auto-assigned |

**Do not** name driver folders `front` / `back`. Same repo can run `java-boot` + `node-vite` or swap backend to `node-api` by changing only `modules.<id>.type`.

## Consequences

- New stack = new `drivers/<type>/` + registry entry + catalog/secrets as needed.
- Node backend does not replace or merge with `node-vite`; add `node-api` (or similar).
- Breaking: manifest must include `modules.*.type`; missing type fails fast.

## See also

- [kits/geostat-kit/drivers/README.md](../../kits/geostat-kit/drivers/README.md)
- [kits/geostat-kit/drivers/node-api/README.md](../../kits/geostat-kit/drivers/node-api/README.md) (placeholder)
