#!/usr/bin/env python3
"""Verify .cursor/ matches .cursor/ops/restore-manifest.sha256 (byte-exact)."""
from __future__ import annotations

import hashlib
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / ".cursor/ops/restore-manifest.sha256"


def sha256(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def main() -> int:
    if not MANIFEST.exists():
        print(f"Missing manifest: {MANIFEST}", file=sys.stderr)
        print("Run: python tools/restore-cursor-from-transcript.py", file=sys.stderr)
        return 1

    ok = 0
    bad: list[str] = []
    for line in MANIFEST.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        if line.startswith("DELETED  "):
            rel = line.removeprefix("DELETED  ").strip()
            if (ROOT / rel).exists():
                bad.append(f"SHOULD_BE_DELETED {rel}")
            else:
                ok += 1
            continue
        digest, rel = line.split("  ", 1)
        out = ROOT / rel
        if not out.exists():
            bad.append(f"MISSING {rel}")
            continue
        actual = sha256(out.read_text(encoding="utf-8"))
        if actual != digest:
            bad.append(f"DIFF {rel}")
        else:
            ok += 1

    print(f"verified_ok={ok} failures={len(bad)}")
    for b in bad[:30]:
        print(f"  {b}")
    return 1 if bad else 0


if __name__ == "__main__":
    raise SystemExit(main())
