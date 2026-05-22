# ADR 006: geostat-kit as a reusable package

## Status

Accepted

## Context

Deploy, compose, env, and manage logic lived under `scripts/ops/` mixed with project paths. New teams would copy-paste and drift. Application artifacts (nginx CSP, catalog services) were adjacent to generic SSH/Gradle tooling.

## Decision

1. Extract reusable code to **`kits/geostat-kit/`**.
2. Bind projects with **`geostat.ops.json`** (manifest).
3. Keep **project-only** assets outside the package: `ops/config/`, `ops/compose/catalog.json`, nginx template paths in manifest `adapters.nginx`.
4. **`tools/geostat`** delegates to `kits/geostat-kit/cli/`; project `scripts/` holds CI only.
5. Removed duplicate `scripts/lib`, `scripts/compose`, `scripts/ops` trees from the consumer repo.
5. Module entrypoints are **`tools/geostat`** + `ops.config.*` at module roots; drivers in `kits/geostat-kit/drivers/`.

## Consequences

- Other repos can submodule `kits/geostat-kit` and add a manifest without copying 40+ scripts.
- CI shellchecks `kits/geostat-kit/toolkit/` as the source of truth.
- Breaking changes to the package bump `VERSION` and are documented in `kits/geostat-kit/CHANGELOG.md` (optional).

## Non-goals

- Publishing to npm/PyPI in this ADR (path/submodule is enough).
- Moving `scripts/infra` or `ops/ci` into the package (stay project-specific until generalized).
