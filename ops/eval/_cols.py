"""Fetch stat category pages and extract sub-categories for topic catalog."""
from __future__ import annotations

import json
import re
import ssl
import time
import urllib.error
import urllib.request
from pathlib import Path

CTX = ssl.create_default_context()
CTX.check_hostname = False
CTX.verify_mode = ssl.CERT_NONE
UA = {"User-Agent": "Mozilla/5.0 (compatible; geostat-audit/1.0)"}
OUT = Path(__file__).resolve().parent / "reports"


def fetch(url: str, retries: int = 3) -> tuple[int | None, str]:
    for attempt in range(retries):
        req = urllib.request.Request(url, headers=UA)
        try:
            with urllib.request.urlopen(req, timeout=25, context=CTX) as resp:
                return resp.status, resp.read().decode("utf-8", errors="replace")
        except Exception:
            time.sleep(1.5 * (attempt + 1))
    return None, ""


def extract_subcats(url: str, html: str, status: int | None) -> dict:
    """Extract sub-category links from a stat category page."""
    # Links in content area (value-databases-section, archive-section, left column)
    # Look for internal module links that are NOT nav links
    # The nav block spans the top ~860 lines in the HTML; content starts after <header> closes

    # Remove the header+nav block (everything before breadcrumb)
    content_start = html.find("breadcrumb-wrapper")
    content = html[content_start:] if content_start > 0 else html

    # All links inside content
    all_links = re.findall(r'href="(https?://[^"]+)"[^>]*>\s*(?:<[^>]+>\s*)*([^<]{3,80})', content)
    sub_links = []
    seen = set()
    for href, text in all_links:
        text = re.sub(r"\s+", " ", text).strip()
        if not text or href in seen:
            continue
        # Keep internal geostat links, skip social / media / pdf / external
        if "geostat.ge" not in href:
            continue
        if any(x in href for x in (".pdf", ".xlsx", ".csv", "facebook", "linkedin", "twitter", "#")):
            continue
        if any(x in href for x in ("/albums", "/video-gallery", "/news", "/infographic", "/search", "/contact")):
            continue
        seen.add(href)
        sub_links.append({"url": href, "label": text[:80]})

    # Section class present?
    section_classes = re.findall(r"<section[^>]*class='([^']+)'", html)

    # h1/h2 headings in content
    headings = []
    for m in re.finditer(r"<h[123][^>]*>(.*?)</h[123]>", content, re.I | re.S):
        t = re.sub(r"<[^>]+>", " ", m.group(1)).strip()
        if t and "{__" not in t and len(t) > 2:
            headings.append(t[:100])

    # Date / publication entries (useful for dataset pages)
    dates = list(set(re.findall(r"<p[^>]*id='date'[^>]*>(.*?)</p>", content, re.I | re.S)))[:5]
    date_clean = [re.sub(r"<[^>]+>", "", d).strip() for d in dates]

    return {
        "url": url,
        "status": status,
        "section_classes": section_classes[:10],
        "headings": headings[:20],
        "sub_links": sub_links[:60],
        "date_samples": date_clean[:5],
    }


# All 20 top-level statistical info categories + publications + methodology parents
STAT_CATEGORIES = [
    ("business_sector",        "https://www.geostat.ge/ka/modules/categories/195"),
    ("monetary_statistics",    "https://www.geostat.ge/ka/modules/categories/92"),
    ("crime_statistics",       "https://www.geostat.ge/ka/modules/categories/131"),
    ("business_register",      "https://www.geostat.ge/ka/modules/categories/64"),
    ("population_demography",  "https://www.geostat.ge/ka/modules/categories/316"),
    ("state_finance",          "https://www.geostat.ge/ka/modules/categories/91"),
    ("education_science",      "https://www.geostat.ge/ka/modules/categories/56"),
    ("industry_construction",  "https://www.geostat.ge/ka/modules/categories/74"),
    ("agriculture_food",       "https://www.geostat.ge/ka/modules/categories/70"),
    ("environment",            "https://www.geostat.ge/ka/modules/categories/73"),
    ("fdi",                    "https://www.geostat.ge/ka/modules/categories/191"),
    ("tourism",                "https://www.geostat.ge/ka/modules/categories/100"),
    ("employment_wages",       "https://www.geostat.ge/ka/modules/categories/37"),
    ("regional_statistics",    "https://www.geostat.ge/ka/modules/categories/93"),
    ("prices",                 "https://www.geostat.ge/ka/modules/categories/25"),
    ("national_accounts",      "https://www.geostat.ge/ka/modules/categories/22"),
    ("ict",                    "https://www.geostat.ge/ka/modules/categories/103"),
    ("living_standards",       "https://www.geostat.ge/ka/modules/categories/48"),
    ("services",               "https://www.geostat.ge/ka/modules/categories/387"),
    ("external_trade",         "https://www.geostat.ge/ka/modules/categories/35"),
    ("health_social",          "https://www.geostat.ge/ka/modules/categories/53"),
    ("mics",                   "https://www.geostat.ge/ka/modules/categories/692"),
    # Publications
    ("publications_quarterly", "https://www.geostat.ge/ka/single-categories/121"),
    ("publications_annual",    "https://www.geostat.ge/ka/single-categories/122"),
    # About/governance
    ("about_geostat",          "https://www.geostat.ge/ka/modules/categories/189"),
    ("governance",             "https://www.geostat.ge/ka/modules/categories/303"),
    # Calendar, projects, news
    ("calendar",               "https://www.geostat.ge/ka/calendar"),
    ("projects",               "https://www.geostat.ge/ka/projects"),
    ("news_list",              "https://www.geostat.ge/ka/news"),
    ("infographic",            "https://www.geostat.ge/ka/infographic"),
    # EN equivalents for cross-reference
    ("business_sector_en",     "https://www.geostat.ge/en/modules/categories/195"),
    ("population_en",          "https://www.geostat.ge/en/modules/categories/316"),
    ("national_accounts_en",   "https://www.geostat.ge/en/modules/categories/22"),
    ("prices_en",              "https://www.geostat.ge/en/modules/categories/25"),
    ("external_trade_en",      "https://www.geostat.ge/en/modules/categories/35"),
]


def main() -> None:
    results = {}
    for name, url in STAT_CATEGORIES:
        print(f"  fetching {name}...")
        status, html = fetch(url)
        if html:
            results[name] = extract_subcats(url, html, status)
        else:
            results[name] = {"url": url, "status": status, "error": "no content"}
        time.sleep(0.7)

    OUT.joinpath("_cols_output.json").write_text(
        json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print("done.")


if __name__ == "__main__":
    main()
