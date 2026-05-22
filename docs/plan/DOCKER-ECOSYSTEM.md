# Docker ეკოსისტემა — ერთი ქსელი, კომუნიკაცია, შენარჩუნება

განახლება: **2026-05-22**  
დაკავშირება: [SERVER-DEPLOY-LAYOUT.md](SERVER-DEPLOY-LAYOUT.md) · [INFRA-DATA-STORES.md](INFRA-DATA-STORES.md) · [HYBRID-DEV-ARCHITECTURE.md](HYBRID-DEV-ARCHITECTURE.md) · [PROJECT-PLAN.md](PROJECT-PLAN.md) · [ops/compose/stack/docker-compose.yml](../../ops/compose/stack/docker-compose.yml) · [ops/compose/catalog.json](../../ops/compose/catalog.json)

**სტატუსი:** დამტკიცებული (D-07…D-09) — **იმპლემენტაცია:** P0-infra ✅, P6 compose-gen/stack ✅ (2026-05-22).

---

## 1. რა არის „ერთი ეკოსისტემა“

**ერთი ეკოსისტემა** = ერთი **Docker user-defined network** სტაბილური სახელით, სადაც კონტეინერები ერთმანეთს **სერვისის სახელით (DNS)** ხედავენ: `postgres`, `redis`, `qdrant`, `geostat-chat-ai-api`, `retrieval`, `ingestion`, `geostat-chat-ai-fe`.

ჩვენს repo-ში უკვე არსებობს stack ქსელი:

```yaml
# ops/compose/stack/docker-compose.yml
networks:
  geostat-chat-ai-net:
    name: geostat-chat-ai-net
```

compose-gen (`kits/geostat-kit/compose/build.py`) იყენებს `{network_name}` / `{compose_project_name}` — **ერთი წყარო ჭეშმარიტი**, არა copy-paste თითო compose-ში.

---

## 2. სამი წესი (რომ არ დაიშალოს)

| # | წესი | მაგალითი |
|---|------|----------|
| **E-1** | **ერთი ქსელის სახელი** ყველა compose-ში (infra + apps + stack) | `geostat-chat-ai-net` |
| **E-2** | **შიდა URL** = Docker DNS — მხოლოდ **კონტეინერებს შორის** | `jdbc:postgresql://postgres:5432/geostat`, `http://retrieval:8092` |
| **E-3** | **გარე URL** = host IP ან SSH tunnel — **ლაპტოპიდან / hybrid host-ზე** | `INFRA_HOST=127.0.0.1`, `RETRIEVAL_BASE_URL=http://localhost:8092` |

---

## 3. სრული Docker ეკოსისტემა (რეჟიმი ② ან სერვერზე ③/⑥)

ყველა კონტეინერი **ერთ ქსელში** — „ყველა ხედავს ერთმანეთს“.

```text
network: geostat-chat-ai-net  (name: geostat-chat-ai-net)

  ┌─────────────┐  ┌──────────────┐  ┌─────────────┐
  │ postgres    │  │ redis        │  │ qdrant      │
  └──────▲──────┘  └──────▲───────┘  └──────▲──────┘
         │                 │                 │
  ┌──────┴──────┐  ┌──────┴───────┐  ┌──────┴──────┐
  │ ingestion   │  │ retrieval    │  │ chat-api    │
  │ :8093       │  │ :8092        │  │ :8090       │
  └─────────────┘  └──────────────┘  └──────┬──────┘
                                            │
                                     ┌──────┴──────┐
                                     │ frontend    │
                                     └─────────────┘
```

### 3.1 კომუნიკაცია (შიდა DNS)

| მანგანა | მიზანი | შიდა მისამართი (docker profile) |
|---------|--------|----------------------------------|
| ingestion | DB jobs | `jdbc:postgresql://postgres:5432/geostat` |
| ingestion | async (P5) | `redis:6379` |
| ingestion / retrieval | vectors | `http://qdrant:6333` |
| retrieval | search API | (სხვა აპები → `http://retrieval:8092`) |
| chat-api | RAG | `http://retrieval:8092` |
| ingestion (worker role) | pipeline / future bus | `8093`; embedded `backend/worker` **off** (`features.worker: false`) |
| frontend (ბრაუზერი) | API | **არა** Docker DNS — `VITE_API_URL` → host port ან nginx |

### 3.2 ორი გზა compose-ის ორგანიზაციისთვის (P6)

