# Monorepo migration (optional)

The workspace is designed as a **logical monorepo**:

```
geostat-chat-ai/     ← workspace root (secrets, tools, ops/compose/stack)
├── apps/frontend/         ← may remain its own git repo
└── apps/backend/          ← may remain its own git repo
```

## Current state

- Shared secrets and tooling live at workspace root.
- `apps/frontend/.git` and `apps/backend/.git` may still exist independently.

## To unify into one git root

1. Back up both module repos.
2. From workspace root: `git init` (if not already).
3. Add root `.gitignore` (already ignores `ops/config/*` except examples).
4. Choose strategy:
   - **Subtree**: merge frontend/backend history into root.
   - **Fresh root**: single new history; archive old repos.
5. Point CI to root `.github/workflows/ci.yml`.
6. Remove nested `.git` in modules only after team agreement.

Until then, use `tools/geostat.ps1` and [docs/](.) as the single contract.
