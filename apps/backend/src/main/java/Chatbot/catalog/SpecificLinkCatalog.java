package Chatbot.catalog;

import Chatbot.model.LinkInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Keyword-triggered specific links — highest priority match in link resolution.
 * Each entry: url, titleKa, titleEn, keywords (matched against user query).
 */
public final class SpecificLinkCatalog {

    private SpecificLinkCatalog() {}

    public record SpecificLink(String url, String titleKa, String titleEn,
                                List<String> keywords, List<String> excludeKeywords) {
        /** Convenience constructor — no exclusions. */
        public SpecificLink(String url, String titleKa, String titleEn, List<String> keywords) {
            this(url, titleKa, titleEn, keywords, List.of());
        }

        public LinkInfo toLinkInfo() {
            return new LinkInfo(url, titleKa, titleEn);
        }
    }

    public static final List<SpecificLink> ALL = List.of(

            new SpecificLink("https://www.geostat.ge/ka/modules/categories/61/umaghlesi-ganatleba",
                    "სახლემწიფო ბიუჯეტი", "Government Budge",
                    List.of("სახლემწიფო ბიუჯეტი", "ბიუჯეტ", "Government Budge", "Budge", "სახელმწიფო ხარჯები", "სახელმწიფო შემოსავლები", "სახელმწიფო ფინანსები")),

            new SpecificLink("https://www.geostat.ge/ka/modules/categories/27/mtsarmoebelta-da-importis-fasebis-indeksi",
                    "მწარმოებელთა და იმპორტის ფასების ინდექსები", "Producer and Import Price Indices",
                    List.of("მწარმოებელთა ფასები", "ექსპორტის ფასები", "იმპორტის ფასები", "მშენებლობისთვის შეძენილი მასალები", "მშენებლობის ღირებულების ინდექსი", "მწარმოებელთა ფასების ინდექსი", "ექსპორტის ფასების ინდექსი", "იმპორტის ფასების ინდექსი",
                            "producer price index", "export price index", "import price index", "construction materials", "construction cost index")),


            new SpecificLink("https://www.geostat.ge/ka/modules/categories/297/sasursato-usafrtkhoeba",
                                     "სასურსათო უსაფრთხოება", "Food Security",
                             List.of("სასურსათო ბალანსი", "სურსათი", "სასურსათო უსაფრთხოებ", "სასურსათო ხარჯები", "სასურსათო", "ხორბლ", "სიმინდ", "კარტოფი", "ბოსტნეული", "ყურძენ", "ხორც", "რძის", "კვერცხის", "Food balance", "nutritional status", "nutritional security", "food security", "food expenditure", "food", "wheat", "corn", "potato", "vegetable", "milk", "meat", "dairy products", "egg", "insecure indicators")),

            new SpecificLink("https://www.geostat.ge/ka/modules/categories/698/satskhovrebeli-udzravi-konebis-fasebis-indeksi",
                    "საცხოვრებელი უძრავი ქონების ფასების ინდექსი", "Residential Real Estate Price Index",
                     List.of("საცხოვრებელი უძრავი ქონების ფასები", "საცხოვრებელი უძრავი ქონების ფასების ინდექსი", "real estate price index", "residential real estate price index")),


            new SpecificLink("https://www.geostat.ge/ka/modules/categories/55/sotsialuri-uzrunvelqofa",
                    "სოციალური უზრუნველყოფა", "Social Security",
                    List.of("პენსიონერ", "სოციალური პაკეტ", "საარსებო შემწეობ", "პენსია",
                            "სოციალური უზრუნველყოფ", "სოციალური დახმარებ", "რამდენი იღებს", "ბენეფიციარ")),

            new SpecificLink("https://www.geostat.ge/ka/modules/categories/58/adreuli-da-skolamdeli-aghzrda-da-ganatleba",
                    "ადრეული და სკოლამდელი აღზრდა და განათლება", "Early and Preschool Education",
                    List.of("სკოლამდელ", "ბაღ", "საბავშვო", "ადრეული განათლებ")),

            new SpecificLink("https://www.geostat.ge/ka/modules/categories/59/zogadi-ganatleba",
                    "ზოგადი განათლება", "General Education",
                    List.of("სკოლ", "ზოგადი განათლებ", "მოსწავლ", "საჯარო სკოლ", "კერძო სკოლ")),

            new SpecificLink("https://www.geostat.ge/ka/modules/categories/60/profesiuli-ganatleba",
                    "პროფესიული განათლება", "Professional Education",
                    List.of("პროფესიული განათლებ", "პროფესიული სასწავლებ", "პტუ")),

            new SpecificLink("https://www.geostat.ge/ka/modules/categories/61/umaghlesi-ganatleba",
                    "უმაღლესი განათლება", "Higher Education",
                    List.of("უმაღლეს", "უნივერსიტეტ", "სტუდენტ", "ბაკალავრ", "მაგისტრ")),

            new SpecificLink("https://www.geostat.ge/ka/modules/categories/194/metsniereba",
                    "მეცნიერება", "Science",
                    List.of("მკვლევარ", "მეცნიერ", "სამეცნიერო", "პატენტ", "კვლევ")),

            new SpecificLink("https://www.geostat.ge/ka/modules/categories/759/akvakultura",
                    "აკვაკულტურა", "Aquaculture",
                    List.of("თევზი", "წყალსატევები", "აკვაკულტურ", "სასუქ", "მოლუსკ", "მოლუსკების წარმოება", "მოლუსკების ექსპორტი", "მოლუსკების იმპორტი")),

            new SpecificLink("https://www.geostat.ge/ka/modules/categories/615/kultura",
                    "კულტურა", "Culture",
                    List.of("თეატრ", "მუზეუმ", "კულტურ", "ღონისძიებ", "ბიბლიოთეკ"),
                    List.of("აკვაკულტ", "aquaculture")),

            new SpecificLink("https://www.geostat.ge/ka/modules/categories/710/sporti",
                    "სპორტი", "Sport",
                    List.of("სპორტ", "სპორტსმენ", "სპორტული", "ოლიმპიურ", "მედალ", "პარაოლიმპ")),

            new SpecificLink("https://www.geostat.ge/ka/modules/categories/54/jandatsva",
                    "ჯანდაცვა", "Healthcare",
                    List.of("დაავადებ", "ავადობ", "ექიმ", "ექთან", "საავადმყოფო", "საწოლ", "სამედიცინო პერსონალ", "ჯანდაცვ")),

            new SpecificLink("https://www.geostat.ge/ka/modules/categories/132/siskhlis-samartlis-statistika",
                    "სისხლის სამართლის სტატისტიკა", "Criminal Statistics",
                    List.of("დანაშაულ", "პატიმარ", "მსჯავრდებულ", "სისხლის სამართ")),

            new SpecificLink("https://www.geostat.ge/ka/page/aghmasrulebeli-direqtori",
                    "აღმასრულებელი დირექტორი", "Executive Director",
                    List.of("დირექტორ", "გოგიტა თოდრაძე", "თოდრაძე", "ხელმძღვანელ", "მმართველობ", "მართავს", "ხელმძღვანელობ")),

            new SpecificLink("https://www.geostat.ge/ka/page/moadgile-paata-shavishvili",
                    "აღმასრულებელი დირექტორის მოადგილე", "Deputy Executive Director",
                    List.of("აღმასრულებელი დირექტორის მოადგილე", "დირექტორის მოადგილე", "მოადგილე", "პაატა შავიშვ")),

            new SpecificLink("https://www.geostat.ge/ka/page/moadgile-irakli-apkhaidze",
                    "აღმასრულებელი დირექტორის მოადგილე", "Deputy Executive Director",
                    List.of("ირაკლი აფხაიძ")),

            new SpecificLink("https://www.geostat.ge/ka/modules/categories/25/fasebis-statistika",
                    "ფასების სტატისტიკა", "Price Statistics",
                    List.of("ინფლაცი", "ფასებ", "გაძვირებ")),

            new SpecificLink("https://www.geostat.ge/ka/modules/categories/589/statistikis-samsakhuris-yofili-khelmdzghvanelebi",
                    "სტატისტიკის სამსახურის ყოფილი ხელმძღვანელები", "Former Heads of the Statistics Service",
                    List.of("ვალერიან მელქაძე", "გრიგოლ ჟორჟიკაშვილი", "რევაზ ბასარია", "ანზორ ჯანჯღავა",
                            "ლერი გიგინეიშვილი", "თეიმურაზ ბერიძე", "ზაზა ბროლაძე", "ირაკლი სირაძე",
                            "გრიგოლ ფანცულაია", "ზაზა ჭელიძე", "მერი დაუშვილი")),

            new SpecificLink("https://www.geostat.ge/ka/modules/categories/41/mosakhleoba",
                    "მოსახლეობა", "Population",
                    List.of("მოსახლეობ", "დემოგრაფი", "დაბადებ", "გარდაცვალებ", "მიგრაცი", "ქორწინებ")),

            new SpecificLink("https://census2024.geostat.ge/ka",
                    "მოსახლეობის აღწერა 2024", "Census 2024",
                    List.of("აღწერ", "ცენზუს", "census")),

            new SpecificLink("https://www.geostat.ge/ka/modules/categories/637/eksporti",
                    "ექსპორტი", "Export",
                    List.of("ექსპორტ", "გატან")),

            new SpecificLink("https://www.geostat.ge/ka/modules/categories/638/importi",
                    "იმპორტი", "Import",
                    List.of("იმპორტ", "შემოტან")),

            new SpecificLink("https://www.geostat.ge/ka/modules/categories/37/dasakmeba-khelfasebi",
                    "დასაქმება და ხელფასები", "Employment and Wages",
                    List.of("დასაქმებ", "უმუშევრობ", "ხელფას", "სამუშაო ძალ", "შრომის ბაზარ")),

            new SpecificLink("https://www.geostat.ge/ka/modules/categories/23/mtliani-shida-produkti-mshp",
                    "მთლიანი შიდა პროდუქტი (მშპ)", "Gross Domestic Product (GDP)",
                    List.of("მშპ", "მთლიანი შიდა პროდუქტ", "ეკონომიკური ზრდ")),

            new SpecificLink("https://www.geostat.ge/ka/modules/categories/866/sektoruli-angarishebi",
                    "სექტორული ანგარიშები", "Sectoral Accounts",
                    List.of("სექტორულ ანგარიშ", "sectoral accounts", "sector accounts",
                            "ინსტიტუციური სექტორ", "ფინანსური სექტორ", "არაფინანსურ კორპორაცი",
                            "institutional sector", "financial sector", "non-financial corporations")),

            new SpecificLink("https://www.geostat.ge/ka/modules/categories/892/sportis-satelituri-angarishi",
                    "სპორტის სატელიტური ანგარიში", "Sports Satellite Account",
                    List.of("სპორტის სატელიტურ", "სატელიტური ანგარიშ", "სატელიტური", "sports satellite", "satellite account")),

            new SpecificLink("https://www.geostat.ge/ka/modules/categories/769/danakharjebi-gamoshvebis-tskhrilebi",
                    "დანახარჯები-გამოშვების ცხრილები", "Supply and Use Tables",
                    List.of("დანახარჯები-გამოშვებ", "გამოშვების ცხრილ", "supply and use")),

            new SpecificLink("https://www.geostat.ge/ka/modules/categories/632/resursebisa-da-gamoqenebis-tskhrilebi-new",
                    "რესურსებისა და გამოყენების ცხრილები", "Resources and Use Tables",
                    List.of("რესურსებისა და გამოყენებ", "გამოყენების ცხრილ", "resources and use", "resource table")),

            new SpecificLink("https://www.geostat.ge/ka/modules/categories/24/mtliani-erovnuli-shemosavali-mesh",
                    "მთლიანი ეროვნული შემოსავალი (მეშ)", "Gross National Income (GNI)",
                    List.of("მეშ", "მთლიანი ეროვნული შემოსავ", "ეროვნული შემოსავ", "gni", "gross national income", "national income")),

            new SpecificLink("https://www.geostat.ge/ka/modules/categories/64/biznes-registri",
                    "ბიზნეს რეგისტრის სტატისტიკა", "Business Register Statistics",
                    List.of("ბიზნეს რეგისტრ", "ბიზნესრეგისტრ", "business register", "business registry",
                            "რეგისტრირებული საწარმო", "registered enterprise", "რეგისტრაცია საწარმო")),

            new SpecificLink("https://www.geostat.ge/ka/modules/categories/100/turizmis-statistika",
                    "ტურიზმის სტატისტიკა", "Tourism Statistics",
                    List.of("ტურიზმ", "ვიზიტორ", "ტურისტ", "სასტუმრო")),

            new SpecificLink("https://www.geostat.ge/ka/modules/categories/196/soflis-meurneoba",
                    "სოფლის მეურნეობა", "Agriculture",
                    List.of("სოფლის მეურნეობ", "მემცენარეობ", "მეცხოველეობ", "მოსავ", "ფერმ")),

            new SpecificLink("https://www.geostat.ge/ka/modules/categories/73/garemos-statistika",
                    "გარემოს სტატისტიკა", "Environment Statistics",
                    List.of("გარემო", "ეკოლოგი", "დაბინძურებ", "ნარჩენ", "წყალ", "ჰაერ")),

            new SpecificLink("https://www.geostat.ge/ka/modules/categories/48/tskhovrebis-done",
                    "ცხოვრების დონე", "Living Standards",
                    List.of("სიღარიბ", "ცხოვრების დონ", "შინამეურნეობ", "ჯინის კოეფიციენტ", "საარსებო მინიმუმ")),

            new SpecificLink("https://regions.geostat.ge/regions/",
                    "რეგიონული სტატისტიკის პორტალი", "Regional Statistics Portal",
                    List.of("რეგიონ", "მუნიციპალიტეტ", "ადგილობრივ")),

            new SpecificLink("https://www.geostat.ge/ka/calendar",
                    "სტატისტიკური კალენდარი", "Statistical Calendar",
                    List.of("კალენდარ", "გამოქვეყნების გრაფიკ", "გამოქვეყნების განრიგ")),

            new SpecificLink("https://www.geostat.ge/ka/news",
                    "სიახლეები", "News",
                    List.of("სიახლ", "ახალი ამბ", "news", "უახლეს")),

            new SpecificLink("https://www.geostat.ge/ka/archive",
                    "პუბლიკაციები / არქივი", "Publications / Archive",
                    List.of("პუბლიკაცი", "არქივ", "publication", "archive", "გამოცემ")),

            new SpecificLink("https://www.geostat.ge/ka/modules/categories/35/sagareo-vachroba",
                    "საგარეო ვაჭრობა", "External Trade",
                    List.of("საგარეო ვაჭრობ", "საგარეო ეკონომიკ", "external trade")),

            new SpecificLink("https://i-rating.geostat.ge/",
                    "i-რეიტინგი", "i-Rating",
                    List.of("რეიტინგ", "rating", "i-rating", "i-რეიტინგ", "რანკინგ", "ranking")),

            new SpecificLink("https://www.geostat.ge/ka/modules/categories/306/kanonmdebloba",
                    "კანონმდებლობა", "Legislation",
                    List.of("კანონმდებლობ", "კანონ", "შინაგანაწეს", "რეგულაცი", "წესდება",
                            "დებულება", "legislation", "regulation", "სამართლებრივ")),

            new SpecificLink("https://www.geostat.ge/ka/modules/categories/707/mravalindikatoruli-klasteruli-gamokvleva",
                    "მრავალინდიკატორული კლასტერული გამოკვლევა (MICS)", "Multiple Indicator Cluster Survey (MICS)",
                    List.of("mics", "კლასტერულ", "მრავალინდიკატორულ", "cluster survey",
                            "multiple indicator", "ბავშვთა კეთილდღეობ", "child wellbeing", "unicef", "უნისეფ")),

            new SpecificLink("https://automobile.geostat.ge/",
                    "ავტომობილების სტატისტიკის პორტალი", "Automobile Statistics Portal",
                    List.of("ავტომობილ", "მანქან", "automobile", "car", "vehicle", "ავტო")),

            new SpecificLink("https://indexation.geostat.ge/indexation/?lang=ka",
                    "ფასთა ინდექსაციის კალკულატორი", "Price Indexation Calculator",
                    List.of("ინდექსაცი", "indexation", "ფასთა ინდექს")),

            new SpecificLink("https://cpi.geostat.ge/",
                    "სამომხმარებლო ფასების ინდექსის კალკულატორი", "CPI Calculator",
                    List.of("cpi", "სამომხმარებლო ფასებ", "consumer price index", "სფი")),

            new SpecificLink("https://mytaxes.geostat.ge/mytaxes/",
                    "გადახდების კალკულატორი", "Payments Calculator",
                    List.of("გადახდ", "გადასახად", "payment", "tax calculator", "mytaxes")),

            new SpecificLink("https://personalinflation.geostat.ge/",
                    "პერსონალური ინფლაციის კალკულატორი", "Personal Inflation Calculator",
                    List.of("პერსონალურ ინფლაცი", "personal inflation", "ჩემი ინფლაცი")),

            new SpecificLink("https://youth.geostat.ge/",
                    "სტატისტიკა ბავშვებისა და მოზარდებისთვის", "Statistics for Children and Youth",
                    List.of("ბავშვ", "მოზარდ", "ახალგაზრდ", "youth", "children", "child",
                            "teenager", "ბავშვებისა", "მოზარდებისთვის", "ბავშვთა", "არასრულწლოვან")),

            new SpecificLink("https://www.geostat.ge/ka/modules/categories/307/statistikur-samushaota-programa",
                    "სტატისტიკური სამუშაოთა პროგრამა", "Statistical Work Programme",
                    List.of("სამუშაოთა პროგრამ", "სტატისტიკური სამუშაო", "work programme",
                            "work program", "სამუშაო გეგმ", "სამუშაოების პროგრამ")),

            new SpecificLink("https://www.geostat.ge/ka/modules/categories/308/sakmianobis-angarishi",
                    "საქსტატის საქმიანობის ანგარიში", "GeoStat Activity Report",
                    List.of("საქმიანობის ანგარიშ", "activity report", "annual report",
                            "ყოველწლიური ანგარიშ", "წლიური ანგარიშ")),

            new SpecificLink("https://www.geostat.ge/ka/modules/categories/766/momsakhurebit-saertashoriso-vachroba",
                    "მომსახურებით საერთაშორისო ვაჭრობა", "International Trade in Services",
                    List.of("მომსახურებით საერთ", "მომსახურებით ვაჭრობ", "services trade",
                            "international trade in services", "მომსახურების ექსპორტ", "მომსახურების იმპორტ")),

            new SpecificLink("https://www.geostat.ge/ka/single-categories/121/kvartaluri",
                    "კვარტალური პუბლიკაციები", "Quarterly Publications",
                    List.of("კვარტალური პუბლიკ", "quarterly publication", "კვარტალური გამოცემ", "კვარტალური ბიულეტენ")),

            new SpecificLink("https://www.geostat.ge/ka/single-categories/122/tsliuri",
                    "წლიური პუბლიკაციები", "Annual Publications",
                    List.of("წლიური პუბლიკ", "annual publication", "წლიური გამოცემ", "წლიური ბიულეტენ", "სტატისტიკური წელიწდეული")),

            new SpecificLink("https://www.geostat.ge/ka/modules/categories/555/kitxvarebi",
                    "გამოკვლევების კითხვარები", "Survey Questionnaires",
                    List.of("გამოკვლევის კითხვარ", "survey questionnaire", "კითხვარის ფორმ", "questionnaire form", "კითხვარები გამოკვლევ"))
    );

