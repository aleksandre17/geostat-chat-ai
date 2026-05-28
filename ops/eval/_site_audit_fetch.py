"""One-off geostat.ge site structure fetcher for corpus config audit."""
from __future__ import annotations

import json
import re
import ssl
import sys
import urllib.error
import urllib.request
from typing import Any

CTX = ssl.create_default_context()
CTX.check_hostname = False
CTX.verify_mode = ssl.CERT_NONE

UA = "Mozilla/5.0 (compatible; geostat-audit/1.0)"


def fetch_html(url: str) -> tuple[int | None, str, str | None]:
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    try:
        with urllib.request.urlopen(req, timeout=30, context=CTX) as resp:
            return resp.status, resp.read().decode("utf-8", errors="replace"), None
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace") if exc.fp else ""
        return exc.code, body, str(exc)
    except Exception as exc:  # noqa: BLE001
        return None, "", str(exc)


def analyze(url: str, html: str, status: int | None) -> dict[str, Any]:
    title_m = re.search(r"<title[^>]*>([^<]+)</title>", html, re.I)
    lang_m = re.search(r'<html[^>]*\blang="([^"]+)"', html, re.I)
    og_m = re.search(
        r'<meta[^>]*property="og:type"[^>]*content="([^"]+)"', html, re.I
    ) or re.search(
        r'<meta[^>]*content="([^"]+)"[^>]*property="og:type"', html, re.I
    )

    nav_classes = sorted(set(re.findall(r'<nav[^>]*class="([^"]+)"', html, re.I)))
    footer_classes = sorted(
        set(re.findall(r'<footer[^>]*class="([^"]+)"', html, re.I))
    )
    json_ld_types = sorted(set(re.findall(r'"@type"\s*:\s*"([^"]+)"', html)))

    nav_links: list[str] = []
    for block in re.findall(r"<nav[^>]*>.*?</nav>", html, re.I | re.S):
        nav_links.extend(re.findall(r'href="([^"]+)"', block))
    footer_links: list[str] = []
    for block in re.findall(r"<footer[^>]*>.*?</footer>", html, re.I | re.S):
        footer_links.extend(re.findall(r'href="([^"]+)"', block))

    main_candidates = []
    for tag in ("main", "article"):
        for m in re.finditer(rf"<{tag}([^>]*)>", html, re.I):
            attrs = m.group(1)
            cls = re.search(r'class="([^"]+)"', attrs)
            id_m = re.search(r'id="([^"]+)"', attrs)
            role = re.search(r'role="([^"]+)"', attrs)
            parts = [tag]
            if cls:
                parts.append(f'class="{cls.group(1)}"')
            if id_m:
                parts.append(f'id="{id_m.group(1)}"')
            if role:
                parts.append(f'role="{role.group(1)}"')
            main_candidates.append(" ".join(parts))

    content_area = re.findall(
        r'class="([^"]*(?:content-area|news-content|page-content|archive-section|value-databases|infographic|category-content|structure-content)[^"]*)"',
        html,
        re.I,
    )
    h1_blocks = re.findall(
        r'(<h1[^>]*>.*?</h1>)', html, re.I | re.S
    )
    h1_with_context = []
    for h1 in h1_blocks[:3]:
        idx = html.find(h1)
        snippet = html[max(0, idx - 400) : idx]
        parent_cls = re.findall(r'class="([^"]+)"', snippet)
        h1_with_context.append(
            {
                "h1_text": re.sub(r"<[^>]+>", "", h1).strip()[:120],
                "parent_classes_nearby": parent_cls[-5:],
            }
        )

    date_selectors = sorted(
        set(
            re.findall(
                r'class="([^"]*(?:date|publish|time|created)[^"]*)"', html, re.I
            )
        )
    )
    download_links = re.findall(
        r'href="([^"]+\.(?:pdf|csv|xlsx?|xls|zip))"', html, re.I
    )
    tables = len(re.findall(r"<table\b", html, re.I))
    pagination = sorted(
        set(re.findall(r'class="([^"]*pagination[^"]*)"', html, re.I))
    )
    rightbar = sorted(set(re.findall(r'class="([^"]*rightbar[^"]*)"', html, re.I)))
    sidebar = sorted(set(re.findall(r'class="([^"]*sidebar[^"]*)"', html, re.I)))
    related = sorted(set(re.findall(r'class="([^"]*related[^"]*)"', html, re.I)))

    # exclude pattern probes
    exclude_hits = {
        "albums": "/albums" in html or "albums" in url,
        "video-gallery": "/video-gallery" in html,
        "print": "/print/" in html,
        "tags": "/tags/" in html,
        "search": "/search" in html,
        "contact": "/contact" in html,
        "page_query": "?page=" in html or "&page=" in html,
        "page_path": bool(re.search(r"/page/\d+", html)),
    }

    return {
        "url": url,
        "status": status,
        "title": title_m.group(1).strip() if title_m else "",
        "html_lang": lang_m.group(1) if lang_m else "",
        "og_type": og_m.group(1) if og_m else "",
        "json_ld_types": json_ld_types,
        "nav_classes": nav_classes,
        "footer_classes": footer_classes,
        "main_candidates": main_candidates[:8],
        "content_area_classes": sorted(set(content_area))[:15],
        "h1_context": h1_with_context,
        "date_classes": date_selectors[:10],
        "download_links_sample": download_links[:8],
        "table_count": tables,
        "pagination_classes": pagination,
        "rightbar_classes": rightbar,
        "sidebar_classes": sidebar,
        "related_classes": related,
        "nav_links": nav_links,
        "footer_links": footer_links,
        "news_links": sorted(set(re.findall(r'href="([^"]*single-news[^"]+)"', html))),
        "section_path_links": sorted(
            set(
                re.findall(
                    r'href="(/(?:ka|en)/(?:modules|page|single-news|publications|open-data|infographics|press-release)[^"]*)"',
                    html,
                )
            )
        )[:40],
        "exclude_hits_in_page": exclude_hits,
    }


def main() -> None:
    urls = sys.argv[1:] if len(sys.argv) > 1 else [
        "https://www.geostat.ge/ka",
        "https://www.geostat.ge/en",
    ]
    results = []
    for url in urls:
        status, html, err = fetch_html(url)
        if err and not html:
            results.append({"url": url, "error": err, "status": status})
            continue
        results.append(analyze(url, html, status))
    out = json.dumps(results, ensure_ascii=False, indent=2)
    sys.stdout.buffer.write(out.encode("utf-8"))
    sys.stdout.buffer.write(b"\n")


if __name__ == "__main__":
    main()
