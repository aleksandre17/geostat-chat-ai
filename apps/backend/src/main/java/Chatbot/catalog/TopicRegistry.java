package Chatbot.catalog;

import Chatbot.model.*;

import java.util.*;

/**
 * Central registry — one TopicDefinition per topic.
 *
 * Adding a new topic: add one m.put() block here. Nothing else changes.
 *
 * Layout per entry:
 *   rules        — detection rules (compound priority-10 first, simple priority-20)
 *   portals      — interactive tools / calculators (shown first, capped at 2 in ResponseBuilder)
 *   statistics   — main GeoStat statistics page
 *   metadata     — metadata page (null if none)
 *   methodology  — methodology page (null if none)
 *   specialLinks — replaces standard hierarchy for organisational topics
 *   style        — icon name + colours for the frontend card
 *   newsCategoryId — GeoStat news filter ID (0 = no category news)
 */
public final class TopicRegistry {

    private TopicRegistry() {}

    // ─── Compact helpers ────────────────────────────────────────────────────

    private static LinkInfo li(String url, String ka, String en) {
        return new LinkInfo(url, ka, en);
    }

    private static PortalInfo pi(String url, String ka, String en, String desc) {
        return new PortalInfo(url, ka, en, desc);
    }

    private static TopicDefinition.TopicStyle st(String icon, String bg, String light) {
        return new TopicDefinition.TopicStyle(icon, bg, light);
    }

    /** Topic without special links (most topics). */
    private static TopicDefinition def(
            Topic t, List<TopicRule> rules, List<PortalInfo> portals,
            LinkInfo stats, LinkInfo meta, LinkInfo method,
            TopicDefinition.TopicStyle style, int newsId) {
        return new TopicDefinition(t, rules, portals, stats, meta, method, List.of(), style, newsId);
    }

    /** Topic with special links (organisational topics). */
    private static TopicDefinition defS(
            Topic t, List<TopicRule> rules, List<PortalInfo> portals,
            LinkInfo stats, LinkInfo meta, LinkInfo method,
            List<LinkInfo> special, TopicDefinition.TopicStyle style, int newsId) {
        return new TopicDefinition(t, rules, portals, stats, meta, method, special, style, newsId);
    }

    // ─── Shared methodology LinkInfos (several topics share the same URL) ──

    private static final LinkInfo SOCIAL_METHODOLOGY = li(
            "https://www.geostat.ge/ka/modules/categories/552/methodologia-sotsialuri-statistika",
            "მეთოდოლოგია: სოციალური სტატისტიკა", "Methodology: Social Statistics");

    private static final LinkInfo BUSINESS_METHODOLOGY = li(
            "https://www.geostat.ge/ka/modules/categories/121/methodologia-biznes-statistika",
            "მეთოდოლოგია: ბიზნეს სტატისტიკა", "Methodology: Business Statistics");

    // ─── Agricultural product keywords — used for "წარმოება" disambiguation ──
    // FIX: "ვაშლის წარმოება", "თევზის წარმოება" etc. must go to AGRICULTURE, not INDUSTRY.
    // These are food/natural product keywords. If query contains any of these + "წარმოება",
    // the compound rule in AGRICULTURE fires at priority 10 (before INDUSTRY's priority 20).
    private static final List<String> AGRI_PRODUCT_KEYWORDS = List.of(
            "ვაშლ","მსხალ","ქლიავ","ბალი","ატამ","გარგარ","ყურძენ","ციტრუს","ლიმონ","მანდარინ",
            "მარწყვ","კივი","ბროწეულ","ლეღვ","თხილ","კაკალ","ნუშ","ფიჭვ","მარცვლეულ",
            "ხორბალ","სიმინდ","ქერ","შვრი","სოიო","მზესუმზირ","შაქრის ჭარხალ",
            "კარტოფილ","პომიდვრ","კიტრ","ბოლოკ","ხახვ","ნიორ","კომბოსტ","ბადრიჯან",
            "ბოსტნეულ","მწვანილ","სალათ","ლობი","ბარდ","ოხრახუშ",
            "ბალახ","სახამებელ","ბამბ","სელ","თამბაქო","ჩაი","ზეთისხილ",
            "რძ","ყველ","კარაქ","კვერцх","კვერც","კვერძ","ხორც","ქათმ","ღორ","საქონელ",
            "ცხვარ","თხ","ფრინველ","ნადირ","ფუტკარ","თაფლ","ბლომ",
            "თევზ","ორაგულ","ლოქო","კალმახ","ზუთხ","ბოჭალ","კიბოხ","კიბო",
            "სოკო","კენკრ","ხილ","ხილის","ღვინ","ლუდ","წვენ","კომპოტ",
            "სოფლის","სასოფლო","ფერმ","აგრო","სათბურ","სარწყავ"
    );

    // ─── Registry ───────────────────────────────────────────────────────────

    private static final Map<Topic, TopicDefinition> REGISTRY;