    // ========================================================================
    // LOOKUP
    // ========================================================================

    public static List<LinkInfo> findMatches(String query) {
        if (query == null || query.isBlank()) return List.of();
        String lower = query.toLowerCase();

        List<SpecificLink> matches = new ArrayList<>();
        for (SpecificLink link : ALL) {
            if (countMatches(lower, link.keywords()) > 0
                    && link.excludeKeywords().stream().noneMatch(lower::contains)) {
                matches.add(link);
            }
        }
        matches.sort((a, b) -> Integer.compare(
                countMatches(lower, b.keywords()),
                countMatches(lower, a.keywords())));

        return matches.stream().map(SpecificLink::toLinkInfo).toList();
    }

    private static int countMatches(String lower, List<String> keywords) {
        int count = 0;
        for (String kw : keywords) {
            boolean matched = kw.length() <= 4
                    ? containsWholeWord(lower, kw)
                    : lower.contains(kw.toLowerCase());
            if (matched) count++;
        }
        return count;
    }

    private static boolean containsWholeWord(String text, String word) {
        String lw = word.toLowerCase();
        int i = text.indexOf(lw);
        while (i >= 0) {
            boolean start = (i == 0) || !Character.isLetterOrDigit(text.charAt(i - 1));
            boolean end   = (i + lw.length() >= text.length()) || !Character.isLetterOrDigit(text.charAt(i + lw.length()));
            if (start && end) return true;
            i = text.indexOf(lw, i + 1);
        }
        return false;
    }
}