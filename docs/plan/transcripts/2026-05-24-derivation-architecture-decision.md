# Decision narrative — RAG derivation architecture (Phase 8)

- **თარიღი:** 2026-05-24
- **მონაწილეები:** Owner · Senior architect agent
- **შედეგი:** [ADR-011](../../adr/011-rag-derivation-architecture.md) Accepted; [RAG-DERIVATION-ARCHITECTURE.md](../RAG-DERIVATION-ARCHITECTURE.md) approved baseline; D-25, D-26 approved
- **ცვლილების მასშტაბი:** ფაზა 8 (Phase 8) დამატება; `topics.yaml` 2364 ხაზი deprecated; 8 ახალი deriver; ADR-011
- **წყარო transcript:** Cursor agent chat `fc107a92-e930-4f2a-88ee-aa956fe7ab88` (lines ~3135–3205)

ეს დოკუმენტი არის **decision narrative** — გადაწყვეტილების მისულობის გზა, არა თავად გადაწყვეტილების ფორმალური აღწერა (ის ADR-ში დგას). მიზანი: მომავალ აგენტს ან ახალ ინჟინერს ესმოდეს **რატომ** მივიღეთ ეს გზა, რა იყო ალტერნატივები, რა არგუმენტებმა გადაწყვიტეს.

---

## 0. დაუყოვნებელი pre-context (19:40)

RAG-L11 — display metadata layer — დასრულებული. Chat-API აბრუნებდა locale-aware citation snippet-ებს. მუშავდებოდა polish:

- Boilerplate filtering (`DisplayBoilerplate`, ingestion-side და chat-side)
- Snippet quality (mid-word cuts გაასწორდა, title-snippet duplicate eliminated)
- Hybrid JAR boot fixes (PowerShell parser, port wait, env arg passing)

Owner-მა აღნიშნა snippet-ის პრობლემა — პირველი სიტყვის ნაწილი იჭრებოდა („დექსი" ნაცვლად „ინდექსი"). ეს polish-ი იყო; **არქიტექტურული დიალოგი დაიწყო შემდეგ მესიჯით.**

---

## 1. საწყისი მდგომარეობა (2026-05-24 დილით)

**Catalog მოდელი იყო YAML-based:**

- `apps/backend/src/main/resources/catalog/topics.yaml` — **2364 ხაზი** ხელით დაწერილი
- `specific-links.yaml`, `news-categories.yaml` — დამატებითი ხელის-არტეფაქტები
- `YamlTopicCatalog`, `TopicCatalogLoader`, `SpecificLinkLoader`, `NewsCategoryLoader` — ლოადერი კოდი

**პრობლემა:** ყოველი ახალი თემა / პორტალი / ბმული ითხოვდა YAML-ის რედაქტირებას. ka↔en pair-ები ხელით ეწერა. „official portal" სტატუსი იყო ედიტორული, არა მონაცემზე-ბაზირებული. კივორდები — ფიქსირებული snapshot.

**Zero-gap დარღვევა:** სუბდომენები (`kaleidoscope`, `regions`, `cpi`, `personalinflation`, `census2024`, `sna`, `disability`, `database`, `surveycalendar`, `indexation`) YAML-ში იყო, მაგრამ **crawler frontier-ში არა** — მომხმარებელს ვუგზავნიდით URL-ებს, რომლებიც RAG-ში არ იყო ინდექსირებული.

---

## 2. Owner Message 0 — unified RAG (19:50)

> *„მისმინე, არ შეიძლება ის რაც ახლა ლოკალურად გვაქვს, ანუ rag-ის გარდა რომ არის დატა — ტოპიკები და სხვა კიდევ რაცაა — რომ გამოვიყენოთ დამტკიცებული მეთოდები, რომ გამოვიყენოთ წარმატებული გუნდების გამოცდილება და პრაქტიკა. გადავიტანოთ ეს სტატიკური რაცაა მთლიანად ჩვენ RAG არქიტექტურაზე. გამოვა? ჩვენი ბაზის არქიტექტურა კიდევ უფრო დახვეწილი, აგნოსტიკური, ორგანიზირებული, ზრდაზე ორიენტირებული. თან ახლა რა საიტებიც გვაქვს რომელიც სტატიკურადა, ეგენიც crawl-ზე რომ დაემატოს და ერთიანი სისტემა რომ მივიღოთ."*

