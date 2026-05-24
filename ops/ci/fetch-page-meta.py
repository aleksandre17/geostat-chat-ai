#!/usr/bin/env python3
import json
import re
import urllib.request

URL = "https://www.geostat.ge/ka/modules/categories/26/samomkhmareblo-fasebis-indeksi-inflatsia"
html = urllib.request.urlopen(URL, timeout=20).read().decode("utf-8", "replace")

metas = []
for m in re.finditer(
    r'<meta[^>]+(?:name|property)=["\']([^"\']+)["\'][^>]+content=["\']([^"\']*)["\']',
    html,
    re.I,
):
    name, content = m.group(1), m.group(2).strip()
    if name.lower() in ("description", "og:description", "twitter:description"):
        metas.append({"name": name, "content": content})

ps = []
for m in re.finditer(r"<p[^>]*>([^<]{30,300})", html, re.I):
    ps.append(m.group(1).strip()[:200])

out = {"url": URL, "metas": metas, "paragraphs": ps[:10]}
with open("ops/ci/fetch-page-meta-out.json", "w", encoding="utf-8") as f:
    json.dump(out, f, ensure_ascii=False, indent=2)
print("written ops/ci/fetch-page-meta-out.json")