    static {
        Map<Topic, TopicDefinition> m = new LinkedHashMap<>();

        // ====================================================================
        // ECONOMIC
        // ====================================================================

        m.put(Topic.NATIONAL_ACCOUNTS, def(Topic.NATIONAL_ACCOUNTS,
                List.of(
                        TopicRule.compound(List.of("რეალური"),
                                List.of("ზრდ","growth","მშპ","gdp","ეკონომიკ"), Set.of()),
                        TopicRule.compound(List.of("სექტორულ"),
                                List.of("ანგარიშ","accounts","ეკონომიკ"), Set.of()),
                        TopicRule.simple(20, List.of(
                                "მშპ","gdp","მთლიანი შიდა პროდუქტ","gross domestic",
                                "მეშ","მთლიანი ეროვნული შემოსავ","ეროვნული შემოსავ",
                                "gni","gross national income","ეროვნული ანგარიშ","national accounts",
                                "სატელიტური","satellite account","დანახარჯები-გამოშვებ",
                                "გამოშვების ცხრილ","supply and use","რესურსებისა და გამოყენებ",
                                "ეკონომიკური ზრდ","რეცესი","ეკონომიკ","economy",
                                // FIX: დაუკვირვებადი ეკონომიკა → NATIONAL_ACCOUNTS
                                "დაუკვირვებადი ეკონომიკ","დაუკვირვებელი ეკონომიკ","unobserved economy","shadow economy",
                                "არარეგისტრირებული ეკონომიკ","informal economy","ჩრდილოვანი ეკონომიკ"))
                ),
                List.of(
                        pi("https://sna.geostat.ge/ka/4/Mtavari",
                                "ეროვნული ანგარიშების პორტალი","National Accounts Portal",
                                "მშპ, სექტორული ანალიზი, ეკონომიკური ზრდა"),
                        pi("https://eap.geostat.ge/",
                                "ეკონომიკური ანალიზის პორტალი","Economic Analysis Portal",
                                "ეკონომიკური ინდიკატორები და ანალიზი")
                ),
                li("https://www.geostat.ge/ka/modules/categories/23/mtliani-shida-produkti-mshp",
                        "მთლიანი შიდა პროდუქტი (მშპ)","Gross Domestic Product (GDP)"),
                li("https://www.geostat.ge/ka/modules/categories/110/metadata-erovnuli-angarishebi",
                        "მეტამონაცემები: ეროვნული ანგარიშები","Metadata: National Accounts"),
                li("https://www.geostat.ge/ka/modules/categories/119/methodologia-erovnuli-angarishebi",
                        "მეთოდოლოგია: ეროვნული ანგარიშები","Methodology: National Accounts"),
                st("erovnuli_angarishebi","#3B82F6","#DBEAFE"), 3));

        m.put(Topic.BUSINESS, def(Topic.BUSINESS,
                List.of(
                        TopicRule.compound(List.of("მმართველობ"),
                                List.of("კორპორატიულ","corporate"), Set.of(Topic.MANAGEMENT)),
                        TopicRule.simple(20, List.of(
                                "ბიზნეს","საწარმო","კომპანი","რეგისტრაცი",
                                "business","enterprise","ფირმ","ორგანიზაცი"))
                ),
                List.of(
                        pi("https://br.geostat.ge/",
                                "ბიზნეს რეგისტრი","Business Register","საწარმოების საძიებო სისტემა"),
                        pi("https://eap.geostat.ge/",
                                "ეკონომიკური ანალიზის პორტალი","Economic Analysis Portal",
                                "ბიზნეს სექტორის ანალიზი")
                ),
                li("https://www.geostat.ge/ka/modules/categories/195/biznes-sektori",
                        "ბიზნეს სექტორი","Business Sector"),
                li("https://www.geostat.ge/ka/modules/categories/530/metadata-biznes-sektori",
                        "მეტამონაცემები: ბიზნეს სექტორი","Metadata: Business Sector"),
                BUSINESS_METHODOLOGY,
                st("biznesSeqtori","#64748B","#F1F5F9"), 6));

        m.put(Topic.PRICES, defS(Topic.PRICES,
                List.of(
                        TopicRule.simple(20, List.of(
                                "ინფლაცი","ფას","cpi","price","inflation",
                                "გაძვირებ","სამომხმარებლო","დეფლაცი",
                                // FIX: ჰარმონიზებული + producer/import prices + real estate prices
                                "ჰარმონიზებული","HICP","harmonized","harmonised","hicp",
                                "ფასების ინდექს","price index",
                                "მწარმოებელთა ფასების","producer price","ppi",
                                "იმპორტის ფასების","import price",
                                "ექსპორტის ფასების","export price",
                                "მშენებლობის ღირებულების ინდექს","construction cost index",
                                "საცხოვრებელი უძრავი ქონების ფასების","residential property price",
                                "უძრავი ქონების ფასების","real estate price",
                                "სოფლის მეურნეობის პროდუქციის ღირებულების ინდექს",
                                "სატელეკომუნიკაციო მომსახურების ფასების","გამოშვების ფასი",
                                "საბითუმო ფას","wholesale price","deflator"))
                ),
                List.of(
                        pi("https://kaleidoscope.geostat.ge/",
                                "ფასების კალეიდოსკოპი","Price Kaleidoscope","ფასების დინამიკა და შედარება"),
                        pi("https://personalinflation.geostat.ge/",
                                "პერსონალური ინფლაციის კალკულატორი","Personal Inflation Calculator",
                                "თქვენი ინდივიდუალური ინფლაციის გამოთვლა"),
                        pi("https://cpi.geostat.ge/",
                                "სამომხმარებლო ფასების ინდექსის კალკულატორი","CPI Calculator",
                                "სამომხმარებლო ფასების ინდექსის გამოთვლა"),
                        pi("https://indexation.geostat.ge/indexation/?lang=ka",
                                "ფასთა ინდექსაციის კალკულატორი","Price Indexation Calculator",
                                "ფასთა ინდექსაციის გამოთვლა")
                ),
                li("https://www.geostat.ge/ka/modules/categories/25/fasebis-statistika",
                        "ფასების სტატისტიკა","Price Statistics"),
                li("https://www.geostat.ge/ka/modules/categories/537/metadata-fasebis-statistika",
                        "მეტამონაცემები: ფასების სტატისტიკა","Metadata: Price Statistics"),
                li("https://www.geostat.ge/ka/modules/categories/122/methodologia-fasebis-statistika",
                        "მეთოდოლოგია: ფასების სტატისტიკა","Methodology: Price Statistics"),
                // FIX: producer/import price indices + real estate price index — ახალი URL-ები
                List.of(
                        li("https://www.geostat.ge/ka/modules/categories/25/fasebis-statistika",
                                "ფასების სტატისტიკა","Price Statistics"),
                        li("https://www.geostat.ge/ka/modules/categories/27/mtsarmoebelta-da-importis-fasebis-indeksi",
                                "მწარმოებელთა და იმპორტის ფასების ინდექსები",
                                "Producer and Import Price Indices"),
                        li("https://www.geostat.ge/ka/modules/categories/698/satskhovrebeli-udzravi-konebis-fasebis-indeksi",
                                "საცხოვრებელი უძრავი ქონების ფასების ინდექსი",
                                "Residential Real Estate Price Index"),
                        li("https://www.geostat.ge/ka/modules/categories/537/metadata-fasebis-statistika",
                                "მეტამონაცემები: ფასების სტატისტიკა","Metadata: Price Statistics"),
                        li("https://www.geostat.ge/ka/modules/categories/122/methodologia-fasebis-statistika",
                                "მეთოდოლოგია: ფასების სტატისტიკა","Methodology: Price Statistics")
                ),
                st("fasebis_statistika","#22C55E","#DCFCE7"), 7));

        m.put(Topic.TRADE, def(Topic.TRADE,
                List.of(
                        TopicRule.simple(20, List.of(
                                "საგარეო","ექსპორტ","იმპორტ","ვაჭრობ","trade","export","import",
                                "external","სავაჭრო","პარტნიორ","დეფიციტ"))
                ),
                List.of(
                        pi("https://ex-trade.geostat.ge/",
                                "საგარეო ვაჭრობის პორტალი","External Trade Portal",
                                "ექსპორტი, იმპორტი, სავაჭრო პარტნიორები")
                ),
                li("https://www.geostat.ge/ka/modules/categories/35/sagareo-vachroba",
                        "საგარეო ვაჭრობა","External Trade"),
                li("https://www.geostat.ge/ka/modules/categories/112/metadata-sagareo-vachroba",
                        "მეტამონაცემები: საგარეო ვაჭრობა","Metadata: External Trade"),
                li("https://www.geostat.ge/ka/modules/categories/120/methodologia-sagareo-ekonomikuri-urtiertobebi",
                        "მეთოდოლოგია: საგარეო ეკონომიკური ურთიერთობები",
                        "Methodology: External Economic Relations"),
                st("sagareovachroba","#06B6D4","#CFFAFE"), 4));

        m.put(Topic.FDI, def(Topic.FDI,
                List.of(
                        TopicRule.compound(List.of("კაპიტალ"),
                                List.of("უცხო","foreign","ინვესტ","invest"), Set.of()),
                        TopicRule.simple(20, List.of(
                                "ინვესტიცი","უცხოური","fdi","investment","foreign direct","პირდაპირი"))
                ),
                List.of(
                        pi("https://fdi.geostat.ge/",
                                "პირდაპირი უცხოური ინვესტიციების პორტალი","FDI Portal",
                                "ინვესტიციების მოცულობა და წყაროები")
                ),
                li("https://www.geostat.ge/ka/modules/categories/191/pirdapiri-utskhouri-investitsiebi",
                        "პირდაპირი უცხოური ინვესტიციები","Foreign Direct Investment"),
                li("https://www.geostat.ge/ka/modules/categories/536/metadata-pirdapiri-utskhouri-investitsiebi",
                        "მეტამონაცემები: პირდაპირი უცხოური ინვესტიციები","Metadata: FDI"),
                null,
                st("pirdapiri_ucxouri_invisticiebi","#F59E0B","#FEF3C7"), 1));

        m.put(Topic.MONETARY, def(Topic.MONETARY,
                List.of(
                        TopicRule.simple(20, List.of(
                                "მონეტარულ","monetary","ფულის მას","საკრედიტო","საპროცენტო"))
                ),
                List.of(),
                li("https://www.geostat.ge/ka/modules/categories/92/monetaruli-statistika",
                        "მონეტარული სტატისტიკა","Monetary Statistics"),
                li("https://www.geostat.ge/ka/modules/categories/535/metadata-monetaruli-statistika",
                        "მეტამონაცემები: მონეტარული სტატისტიკა","Metadata: Monetary Statistics"),
                null,
                st("erovnuli_angarishebi","#6366F1","#E0E7FF"), 3));

        m.put(Topic.GOVERNMENT_FINANCE, def(Topic.GOVERNMENT_FINANCE,
                List.of(
                        // FIX: "სახელმწიფო ბიუჯეტი" → GOVERNMENT_FINANCE (არა BUDGET/საქსტატის ბიუჯეტი)
                        TopicRule.compound(List.of("სახელმწიფო","state","central","მთავრობის"),
                                List.of("ბიუჯეტ","budget","შემოსავ","ხარჯ","დეფიციტ","deficit"),
                                Set.of(Topic.BUDGET)),
                        TopicRule.simple(20, List.of(
                                "სახელმწიფო ფინანს","government finance","ფისკალ","fiscal",
                                // FIX: დამატება
                                "საბიუჯეტო","სახელმწიფო ხარჯ","საგადასახადო შემოსავ",
                                "tax revenue","public finance","საჯარო ფინანს",
                                "სახელმწიფო ვალ","public debt","national debt"))
                ),
                List.of(),
                li("https://www.geostat.ge/ka/modules/categories/91/sakhelmtsifo-finansebis-statistika",
                        "სახელმწიფო ფინანსების სტატისტიკა","Government Finance Statistics"),
                null, null,
                st("saxemlmwipo_finansebis_stat","#78716C","#F5F5F4"), 3));

        // ====================================================================
        // SOCIAL
        // ====================================================================

        m.put(Topic.POPULATION, def(Topic.POPULATION,
                List.of(
                        TopicRule.simple(20, List.of(
                                "მოსახლეობ","დემოგრაფ","აღწერ","census","population",
                                "დაბადებ","გარდაცვალებ","მიგრაცი","სიკვდილიანობ","შობადობ"))
                ),
                List.of(
                        pi("https://census2024.geostat.ge/ka/",
                                "მოსახლეობის აღწერის პორტალი","Census Portal",
                                "მოსახლეობის აღწერის შედეგები"),
                        pi("https://database.geostat.ge/pyramid/index.php?lang=ka",
                                "დემოგრაფიული პორტალი","Demographic Portal",
                                "ასაკობრივი პირამიდა და დემოგრაფია")
                ),
                li("https://www.geostat.ge/ka/modules/categories/316/mosakhleoba-da-demografia",
                        "მოსახლეობა და დემოგრაფია","Population and Demographics"),
                li("https://www.geostat.ge/ka/modules/categories/538/metadata-mosakhleoba",
                        "მეტამონაცემები: მოსახლეობა","Metadata: Population"),
                li("https://www.geostat.ge/ka/modules/categories/124/methodologia-mosakhleobis-aghtsera-da-demografia",
                        "მეთოდოლოგია: მოსახლეობის აღწერა და დემოგრაფია",
                        "Methodology: Census and Demographics"),
                st("mosaxleoba_statistika","#0EA5E9","#E0F2FE"), 9));

        m.put(Topic.EMPLOYMENT, def(Topic.EMPLOYMENT,
                List.of(
                        TopicRule.compound(List.of("ხელფას"), List.of(), Set.of(Topic.PRICES)),
                        TopicRule.simple(20, List.of(
                                "დასაქმებ","უმუშევრობ","ხელფას","შრომ","employment",
                                "wage","salary","job","labor","პროფესი","სამუშაო ძალ",
                                // FIX: დამატება
                                "მინიმალური ხელფას","minimum wage","გაფიცვ","strike",
                                "შრომის ბირჟ","პროფკავშირ","trade union",
                                "სეზონური დასაქმებ","seasonal employment",
                                "სრულ განაკვეთ","ნახევარ განაკვეთ","part-time","full-time"))
                ),
                List.of(
                        pi("https://salarium.geostat.ge/",
                                "ხელფასების კალკულატორი","Salary Calculator",
                                "ხელფასების შედარება პროფესიებისა და რეგიონების მიხედვით")
                ),
                li("https://www.geostat.ge/ka/modules/categories/37/dasakmeba-khelfasebi",
                        "დასაქმება და ხელფასები","Employment and Wages"),
                li("https://www.geostat.ge/ka/modules/categories/661/metadata-dasaqmeba-xelfasebi",
                        "მეტამონაცემები: დასაქმება, ხელფასები","Metadata: Employment, Wages"),
                SOCIAL_METHODOLOGY,
                st("dasaqmeba_xelpasi","#2563EB","#DBEAFE"), 8));

        m.put(Topic.LIVING_STANDARDS, def(Topic.LIVING_STANDARDS,
                List.of(
                        TopicRule.simple(20, List.of(
                                "სოციალურ","შემწეობ","პენსი","დახმარებ","ბენეფიციარ",
                                "სიღარიბ","ცხოვრების დონ","შემოსავ","საარსებო","შინამეურნეობ",
                                "poverty","living","ხარჯ","consumption"))
                ),
                List.of(
                        pi("https://mytaxes.geostat.ge/mytaxes/",
                                "გადახდების კალკულატორი","Payments Calculator","გადასახადების გამოთვლა")
                ),
                li("https://www.geostat.ge/ka/modules/categories/48/tskhovrebis-done",
                        "ცხოვრების დონე","Living Standards"),
                li("https://www.geostat.ge/ka/modules/categories/582/metadata-tskhovrebis-done-saarsebo-minimumi",
                        "მეტამონაცემები: ცხოვრების დონე","Metadata: Living Standards"),
                SOCIAL_METHODOLOGY,
                st("cxovrebis_done","#F97316","#FFEDD5"), 10));

        m.put(Topic.HEALTHCARE, defS(Topic.HEALTHCARE,
                List.of(
                        TopicRule.compound(List.of("სოციალურ"),
                                List.of("ჯანდაცვ","ჯანმრთელობ","საავადმყოფო","უზრუნველყოფ"),
                                Set.of(Topic.LIVING_STANDARDS)),
                        TopicRule.simple(20, List.of(
                                "ჯანდაცვ","ჯანმრთელობ","საავადმყოფო","ექიმ","health","hospital",
                                "დაავადებ","პაციენტ","სამედიცინო",
                                // FIX: დამატება
                                "ვაქცინ","vaccination","იმუნიზაცი","immunization",
                                "ფსიქიკურ","mental health","სამედიცინო პერსონალ",
                                "სიკვდილიანობ","mortality","ავადობ","morbidity",
                                "ფარმაცევტ","pharmaceutical","მედიკამენტ","სიმსივნ","cancer",
                                "სიცოცხლის ხანგრძლივობ","life expectancy"))
                ),
                List.of(),
                li("https://www.geostat.ge/ka/modules/categories/54/jandatsva",
                        "ჯანდაცვა და სოციალური უზრუნველყოფა","Healthcare and Social Security"),
                li("https://www.geostat.ge/ka/modules/categories/649/matadata-jandacva-socuzrunvelkofa",
                        "მეტამონაცემები: ჯანდაცვა","Metadata: Healthcare"),
                SOCIAL_METHODOLOGY,
                List.of(
                        li("https://www.geostat.ge/ka/modules/categories/54/jandatsva",
                                "ჯანდაცვა და სოციალური უზრუნველყოფა","Healthcare and Social Security"),
                        li("https://www.geostat.ge/ka/modules/categories/55/sotsialuri-uzrunvelqofa",
                                "სოციალური უზრუნველყოფა","Social Security")
                ),
                st("jadacva_socialuri_uzrunvelyopa","#EF4444","#FEE2E2"), 11));

        m.put(Topic.EDUCATION, def(Topic.EDUCATION,
                List.of(
                        // FIX: "კულტურ" მხოლოდ მაშინ = EDUCATION, თუ სხვა განათლების/კულტურის
                        // სიტყვებთან ერთად გვხვდება. "აკვაკულტურა" გამოირიცხება ამ rule-ის გარეშე.
                        TopicRule.compound(List.of("კულტურ"),
                                List.of("ხელოვნებ","მუზეუმ","თეატრ","კინო","ბიბლიოთეკ",
                                        "მეცნიერ","განათლებ","სკოლ","სტუდენტ"),
                                Set.of(Topic.AGRICULTURE, Topic.TOURISM)),
                        TopicRule.compound(List.of("პროფესიულ"),
                                List.of("განათლებ","სასწავლებ","სწავლ","პტუ"),
                                Set.of(Topic.EMPLOYMENT)),
                        TopicRule.compound(List.of("ვიზიტორ"),
                                List.of("თეატრ","მუზეუმ","კულტურ","ბიბლიოთეკ","გალერე"),
                                Set.of(Topic.TOURISM)),
                        TopicRule.simple(20, List.of(
                                "განათლებ","სკოლ","უნივერსიტეტ","სტუდენტ","მოსწავლ",
                                "education","school","მასწავლებელ","აკადემიურ"))
                ),
                List.of(),
                li("https://www.geostat.ge/ka/modules/categories/56/ganatleba-kultura",
                        "განათლება, მეცნიერება, კულტურა","Education, Science, Culture"),
                li("https://www.geostat.ge/ka/modules/categories/644/metadata-ganatleba-mecniereba-kultura",
                        "მეტამონაცემები: განათლება, მეცნიერება, კულტურა",
                        "Metadata: Education, Science, Culture"),
                SOCIAL_METHODOLOGY,
                st("ganatleba_mecnier_sportl_kultura","#A855F7","#F3E8FF"), 12));

        m.put(Topic.CRIME, def(Topic.CRIME,
                List.of(
                        TopicRule.simple(20, List.of(
                                "დანაშაულ","კრიმინალ","crime","სამართალდარღვევ",
                                "პატიმ","მსჯავრდებულ","პოლიცი"))
                ),
                List.of(),
                li("https://www.geostat.ge/ka/modules/categories/131/samartaldarghvevebis-statistika",
                        "სამართალდარღვევების სტატისტიკა","Crime Statistics"),
                li("https://www.geostat.ge/ka/modules/categories/648/metadata-samartaldargvevebis-statistika",
                        "მეტამონაცემები: სამართალდარღვევები","Metadata: Crime"),
                SOCIAL_METHODOLOGY,
                st("samartaldargvevisStatistika","#4B5563","#F3F4F6"), 0));

        m.put(Topic.GENDER, def(Topic.GENDER,
                List.of(
                        TopicRule.simple(20, List.of("გენდერ","gender","თანასწორობ"))
                ),
                List.of(
                        pi("https://gender.geostat.ge/gender/index.php",
                                "გენდერული სტატისტიკის პორტალი","Gender Statistics Portal",
                                "გენდერული თანასწორობის ინდიკატორები")
                ),
                null, null, null,
                st("mosaxleoba_statistika","#EC4899","#FCE7F3"), 16));

        m.put(Topic.YOUTH, def(Topic.YOUTH,
                List.of(
                        TopicRule.simple(20, List.of(
                                "ბავშვ","ახალგაზრდ","მოზარდ","youth","child","არასრულწლოვან"))
                ),
                List.of(
                        pi("https://youth.geostat.ge/",
                                "ბავშვებისა და მოზარდების სტატისტიკის პორტალი","Youth Statistics Portal",
                                "ახალგაზრდობის სტატისტიკა")
                ),
                null, null, null,
                st("mosaxleoba_statistika","#EAB308","#FEF9C3"), 5));

        m.put(Topic.DISABILITY, def(Topic.DISABILITY,
                List.of(
                        TopicRule.simple(20, List.of(
                                "შშმ","შეზღუდული შესაძლებლობ","disability","ინვალიდ"))
                ),
                List.of(
                        pi("https://disability.geostat.ge/shshm/index.php?lang=ka",
                                "შშმ პირთა სტატისტიკის პორტალი","Disability Statistics Portal",
                                "შეზღუდული შესაძლებლობის მქონე პირთა სტატისტიკა")
                ),
                li("https://www.geostat.ge/ka/modules/categories/55/sotsialuri-uzrunvelqofa",
                        "სოციალური უზრუნველყოფა","Social Security"),
                li("https://www.geostat.ge/ka/modules/categories/650/sotsialuri-uzrunvelqofa",
                        "მეტამონაცემები: სოციალური უზრუნველყოფა","Metadata: Social Security"),
                null,
                st("jadacva_socialuri_uzrunvelyopa","#3B82F6","#DBEAFE"), 5));

        // ====================================================================
        // SECTORAL
        // ====================================================================

        m.put(Topic.AGRICULTURE, def(Topic.AGRICULTURE,
                List.of(
                        // FIX (Priority 10): "ვაშლის წარმოება", "თევზის წარმოება" etc.
                        // Agricultural product + "წარმოება" → AGRICULTURE (not INDUSTRY).
                        // ეს rule priority 10-ზეა, ანუ INDUSTRY-ს priority 20-ზე ადრე გააქტიურდება.
                        TopicRule.compound(List.of("წარმოებ","production","output","harvest"),
                                AGRI_PRODUCT_KEYWORDS,
                                Set.of(Topic.INDUSTRY)),
                        // FIX: aquaculture / fishery cluster
                        TopicRule.simple(10, List.of(
                                "აკვაკულტურ","aquaculture","მეთევზეობ","fishery","fish farming",
                                "თევზის მეურნეობ","თევზჭერ","fishing")),
                        TopicRule.simple(20, List.of(
                                "სოფლის მეურნეობ","მოსავ","ფერმ","agriculture","farm",
                                "მემცენარეობ","მეცხოველეობ","პირუტყვ","crop",
                                "სასურსათო ბალანსი", "სურსათი", "სასურსათო უსაფრთხოებ", "სასურსათო ხარჯები", "სასურსათო", "ხორბლ", "სიმინდ", "კარტოფი", "ბოსტნეული", "ყურძენ", "ხორც", "რძის", "კვერცხის", "Food balance", "nutritional status", "nutritional security", "food security", "food expenditure", "food", "wheat", "corn", "potato", "vegetable", "milk", "meat", "dairy products", "egg", "insecure indicators",
                                // FIX: დამატება
                                "მეფუტკრეობ","beekeeping","მეღვინეობ","viticulture","ვენახ",
                                "სათბური","greenhouse farming","სარწყავ","irrigation",
                                "მიწის ფართ","land use","სასოფლო-სამეურნეო","სასოფლო",
                                "მარცვლეულ","ბოსტნეულ","ხილ","fruit","vegetable","grain",
                                "ნიადაგ","soil","სასუქ","fertilizer","პესტიციდ","pesticide",
                                "სოფლის მეურნეობის აღწერ","agricultural census"))
                ),
                List.of(
                        pi("https://agriculture.geostat.ge/",
                                "სოფლის მეურნეობის პორტალი","Agriculture Portal",
                                "აგროსტატისტიკა და მოსავლიანობა")
                ),
                li("https://www.geostat.ge/ka/modules/categories/196/soflis-meurneoba",
                        "სოფლის მეურნეობა","Agriculture"),
                li("https://www.geostat.ge/ka/modules/categories/570/metadata-soflis-meurneobis-statistika",
                        "მეტამონაცემები: სოფლის მეურნეობა","Metadata: Agriculture"),
                li("https://www.geostat.ge/ka/modules/categories/123/methodologia--soflis-meurneobis-statistika",
                        "მეთოდოლოგია: სოფლის მეურნეობის სტატისტიკა","Methodology: Agriculture Statistics"),
                st("soflis_meurneoba_sasursato_usap","#84CC16","#ECFCCB"), 13));

        m.put(Topic.INDUSTRY, def(Topic.INDUSTRY,
                List.of(
                        // FIX: "წარმოება" standalone (industrial) — exclude if agricultural product present.
                        // compound rule: "წარმოება" + industrial keywords → INDUSTRY
                        TopicRule.compound(List.of("წარმოებ","manufacturing","production"),
                                List.of("სამრეწველო","industrial","ქარხან","factory",
                                        "მეტალურგ","ქიმიურ","სამთო","mining","ტექსტილ",
                                        "ელექტრო","ენერგო","მშენებ","construction",
                                        "მრეწველობ","industry"),
                                Set.of(Topic.AGRICULTURE)),
                        TopicRule.simple(20, List.of(
                                "მრეწველობ","მშენებლობ","ენერგეტიკ","ენერგ","industry",
                                "construction","energy","manufacturing","ქარხან",
                                // FIX: დამატება
                                "მეტალურგ","metallurgy","ქიმიური მრეწველობ","chemical industry",
                                "სამთომოპოვებ","mining","ტექსტილ","textile",
                                "კვების მრეწველობ","food industry","მსუბუქი მრეწველობ",
                                "light industry","სამშენებლო მასალ","building materials",
                                "ელექტრო ენერგი","electric energy","გაზმომარაგებ","gas supply",
                                "წყალმომარაგებ","water supply","ინდუსტრი"))
                ),
                List.of(
                        pi("https://energy.geostat.ge/",
                                "ენერგეტიკის სტატისტიკის პორტალი","Energy Statistics Portal",
                                "ენერგომოხმარება და ენერგორესურსები"),
                        pi("https://automobile.geostat.ge/",
                                "ავტომობილების სტატისტიკის პორტალი","Automobile Statistics Portal",
                                "ავტომობილების სტატისტიკა")
                ),
                li("https://www.geostat.ge/ka/modules/categories/74/mretsveloba-mshenebloba-da-energetika",
                        "მრეწველობა, მშენებლობა და ენერგეტიკა","Industry, Construction and Energy"),
                li("https://www.geostat.ge/ka/modules/categories/533/metadata-mretsveloba-mshenebloba-da-energetika",
                        "მეტამონაცემები: მრეწველობა, მშენებლობა","Metadata: Industry, Construction"),
                li("https://www.geostat.ge/ka/modules/categories/126/methodologia-sawarmo-statistika",
                        "მეთოდოლოგია: საწარმო სტატისტიკა","Methodology: Enterprise Statistics"),
                st("mrewveloba_energetika_msh","#71717A","#F4F4F5"), 18));

        m.put(Topic.TOURISM, def(Topic.TOURISM,
                List.of(
                        // FIX: "რესტორანი" tourism context disambiguation
                        TopicRule.compound(List.of("რესტორან","კაფე","სასადილო"),
                                List.of("ტურისტ","ვიზიტორ","სასტუმრო","tourism"),
                                Set.of(Topic.SERVICES)),
                        TopicRule.simple(20, List.of(
                                "ტურიზმ","ტურისტ","ვიზიტორ","სასტუმრო","tourism",
                                "visitor","hotel","მოგზაურ","ღამისთევ"))
                ),
                List.of(
                        pi("https://tourism.geostat.ge/",
                                "ტურიზმის სტატისტიკის პორტალი","Tourism Statistics Portal",
                                "ვიზიტორები, შემოსავლები, ტურისტული ნაკადები")
                ),
                li("https://www.geostat.ge/ka/modules/categories/100/turizmis-statistika",
                        "ტურიზმის სტატისტიკა","Tourism Statistics"),
                li("https://www.geostat.ge/ka/modules/categories/658/metadata-turizmis-statistika",
                        "მეტამონაცემები: ტურიზმის სტატისტიკა","Metadata: Tourism"),
                BUSINESS_METHODOLOGY,
                st("turizmis_statistika","#14B8A6","#CCFBF1"), 15));

        m.put(Topic.SERVICES, def(Topic.SERVICES,
                List.of(
                        TopicRule.simple(20, List.of(
                                "მომსახურებ","services","აზარტულ","სათამაშო","კაზინო",
                                "საფოსტო","კურიერ","სატრანსპორტო","სასაწყობ","ლოჯისტიკ",
                                "საკონსულტაციო","სარეკლამო","დაზღვევ",
                                // FIX: კვების ობიექტები (standalone, tourism context-ის გარეშე)
                                "კვების ობიექტ","რესტოران","კაფე","ბარ","სასადილო",
                                "catering","საზოგადოებრივი კვებ"))
                ),
                List.of(),
                li("https://www.geostat.ge/ka/modules/categories/387/momsakhurebis-statistika298",
                        "მომსახურების სტატისტიკა","Services Statistics"),
                li("https://www.geostat.ge/ka/modules/categories/532/metadata-momsakhurebis-statistika",
                        "მეტამონაცემები: მომსახურების სტატისტიკა","Metadata: Services"),
                BUSINESS_METHODOLOGY,
                st("momxsaxurebis_statistika","#F43F5E","#FFE4E6"), 19));

        m.put(Topic.ICT, def(Topic.ICT,
                List.of(
                        TopicRule.compound(List.of("შინამეურნეობ"),
                                List.of("ტექნოლოგი","ინფორმაციულ","კომპიუტერ","ინტერნეტ","ict"),
                                Set.of(Topic.LIVING_STANDARDS)),
                        TopicRule.simple(20, List.of(
                                "ინტერნეტ","ტექნოლოგი","ict","კომპიუტერ","ციფრულ",
                                "მობილურ","სმარტფონ","ინფორმაციულ","საინფორმაციო","საკომუნიკაციო"))
                ),
                List.of(
                        pi("https://pc-axis.geostat.ge/PXWeb/",
                                "მონაცემთა ბაზები PC-AXIS","PC-AXIS Database","სტატისტიკური მონაცემთა ბაზა")
                ),
                li("https://www.geostat.ge/ka/modules/categories/103/sainformatsio-sakomunikatsio-teknologiebi",
                        "საინფორმაციო-საკომუნიკაციო ტექნოლოგიები",
                        "Information and Communication Technologies"),
                li("https://www.geostat.ge/ka/modules/categories/534/metadata-sainformatsio-sakomunikatsio-teknologiebi",
                        "მეტამონაცემები: საინფორმაციო ტექნოლოგიები","Metadata: ICT"),
                null,
                st("sainpormacio_sakomunikacia","#8B5CF6","#EDE9FE"), 17));

        m.put(Topic.ENVIRONMENT, def(Topic.ENVIRONMENT,
                List.of(
                        TopicRule.simple(20, List.of(
                                "გარემო","ეკოლოგ","environment","ecology","დაბინძურებ","ნარჩენ","კლიმატ",
                                // FIX: დამატება
                                "ატმოსფერ","atmosphere","ჰაერის ხარისხ","air quality",
                                "სათბური გაზ","greenhouse gas","carbon","ნახშირბ",
                                "ტყ","forest","ბიომრავალფეროვნებ","biodiversity",
                                "წყლის რესურს","water resource","მდინარ","ტბ","lake","river",
                                "ნიადაგის დეგრადაცი","land degradation",
                                "განახლებადი ენერგი","renewable energy",
                                "ეროვნული პარკ","national park","დაცული ტერიტორი","protected area"))
                ),
                List.of(
                        pi("https://environment.geostat.ge/",
                                "გარემოს სტატისტიკის პორტალი","Environment Statistics Portal",
                                "ეკოლოგია, დაბინძურება, ბუნებრივი რესურსები")
                ),
                li("https://www.geostat.ge/ka/modules/categories/73/garemos-statistika",
                        "გარემოს სტატისტიკა","Environment Statistics"),
                li("https://www.geostat.ge/ka/modules/categories/651/metadata-garemos-statistika",
                        "მეტამონაცემები: გარემოს სტატისტიკა","Metadata: Environment"),
                li("https://www.geostat.ge/ka/modules/categories/809/garemos-statistika",
                        "მეთოდოლოგია: გარემოს სტატისტიკა","Methodology: Environment Statistics"),
                st("garemosStatistika","#10B981","#D1FAE5"), 14));

        m.put(Topic.REGIONS, def(Topic.REGIONS,
                List.of(
                        TopicRule.simple(20, List.of(
                                "რეგიონ","მუნიციპალიტეტ","region","municipal","რაიონ",
                                "თბილის","ბათუმ","ქუთაის",
                                // FIX: ქალაქები და რეგიონები დამატება
                                "რუსთავ","ზუგდიდ","გორ","ფოთ","ტელავ","სიღნაღ",
                                "ახალციხ","ოზურგეთ","ამბროლაურ","მცხეთ","ახალქალაქ",
                                "ხაშურ","ბორჯომ","სამტრედი","სენაკ","ხობ","ჩხოროწყუ",
                                "კახეთ","იმერეთ","სამეგრელ","გური","სამცხ","ქართლ",
                                "მუნიციპალური სტატისტიკ","territorial unit",
                                "საქართველოს რეგიონ","regional statistics"))
                ),
                List.of(
                        pi("https://regions.geostat.ge/regions/",
                                "რეგიონული სტატისტიკის პორტალი","Regional Statistics Portal",
                                "მუნიციპალური და რეგიონული მონაცემები"),
                        pi("https://gis.geostat.ge/geomap/index.html",
                                "GIS ანალიზი","GIS Analysis","გეოგრაფიული ინფორმაციული სისტემა")
                ),
                li("https://www.geostat.ge/ka/modules/categories/93/regionuli-statistika",
                        "რეგიონული სტატისტიკა","Regional Statistics"),
                null, null,
                st("regionaluri_statistika","#D97706","#FEF3C7"), 0));

        // ====================================================================
        // ORGANISATIONAL
        // ====================================================================

        m.put(Topic.CALENDAR, def(Topic.CALENDAR,
                List.of(
                        TopicRule.simple(20, List.of("კალენდარ","calendar","გამოქვეყნების გრაფიკ"))
                ),
                List.of(),
                li("https://www.geostat.ge/ka/calendar","კალენდარი","Calendar"),
                null, null,
                st("news","#F87171","#FEE2E2"), 0));

        m.put(Topic.NEWS, def(Topic.NEWS,
                List.of(
                        TopicRule.simple(20, List.of("სიახლ","ახალი ამბ","news"))
                ),
                List.of(),
                li("https://www.geostat.ge/ka/news","სიახლეები","News"),
                null, null,
                st("news","#737373","#F5F5F5"), 2));

        m.put(Topic.METHODOLOGY, def(Topic.METHODOLOGY,
                List.of(
                        // FIX: "კლასიფიკატორ" standalone → CLASSIFIER has priority; methodology only
                        // when combined with methodological context words
                        TopicRule.compound(List.of("კლასიფიკატორ"),
                                List.of("მეთოდ","სტანდარტ","სტატისტიკურ","გამოყენებ"),
                                Set.of(Topic.CLASSIFIER)),
                        TopicRule.simple(20, List.of(
                                "მეთოდოლოგი","methodology","როგორ ითვლებ"))
                ),
                List.of(),
                li("https://www.geostat.ge/ka/modules/categories/125/klasifikatsiebi",
                        "კლასიფიკაციები","Classifications"),
                null, null,
                st("methodology","#92400E","#FEF3C7"), 0));

        m.put(Topic.SURVEYS, defS(Topic.SURVEYS,
                List.of(
                        TopicRule.compound(List.of("კვლევ"),
                                List.of("გამოკვლევ","სტატისტიკურ","statistical","კითხვარ"), Set.of()),
                        TopicRule.simple(20, List.of("გამოკვლევ","კითხვარ","survey","questionnaire"))
                ),
                List.of(
                        pi("https://surveycalendar.geostat.ge/",
                                "გამოკვლევების კალენდარი","Survey Calendar","გამოკვლევების განრიგი"),
                        pi("https://questionnaires.geostat.ge/login",
                                "კითხვარების პორტალი","Questionnaires Portal","ონლაინ კითხვარების შევსება")
                ),
                li("https://www.geostat.ge/ka/user-survey","გამოკვლევები","Surveys"),
                null, null,
                List.of(
                        li("https://www.geostat.ge/ka/user-survey","გამოკვლევები","Surveys"),
                        li("https://www.geostat.ge/ka/page/satsarmoebi-da-datsesebulebebi",
                                "საწარმოები და დაწესებულებები","Enterprises and Institutions"),
                        li("https://www.geostat.ge/ka/page/shinameurneobebi-da-fizikuri-pirebi",
                                "შინამეურნეობები და ფიზიკური პირები","Households and Individuals"),
                        li("https://surveycalendar.geostat.ge/","გამოკვლევების კალენდარი","Survey Calendar"),
                        li("https://questionnaires.geostat.ge/login","კითხვარების პორტალი","Questionnaires Portal"),
                        li("https://www.geostat.ge/ka/modules/categories/707/mravalindikatoruli-klasteruli-gamokvleva",
                                "მრავალინდიკატორული კლასტერული გამოკვლევა (MICS)",
                                "Multiple Indicator Cluster Survey (MICS)")
                ),
                st("quest","#0891B2","#CFFAFE"), 0));

        m.put(Topic.PUBLICATIONS, def(Topic.PUBLICATIONS,
                List.of(
                        TopicRule.simple(20, List.of(
                                "პუბლიკაცი","არქივ","publication","archive","გამოცემ",
                                "კვარტალური","წლიური პუბლ","quarterly","annual publication"))
                ),
                List.of(),
                li("https://www.geostat.ge/ka/archive","პუბლიკაციები / არქივი","Publications / Archive"),
                null, null,
                st("publication","#B45309","#FEF3C7"), 0));

        m.put(Topic.CONTACT, def(Topic.CONTACT,
                List.of(
                        TopicRule.simple(20, List.of(
                                "კონტაქტ","ტელეფონ","მისამართ","contact","ელფოსტა","email"))
                ),
                List.of(),
                li("https://www.geostat.ge/ka/contact","საკონტაქტო ინფორმაცია","Contact Information"),
                null, null,
                st("contact","#16A34A","#DCFCE7"), 0));

        m.put(Topic.STRUCTURE, def(Topic.STRUCTURE,
                List.of(
                        TopicRule.simple(20, List.of(
                                "სტრუქტურ","საქსტატის სტრუქტურა","დეპარტამენტები","განყოფილებ",
                                "საკონტაქტო ინფორმაც","სამმართველოების შესახებ","სამმართველო",
                                "structure","department"))
                ),
                List.of(),
                li("https://www.geostat.ge/ka/structure","სტრუქტურა","Structure"),
                null, null,
                st("biznes_registri","#475569","#F1F5F9"), 0));

        m.put(Topic.DATABASE, def(Topic.DATABASE,
                List.of(
                        TopicRule.simple(20, List.of(
                                // FIX: ამოღებულია "excel","csv" — ზედმეტად გენერიკული იყო.
                                // ახლა მხოლოდ კონკრეტული DB keywords:
                                "PX-Web","px-web","px web","მონაცემთა ბაზ","database",
                                "pc-axis","ჩამოტვირთ","გადმოწერ",
                                "სტატისტიკური მონაცემების ჩამოტვირთ","raw data",
                                "open data","ღია მონაცემ","API","საჯარო მონაცემ"))
                ),
                List.of(
                        pi("https://pc-axis.geostat.ge/PXWeb/pxweb/ka/Database/",
                                "მონაცემთა ბაზები PC-AXIS","PC-AXIS Database",
                                "სტატისტიკური მონაცემების ჩამოტვირთვა და ანალიზი")
                ),
                li("https://pc-axis.geostat.ge/PXWeb/pxweb/ka/Database/",
                        "მონაცემთა ბაზები (PC-AXIS)","Databases (PC-AXIS)"),
                null, null,
                st("metadata","#6B7280","#F3F4F6"), 0));

        // ====================================================================
        // ABOUT / MANAGEMENT / ADMINISTRATIVE
        // ====================================================================

        m.put(Topic.ABOUT_US, defS(Topic.ABOUT_US,
                List.of(
                        TopicRule.compound(List.of("ისტორი"),
                                List.of("საქსტატ","სამსახურ","ორგანიზაცი","geostat"), Set.of()),
                        TopicRule.simple(20, List.of(
                                "ჩვენ შესახებ","about us","ვინ ხართ","რა არის საქსტატ",
                                "ისტორიული ცნობები","ქართული სტატისტი",
                                "სამუშაოთა პროგრამ","work programme","work program",
                                "საქმიანობის ანგარიშ","activity report"))
                ),
                List.of(),
                li("https://www.geostat.ge/ka/modules/categories/187/chven-shesakheb",
                        "ჩვენ შესახებ","About Us"),
                null, null,
                List.of(
                        li("https://www.geostat.ge/ka/modules/categories/187/chven-shesakheb",
                                "ჩვენ შესახებ","About Us"),
                        li("https://www.geostat.ge/ka/modules/categories/188/istoria","ისტორია","History"),
                        li("https://www.geostat.ge/ka/modules/categories/189/sakstatis-shesakheb",
                                "საქსტატის შესახებ","About GeoStat")
                ),
                st("biznes_registri","#3B82F6","#DBEAFE"), 0));

        m.put(Topic.MANAGEMENT, defS(Topic.MANAGEMENT,
                List.of(
                        TopicRule.simple(20, List.of(
                                "დირექტორ","ხელმძღვანელ","ყოფილი ხელმძღვანელ","თოდრაძ",
                                "საბჭო","მრჩეველთა საბჭო","მენეჯმენტ","მოადგილე",
                                "მართავს","ხელმძღვანელობ","მმართველობ"))
                ),
                List.of(),
                li("https://www.geostat.ge/ka/modules/categories/303/mmartveloba",
                        "მმართველობა","Management"),
                null, null,
                List.of(
                        li("https://www.geostat.ge/ka/page/aghmasrulebeli-direqtori",
                                "აღმასრულებელი დირექტორი","Executive Director"),
                        li("https://www.geostat.ge/ka/page/saqstatis-sabtcho",
                                "საქსტატის საბჭო","GeoStat Council"),
                        li("https://www.geostat.ge/ka/structure","სტრუქტურა","Structure"),
                        li("https://www.geostat.ge/ka/modules/categories/589/statistikis-samsakhuris-yofili-khelmdzghvanelebi",
                                "ყოფილი ხელმძღვანელები","Former Directors")
                ),
                st("biznes_registri","#4F46E5","#E0E7FF"), 0));

        m.put(Topic.TERRITORIAL, defS(Topic.TERRITORIAL,
                List.of(
                        TopicRule.simple(20, List.of("ტერიტორიულ","territorial","ბიურო"))
                ),
                List.of(),
                li("https://www.geostat.ge/ka/modules/categories/305/teritoriuli-organoebi",
                        "ტერიტორიული ორგანოები","Territorial Bodies"),
                null, null,
                List.of(
                        li("https://www.geostat.ge/ka/modules/categories/272/tbilisis-statistikis-biuro",
                                "თბილისის სტატისტიკის ბიურო","Tbilisi Statistics Bureau"),
                        li("https://www.geostat.ge/ka/modules/categories/273/acharis-statistikis-biuro",
                                "აჭარის სტატისტიკის ბიურო","Adjara Statistics Bureau"),
                        li("https://www.geostat.ge/ka/modules/categories/274/samtskhe-javakhetis-statistikis-biuro",
                                "სამცხე-ჯავახეთის სტატისტიკის ბიურო","Samtskhe-Javakheti Statistics Bureau"),
                        li("https://www.geostat.ge/ka/modules/categories/275/shida-kartlis-statistikis-biuro",
                                "შიდა ქართლის სტატისტიკის ბიურო","Shida Kartli Statistics Bureau"),
                        li("https://www.geostat.ge/ka/modules/categories/276/kvemo-kartlis-statistikis-biuro",
                                "ქვემო ქართლის სტატისტიკის ბიურო","Kvemo Kartli Statistics Bureau"),
                        li("https://www.geostat.ge/ka/modules/categories/277/samegrelo-zemo-svanetis-statistikis-biuro",
                                "სამეგრელო-ზემო სვანეთის სტატისტიკის ბიურო","Samegrelo-Zemo Svaneti Statistics Bureau"),
                        li("https://www.geostat.ge/ka/modules/categories/278/imeretis-statistikis-biuro",
                                "იმერეთის სტატისტიკის ბიურო","Imereti Statistics Bureau"),
                        li("https://www.geostat.ge/ka/modules/categories/279/kakhetis-statistikis-biuro",
                                "კახეთის სტატისტიკის ბიურო","Kakheti Statistics Bureau"),
                        li("https://www.geostat.ge/ka/modules/categories/280/mtskheta-mtianetis-statistikis-biuro",
                                "მცხეთა-მთიანეთის სტატისტიკის ბიურო","Mtskheta-Mtianeti Statistics Bureau"),
                        li("https://www.geostat.ge/ka/modules/categories/281/racha-lechkhumisa-da-kvemo-svanetis-statistikis-biuro",
                                "რაჭა-ლეჩხუმისა და ქვემო სვანეთის სტატისტიკის ბიურო",
                                "Racha-Lechkhumi and Kvemo Svaneti Statistics Bureau"),
                        li("https://www.geostat.ge/ka/modules/categories/282/guriis-statistikis-biuro",
                                "გურიის სტატისტიკის ბიურო","Guria Statistics Bureau")
                ),
                st("regionaluri_statistika","#57534E","#F5F5F4"), 0));

        m.put(Topic.VACANCIES, def(Topic.VACANCIES,
                List.of(
                        TopicRule.simple(20, List.of("ვაკანსი","vacancy","სამუშაო ადგილ","career"))
                ),
                List.of(),
                li("https://www.geostat.ge/ka/modules/categories/315/vakansiebi","ვაკანსიები","Vacancies"),
                null, null,
                st("dasaqmeba_xelpasi","#059669","#D1FAE5"), 0));

        m.put(Topic.TENDERS, def(Topic.TENDERS,
                List.of(
                        TopicRule.simple(20, List.of("ტენდერ","შესყიდვ","tender"))
                ),
                List.of(),
                li("https://www.geostat.ge/ka/modules/categories/309/tenderebi","ტენდერები","Tenders"),
                null, null,
                st("publication","#CA8A04","#FEF9C3"), 0));

        m.put(Topic.LEGISLATION, def(Topic.LEGISLATION,
                List.of(
                        TopicRule.compound(
                                List.of("შრომ"),
                                List.of("კანონ","კოდექს","დებულება","მექანიზმ","სამოქმედო გეგმ","ნორმ","სამართლებრივ"),
                                Set.of()),
                        TopicRule.simple(20, List.of(
                                "კანონმდებლობ","legislation","კანონ","შინაგანაწეს","შინაგანწეს",
                                "რეგულაცი","წესდება","დებულება","კოდექს","დადგენილება",
                                "კონფიდენციალურ","შევიწროებ","ხელწერილ","კრიტერიუმ","ხელმისაწვდომ"))
                ),
                List.of(),
                li("https://www.geostat.ge/ka/modules/categories/306/kanonmdebloba",
                        "კანონმდებლობა","Legislation"),
                null, null,
                st("samartaldargvevisStatistika","#334155","#F1F5F9"), 0));

        m.put(Topic.BUDGET, def(Topic.BUDGET,
                List.of(
                        TopicRule.simple(20, List.of("ბიუჯეტ","budget"))
                ),
                List.of(),
                li("https://www.geostat.ge/ka/modules/categories/577/biujeti","ბიუჯეტი","Budget"),
                null, null,
                st("saxemlmwipo_finansebis_stat","#EAB308","#FEF9C3"), 0));

        m.put(Topic.DATA_QUALITY, def(Topic.DATA_QUALITY,
                List.of(
                        TopicRule.simple(20, List.of("მონაცემთა ხარისხ","data quality"))
                ),
                List.of(),
                li("https://www.geostat.ge/ka/modules/categories/730/methodologia-monatsemta-khariskhi",
                        "მონაცემთა ხარისხი","Data Quality"),
                null, null,
                st("methodology","#22C55E","#DCFCE7"), 0));

        m.put(Topic.INTERNATIONAL, def(Topic.INTERNATIONAL,
                List.of(
                        TopicRule.compound(List.of("სტანდარტ"),
                                List.of("შეფასებ","ნორმ","საერთაშორისო"), Set.of(Topic.I_RATING)),
                        TopicRule.simple(20, List.of("საერთაშორისო","international","სტანდარტ"))
                ),
                List.of(),
                li("https://www.geostat.ge/ka/modules/categories/618/saertashoriso-normebi-da-standartebi",
                        "საერთაშორისო ნორმები და სტანდარტები","International Norms and Standards"),
                null, null,
                st("sagareovachroba","#0284C7","#E0F2FE"), 0));

        m.put(Topic.ANNIVERSARY, def(Topic.ANNIVERSARY,
                List.of(
                        TopicRule.simple(20, List.of("იუბილე","105","100 წელ","anniversary"))
                ),
                List.of(),
                li("https://www.geostat.ge/ka/modules/categories/15/sakstati-105",
                        "საქსტატი 105 წელი","GeoStat 105 Years"),
                null, null,
                st("news","#EC4899","#FCE7F3"), 0));

        m.put(Topic.SDG, def(Topic.SDG,
                List.of(
                        TopicRule.simple(20, List.of("sdg","მდგრადი განვითარებ","sustainable"))
                ),
                List.of(
                        pi("https://sdg.gov.ge/intro",
                                "მდგრადი განვითარების მიზნები (SDG)","Sustainable Development Goals (SDG)",
                                "მდგრადი განვითარების მიზნების მონიტორინგი და ინდიკატორები")
                ),
                li("https://sdg.gov.ge/intro",
                        "მდგრადი განვითარების მიზნები (SDG)","Sustainable Development Goals (SDG)"),
                null, null,
                st("garemosStatistika","#EA580C","#FFEDD5"), 0));

        // ====================================================================
        // MEDIA / GALLERY
        // ====================================================================

        m.put(Topic.PHOTO_GALLERY, defS(Topic.PHOTO_GALLERY,
                List.of(
                        TopicRule.simple(20, List.of("ფოტო","photo","გალერეა","albums"))
                ),
                List.of(),
                li("https://www.geostat.ge/ka/albums","ფოტო გალერეა","Photo Gallery"),
                null, null,
                List.of(
                        li("https://www.geostat.ge/ka/albums","ფოტო გალერეა","Photo Gallery"),
                        li("https://www.geostat.ge/ka/video-gallery","ვიდეო გალერეა","Video Gallery"),
                        li("https://www.geostat.ge/ka/infographic","ინფოგრაფიკა","Infographic")
                ),
                st("infographic","#D946EF","#FAE8FF"), 0));

        m.put(Topic.VIDEO_GALLERY, defS(Topic.VIDEO_GALLERY,
                List.of(
                        TopicRule.simple(20, List.of("ვიდეო","video"))
                ),
                List.of(),
                li("https://www.geostat.ge/ka/video-gallery","ვიდეო გალერეა","Video Gallery"),
                null, null,
                List.of(
                        li("https://www.geostat.ge/ka/albums","ფოტო გალერეა","Photo Gallery"),
                        li("https://www.geostat.ge/ka/video-gallery","ვიდეო გალერეა","Video Gallery"),
                        li("https://www.geostat.ge/ka/infographic","ინფოგრაფიკა","Infographic")
                ),
                st("video","#DC2626","#FEE2E2"), 0));

        m.put(Topic.INFOGRAPHIC, defS(Topic.INFOGRAPHIC,
                List.of(
                        TopicRule.simple(20, List.of("ინფოგრაფიკ","infographic"))
                ),
                List.of(),
                li("https://www.geostat.ge/ka/infographic","ინფოგრაფიკა","Infographic"),
                null, null,
                List.of(
                        li("https://www.geostat.ge/ka/albums","ფოტო გალერეა","Photo Gallery"),
                        li("https://www.geostat.ge/ka/video-gallery","ვიდეო გალერეა","Video Gallery"),
                        li("https://www.geostat.ge/ka/infographic","ინფოგრაფიკა","Infographic")
                ),
                st("infographic","#7C3AED","#EDE9FE"), 0));

        m.put(Topic.MEDIA, defS(Topic.MEDIA,
                List.of(
                        TopicRule.simple(20, List.of("მედია","media"))
                ),
                List.of(),
                null, null, null,
                List.of(
                        li("https://www.geostat.ge/ka/albums","ფოტო გალერეა","Photo Gallery"),
                        li("https://www.geostat.ge/ka/video-gallery","ვიდეო გალერეა","Video Gallery"),
                        li("https://www.geostat.ge/ka/infographic","ინფოგრაფიკა","Infographic")
                ),
                st("video","#A855F7","#F3E8FF"), 0));

        // ====================================================================
        // PROJECTS / EXTERNAL / CLASSIFIERS / RATINGS
        // ====================================================================

        m.put(Topic.PROJECTS, def(Topic.PROJECTS,
                List.of(
                        TopicRule.simple(20, List.of("პროექტ","project"))
                ),
                List.of(),
                li("https://www.geostat.ge/ka/projects","პროექტები","Projects"),
                null, null,
                st("mravalindeksat_gamokvlelva","#2563EB","#DBEAFE"), 0));

        m.put(Topic.EXTERNAL_LINKS, defS(Topic.EXTERNAL_LINKS,
                List.of(
                        TopicRule.simple(20, List.of("გარე ბმულ","external link"))
                ),
                List.of(),
                null, null, null,
                List.of(
                        li("https://www.geostat.ge/ka/links/5/msoflio-qveknebis-statsamsakhurebi",
                                "მსოფლიოს ქვეყნების სტატისტიკის სამსახურები",
                                "World Countries Statistical Services"),
                        li("https://www.geostat.ge/ka/links/2/saertashoriso-organizaciebi",
                                "საერთაშორისო ორგანიზაციები","International Organizations"),
                        li("https://www.geostat.ge/ka/links/1/samtavrobo-dacesebulebebi",
                                "სამთავრობო დაწესებულებები","Government Institutions")
                ),
                st("sagareovachroba","#6B7280","#F3F4F6"), 0));

        m.put(Topic.CLASSIFIER, defS(Topic.CLASSIFIER,
                List.of(
                        // FIX: "nace" და "კლასიფიკატორ" → CLASSIFIER priority (standalone)
                        TopicRule.simple(10, List.of("nace","კლასიფიკატორ","classifier","nace rev"))
                ),
                List.of(),
                null, null, null,
                List.of(
                        li("https://www.geostat.ge/ka/modules/categories/76/ekonomikuri-sakmianobis-sakheebis-klasifikatoris-nace-rev11-mikhedvit",
                                "ეკონომიკური საქმიანობის კლასიფიკატორი (NACE rev.1.1)",
                                "Economic Activities Classifier (NACE rev.1.1)"),
                        li("https://www.geostat.ge/ka/modules/categories/79/mshenebloba-ekonomikuri-sakmianobis-sakheebis-klasifikatoris-nace-rev11-mikhedvit",
                                "მშენებლობა - ეკონომიკური საქმიანობის კლასიფიკატორი",
                                "Construction - Economic Activities Classifier")
                ),
                st("metadata","#0D9488","#CCFBF1"), 0));

        m.put(Topic.I_RATING, def(Topic.I_RATING,
                List.of(
                        TopicRule.simple(10, List.of("რეიტინგ","rating","i-rating","i-რეიტინგ","რანკინგ","ranking"))
                ),
                List.of(
                        pi("https://i-rating.geostat.ge/",
                                "i-რეიტინგი - ინტერაქტიული პლატფორმა","i-Rating - Interactive Platform",
                                "სტატისტიკური რეიტინგები და შედარებები")
                ),
                li("https://i-rating.geostat.ge/","i-რეიტინგი","i-Rating"),
                null, null,
                st("erovnuli_angarishebi","#F59E0B","#FEF3C7"), 0));

        // ====================================================================
        // GENERAL (fallback)
        // ====================================================================

        m.put(Topic.GENERAL, defS(Topic.GENERAL,
                List.of(),   // no rules — GENERAL is the fallback when nothing else matches
                List.of(
                        pi("https://www.geostat.ge/ka/contact",
                                "დაგვიკავშირდით","Contact Us","საქსტატთან დაკავშირება"),
                        pi("https://www.geostat.ge/ka/site-map",
                                "საიტის რუკა","Site Map","საიტის სრული სტრუქტურა")
                ),
                null, null, null,
                List.of(
                        li("https://www.geostat.ge/ka/contact","დაგვიკავშირდით","Contact Us"),
                        li("https://www.geostat.ge/ka/site-map","საიტის რუკა","Site Map")
                ),
                st("erovnuli_angarishebi","#3B82F6","#DBEAFE"), 2));

        REGISTRY = Collections.unmodifiableMap(m);
    }

