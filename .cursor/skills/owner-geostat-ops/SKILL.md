---
name: owner-geostat-ops
description: >-
  Implements and reviews geostat-kit / geostat.ops.json work: validate, migrate,
  vscode-gen, dev modes, drivers (java-boot, node-vite), CI seed, N-module CLI.
  Use when editing kits/geostat-kit, tools/geostat.ps1, ops/, or consumer
  geostat-chat-ai wiring.
---

# Owner geostat-ops context

Apply **senior engineer/architect** bar: Clean Architecture boundaries (kit vs apps), SOLID in `lib/` and drivers, manifest as contract — see `owner-architecture` skill.

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

მუშაობისას, **სენიორ არქიტექტორის ხედვით**, თუ გამოჩნდება:

- ახალი reusable **პაკეტის** / kit-ის იდეა (`kits/<name>/`, ახალი driver, manifest capability)
- არსებული **geostat-kit** (ან სხვა kit) **გაუმჯობესება** ან consumer ლოგიკის extract upstream

→ **ჯერ owner-თან ვისაუბრებთ** — არ ვაგრძელებთ ჩუმად, არ ვწერთ kit runtime-ში consumer brand/artifact/alias-ებს.

**ფორმატი:** `.cursor/rules/plan-automation-gate.mdc` — problem, baseline variant, plan ID (`P0-kit-*`, `BACKLOG`).

**Owner-ს ვკითხოთ (Georgian):**

- „ჩავინიშნოთ გეგმაში საბაზისო ვარიანტით?“
- „გავიტანოთ `kits/`-ში reusable package-ად (geostat-kit სტანდარტით)?“

Implement kit/package extraction **მხოლოდ** owner-ის approval-ის შემდეგ.

## Package principles (უცვლელი bar)

ვიცავთ იმ პრინციპებს, რომ kit **სხვა developer base-ებისა და აპლიკაციებისთვის** გამოყენებადი ops framework იყოს — არა ეს პროდუქტი.

| Kit runtime **არ არის** | Kit runtime **არის** |
|-------------------------|----------------------|
| consumer brand, product alias (`fe`, `be`, …) help/კოდში | manifest-driven CLI (`cli.aliases` consumer manifest-ში) |
| consumer repo path (`kits/geostat-kit`) runtime string-ებში | generic ops (`hybrid boot <alias\|moduleId>`) |
| artifacts, secrets, `.env` values, app/domain logic | drivers, compose-gen, tunnel, deploy paths |
| ფიქსირებული postgres/redis/qdrant სამეული | `stack.infra.services` + consumer `services/*.yml` |

**Reference:** `kits/geostat-kit/docs/PACKAGE-PRINCIPLES.md`, `tests/test_toolkit_hardcodes.py`, `.cursor/rules/kit-package-architecture.mdc`.

## Submodule install (share with others)

```bash
git submodule add https://github.com/aleksandre17/geostat-kit.git kits/geostat-kit
cd kits/geostat-kit && git checkout v1.0.0
```

Replace org/username in docs when publishing under a different account.