ეს იყო **პირველი არქიტექტურული trigger** — YAML + RAG ორი წყაროს ერთიან pipeline-ში შერწყმა.

---

## 3. Agent-ის პირველი წინადადება — RAG-U01..U06 (YAML→DB)

Agent-მა შესთავაზა **Unified RAG Knowledge Pipeline** — 6 ფაზა:

| ID | ფაზა | იდეა |
|---|---|---|
| RAG-U01 | DB-backed Topic + Flyway V9 | YAML→DB seed importer; `JpaTopicCatalog` ცვლის `YamlTopicCatalog`-ს |
| RAG-U02 | CrawlSeed + subdomain whitelist | kaleidoscope, regions, cpi და სხვ. seed-ად + reindex |
| RAG-U03 | Document.kind + description columns | page/portal/calculator/… unified model |
| RAG-U04 | SourceComposer | curated + RAG → ranked `LinkCard` items; `ResponseBuilder` deprecated |
| RAG-U05 | HybridTopicClassifier | lexical rules + topic centroid vectors |
| RAG-U06 | YAML წაშლა + admin CRUD | სრული zero-gap |

**პრობლემა (შემდგომი insight-ით):** ეს მაინც **ხელით ცოდნის გადატანაა** — YAML → DB. Source of truth ადამიანი რჩებოდა.

---

## 4. Owner Message 1 — strategic challenge (20:03)

> *„რომ გადახედო, საუკეთესო წარმატებულ პროექტებს, მათ გამოცდილებებს, მათ ხედვებს და პრობლემის გადაჭრის გზებს. რამეს შეცვლიდი? მე საუკეთესო Senior software engineer and architect and design მინდა. ზრდაზე ორენტირებული, სწრაფი, მოქნილი, საუკეთესო სტრუქტურიზებული, ორგანიზირებული. რაც შეიძლება მაღალი სანდობიის დატას რომ აბრუნებდეს. სიტყვებს საუკეთესოდ იჭერდეს. თუ საჭიროა დამატებითი უფასო პაკეტების, სერვისების ჩართვითაც. ოღონდ რაც შეიძლება სწორედ გამოთვალოს რა უნდა მომხმარებელს."*

ეს არ იყო „გააკეთე X". ეს იყო **სტრატეგიული challenge** — genuinely critique, not ratify.

---

## 5. Senior review — რა აღმოჩნდა

წარმატებული RAG სისტემების ფონზე (Anthropic, Perplexity, Notion AI, Microsoft GraphRAG, Vespa, Pinecone):

| კომპონენტი | წარმატებული | ჩვენ | Gap |
|---|---|---|---|
| Query understanding | spell-fix → intent → entity → expand | language detect + keyword `TopicDetector` | **HUGE** |
| Multi-vector index | named vectors title/summary/body | 1 vector per chunk | **large** |
| Abstractive summary | LLM batch, indexed | არ გვაქვს | **large** |
| HyDE / multi-query | სტანდარტი | არ გვაქვს | **large** |
| RRF hybrid fusion | სტანდარტი | naive append | medium |
| Confidence + clarify | tier-based | partial | medium |
| Eval harness | golden set, hit@k, MRR | არ გვაქვს | **large** |
| Feedback boost | მიღებული | telemetry only | medium |

**გაფართოებული roadmap** — RAG-U07..U15:

- U07 Query understanding (SymSpell + IntentClassifier + EntityExtractor + QueryExpander)
- U08 Multi-vector + abstractive summary
- U09 HyDE + multi-query
- U10 Hybrid RRF + MMR
- U11 Confidence tier + ResponseRouter
- U12 Eval harness (golden 150–300, CI gate 5%)
- U13 Feedback-driven score_boost
- U14 Caching tier
- U15 Knowledge graph (deferred P4+)