    // ─── Public API ──────────────────────────────────────────────────────────

    public static TopicDefinition get(Topic t) {
        return REGISTRY.getOrDefault(t, REGISTRY.get(Topic.GENERAL));
    }

    public static Collection<TopicDefinition> all() {
        return REGISTRY.values();
    }

    /**
     * Curated master list of all interactive portals/calculators.
     * Returned when the user asks "what portals do you have?".
     */
    public static final List<LinkInfo> ALL_PORTALS = List.of(
            li("https://sna.geostat.ge/ka/4/Mtavari",
                    "ეროვნული ანგარიშების პორტალი","National Accounts Portal"),
            li("https://eap.geostat.ge/",
                    "ეკონომიკური ანალიზის პორტალი","Economic Analysis Portal"),
            li("https://br.geostat.ge/","ბიზნეს რეგისტრი","Business Register"),
            li("https://kaleidoscope.geostat.ge/","ფასების კალეიდოსკოპი","Price Kaleidoscope"),
            li("https://personalinflation.geostat.ge/",
                    "პერსონალური ინფლაციის კალკულატორი","Personal Inflation Calculator"),
            li("https://cpi.geostat.ge/",
                    "სამომხმარებლო ფასების ინდექსის კალკულატორი","CPI Calculator"),
            li("https://indexation.geostat.ge/indexation/?lang=ka",
                    "ფასთა ინდექსაციის კალკულატორი","Price Indexation Calculator"),
            li("https://ex-trade.geostat.ge/","საგარეო ვაჭრობის პორტალი","External Trade Portal"),
            li("https://fdi.geostat.ge/",
                    "პირდაპირი უცხოური ინვესტიციების პორტალი","FDI Portal"),
            li("https://census2024.geostat.ge/ka/","მოსახლეობის აღწერის პორტალი","Census Portal"),
            li("https://database.geostat.ge/pyramid/index.php?lang=ka",
                    "დემოგრაფიული პორტალი","Demographic Portal"),
            li("https://salarium.geostat.ge/","ხელფასების კალკულატორი","Salary Calculator"),
            li("https://agriculture.geostat.ge/","სოფლის მეურნეობის პორტალი","Agriculture Portal"),
            li("https://tourism.geostat.ge/","ტურიზმის სტატისტიკის პორტალი","Tourism Statistics Portal"),
            li("https://environment.geostat.ge/","გარემოს სტატისტიკის პორტალი","Environment Statistics Portal"),
            li("https://energy.geostat.ge/","ენერგეტიკის სტატისტიკის პორტალი","Energy Statistics Portal"),
            li("https://automobile.geostat.ge/",
                    "ავტომობილების სტატისტიკის პორტალი","Automobile Statistics Portal"),
            li("https://regions.geostat.ge/regions/","რეგიონული სტატისტიკის პორტალი","Regional Statistics Portal"),
            li("https://gis.geostat.ge/geomap/index.html","GIS ანალიზი","GIS Analysis"),
            li("https://gender.geostat.ge/gender/index.php",
                    "გენდერული სტატისტიკის პორტალი","Gender Statistics Portal"),
            li("https://youth.geostat.ge/",
                    "სტატისტიკა ბავშვებისა და მოზარდებისთვის","Youth Statistics Portal"),
            li("https://disability.geostat.ge/shshm/index.php?lang=ka",
                    "შშმ პირთა სტატისტიკის პორტალი","Disability Statistics Portal"),
            li("https://mytaxes.geostat.ge/mytaxes/","გადახდების კალკულატორი","Payments Calculator"),
            li("https://pc-axis.geostat.ge/PXWeb/","მონაცემთა ბაზები PC-AXIS","PC-AXIS Database"),
            li("https://sdg.gov.ge/intro","მდგრადი განვითარების მიზნები (SDG)","Sustainable Development Goals"),
            li("https://surveycalendar.geostat.ge/","გამოკვლევების კალენდარი","Survey Calendar"),
            li("https://questionnaires.geostat.ge/login","კითხვარების პორტალი","Questionnaires Portal"),
            li("https://i-rating.geostat.ge/","i-რეიტინგი","i-Rating")
    );

