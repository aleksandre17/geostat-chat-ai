#!/usr/bin/env python3
import io
import json
import os
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")

path = os.path.join(os.path.dirname(__file__), "test-inflation.json")
data = json.load(open(path, encoding="utf-8"))
out = []
for it in data.get("items", [])[:6]:
    link = it["link"]
    out.append({
        "title": link.get("titleKa") or link.get("title"),
        "type": link.get("type"),
        "sourceType": link.get("sourceType"),
        "snippet": (link.get("snippet") or "")[:160],
    })
print(json.dumps(out, ensure_ascii=False, indent=2))
