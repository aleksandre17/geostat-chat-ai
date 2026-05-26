# RAG eval harness (RAG-U12)

Golden-set regression for retrieval quality.

| Artifact | Purpose |
|----------|---------|
| `baseline.json` | Active CI regression baseline (`--write-baseline` updates this) |
| `baseline.yaml-frozen.json` | **Frozen** pre-cutover YAML-era reference — never auto-updated |
| `reports/` | Daily run output (`YYYY-MM-DD.json`) |

Golden set: **150 queries** after Flyway V14 (131 curated + V4/V7 seed). Stored in `ingestion.evaluation_query` with `expected_intent` + `difficulty`.

## Run

```powershell
# Requires retrieval + ingestion (db profile) up
.\ops\ci\rag-eval-harness.ps1

# Capture current stack as new baseline (owner action after verified good run)
.\ops\ci\rag-eval-harness.ps1 -WriteBaseline

# Full P1-15 gate: prerequisites + lifecycle sync + harness + cutover checklist
.\ops\ci\rag-eval-gate.ps1 -WriteBaseline

# YAML-only baseline capture (enrichment OFF)
.\ops\ci\rag-eval-gate.ps1 -AllowNotReady -WriteBaseline

# Load queries only (no retrieval calls)
.\ops\ci\rag-eval-harness.ps1 -DryRun -SkipMinQueries
```

## Cutover gate (P1-15) — dual baseline

**Step 0 (once, YAML era — do not skip):**

```powershell
# chat-api: GEOSTAT_CHAT_CATALOG_SOURCE=yaml, enrichment OFF
.\ops\ci\rag-eval-freeze-yaml-baseline.ps1
# → ops/eval/baseline.yaml-frozen.json (real metrics, not seed values)
```

Retrieval eval and derived catalog are **separate checks** (spec §9 + RAG-U02):

| Check | Script / flag | Pass criteria |
|-------|----------------|---------------|
| Retrieval regression | `rag-eval-gate.ps1` | metrics within 5% of `baseline.json` |
| YAML-era floor | `-CompareYamlReference` | metrics ≥ `baseline.yaml-frozen.json` (strict, 0% drop) |
| Derived catalog links | `-RunCatalogSmoke` | chat-api `source=derived` + catalog link cards |

**Owner sequence** (live stack, enrichment ON):

```powershell
# Status + guided steps (recommended)
.\ops\ci\rag-p1-cutover.ps1 -Step status
.\ops\ci\rag-p1-cutover.ps1 -Step run -WaitForDerived -WriteBaseline

# Or step-by-step:
.\ops\ci\rag-eval-freeze-yaml-baseline.ps1
.\ops\ci\rag-p1-cutover.ps1 -Step prep
# chat-api: derived + JDBC, restart
.\ops\ci\rag-p1-cutover.ps1 -Step gate -WriteBaseline
```

After pass: owner OK → keep `derived` → **only then** delete `topics.yaml` (spec §17).

Manifest: `geostat.ops.json` → `ci.ragEvalFreezeYamlBaseline` (step 0), `ci.ragEvalGate`, `ci.ragEvalHarness`, `ci.ragEvalSmoke`, `ci.ragDerivationCutoverPrep`, `ci.chatDerivedCatalogSmoke`.

Spec: [RAG-DERIVATION-ARCHITECTURE.md §9](../plan/RAG-DERIVATION-ARCHITECTURE.md)
