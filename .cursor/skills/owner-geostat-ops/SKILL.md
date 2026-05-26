---
name: owner-geostat-ops
description: >-
  Implements and reviews geostat-kit / geostat.ops.json work: validate, migrate,
  vscode-gen, dev modes, drivers (java-boot, node-vite), CI seed, N-module CLI.
  Use when editing kits/geostat-kit, tools/geostat.ps1, ops/, or consumer
  geostat-chat-ai wiring.
---

# Owner geostat-ops context

Apply **senior engineer/architect** bar: Clean Architecture boundaries (kit vs apps), SOLID in `lib/` and drivers, manifest as contract — see `owner-architecture` skill. **Rules:** `kit-upstream.mdc`, `kit-package-architecture.mdc`, `plan-automation-gate.mdc` · Index: `.cursor/rules/README.md`

## Package

- Published kit: `geostat-kit` (git/submodule), version pinned by tag (e.g. `v1.0.0`).
- Consumer example: `geostat-chat-ai` — manifest at repo root, kit at `kits/geostat-kit`.

## Commands (project root)

```powershell
.\tools\geostat.ps1 validate
.\tools\geostat.ps1 migrate
.\tools\geostat.ps1 vscode-gen
.\tools\geostat.ps1 mod <moduleId> ...
.\tools\geostat.ps1 stack up -d --build
```

## Dev modes (pick explicitly)

| Mode | Docker | Run/debug |
|------|--------|-----------|
| Local host | No | VS Code launch / npm + Gradle |
| Local Docker | Yes | `geostat stack` / compose |
| Remote SSH | On server | `<alias> dev watch` — no local F5 |

See kit `docs/DEV-MODES.md` when details needed.

## Kit maintainer checks

```powershell
cd kits/geostat-kit
$env:PYTHONPATH = (Get-Location).Path
python -m pytest tests -q
.\scripts\dev-modes-verify.ps1 -SkipDocker
```

## Hard rules

- Kit runtime: manifest-only paths (`test_toolkit_hardcodes.py`).
- App code stays in `apps/`; secrets in `ops/config/`.
- No internal GitHub upload docs in the public package repo.
- Kit stays **agnostic & extensible** — `docs/PACKAGE-PRINCIPLES.md`; infra via `stack.infra.services` + `services/*.yml`, not `INFRA_PROFILES` env toggles.

## Package idea & upstream (owner gate)

ახალი kit / extract upstream → **ჯერ owner-თან** — ფორმატი: `plan-automation-gate.mdc`. Package bar: `kit-package-architecture.mdc`, `PACKAGE-PRINCIPLES.md`, `test_toolkit_hardcodes.py`.

**Owner-ს ვკითხოთ (Georgian):** „ჩავინიშნოთ გეგმაში საბაზისო ვარიანტით?“ / „გავიტანოთ `kits/`-ში reusable package-ად?“

Implement extraction **მხოლოდ** approval-ის შემდეგ.

## Submodule install (share with others)

```bash
git submodule add https://github.com/aleksandre17/geostat-kit.git kits/geostat-kit
cd kits/geostat-kit && git checkout v1.0.0
```

Replace org/username in docs when publishing under a different account.
