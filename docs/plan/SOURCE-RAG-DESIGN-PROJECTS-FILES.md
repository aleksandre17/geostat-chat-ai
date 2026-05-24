# წყარო: RAG / crawler დიზაინი (`Desktop/projects-files`)

განახლება: **2026-05-21**  
წყარო: 8 PNG სკრინშოტი ChatGPT დიალოგიდან (`image0` … `image8`, **`image2.png` არ არის**).  
დაკავშირება: [PROJECT-PLAN.md](PROJECT-PLAN.md) · [ARCHITECTURE-B-SERVICES.md](../ARCHITECTURE-B-SERVICES.md) · [ADR-009](../adr/009-architecture-b-separate-deployables.md).

ეს დოკი **არა** იმპლემენტაციის ინსტრუქცია — არის **მაქსიმალური ამოღება** იმისა, რაც სკრინშოტებში ჩანს, პლუს გადაწყვეტილების კითხვები ჩვენი senior / manifest / B-სქემისთვის.

---

## 1. რა ზეა საუბარი

საუბარია **ვებ-საიტზე დაფუძნებულ ჩატბოტზე (RAG)**, რომელიც:

1. **აგროვებს** ოფიციალური ვებ-გვერდების HTML-ს (crawl).
2. **გამოიღებს** სუფთა ტექსტს/სტრუქტურას (parse + clean).
3. **ინახავს** ძებნად ფორმატში (embeddings → **vector database**).
4. **პასუხობს** მომხმარებლის კითხვას — არა „მთელი საიტის“ გაგზავნით LLM-ში, არამედ **მხოლოდ релევантური ნაწილების** (retrieval) მიტანით.

ChatGPT-ში ეს აღწერილია როგორც **Java-ზე აწყობილი, enterprise/scalable** სისტემა — ემთხვევა ჩვენს Spring Boot + ops monorepo მიმართულებას.

**კონტექსტი პროექტისთვის:** Geostat-ის სააგენტოს/სტატისტიკის პორტალის ტიპის კონტენტი (მომხმარებელი სწავლობს საიტს ბოტის საშუალებით).

---

## 2. რა გვინდა (პროდუქტული მიზანი)

| მიზანი | სკრინშოტიდან |
|--------|----------------|
| ავტომატური ცოდნა საიტიდან | Crawl → extract → index |
| ზუსტი პასუხი კონტექსტით | RAG + similarity search |
| არა „ყველაფერი Gemini-ში“ | image8: *„Gemini-ს პირდაპირ მთელ საიტს არ აწვდი“* |
| მასშტაბი / სერიოზული backend | image0: Java enterprise/scalable |
| უფასო/თვითჰოსტი ვარიანტიც | image4: *„საუკეთესო FREE stack“* |
| დინამიკური გვერდებიც (შესაძლო) | image7: Playwright |

**ჩვენი დამატებითი მიზნები** (უკვე დამტკიცებული გეგმაში, სკრინშოტებში არა ჩანს):

- **Architecture B სერვისები** — chat-api, retrieval, ingestion (ცალკე deploy).
- **geostat-kit** — compose, deploy, manifest (`geostat.ops.json`).
- **Senior architecture** — contracts `libs/platform-contracts`, secrets `ops/config/`.

---

## 3. არქიტექტურის 4 ფენა (image0)

```text
┌─────────────┐    ┌──────────────────┐    ┌─────────────────┐    ┌──────────────┐
│ Web Crawler │ →  │ Content Extractor │ →  │ Indexer /       │ →  │ LLM / RAG    │
│             │    │ (clean HTML)      │    │ Vector DB       │    │ Bot          │
└─────────────┘    └──────────────────┘    └─────────────────┘    └──────────────┘
```

ჩვენი B-სქემის მაპინგი:

| ფენა (სკრინი) | სერვისი (ჩვენ) |
|----------------|----------------|
| Crawler + Extract + Chunk + Embed (write) | **ingestion-service** |
| Vector DB + Similarity search | **retrieval-service** + Qdrant |
| LLM + user session + UI | **chat-api** (`apps/backend`) + **frontend** |

