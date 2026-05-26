# Golden HTML fixtures (HANDOFF-P0-L1 §3 Artifact 3)

Trimmed from live `view-source:https://www.geostat.ge/*` (2026-05). Used by `JsoupContentExtractorFixtureTest`.

| File | Live URL | Asserts |
|------|----------|---------|
| `portal-landing-ka.html` | `/ka` | KPI strip via `.home-statistic-category`; boilerplate stripped |
| `portal-landing-en.html` | `/en` | English landing KPI strip |
| `dataset-page-ka.html` | `/ka/modules/categories/195/biznes-sektori` | Table metrics; sidebar removed |
| `news-listing-ka.html` | `/ka/news` | News titles; pagination removed |
| `news-article-ka.html` | `/ka/news/*` | Long prose + lead; headings preserved |
| `albums-listing-ka.html` | `/ka/albums` | Album titles (policy excludes crawl) |
| `archive-page-ka.html` | `/ka/single-categories/121/kvartaluri` | Publication folder links |
| `navigation-about.html` | `/ka/about-us` | About page content (policy excludes crawl) |
| `bilingual-pair-ka.html` | `/ka/modules/...` | Locale `ka` from URL + html lang |
| `bilingual-pair-en.html` | `/en/modules/...` | Locale `en` |
| `empty-subdomain.html` | `gis.geostat.ge` | Quality gate rejects |
