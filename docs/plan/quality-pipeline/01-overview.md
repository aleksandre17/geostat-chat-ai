# Quality Pipeline Plan — geostat-chat-ai

> **მიზანი:** უმაღლესი ხარისხის ინფორმაციის მიწოდება.
> **მიდგომა:** bottom-up — ჯერ მონაცემთა ბაზის საფუძველი, შემდეგ retrieval, query understanding, response.
> **Audit report:** `ops/eval/reports/2026-05-26-db-data-quality-audit.txt`

---

## Pipeline Overview

```
[L-1] ingestion-service (crawl → parse → enrich → chunk)
          ↓
[L0]  Postgres ingestion.*  →  [L1] Qdrant (embeddings)
          ↓                              ↓
[L2]  retrieval-service      ←→   [L3] chat-api catalog
                                        ↓
                               [L4] Query Understanding (U07)
                                        ↓
                               [L5] Gemini prompt + response
```

---

## Audit Snapshot (2026-05-26)

| მეტრიკა | მნიშვნელობა | სტატუსი |
|--------|------------|---------|
| `document` სულ | 4 215 | — |
| `chunk` სულ | 4 970 | — |
| `topic_cluster` სულ | 5 | — |
| `page_kind = 'unknown'` | 3 928 / 4 215 (93%) | 🔴 |
| `authority_score = 0.0` | 3 974 / 4 215 (94%) | 🔴 |
| `summary_ka` ცარიელი | 3 974 / 4 215 (94%) | 🔴 |
| `summary_en` ცარიელი | 3 974 / 4 215 (94%) | 🔴 |
| `lead_text` cross-contamination | ≥3 document; ერთი და იგივე lead_text | 🔴 |
| duplicate `(title, language)` groups | 1 090 (2 464 extra rows) | 🟠 |
| boilerplate chunks (navigation/download text) | confirmed in sample | 🟠 |
| `meta_description` ცარიელი | 2 401 / 4 215 (57%) | 🟡 |
| `mv_portal_link` სულ | 10 | 🟡 |
| `mv_specific_link` სულ | 98 | 🟡 |

> 94% documents unenriched → enrichment job არ გამუშავებია corpus-ის უმეტეს ნაწილზე.
> 10 portal link MV-ში → catalog routing ფაქტობრივად ვერ მუშაობს.

---