---

## 4. Ingestion pipeline (რა ჩანს სკრინშოტებში)

### 4.1 მოკლე ჯაჭვი (image1)

```text
Website
  → Crawl HTML pages
  → Java Crawler (Jsoup / crawler4j)
  → Extract clean content
  → Content Processing: Chunking + Cleaning
  → Generate embeddings
  → (ქვემოთ ჩანჭრილი — Vector DB)
```

### 4.2 დეტალური ჯაჭვი (image6 — სათაური „Architecture“)

```text
crawler4j
  → URL Queue
  → Fetch HTML
  → Jsoup Parse
  → Content Cleaner
  → Chunking
  → Embeddings
  → Qdrant
```

### 4.3 Crawling წესები (image3)

| თემა | რა ჩანს |
|------|---------|
| HTML parsing | **Jsoup** — „მარტივი და ძლიერი“ |
| Full crawler | **crawler4j** |
| crawler4j ფუნქციები | `robots.txt`, **depth limit**, **parallel crawling**, **URL filtering** |
| კონტექსტი | ქვედა ტექსტი ნაწილობრივ დაფარულია; ჩანს *„უკვე თითქმის სტანდარტია Java AI…“* |

### 4.4 RAG storage ნაწილი (image5)

```text
Content Processing (Chunking + Cleaning)   [ზედა ნაწილი ნაწილობრივ მოჭრილი]
  → Generate embeddings
  → Vector Database (Qdrant / Weaviate)
  → User question
  → Retrieval (RAG) — Similarity search
  → LLM (OpenAI / Llama)                    [ქვედა ნაწილი ნაწილობრივ მოჭრილი]
```

---

## 5. Query pipeline (მომხმარებლის კითხვა)

### 5.1 image8 — Gemini-ზორიენტირებული ჯაჭვი

```text
Website Crawl
  → Extract Content
  → Chunking
  → Embeddings
  → Vector DB
  → User Question
  → Relevant Chunks მოძებნა (vector search)
  → Gemini API
  → Final Answer
```

**ძირითადი პრინციპი (image8, ქართულად):**  
LLM-ს **არ** გაუგზავნო მთელი საიტი — მხოლოდ retrieval-ით შერჩეული chunks + კითხვა.

### 5.2 image5 — LLM ალტერნატივები

ჩანს: **OpenAI / Llama** (არა მხოლოდ Gemini).

---

## 6. რას გამოვიყენებთ — ორი stack ვარიანტი სკრინშოტებში

სკრინშოტები **ორი სტეკს** ახლოვებს — სრულად ერთად არ ჯდება უკვე არსებულ repo-ში (Spring AI + **Gemini**).

### 6.1 „საუკეთესო FREE stack“ (image4)

| ნაწილი | ტექნოლოგია |
|--------|------------|
| Backend | **Java Spring Boot** |
| Crawl | **Jsoup + crawler4j** |
| AI framework | **LangChain4j** |
| LLM | **Ollama + Llama 3** |
| Embeddings | **nomic-embed-text** |
| Vector DB | **Qdrant** |
| Frontend | **React** |

### 6.2 „Architecture“ / გაფართოებული stack (image7)

| Layer | Tool |
|-------|------|
| Crawl | **crawler4j** |
| Parse | **Jsoup** |
| Dynamic pages | **Playwright** |
| AI | **LangChain4j** |
| Embeddings | **Ollama** |
| Vector DB | **Qdrant** |
| Backend | **Spring Boot** |

**შენიშვნა image7-ში (ქართულად, ნაწილობრივ):**  
*„Project ძალიან აქტიური აღარ არის“* — სავარაუდოდ **საუბარია კონკრეტულ OSS პროექტზე** (მაგ. crawler4j ან სხვა), **არა ჩვენს geostat-chat-ai-ზე**. გეგმაში: გავიგოთ რომელი repo გულისხმობდნენ სანამ dependency-ს დავამატებთ.

### 6.3 Gemini ხაზი (image8)

