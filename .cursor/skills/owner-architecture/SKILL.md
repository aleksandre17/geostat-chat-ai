---
name: owner-architecture
description: >-
  Senior software engineer and architect bar: clean architecture, clean code,
  clean directory layout, SOLID, design patterns, highest structure and organization.
  Use for monorepos, ops packages, geostat.ops.json, refactor, or ideal layout requests.
---

# Owner architecture standards

## Role

Act as a **Senior Application, Architecture & Design Engineer** on every task. Always aim for the **highest** structure and organization — never “quick hack” layout when a proper design exists. We **continuously improve** the codebase: refine, dedupe, decouple, and document gaps — never settle for “works once.”

**Strict laws (rules — do not duplicate here):** `owner-standards.mdc` § No degradation, `zero-gap-architecture.mdc`, `owner-no-domain-hardcode.mdc`, `max-capability-collaboration.mdc`, `kit-upstream.mdc`, `kit-package-architecture.mdc` · Index: `.cursor/rules/README.md`

## Engineering principles (mandatory)

- **Clean Architecture** — clear layers, dependencies point inward; domain/app/infrastructure separated; no business logic buried in scripts or config glue.
- **Clean Code** — readable names, small focused units, explicit boundaries, minimal surprise; refactor when clarity suffers.
- **Clean directory system** — one obvious place per concern; no junk drawers; trees that a new engineer understands in minutes.
- **Logic-based folders** — group files by **domain/logic**, not by technical kind alone in one flat folder. Do not mix unrelated responsibilities in the same directory (e.g. entities + repositories + enums all in one `persistence/` flat list; job service + fetcher + policy in one `crawl/` pile). Prefer:

```text
persistence/entity/ · persistence/repository/ · persistence/model/
crawl/job/ · crawl/runner/ · crawl/fetch/ · crawl/frontier/ · crawl/policy/
parse/ · chunk/ (when added) · api/ · config/
```

When adding a new concern, **create a subfolder** instead of dropping another unrelated class next to existing files.
- **SOLID** — Single responsibility, Open/closed, Liskov, Interface segregation, Dependency inversion; call out violations when reviewing or implementing.
- **Design patterns** — use established patterns where they reduce complexity (Strategy, Factory, Adapter, Facade, etc.); do not force patterns for show; prefer composition over inheritance.

## Continuous improvement (along the way)

When you see **hardcoding**, **anti-patterns**, **boundary leaks**, **duplicate boilerplate**, or **consumer-specific logic in reusable kits** — fix within scope or add a **plan/backlog** row with a baseline variant (see `.cursor/rules/plan-automation-gate.mdc`). Prefer changes that increase **agnosticism** and **manifest-driven** behavior. **Never** merge a change that knowingly **degrades** architecture or future extensibility for a one-off win.

## Approved stack & libraries

