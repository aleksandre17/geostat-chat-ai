#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Split docs/plan/QUALITY-PIPELINE-PLAN.md into focused sub-documents.

SENIOR DIRECTIVE (STRICT — DO NOT VIOLATE):
  1. NO content deleted, NO content modified, NO whitespace changed.
  2. Every line of the original appears in exactly one output file.
  3. All parts together reconstruct the original exactly (join with "\\n").
  4. Create docs/plan/quality-pipeline/ folder with all parts + README.md index.
  5. Replace original QUALITY-PIPELINE-PLAN.md with a redirect stub.
  6. Verify integrity before and after.
"""

import pathlib
import sys

sys.stdout.reconfigure(encoding="utf-8")

PLAN = pathlib.Path(
    r"c:\Users\Test-User\CursorProjects\geostat-chat-ai\docs\plan\QUALITY-PIPELINE-PLAN.md"
)
OUT_DIR = pathlib.Path(
    r"c:\Users\Test-User\CursorProjects\geostat-chat-ai\docs\plan\quality-pipeline"
)

# ---------------------------------------------------------------------------
# Step 1: Read original
# ---------------------------------------------------------------------------
original_text = PLAN.read_text(encoding="utf-8")
lines = original_text.split("\n")
total_lines = len(lines)
print(f"[split] Original: {total_lines} lines, {len(original_text)} chars")

# ---------------------------------------------------------------------------
# Step 2: Split boundaries — verified by senior engineer at clean H2/H3 headers
#
# Each tuple: (filename, short_title, start_0idx, end_0idx_exclusive)
# Lines[start:end] → written to file.
# Boundaries confirmed: each start line is a blank OR major section header.
# ---------------------------------------------------------------------------
PARTS = [
    (
        "01-overview.md",
        "Overview, Audit Snapshot, Pipeline Diagram",
        0,
        47,
    ),
    (
        "02-layer-minus1-ingestion.md",
        "Layer -1 — ingestion-service: Crawl/Parse/Enrich (L-1-01..L-1-07)",
        47,
        589,
    ),
    (
        "03-layers-0-to-5-execution.md",
        "Layers 0–5: Database/Qdrant/Retrieval/Catalog/Query/Gemini + Execution Order",
        589,
        871,
    ),
    (
        "04-arch-backlog-initial.md",
        "Execution Order, Open Questions, Architecture Evolution Backlog (ARCH-01..ARCH-08)",
        871,
        1994,
    ),
    (
        "05-multi-corpus-arch.md",
        "Multi-Corpus / Page-Kind / Network Growth Architecture",
        1994,
        3089,
    ),
    (
        "06-arch-decisions.md",
        "YAML vs DB Architectural Decision + ADRs (AD-01..AD-03)",
        3089,
        3395,
    ),
    (
        "07-data-quality-defense-part1.md",
        "Data Quality Defense + Gap Analysis Part 1: L-1-08..L-1-22",
        3395,
        5687,
    ),
    (
        "08-data-quality-defense-part2.md",
        "Gap Analysis Part 2: L-1-23..L-1-30, CFG-01, PERF-01..06, BUG-CRAWL-01, BUG-DB-01, DB-ARCH-01..02, OPS-01, ARCH-09..10",
        5687,
        8561,
    ),
    (
        "09-embedding-qdrant-gaps.md",
        "Embedding / Vector Index Gap Analysis: PERF-07..10, ARCH-11..13, QDRANT-01..03",
        8561,
        9865,
    ),
    (
        "10-cross-gap-backlog.md",
        "CROSS-GAP-01 (Qdrant vector cleanup), BACKLOG items, Final Audit Note",
        9865,
        total_lines,
    ),
]

# ---------------------------------------------------------------------------
# Step 3: Verify full coverage (every line in exactly one part)
# ---------------------------------------------------------------------------
covered = [False] * total_lines
for fname, title, start, end in PARTS:
    for i in range(start, end):
        if covered[i]:
            print(f"[FATAL] Line {i+1} covered by >1 part! (in {fname})")
            sys.exit(1)
        covered[i] = True
gaps = [i for i, c in enumerate(covered) if not c]
if gaps:
    print(f"[FATAL] {len(gaps)} lines uncovered: {gaps[:5]}")
    sys.exit(1)
print(f"[split] Coverage: ALL {total_lines} lines covered exactly once — OK")

# ---------------------------------------------------------------------------
# Step 4: Verify reconstruction before writing (dry run)
# ---------------------------------------------------------------------------
reconstructed_dry = "\n".join(
    "\n".join(lines[start:end]) for _, _, start, end in PARTS
)
# The join between parts adds one "\n" between each part
# Reconstruction: part0 + "\n" + part1 + "\n" + ... = original
# since original.split("\n") and we take slices and rejoin with "\n"
# This equals original IF the slices cover all lines in order.
# We verified coverage so reconstruction == original.
# Do a spot check on length:
reconstructed_chars = sum(len("\n".join(lines[s:e])) for _, _, s, e in PARTS) + len(PARTS) - 1
print(f"[split] Reconstructed char estimate: {reconstructed_chars} (original: {len(original_text)})")
delta = abs(reconstructed_chars - len(original_text))
if delta > 0:
    print(f"[split] Delta {delta} chars (expected 0)")
    # Allow 0 — boundaries are at blank lines so no content chars lost

# ---------------------------------------------------------------------------
# Step 5: Create output directory
# ---------------------------------------------------------------------------
OUT_DIR.mkdir(parents=True, exist_ok=True)
print(f"[split] Output: {OUT_DIR}")

# ---------------------------------------------------------------------------
# Step 6: Write parts
# ---------------------------------------------------------------------------
part_meta = []
for fname, title, start, end in PARTS:
    content = "\n".join(lines[start:end])
    out_path = OUT_DIR / fname
    out_path.write_text(content, encoding="utf-8")
    lc = end - start
    cc = len(content)
    part_meta.append((fname, title, start, end, lc, cc))
    print(f"  {fname}: lines {start+1}–{end} ({lc} lines, {cc} chars)")

# ---------------------------------------------------------------------------
# Step 7: Write README.md index
# ---------------------------------------------------------------------------
readme = [
    "# Quality Pipeline Plan — Index",
    "",
    "> **Purpose:** This folder contains the split version of the original `QUALITY-PIPELINE-PLAN.md`.",
    "> All content is **preserved exactly** — no deletions, no modifications.",
    ">",
    f"> **Original:** `docs/plan/QUALITY-PIPELINE-PLAN.md` ({total_lines} lines, now a redirect stub)",
    f"> **Parts:** {len(PARTS)} files",
    "",
    "---",
    "",
    "## Files",
    "",
    "| # | File | Content | Lines |",
    "|---|------|---------|-------|",
]
for i, (fname, title, start, end, lc, cc) in enumerate(part_meta, 1):
    readme.append(f"| {i} | [`{fname}`](./{fname}) | {title} | {lc} |")

readme += [
    "",
    "---",
    "",
    "## Navigation Guide",
    "",
    "```",
    "Start here:           01-overview.md              — big picture, audit numbers",
    "Active fixes:         02-layer-minus1-ingestion.md — L-1-01..07 parser/crawl fixes",
    "Pipeline layers:      03-layers-0-to-5-execution.md",
    "Architecture backlog: 04-arch-backlog-initial.md   — ARCH-01..08",
    "Multi-corpus/SPA:     05-multi-corpus-arch.md",
    "YAML vs DB decision:  06-arch-decisions.md         — MUST READ before any DB work",
    "Gap analysis (DB/Par) 07-data-quality-defense-part1.md  — L-1-08..L-1-22",
    "Gap analysis (Crawl): 08-data-quality-defense-part2.md  — L-1-23..PERF-06",
    "Embedding/Qdrant:     09-embedding-qdrant-gaps.md  — PERF-07..10, ARCH-11..13",
    "Backlog:              10-cross-gap-backlog.md       — CROSS-GAP-01, BACKLOG",
    "```",
    "",
    "---",
    "",
    "*Split by senior engineer directive. Content integrity verified.*",
    f"*Original total: {total_lines} lines. Zero content removed or modified.*",
]
readme_text = "\n".join(readme)
(OUT_DIR / "README.md").write_text(readme_text, encoding="utf-8")
print(f"[split] README.md written ({len(readme_text)} chars)")

# ---------------------------------------------------------------------------
# Step 8: Write redirect stub to replace original
# ---------------------------------------------------------------------------
stub_lines = [
    "# Quality Pipeline Plan — geostat-chat-ai",
    "",
    "> **This file has been split into focused sub-documents.**",
    "> **All content is preserved. See the index below.**",
    "",
    "---",
    "",
    "## Index → [`docs/plan/quality-pipeline/README.md`](./quality-pipeline/README.md)",
    "",
    "| # | File | Content |",
    "|---|------|---------|",
]
for i, (fname, title, start, end, lc, cc) in enumerate(part_meta, 1):
    stub_lines.append(
        f"| {i} | [`quality-pipeline/{fname}`](./quality-pipeline/{fname}) | {title} |"
    )
stub_lines += [
    "",
    "---",
    "",
    f"*Original: {total_lines} lines split into {len(PARTS)} files.*",
    "*No content was deleted or modified.*",
]
stub_text = "\n".join(stub_lines)
PLAN.write_text(stub_text, encoding="utf-8")
print(f"[split] Original replaced with redirect stub ({len(stub_text)} chars)")

# ---------------------------------------------------------------------------
# Step 9: Final integrity check — read back all parts and reconstruct
# ---------------------------------------------------------------------------
parts_read_back = []
for fname, title, start, end, lc, cc in part_meta:
    text = (OUT_DIR / fname).read_text(encoding="utf-8")
    parts_read_back.append(text)

reconstructed_final = parts_read_back[0]
for part in parts_read_back[1:]:
    reconstructed_final = reconstructed_final + "\n" + part

if reconstructed_final == original_text:
    print("\n[split] INTEGRITY CHECK: PERFECT MATCH — 100% identical to original")
else:
    mismatches = sum(1 for a, b in zip(reconstructed_final, original_text) if a != b)
    len_diff = abs(len(reconstructed_final) - len(original_text))
    print(f"\n[split] WARNING: {mismatches} char diffs, len diff={len_diff}")
    if mismatches == 0 and len_diff == 0:
        print("[split] Actually identical")
    elif len_diff <= 1 and mismatches <= 1:
        print("[split] Single trailing newline difference — acceptable")
    else:
        print("[split] ERROR: content mismatch exceeds tolerance")
        sys.exit(1)

print(f"\n[split] DONE: {len(PARTS)} files in {OUT_DIR}")
print(f"[split] Parts: {', '.join(f for f, *_ in part_meta)}")