**მაგრამ** U01–U06 მაინც ტოვებდა **ხელით-მოწერილ catalog-ს** — ეს იყო ბურდასი refactor, არა fundamental fix.

**Target metrics (senior review):**

| მეტრიკა | ახლა (est.) | U07–U12 შემდეგ |
|---|---|---|
| Hit@5 | ~55–65% | 80–90% |
| Intent accuracy | n/a | 90%+ |
| Hallucination rate | უცნობი | <2% |
| Mean response time | ~3–4s | 2–3s |

---

## 6. Owner Message 2 — derivation insight (20:16)

> *„იცი რა მაინტერესებს, ახლა რაც სტატიკურად გვაქვს, ვფიქრობ პირდაპირ კი არ უნდა შევყაროთ ბაზაში, არამედ ჩვენმა არქიტექტურამ თვითონ უნდა შეძლოს მაგ ინფორმაციის შეკვრა და შემდგომი გაფართოება. რას იტყვი ამაზე? და მოდი, ზოგადად კივორდები დაგვჭირდება თუ არა?"*

**ფუნდამენტური განსხვავება:**

- Agent-ის წინადადება: „migrate YAML rows → DB tables" → მაინც **ხელით** მოვიდოდა ცოდნა
- Owner-ის წინადადება: **„architecture should derive that information itself"**

Owner-მა მითხრა: **„ცოდნა არ უნდა იწერებოდეს ხელით სრულიად — არც YAML-ში, არც DB-ში. სისტემა თვითონ უნდა ხედავდეს corpus-ს და ხატავდეს რუკას."**

Norvig: *„The unreasonable effectiveness of data"* — Google, Notion AI, Perplexity, LinkedIn არ ინახავენ knowledge catalog-ს ხელით; **იშვება corpus-იდან**.

---

## 7. Refined architecture — derivation model

```text
L1 Corpus              ← single source of truth (crawled documents)
L2 Enrichment          ← per-doc derived (summary, keywords, entities,
                         locale_pair, authority, page_kind, topic_cluster,
                         multi-vector embeddings)
L3 Catalog views       ← materialized views (mv_portal_link,
                         mv_specific_link, mv_topic_keywords)
L4 Curation overlay    ← tiny human nudge layer (≤50 rows;
                         actions: boost/demote/exclude/pin_as_portal/rename_topic)
L5 Online query        ← intent → hybrid retrieve → rerank → confidence → respond
```

**კრიტიკული პუნქტები:**

1. **YAML არ ქრება სრულად** — `topic-style.yaml` (icon/color, ~80 ხაზი) და `terminology-overlay.yaml` (synonym graph, ≤40 entries) რჩება. შინაარსი — corpus-დან.

2. **Pure emergent რისკია** — cold start. Mitigation: ≤10 `pin_as_portal` curation override day-1 bootstrap.

3. **Curation overlay ≠ CRUD UI on topics** — overlay არის exception layer. თუ overlay > 50 rows → deriver-ის bug upstream.

**8 deriver (RAG-U01a..h):**

| ID | Deriver | წყარო |
|---|---|---|
| U01a | SummaryExtractor | Gemini batch |
| U01b | KeywordExtractor | YAKE Java port |
| U01c | EntityExtractor | Gemini few-shot |
| U01d | LocalePairer | URL pattern + embedding cosine |
| U01e | AuthorityScorer | JGraphT in-link graph |
| U01f | PageKindClassifier | Gemini few-shot, closed-list |
| U01g | TopicMiner | Smile k-means + Gemini label |
| U01h | CatalogViewBuilder | materialized views |

---

## 8. კივორდები — senior answer

**დიახ, მაგრამ არასოდეს ხელით დაწერილი.**

| კატეგორია | ვინ ქმნის | სად ცხოვრობს |
|---|---|---|
| Lexical retrieval (BM25) | Postgres `tsvector` ავტომატურად | already there |
| Per-document keywords | YAKE Java port (top-15) | `document.keywords TEXT[]` |
| Per-topic keywords | TF-IDF aggregated nightly | `mv_topic_keywords` |
| Synonym overlay (≤40) | ხელით — acronym↔full form | `terminology-overlay.yaml` |