| ვარიანტი | აღწერა | როდის |
|----------|--------|-------|
| **A — ერთი stack compose** | infra + apps ერთ `ops/compose/stack/docker-compose.yml`-ში (catalog templates) | `geostat stack up` — მარტივი, ერთი ბრძანება |
| **B — infra + apps, ერთი ქსელი (რეკომენდებული hybrid-თან)** | 1) `ops/compose/infra` ქმნის `geostat-chat-ai-net` 2) stack/per-app ურთავს **external** ქსელს | infra ყოველთვის ჩართული; apps ცალ-ცალკე ან stack |

**ვარიანტი B — external network (სამიზნე yaml):**

```yaml
# apps ან stack compose-ში
networks:
  geostat-chat-ai-net:
    external: true
    name: geostat-chat-ai-net
```

**გაშვების რიგი (B):**

1. `geostat infra remote up` — postgres + redis + qdrant + ქსელი  
2. `geostat stack up` ან `geostat ret compose up` — apps უკვე იმავე ქსელში  

---

## 4. Hybrid (④) — ლოგიკური vs ფიზიკური ეკოსისტემა

Hybrid-ში **Windows host-ზე JVM არ არის** იმავე Docker network-ზე, როგორც `postgres`. ეკოსისტემა **ორი ფიზიკური ზონა**, **ერთი ლოგიკური** (env-ით).

```text
  Windows (host)                         Linux server
  ┌─────────────────┐                    ┌──────────────────────────┐
  │ chat-api :8090  │──tunnel/LAN──────►│ geostat-chat-ai-net      │
  │ retrieval :8092 │                    │  postgres, redis, qdrant  │
  │ ingestion :8093 │                    │  (ინფრა ერთ ქსელში)       │
  │ Vite :5173      │                    └──────────────────────────┘
  └─────────────────┘
         │
         ├── peer HTTP: localhost:8090 ↔ 8092 ↔ 8093
         └── state:     INFRA_HOST:5432 / 6379 / 6333 (tunnel)
```

| კავშირი | Hybrid (apps host) | Full Docker (ყველა კონტეინერი) |
|---------|-------------------|--------------------------------|
| chat → retrieval | `http://localhost:8092` | `http://retrieval:8092` |
| ingestion → postgres | `jdbc:postgresql://127.0.0.1:5432/...` (tunnel) | `jdbc:postgresql://postgres:5432/...` |
| კონტეინერი → postgres (სერვერზე ③) | — | `postgres:5432` |

დეტალი tunnel: [HYBRID-DEV-ARCHITECTURE.md](HYBRID-DEV-ARCHITECTURE.md) §4.

---

## 5. Env პროფილები — ერთი ეკოსისტემა, ორი hostname-სეტი

| ცვლადი | `docker` (ყველა კონტეინერში) | `hybrid` (apps host-ზე) |
|--------|-------------------------------|-------------------------|
| DB | `postgres:5432` | `${INFRA_HOST}:5432` |
| Redis | `redis:6379` | `${INFRA_HOST}:6379` |
| Qdrant | `http://qdrant:6333` | `http://${INFRA_HOST}:6333` |
| Retrieval (chat-დან) | `http://retrieval:8092` | `http://localhost:8092` |
| Frontend → API | host port / nginx | `http://localhost:8090` |

```properties
SPRING_PROFILES_ACTIVE=dev,docker
# ან
SPRING_PROFILES_ACTIVE=dev,hybrid
```

**პრინციპი:** კოდი ერთი; მისამართები profile-იდან / `ops/config/<module>/.env.dev`.

### 5.1 შიდა vs გარე (შეჯამება)

| ტიპი | ვინ იყენებს | Hostname |
|------|------------|----------|
| შიდა | კონტეინერი → კონტეინერი | `postgres`, `retrieval`, … |
| გარე | Windows JVM, ბრაუზერი | `localhost` / `INFRA_HOST` / SERVER IP |

---

## 6. პრაქტიკული წესრიგი (რეჟიმი → ეკოსისტემა)