    /**
     * Sectoral accounts keywords — used by ResponseBuilder to optionally attach the sectoral accounts link.
     */
    public static final List<String> SECTORAL_KEYWORDS = List.of(
            "სექტორულ ანგარიშ","sectoral accounts","sector accounts",
            "ინსტიტუციური სექტორ","institutional sector",
            "ფინანსური სექტორ","financial sector",
            "არაფინანსურ კორპორაცი","non-financial corporations",
            "სახელმწიფო სექტორ","government sector",
            "შინამეურნეობების სექტორ","household sector"
    );

    public static final LinkInfo SECTORAL_ACCOUNTS = li(
            "https://www.geostat.ge/ka/modules/categories/866/sektoruli-angarishebi",
            "სექტორული ანგარიშები","Sectoral Accounts");

    /** Topics for which a category-specific news link is always included. */
    public static final Set<Topic> NEWS_RELEVANT_TOPICS = Set.of(
            Topic.NATIONAL_ACCOUNTS, Topic.BUSINESS, Topic.PRICES, Topic.TRADE,
            Topic.FDI, Topic.MONETARY, Topic.GOVERNMENT_FINANCE,
            Topic.POPULATION, Topic.EMPLOYMENT, Topic.LIVING_STANDARDS,
            Topic.HEALTHCARE, Topic.EDUCATION, Topic.CRIME,
            Topic.GENDER, Topic.YOUTH, Topic.DISABILITY,
            Topic.AGRICULTURE, Topic.INDUSTRY, Topic.TOURISM,
            Topic.SERVICES, Topic.ENVIRONMENT, Topic.ICT, Topic.REGIONS,
            Topic.I_RATING
    );

    /** Keywords that indicate the user wants latest/recent data (triggers news link promotion). */
    public static final List<String> LATEST_KEYWORDS = List.of(
            "უახლეს","ბოლო","latest","recent","update",
            "ზრდა","growth","კლება","decline","ცვლილება","change",
            "დინამიკ","dynamic","trend","ტენდენცი"
    );
}