BM25 **ყოველთვის გვჭირდება** — embeddings სუსტია იშვიათ ტერმინებზე, აკრონიმებზე, კოდებზე.

---

## 9. Owner Message 3 — save to plan (20:28)

> *„ჯერ რაც ვისაუბრეთ ამ ბოლო რამდენიმე მონაწერებში, სრულიად უმცირესი დეტალების ჩათვლით შეინახე გეგმაში. რაც დავიწუნეთ და მაგის ნაცვლად უკეთესი დავამტკიცეთ, შინახე მხოლოდ უკეთესები. ყველაფერი უნდა იყოს მაქსიმალურად დეტალიზებულად შენახული. ბაზის სტრუქტურის და სრული პაიპლაინის ვერსიები — სრული პაიპლაინის ჩათვლით. ისე რომ, სხვა AI აგენტმაც რომ დაიწყოს, შენცე სუსტი აგენტიც რომ იყოს, უმარტივესად შეძლოს გარკვევა და არ დაკარგოს ხაზი."*

**შედეგი:** `RAG-DERIVATION-ARCHITECTURE.md` (1036 lines), ADR-011, PROJECT-PLAN Phase 8, D-25/D-26.

**ოთხი ღია კითხვა → owner recommendations adopted:**

| კითხვა | არჩევანი |
|---|---|
| Topic naming clustering-ის შემდეგ | **(b)** admin reviews + approves pre-publish |
| `page_kind` taxonomy | **(a)** closed-list (portal/dataset/report/news/faq/navigation/unknown) |
| `ts_stat` keywords vs YAKE | **(b)** YAKE Java port |
| Curation overlay UI | **(a)** P1 minimal UI for bootstrap exceptions |

---

## 10. გადაწყვეტილებები — დაწუნებული vs დამტკიცებული

### დაწუნებული

| ID | რა იყო | რატომ უარი |
|---|---|---|
| **RAG-U01-original** | YAML→DB raw migration | ხელით-მოწერილი ცოდნის გადატანა; არ წყვეტს problem-ს |
| **RAG-U03** | `SourceComposer` (catalog vs RAG separate) | portals/links corpus-ში `document_kind=portal` უნდა ცხოვრობდეს |
| **RAG-U04** | Hybrid keyword topic classifier in chat-api | IntentClassifier (U07c) უფრო სწორია |
| **RAG-U06** | Public catalog REST API | derived materialized views საკმარისია |
| **Python sidecar** (Stanza/BERTopic/KeyBERT) | NER + topic modeling | Java-native first per ADR-010 |
| **Apache AGE / Neo4j (KG day-1)** | Knowledge graph from start | Premature; revisit P4+ |

### დამტკიცებული

| ID | რა |
|---|---|
| **RAG-U01a..h** | 8 deriver port+adapter |
| **RAG-U02** | mv_portal_link / mv_specific_link / mv_topic_keywords + V11 + refresh job |
| **RAG-U05** | Curation overlay UI (P3, ≤50 rows) |
| **RAG-U07..U14** | Query understanding, multi-vector, HyDE, RRF, confidence, eval, feedback, cache |
| **RAG-U15** | KG (deferred P4+) |
| **D-25** | Derivation architecture adopted |
| **D-26** | Curation overlay budget ≤50, TTL 90d, reason mandatory |

---

## 11. შედეგი — ჩაწერილი არტეფაქტები

| არტეფაქტი | ფაილი |
|---|---|
| ADR | [docs/adr/011-rag-derivation-architecture.md](../../adr/011-rag-derivation-architecture.md) |
| Spec | [docs/plan/RAG-DERIVATION-ARCHITECTURE.md](../RAG-DERIVATION-ARCHITECTURE.md) |
| Plan rows | `docs/plan/PROJECT-PLAN.md` Phase 8 + RAG-U01a..h, U02, U05, U07..U15 |
| Approved | `docs/plan/approved/README.md` D-25, D-26 |
| Rejected | `docs/plan/BACKLOG.md` |
| Changelog | `docs/plan/CHANGELOG-PLAN.md` 2026-05-24 |
| ADR index | `docs/adr/README.md` row 011 |