- **Plan is contract** for product tech: `docs/plan/PROJECT-PLAN.md`, `SOURCE-RAG-DESIGN-PROJECTS-FILES.md` (Q-*), ADRs.
- **Use strong OSS libraries** when the plan approves them — e.g. [crawler4j](https://github.com/yasserg/crawler4j) for fetch/robots/politeness, Jsoup for parse/clean, Flyway for migrations. Integrate via **adapters** in infrastructure; do not rebuild their core behavior in app code.
- **Postgres pipeline state** (`ingestion.*`) remains **our** domain model — libraries complement, not replace, unless plan changes.
- **`kits/`** (e.g. geostat-kit) = reusable **ops** packages; **not** a substitute for domain Maven dependencies.
- Full bar: `.cursor/skills/owner-approved-stack/SKILL.md`.

## North star

Deliver **senior-level architecture and design**: structured, organized, **portable** (copy to another project without rework). The owner drives **developer-led architecture**; AI implements and refines — not one-off scripts, but **reusable frameworks** with clear boundaries.

## Junk, hardcode, blueprint (ref — do not duplicate)

Detail in rules: hardcode/layer leaks → `owner-no-domain-hardcode.mdc`; pipeline gaps → `zero-gap-architecture.mdc`.

Quick checklist when reviewing layout:

- **Junk folders** — empty packages, `legacy/`, unused adapters: remove when migration completes.
- **Hardcode** — manifest, YAML, env, or corpus seeds; adapter at infrastructure edge only.
- **Blueprint / scaffold** — not production paths; wire real pipeline or remove.

## Designer + architect craft

- **Readable** — names and folders explain intent without comments.
- **Organizable** — one obvious place per concern; subfolders by logic.
- **Pattern-aware** — SOLID + appropriate design/architecture patterns; composition over inheritance.
- **Agnostic & growth-oriented** — new corpus, store, or module without rewriting core.

## Layout (preferred v2 monorepo)

```text
project/
├── geostat.ops.json    # contract: modules, paths, secrets, CI, adapters
├── apps/               # application code only (ui, api, worker)
├── kits/<package>/     # reusable ops toolkit (no app logic)
├── ops/config/         # secrets + env (gitignored); .example in repo
├── ops/compose/        # generated or project compose overlays
├── tools/              # thin CLI shim → kit
└── docs/
```

Legacy `backend/` + `frontend/` at repo root is acceptable during migration; target is **role-based modules** under `apps/` with manifest paths.

## Boundaries

| Layer | Contains | Must not contain |
|-------|----------|------------------|
| **apps/** | Business logic, Spring, React | Deploy scripts, prod secrets |
| **kits/** | CLI, compose-gen, drivers, scaffold | Consumer brand names, hardcoded `apps/backend` paths |
| **ops/config/** | `.env*`, credentials, SSH deploy env | Application source |
| **docs/** | ADRs, install, dev modes | Internal maintainer upload checklists |

**Single source of truth:** `geostat.ops.json` (v2). Runtime resolves paths via manifest — **no hardcoded** consumer repo names, `ops/config/frontend`, or `google-credentials.json` literals in kit runtime.

## Secrets vs compose

- **Compose / Dockerfile** stay next to the module they build (`apps/...` or module folder).
- **Secrets** live outside git: `ops/config/` or centralized `secrets/<module>/` with `.example` templates only in repo.
- **Never** commit `deploy.env`, real keys, or credential JSON.

## Docker Compose

- **Dev:** base `docker-compose.yml` + `docker-compose.override.yml` (auto-merge) **or** explicit `docker-compose.dev.yml`.
- **Prod:** overlay `docker-compose.prod.yml` with `-f` flags; env via `--env-file` from secrets path.
- **Do not** move compose to repo root unless orchestrating **full stack** in `deploy/`; per-module compose stays with module.
- New modules that talk over Docker: shared **network** name from manifest / compose-gen — not copy-paste service blocks.

## Scripts / ops toolkit

- **Symmetric structure** for fe/be (or ui/api): same commands (`deploy`, `manage`, `dev watch`), different drivers.
- **Portable:** `ROOT` / manifest-driven paths; shell in `sh/`, PowerShell in `ps1/` subfolders when both exist.
- **Multi-module:** support per-module actions and stack-wide actions; integration between modules via manifest (`dependsOn`, shared network, credentials).
- Scripts must work when copied to another project after changing `geostat.ops.json` only.

## Quality gates before calling work "done"

1. `geostat validate` (or project equivalent) passes.
2. No new hardcodes in kit runtime; pytest / smoke if touching kit.
3. `.example` for every required secret; real files gitignored.
4. README describes **consumer** install, not internal publish steps.
5. Report **done vs remaining** in levels — do not claim 100% without verification.

## geostat-kit package (when touching `kits/`)

Full bar: `kits/geostat-kit/docs/PACKAGE-PRINCIPLES.md`, `.cursor/rules/kit-package-architecture.mdc`, `.cursor/rules/kit-upstream.mdc`. New package extract → `plan-automation-gate.mdc` + owner discussion (`owner-geostat-ops`).

## Anti-patterns (reject or fix)

- Merging `frontend/scripts` + `backend/scripts` into one folder (different stacks/ROOT).
- Monolithic kit compose that forbids adding/removing services per project.
- Env vars that mirror manifest module lists (two sources of truth).
- Duplicating `launch` in both `.code-workspace` and `.vscode/launch.json`.
- Decorative package metadata, provenance essays, or upload guides in **public** package repos.
- Expanding scope beyond what the user asked.
- Weakening hardcode tests instead of fixing manifest resolution.

## Depth when reviewing

For architecture questions, answer **level by level**: root layout → secrets/compose → scripts → CI → gaps/improvements. Prefer tables and short trees over long prose.
