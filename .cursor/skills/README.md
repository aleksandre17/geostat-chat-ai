# Project Cursor skills (geostat-chat-ai)

**Load:** on demand only (not every chat). **Rules:** one slim always-on hub — see [../rules/README.md](../rules/README.md).

| Skill | Role | Rules to pair |
|-------|------|----------------|
| [owner-agent-conduct](owner-agent-conduct/SKILL.md) | Communication, execution, verification | `owner-standards` hub |
| [owner-architecture](owner-architecture/SKILL.md) | Layout, secrets, compose, boundaries | `owner-standards-extended`, `zero-gap`, `owner-no-domain-hardcode` |
| [owner-geostat-ops](owner-geostat-ops/SKILL.md) | `geostat`, manifest, kit CLI | `kit-upstream`, `plan-automation-gate` |
| [owner-approved-stack](owner-approved-stack/SKILL.md) | Plan Q-*, Maven vs kits | `plan-automation-gate`, `zero-gap` |

**Rules index:** [../rules/README.md](../rules/README.md)

## User-folder mirror

Same-named skills may exist in `%USERPROFILE%\.cursor\skills\` for other workspaces. For **this** project, treat **repo** `.cursor/skills/` as source of truth; sync to user folder manually if needed.

Do **not** edit `~/.cursor/skills-cursor/` — Cursor built-ins only.

## Enable

Open workspace → **Settings → Rules / Skills** → `Developer: Reload Window` after changes.