| ნაწილი | ტექნოლოგია |
|--------|------------|
| LLM (generation) | **Gemini API** |
| Retrieval | Vector DB + chunk search |
| Ingestion | crawl → chunk → embed (სხეული იგივე) |

### 6.4 Vector DB ვარიანტები

- **Qdrant** — image4, image6, image7 (primary სკრინშოტებში).
- **Weaviate** — image5 (ალტერნატივა, ერთხელ ჩამოთვლილი).

---

## 7. როგორ გამოვიყენებთ (ოპერაციული მოდელი)

### 7.1 Ingestion (ოფლაინ / ფონური)

1. **Seed URL** (მაგ. geostat.ge root) → `crawler4j` + URL queue.
2. **robots.txt** + depth + filter — მხოლოდ დაშვებული გზები.
3. **Fetch** → **Jsoup** parse; საჭიროებისას **Playwright** (SPA).
4. **Cleaner** — boilerplate ამოღება.
5. **Chunking** — ზომა/overlap (სკრინშოტებში **არ** არის დეტალი).
6. **Embeddings** — მოდელი (nomic / Ollama / cloud — **გადაწყვეტილება**).
7. **Upsert** → **Qdrant**.

**არ უნდა** იყოს მომხმარებლის HTTP მოთხოვნაზე დაკავშირებული — async job / worker.

### 7.2 Query (ონლაინ)

1. მომხმარებელი → **frontend** → **chat-api**.
2. chat-api → **retrieval-service**: `search(query)` → top-k chunks.
3. chat-api → **LLM** (ახლა repo-ში: **Spring AI + Gemini**): prompt = system + chunks + question.
4. პასუხი UI-ში.

**არ უნდა** chat-api პირდაპირ იძახებდეს crawler-ს.

### 7.3 ჩვენი B-სქემა (დამტკიცებული)

```text
                    ┌──────────────┐
  User ──► frontend │   chat-api   │──► Gemini (ახლა)
                    │  (8090)      │
                    └──────┬───────┘
                           │ HTTP sync
                           ▼
                    ┌──────────────┐      ┌─────────┐
                    │  retrieval   │─────►│ Qdrant  │
                    │  (8092)      │      └─────────┘
                    └──────────────┘
                           ▲
                           │ index write (future)
                    ┌──────────────┐
                    │  ingestion   │──► crawler4j / Jsoup / …
                    │  (8093)      │
                    └──────────────┘
```

კონტრაქტები: `libs/platform-contracts` (`RetrievalPort`, DTOs).

---

## 8. რა ჩანს / რა არ ჩანს სკრინშოტებში

### 8.1 ჩანს ცხადად

| თემა | სად |
|------|-----|
| 4 კომპონენტი (crawl, extract, index, RAG bot) | image0 |
| Jsoup vs crawler4j როლი | image1, image3 |
| crawler4j: robots, depth, parallel, URL filter | image3 |
| სრული ingestion ჯაჭვი crawler4j→Qdrant | image6 |
| FREE stack ცხრილი | image4 |
| Playwright დინამიკური გვერდებისთვის | image7 |
| RAG: embed → VDB → similarity → LLM | image5 |
| Gemini + „არა მთელი საიტი“ | image8 |
| React frontend | image4 |
| Chunking + Cleaning | image1, image5 |
| LLM ალტერნატივები OpenAI/Llama | image5 |
| Vector DB ალტერნატივა Weaviate | image5 |

### 8.2 არ ჩანს ან ნაწილობრივ დაფარულია (UI popup)

