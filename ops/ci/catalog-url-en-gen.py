#!/usr/bin/env python3
"""Add urlEn to catalog YAML links (L04 explicit EN URLs)."""
from __future__ import annotations

import re
import sys
from pathlib import Path

try:
    import yaml
except ImportError:
    sys.exit("PyYAML required: pip install pyyaml")


def locale_url(url: str) -> str | None:
    if not url or not isinstance(url, str):
        return None
    if "lang=ka" in url:
        return url.replace("lang=ka", "lang=en")
    if "/ka/" in url:
        return url.replace("/ka/", "/en/", 1)
    if url.endswith("/ka"):
        return url[:-2] + "en"
    if url.endswith("/ka/"):
        return url[:-3] + "en/"
    return None


def enrich_node(node):
    if isinstance(node, dict):
        url = node.get("url")
        if isinstance(url, str) and url and "urlEn" not in node:
            en = locale_url(url)
            if en and en != url:
                node["urlEn"] = en
        for v in node.values():
            enrich_node(v)
    elif isinstance(node, list):
        for item in node:
            enrich_node(item)


def process_file(path: Path) -> int:
    data = yaml.safe_load(path.read_text(encoding="utf-8"))
    before = path.read_text(encoding="utf-8")
    enrich_node(data)
    out = yaml.dump(data, allow_unicode=True, sort_keys=False, width=120)
    if out != before:
        path.write_text(out, encoding="utf-8")
        return 1
    return 0


def main() -> None:
    root = Path(__file__).resolve().parents[2]
    catalog = root / "apps" / "backend" / "src" / "main" / "resources" / "catalog"
    updated = 0
    for name in ("specific-links.yaml", "catalog-meta.yaml", "topics.yaml"):
        p = catalog / name
        if p.exists():
            updated += process_file(p)
            print(f"processed {name}")
    print(f"catalog urlEn enrichment done ({updated} files changed)")


if __name__ == "__main__":
    main()