**Operational constraints (spec-დან):**

- Eval gate: >5% regression → block merge
- Curation overlay: ≤50 rows; TTL 90d; reason mandatory
- Cost ceiling: ~$5/month per 10K docs (Gemini batch enrichment)
- ZERO-GAP: ძველი YAML path ცოცხალია სანამ eval pass

---

## 12. Lessons — ხდომილების takeaway

### 12.1 — „best-in-class refactor" ≠ „architecturally correct"

Senior review-ით ვიპოვე query understanding, multi-vector, eval gaps — მაგრამ **საფუძველში** მაინც ვტოვებდი ხელით-მოწერილ catalog-ს.

**Lesson:** სანამ feature-list-ის გაფართოებას ეცდები, **ჯერ ფუნდამენტი შეამოწმე** — ცოდნა საიდან გამოდის?

### 12.2 — Owner senior insight

Owner-მა არც ერთი ხაზი კოდი არ წაიკითხა — მან გაანათა „static dump არ იხსნის problem-ს".

**Lesson:** სტრატეგიული კითხვა = request to genuinely critique, not ratify.

### 12.3 — Pure emergent რისკები

**Lesson:** emergent-with-tiny-overlay — არც pure curation, არც pure emergent.

### 12.4 — Eval gate

**Lesson:** „Measure first, change second." RAG-U12 პირველი.

### 12.5 — Java-native first

**Lesson:** Stack tourism ≠ improvement. ADR-010 benefit gate.

---

## 13. Junior agent runbook

1. წაიკითხოს **ეს ფაილი** (რატომ)
2. წაიკითხოს `RAG-DERIVATION-ARCHITECTURE.md` (რა — spec §18 acceptance checklist)
3. წაიკითხოს `ADR-011`
4. გახსნას `PROJECT-PLAN.md` Phase 8 + **A2 sequence** (24 numbered steps)
5. **დაიწყოს RAG-U12-ით** (eval harness)
6. V9, V10 migrations → RAG-U01a sequential
7. არ გადახტოს step-ები

**წითელი ხაზი:**

- არ წაშალო `topics.yaml` სანამ eval pass არ გვაქვს
- არ ჩართო derivation feature flag prod-ში სანამ smoke ≥ 95%
- არ გადააჭარბო curation overlay 50 rows budget

---

## 14. Message timeline (transcript index)

| დრო (UTC+4) | Line | მონაწერი | შედეგი |
|---|---|---|---|
| 19:40 | 3135 | Snippet mid-word cut bug | polish (არა არქიტექტურა) |
| 19:50 | 3141 | Message 0 — unified RAG | RAG-U01..U06 წინადადება |
| 20:03 | 3145 | Message 1 — strategic challenge | Senior review U07..U15 |
| 20:16 | 3150 | Message 2 — derive, don't dump + keywords | Derivation model L1..L5 |
| 20:28 | 3152 | Message 3 — save to plan max detail | Spec + ADR + plan rows |
| 20:46 | 3166 | „რატომ გაჩერდი?" | Write tool encoding fix → გაგრძელება |
| 21:08 | 3197 | „როგორ შევინახო ჩატი?" | Export options + ეს narrative ფაილი |
| 21:12 | 3199 | „კარგი" | narrative შექმნა |

---

## 15. References

- [ADR-011](../../adr/011-rag-derivation-architecture.md)
- [RAG-DERIVATION-ARCHITECTURE.md](../RAG-DERIVATION-ARCHITECTURE.md)
- [PROJECT-PLAN.md](../PROJECT-PLAN.md) ფაზა 8 + A2 sequence
- [BACKLOG.md](../BACKLOG.md) rejected variants
- [CHANGELOG-PLAN.md](../CHANGELOG-PLAN.md) 2026-05-24
- `.cursor/skills/owner-architecture` — zero-gap, max-capability (on-demand, not always-applied rules)