| თემა | შენიშვნა |
|------|----------|
| **image2.png** | ფაილი არ არსებობს ფოლდერში |
| კონკრეტური საიტი / seed URL | არც ერთ სკრინში |
| chunk size, overlap, metadata | მხოლოდ „Chunking“ ლეიბლი |
| embedding მოდელის პარამეტრები | სახელები ჩანს, API/key management — არა |
| Qdrant schema (collection, filters) | არა |
| ინდექსის განახლება / re-crawl სქემა | არა |
| მულტიენოვანება (ქართული/ინგლისური) | არა (ჩვენს chat-api-ში უკვე არის) |
| ავტორიზაცია / rate limit / monitoring | არა |
| CI/CD, Docker, prod deploy | არა (ჩვენს kit-ში ცალკე) |
| სერვისებს შორის პროტოკოლი | არა (ჩვენ HTTP + future events) |
| სრული ტექსტი image3 ბოლოში | popup-ით დაფარული |
| „როგორ დავაპატარავოთ…“ (image8 ბოლო) | წინადადება მოჭრილი — სავარაუდოდ token/chunk ოპტიმიზაცია |
| LangChain4j vs Spring AI | image4/7 LangChain4j; ჩვენ repo Spring AI |
| Speech (STT/TTS) | სკრინშოტებში არა; ჩვენს backend-ში უკვე არის |

### 8.3 repo სტatus (2026-05-22 — B-25 zero-gap)

| სკრინშოტის გეგმა | `geostat-chat-ai` ახლა |
|------------------|-------------------------|
| სრული RAG + Qdrant | **კი** — ingestion → Qdrant → retrieval → chat-api |
| crawler4j + Jsoup pipeline | **კი** — `apps/ingestion-service`; chat-api-ში live BFS **არა** (StructureLookup წაშლილი) |
| LangChain4j + Ollama + Llama 3 | **არა** — Spring AI + Gemini prod; Ollama optional (P7-01) |
| Qdrant | **კი** — `libs/qdrant-client`, retrieval-service |
| React + Spring Boot | **კი** — `apps/frontend`, `apps/backend` (`com.geostat.chat`) |
| სერვისები (Architecture B) | **კი** — chat-api, retrieval, ingestion, platform-contracts |
| Org/structure queries | **კი** — corpus seeds (`/ka/structure`, `/en/structure`) + RAG; `CorpusContextFormatter` |

---

## 9. გადაწყვეტილების კითხვები (გეგმაში უნდა დავამტკიცოთ)

თითოეულს სტატუსი: `open` → `approved` → `decided` (ჩაწერა PROJECT-PLAN + CHANGELOG-PLAN).

| ID | კითხვა | ვარიანტები | სკრინის მიხედვით |
|----|--------|------------|------------------|
| Q-01 | **Primary LLM** | A) Gemini (ახლა repo) B) Ollama+Llama C) ჰიბრიდი | **closed** — Gemini prod; Ollama local optional (P7-01) |
| Q-02 | **AI integration** | A) Spring AI B) LangChain4j C) ორივე | **closed** — Spring AI primary |
| Q-03 | **Embeddings** | A) nomic via Ollama B) Google embeddings C) სხვა | **closed** — `embedding-adapters` |
| Q-04 | **Vector DB** | A) Qdrant B) Weaviate | **closed** — Qdrant |
| Q-05 | **Dynamic HTML** | A) მხოლოდ Jsoup B) +Playwright | **closed** — Jsoup default; Playwright P3-03b trigger |
| Q-06 | **Crawler** | A) crawler4j B) crawler4j+Jsoup (ორივე სკრინში) | image3,6 |
| Q-07 | **OSS „inactive project“** | რომელი dependency? გავაკეთოთ fork/pin? | image7 ქართული ხაზი |
| Q-08 | **Chunking strategy** | ზომა, overlap, metadata (URL, title) | image8 „დავაპატარავოთ“ |
| Q-09 | Ingestion trigger | **closed** — B-26 scheduler + API jobs + auto-continue |
| Q-10 | **Event bus** | **RabbitMQ** (P5, self-host) | approved 2026-05-22 |
| Q-11 | **საწყისი crawl scope** | მთელი geostat.ge / სექციები / sitemap | არ ჩანს |
| Q-12 | **ქართული კონტენტი** | embedding/LLM locale strategy | ჩვენი chat უკვე bilingual |
| Q-13 | **Structure / clarification** | corpus-only (B-25) | **superseded** — StructureLookup removed; ingestion seeds + RAG |

