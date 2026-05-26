# Cursor rules index (geostat-chat-ai)

**Hub (always-on, ~minimal tokens):** [owner-standards.mdc](owner-standards.mdc) — checklist only.

**Extended hub detail (on demand):** [owner-standards-extended.mdc](owner-standards-extended.mdc) — Senior bar table, identity detail.

Other rules load by **glob** (matching files open) or **agent-request** (description). Full prose preserved — not deleted.

| Rule | Load trigger | Unique content |
|------|----------------|----------------|
| [owner-standards.mdc](owner-standards.mdc) | **Always** | Active checklist, no degradation, law index |
| [owner-standards-extended.mdc](owner-standards-extended.mdc) | Agent / architecture tasks | Senior bar table, identity detail |
| [zero-gap-architecture.mdc](zero-gap-architecture.mdc) | `apps/**`, `kits/**`, `libs/**`, `docs/plan/**`, `ops/**` | Single pipeline, gap detection |
| [owner-no-domain-hardcode.mdc](owner-no-domain-hardcode.mdc) | `apps/**` | Hardcode, wrong coupling, port gate |
| [max-capability-collaboration.mdc](max-capability-collaboration.mdc) | `apps/**`, `ops/**` | Async/events, ingestion prod |
| [plan-automation-gate.mdc](plan-automation-gate.mdc) | Agent-request | Automation/package gate |
| [kit-upstream.mdc](kit-upstream.mdc) | `kits/**`, `tools/**`, `geostat.ops.json` | Consumer vs kit split |
| [kit-package-architecture.mdc](kit-package-architecture.mdc) | `kits/**` | Kit SOLID, agnostic bar |

## Overlap map (read once)

| Topic | Canonical rule | Elsewhere |
|-------|----------------|-----------|
| Senior bar table | `owner-standards-extended` | hub → ref only |
| No degradation (strict) | `owner-standards` § No degradation | `zero-gap` → ref only |
| Single pipeline | `zero-gap-architecture` | skills → ref only |
| Hardcode / ports / coupling | `owner-no-domain-hardcode` | hub → ref only |
| Async / crawl prod | `max-capability-collaboration` | hub → ref only |
| Kit upstream | `kit-upstream` | `kit-package-architecture` → ref |
| Plan / package gate | `plan-automation-gate` | kit rules § Owner gate → ref |

## Skills

On demand only — [../skills/README.md](../skills/README.md)

## Token note

Only **one** rule is `alwaysApply: true`. All other rule files remain in repo with full text; Cursor attaches them when globs match or the agent selects by `description`.
