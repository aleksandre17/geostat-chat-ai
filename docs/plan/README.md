# გეგმა — `docs/plan/`

აქ იწერება **რას ვამტკიცებთ**, **რას ვამატებთ გზად**, და **რა სტატუსშია**. ერთი წყარო პროგრესისთვის (არა ჩაშლილი chat-ის ისტორია).

## ფაილები

| ფაილი | დანიშნულება |
|--------|-------------|
| **[PROJECT-PLAN.md](PROJECT-PLAN.md)** | **მთავარი გეგმა** — ფაზები, ცხრილი, სტატუსები |
| **[BACKLOG.md](BACKLOG.md)** | იდეები / წინადადებები — ჯერ **არა** დამტკიცებული |
| **[CHANGELOG-PLAN.md](CHANGELOG-PLAN.md)** | გეგმის ცვლილებების ჟურნალი (2026-05-22: ფაზა 0c + infra slug) |
| **[SOURCE-RAG-DESIGN-PROJECTS-FILES.md](SOURCE-RAG-DESIGN-PROJECTS-FILES.md)** | **სრული ანალიზი** `projects-files` სკრინშოტების (რა/როგორ/ტექნოლოგია/კითხვები) |
| **[INGESTION-DATA-MODEL.md](INGESTION-DATA-MODEL.md)** | Postgres `ingestion.*` — corpus, crawl, document, chunk (ingestion-service) |
| **[HYBRID-DEV-ARCHITECTURE.md](HYBRID-DEV-ARCHITECTURE.md)** | **④ Hybrid dev** — apps ლოკალურად, ინფრა remote + tunnel + env |
| **[DOCKER-ECOSYSTEM.md](DOCKER-ECOSYSTEM.md)** | ერთი Docker network, შიდა/გარე URL, compose A/B, `docker`/`hybrid` profiles |
| **[SERVER-DEPLOY-LAYOUT.md](SERVER-DEPLOY-LAYOUT.md)** | სერვერი: `geostat/` → frontend / backend / infra; artifact-ები; multi-project |
| **[approved/](approved/)** | დამტკიცებული ჩანაწერების მოკლე სია + ბმულები ADR-ზე |
| **[adr/](../adr/)** | Architecture Decision Records (დიდი არქიტექტურული გადაწყვეტილებები) |

## სტატუსები (`PROJECT-PLAN.md`)

| სტატუსი | მნიშვნელობა |
|---------|------------|
| **approved** | დამტკიცებული — შეგვიძლია განვაგრძოთ იმპლემენტაცია |
| **in_progress** | მუშავდება ახლა |
| **done** | სკელეტონი ან სრული ფუნქცია მზადაა |
| **proposed** | BACKLOG-ში — ჯერ არა approved |
| **deferred** | გადავიდა მომავალზე |

## როგორ ვმუშაობთ (გზად-გზა)

1. **ახალი იდეა** → დაამატე `BACKLOG.md`-ში (მოკლე, 1 ხაზი + კონტექსტი).
2. **დამტკიცება** → გადაიტანე `PROJECT-PLAN.md`-ში სტატუსით `approved`; ჩაწერე `CHANGELOG-PLAN.md`; საჭიროებისას ADR (`docs/adr/00N-...md`).
3. **დაწყება** → სტატუსი `in_progress`.
4. **დასრულება** → `done` + მოკლე შენიშვნა რა ფაილებში ჩანს.
5. **არქიტექტურა** → დიდი გადაწყვეტილებები მხოლოდ ADR + ბმული `approved/`.

## დაკავშირებული დოკები

- [ARCHITECTURE-B-SERVICES.md](../ARCHITECTURE-B-SERVICES.md) — Architecture B (სერვისები)
- [ARCHITECTURE.md](../ARCHITECTURE.md) — ops / deploy ფენები
- [kits/geostat-kit/docs/DEV-MODES.md](../../kits/geostat-kit/docs/DEV-MODES.md) — ①②③ + plan-ში ④ Hybrid
- [.cursor/skills/](../../.cursor/skills/) — აგენტის სტანდარტები

## წესი

პროდუქტის კოდი **არ** იწერება აქ — მხოლოდ გეგმა. იმპლემენტაციის დეტალი → სერვისის `README` / ADR.