### 9.1 დამტკიცებული პოზიცია (2026-05-23 — [ADR-010](../adr/010-product-stack-benefit-gate.md))

| კითხვა | გადაწყვეტილება | მიზეზი |
|--------|----------------|--------|
| Q-01 | **Gemini prod gen**; Ollama local optional (P7-01) | ერთი paid SaaS (D-16); free local dev |
| Q-02 | **Spring AI** primary; LangChain4j rejected | ერთი stack, უკვე prod |
| Q-03 | Embeddings via **embedding-adapters** | done |
| Q-04 | **Qdrant** | operational + shared lib |
| Q-05 | **Jsoup + crawler4j**; Playwright trigger (P3-03b) | benefit gate |
| Q-06 | **crawler4j + Jsoup** | image6 ჯაჭვი |
| Q-13 | **Single pipeline — corpus + RAG (B-25)** | zero-gap; no live BFS in chat-api |
| Q-10 | **RabbitMQ** (PLAN P5) | done async path |

---

## 10. მაპინგი: სკრინი → PROJECT-PLAN ფაზები

| სკრინშოტის ლოგიკა | ჩვენი ფაზა | სტატუსი |
|------------------|-----------|---------|
| 4 კომპონენტი + Java | ხედვა + ADR-009 | done / accepted |
| crawler4j→Qdrant ჯაჭვი | ფაზა 3 (ingestion) | **done** |
| RAG search | ფაზა 4 (retrieval) | **done** |
| Gemini + chunks | ფაზა 2 + 5 (chat→retrieval, B-15/B-25) | **done** |
| React UI | frontend | **done** |
| Spring Boot | chat-api (`com.geostat.chat`) | **done** |
| Full stack compose + Qdrant | ფაზა 6 | **done** |
| Async ingestion | ფაზა 5 (RabbitMQ) | **done** |

---

## 11. სერვისების პასუხისმგებლობა (SOLID — სკრინშოტი + B)

| პასუხისმგებლობა | სერვისი | არა სად |
|----------------|---------|---------|
| URL queue, crawl, parse, clean, chunk, embed write | ingestion | chat-api |
| Vector store, similarity search | retrieval | chat-api |
| Prompt, session, topics, RAG orchestration, Gemini call | chat-api | ingestion |
| Org/structure clarification | chat-api via **retrieval** (indexed corpus) | live BFS / StructureLookup (**removed B-25**) |
| Full-site background crawl | ingestion `@Async` + frontier until done | **done (B-26)** — `crawlMode: full-site`, scheduler |
| UI | frontend | backend |

---

## 12. შემდეგი ნაბიჯები გეგმაში

1. ~~Owner დაუმტკიცებს Q-01 … Q-13~~ — **done** (2026-05-23, ADR-010); Q-13 superseded B-25 (single pipeline).
2. **Implementation queue:** P3-03b (Playwright trigger), P7-01 (Ollama local gen), Q-06…Q-12 (non-blocking).
3. **Ops:** GEMINI_API_KEY rotation (OPS-01); full corpus crawl/reindex when policy requires (OPS-02, B-26).

---

## 13. სკრინშოტების ინდექსი

ფოლდერი: `C:\Users\Test-User\Desktop\projects-files\`

| ფაილი | მთავარი შინაარსი |
|-------|------------------|
| `image0.png` | 4 კომპონენტი, Java scalable |
| `image1.png` | Crawl→Jsoup/crawler4j→chunk→embed |
| `image2.png` | **არ არის** |
| `image3.png` | Jsoup vs crawler4j, robots/depth/parallel/filter |
| `image4.png` | FREE stack ცხრილი |
| `image5.png` | RAG: VDB, similarity, OpenAI/Llama |
| `image6.png` | Architecture: crawler4j→Qdrant |
| `image7.png` | Stack + Playwright; „project inactive“ შენიშვნა |
| `image8.png` | Gemini RAG; არა მთელი საიტი |

---

*დოკის ავტორი: ამოღება სკრინშოტებიდან + ჩვენი გეგმის კონტექსტი. იმპლემენტაციისას განახლება აუცილებელია.*