| რეჟიმი | რა „აწევს“ | Docker ეკოსისტემა |
|--------|------------|-------------------|
| **④ Hybrid** | `infra remote up` + tunnel | სერვერზე: **მხოლოდ** PG/Redis/Qdrant ერთ ქსელში |
| **④ Hybrid apps** | F5 / `bootRun` | logical peers `localhost`; state `INFRA_HOST` |
| **② Full Docker (laptop)** | `geostat stack up` | ყველაფერი `geostat-chat-ai-net` |
| **③ Remote watch** | `be/fe dev watch` + infra იგივე host | apps + infra — **შიდა DNS** (`postgres`, …) |
| **⑥ Prod server** | stack-deploy / compose prod | იგივე network name; გარედან nginx/443 |

---

## 7. manifest / catalog (სამიზნე)

### 7.1 `geostat.ops.json`

```json
"stack": {
  "composeDir": "ops/compose/stack",
  "networkName": "geostat-chat-ai-net"
},
"infra": {
  "role": "infra",
  "type": "compose-stack",
  "path": "ops/compose/infra",
  "secretsModule": "infra",
  "networkName": "geostat-chat-ai-net",
  "remoteOnly": true
}
```

### 7.2 compose-gen

ყველა template `ops/compose/catalog.json`-ში უკვე იყენებს `{network_key}` + `net_internal` → `name: {network_name}`.  
ახალი infra/retrieval/ingestion targets — **იგივე** `network_name` manifest-იდან.

### 7.3 სერვისის სახელები (კონვენცია)

| compose `services:` key | როლი |
|-------------------------|------|
| `postgres` | DB |
| `redis` | queue/cache |
| `qdrant` | vector DB |
| `geostat-chat-ai-api` (ან manifest target) | chat-api |
| `retrieval` | retrieval-service |
| `ingestion` | ingestion-service |
| `geostat-chat-ai-fe` | frontend |

**არ გავაკეთოთ:** ცალკე compose თითო სერვისზე **სხვა** `network` სახელით — ისინი **ვერ** ნახავენ `postgres`-ს.

---

## 8. დამტკიცებული გადაწყვეტილებები (D-07 … D-09)

| ID | გადაწყვეტილება | სტატუსი |
|----|----------------|---------|
| **D-07** | ერთი Docker network ყველა compose-ში: `geostat-chat-ai-net` | **approved** 2026-05-21 |
| **D-08** | P6: ვარიანტი **B** (infra compose + apps external network) hybrid/production-ისთვის; ვარიანტი **A** optional სრული stack-ისთვის | **approved** |
| **D-09** | Spring/env profiles `docker` vs `hybrid` — შიდა DNS vs `INFRA_HOST` / localhost | **approved** |

იხ. [approved/README.md](approved/README.md).

---

## 9. PLAN მაპინგი

| PLAN ID | ამოცანა |
|---------|---------|
| P0-infra-01 | infra compose + `geostat-chat-ai-net` შექმნა |
| P6-01 | stack/catalog — apps `external: true` network |
| P6-02 | catalog templates: retrieval + ingestion + infra ერთ `network_name`-ზე |
| P0-infra-06 | env profiles `docker` / `hybrid` დოკუმენტაცია per module |

Q-16 ([INFRA-DATA-STORES.md](INFRA-DATA-STORES.md)): **დახურული** რეკომენდაციით — infra ცალკე + stack external network (D-08).

---

## 10. რა **არ** გავაკეთოთ

| არასორსი | რატომ |
|----------|--------|
| ცალკე bridge ქსელი თითო `apps/*/compose`-ზე | იზოლაცია — არ ხედავენ postgres-ს |
| Hybrid-ში `postgres` hostname Windows `.env`-ში | არ resolve-დება |
| `0.0.0.0:5432` ინტერნეტზე | tunnel/VPN; bind `127.0.0.1` სერვერზე |
| Java „infra-service“ `apps/`-ში | მხოლოდ official images — [INFRA-DATA-STORES.md](INFRA-DATA-STORES.md) |

---

## 11. შეჯამება

- **ინფრას** Java აპი არ სჭირდება — მაგრამ **Docker ქსელში** უნდა იყოს `postgres` / `redis` / `qdrant`.
- **ერთი ეკოსისტემა** = `geostat-chat-ai-net` + შიდა service hostnames.
- **Hybrid:** Docker ეკოსისტემა სერვერზე (ინფრა); apps host-ზე + tunnel; peers — `localhost`, state — `INFRA_HOST`.
- **შენარჩუნება:** manifest `networkName`, profiles `docker`/`hybrid`, infra ჯერ → apps შემდეგ (ვარიანტი B).
