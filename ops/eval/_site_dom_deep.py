"""Deep DOM class/link extraction for geostat.ge audit."""
from __future__ import annotations

import json
import re
import ssl
import sys
import time
import urllib.error

CTX = ssl.create_default_context()
CTX.check_hostname = False
CTX.verify_mode = ssl.CERT_NONE
UA = {"User-Agent": "Mozilla/5.0 (compatible; geostat-audit/1.0)"}
OUT = Path(__file__).resolve().parent / "reports"


def fetch(url: str, retries: int = 4) -> tuple[int, str]:
    last_err: Exception | None = None
    for attempt in range(retries):
        req = urllib.request.Request(url, headers=UA)
        try:
            with urllib.request.urlopen(req, timeout=30, context=CTX) as resp:
                return resp.status, resp.read().decode("utf-8", errors="replace")
        except (urllib.error.URLError, ConnectionResetError, TimeoutError) as exc:
            last_err = exc
            time.sleep(1.5 * (attempt + 1))
    raise last_err  # type: ignore[misc]


def dom_report(name: str, url: str, html: str, status: int) -> dict:
    classes = sorted(set(re.findall(r'class="([^"]+)"', html)))
    keywords = (
        "news", "content", "right", "side", "bread", "footer", "header", "nav",
        "main", "article", "archive", "page", "date", "related", "infographic",
        "category", "structure", "album", "gallery", "pagination", "open", "data",
        "public", "press", "download", "attach", "value", "database", "section",
    )
    interesting = [c for c in classes if any(k in c.lower() for k in keywords)]

    links = sorted(set(re.findall(r'href="([^"]+)"', html)))
    link_groups = {}
    for pat in (
        "open-data", "publications", "press", "single-news", "print/", "tags/",
        "/page/", "single-categories", "infographic", "news", "calendar", "projects",
    ):
        hits = [l for l in links if pat in l]
        if hits:
            link_groups[pat] = hits[:12]

    nav_blocks = re.findall(r"<nav[^>]*>.*?</nav>", html, re.I | re.S)
    footer_blocks = re.findall(r"<footer[^>]*>.*?</footer>", html, re.I | re.S)
    header_blocks = re.findall(r"<header[^>]*>.*?</header>", html, re.I | re.S)

    def block_meta(blocks: list[str]) -> list[dict]:
        out = []
        for b in blocks[:5]:
            tag_m = re.match(r"<(\w+)([^>]*)>", b, re.I)
            attrs = tag_m.group(2) if tag_m else ""
            cls = re.search(r'class="([^"]+)"', attrs)
            id_m = re.search(r'id="([^"]+)"', attrs)
            out.append(
                {
                    "tag": tag_m.group(1) if tag_m else "?",
                    "class": cls.group(1) if cls else "",
                    "id": id_m.group(1) if id_m else "",
                    "link_count": len(re.findall(r'href="', b)),
                }
            )
        return out

    h2_h3 = []
    for tag in ("h1", "h2", "h3"):
        for m in re.finditer(rf"<{tag}[^>]*>(.*?)</{tag}>", html, re.I | re.S):
            text = re.sub(r"<[^>]+>", " ", m.group(1)).strip()
            if text and "{__" not in text:
                idx = m.start()
                snippet = html[max(0, idx - 250) : idx]
                parent_cls = re.findall(r'class="([^"]+)"', snippet)
                h2_h3.append(
                    {"tag": tag, "text": text[:100], "parent_classes": parent_cls[-3:]}
                )

    boilerplate_phrases = []
    for phrase in (
        "Crafted by", "უკან დაბრუნება", "skip to content", "გამოიწერეთ სიახლეები",
        "Subscribe to Newsletters", "ვებგვერდის ადაპტირებული", "adapted version",
        "ცენტრალური ოფისი", "central office", "სრულად ნახვა", "read more",
    ):
        if phrase.lower() in html.lower():
            boilerplate_phrases.append(phrase)

    return {
        "name": name,
        "url": url,
        "status": status,
        "html_len": len(html),
        "interesting_classes": interesting,
        "link_groups": link_groups,
        "nav_meta": block_meta(nav_blocks),
        "footer_meta": block_meta(footer_blocks),
        "header_meta": block_meta(header_blocks),
        "headings": h2_h3[:15],
        "boilerplate_phrases_found": boilerplate_phrases,
        "has_main_tag": "<main" in html.lower(),
        "has_article_tag": "<article" in html.lower(),
        "table_count": len(re.findall(r"<table\b", html, re.I)),
    }


def main() -> None:
    pages = [
        ("home_ka", "https://www.geostat.ge/ka"),
        ("home_en", "https://www.geostat.ge/en"),
        ("news_list", "https://www.geostat.ge/ka/news"),
        ("single_news", "https://www.geostat.ge/ka/single-news/1756"),
        ("category189", "https://www.geostat.ge/ka/modules/categories/189"),
        ("category303", "https://www.geostat.ge/ka/modules/categories/303"),
        ("category121", "https://www.geostat.ge/ka/modules/categories/121"),
        ("structure", "https://www.geostat.ge/ka/structure"),
        ("page_dir", "https://www.geostat.ge/ka/page/aghmasrulebeli-direqtori"),
        ("infographic", "https://www.geostat.ge/ka/infographic"),
        ("single_cat121", "https://www.geostat.ge/ka/single-categories/121"),
        ("projects", "https://www.geostat.ge/ka/projects"),
        ("calendar", "https://www.geostat.ge/ka/calendar"),
    ]
    reports = []
    for name, url in pages:
        status, html = fetch(url)
        OUT.joinpath(f"_html_{name}.html").write_text(html, encoding="utf-8")
        reports.append(dom_report(name, url, html, status))
        time.sleep(0.8)
    OUT.joinpath("_dom_deep.json").write_text(
        json.dumps(reports, ensure_ascii=False, indent=2), encoding="utf-8"
    )


if __name__ == "__main__":
    main()
