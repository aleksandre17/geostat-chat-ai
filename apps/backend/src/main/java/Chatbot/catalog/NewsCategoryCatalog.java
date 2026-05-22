package Chatbot.catalog;

import Chatbot.model.LinkInfo;
import Chatbot.model.Topic;

import java.util.Map;

/**
 * Maps topics to GeoStat news category IDs and titles.
 * Single record per topic — eliminates duplicate switch statements.
 */
public final class NewsCategoryCatalog {

    private NewsCategoryCatalog() {}

    private record Entry(int categoryId, String titleKa, String titleEn) {}

    private static final Entry DEFAULT = new Entry(2, "სიახლეები", "News");

    private static final Map<Topic, Entry> ENTRIES = Map.ofEntries(
            Map.entry(Topic.NATIONAL_ACCOUNTS,  new Entry(3,  "სიახლეები: ეროვნული ანგარიშები",      "News: National Accounts")),
            Map.entry(Topic.BUSINESS,           new Entry(6,  "სიახლეები: ბიზნეს სტატისტიკა",        "News: Business Statistics")),
            Map.entry(Topic.PRICES,             new Entry(7,  "სიახლეები: ფასების სტატისტიკა",        "News: Price Statistics")),
            Map.entry(Topic.TRADE,              new Entry(4,  "სიახლეები: საგარეო ვაჭრობა",           "News: External Trade")),
            Map.entry(Topic.FDI,                new Entry(1,  "სიახლეები: უცხოური ინვესტიციები",      "News: Foreign Investment")),
            Map.entry(Topic.MONETARY,           new Entry(3,  "სიახლეები: მონეტარული სტატისტიკა",     "News: Monetary Statistics")),
            Map.entry(Topic.GOVERNMENT_FINANCE, new Entry(3,  "სიახლეები: სახელმწიფო ფინანსები",      "News: Government Finance")),
            Map.entry(Topic.POPULATION,         new Entry(9,  "სიახლეები: მოსახლეობა და დემოგრაფია",  "News: Population & Demographics")),
            Map.entry(Topic.EMPLOYMENT,         new Entry(8,  "სიახლეები: დასაქმება და ხელფასები",    "News: Employment & Wages")),
            Map.entry(Topic.LIVING_STANDARDS,   new Entry(10, "სიახლეები: ცხოვრების დონე",            "News: Living Standards")),
            Map.entry(Topic.HEALTHCARE,         new Entry(11, "სიახლეები: ჯანდაცვა",                 "News: Healthcare")),
            Map.entry(Topic.EDUCATION,          new Entry(12, "სიახლეები: განათლება და კულტურა",      "News: Education & Culture")),
            Map.entry(Topic.GENDER,             new Entry(16, "სიახლეები: გენდერული სტატისტიკა",      "News: Gender Statistics")),
            Map.entry(Topic.YOUTH,              new Entry(5,  "სიახლეები: სოციალური სტატისტიკა",      "News: Social Statistics")),
            Map.entry(Topic.DISABILITY,         new Entry(5,  "სიახლეები: სოციალური სტატისტიკა",      "News: Social Statistics")),
            Map.entry(Topic.AGRICULTURE,        new Entry(13, "სიახლეები: სოფლის მეურნეობა",          "News: Agriculture")),
            Map.entry(Topic.INDUSTRY,           new Entry(18, "სიახლეები: მრეწველობა და ენერგეტიკა",  "News: Industry & Energy")),
            Map.entry(Topic.TOURISM,            new Entry(15, "სიახლეები: ტურიზმი",                   "News: Tourism")),
            Map.entry(Topic.SERVICES,           new Entry(19, "სიახლეები: მომსახურების სტატისტიკა",   "News: Services Statistics")),
            Map.entry(Topic.ENVIRONMENT,        new Entry(14, "სიახლეები: გარემოს სტატისტიკა",        "News: Environment")),
            Map.entry(Topic.ICT,                new Entry(17, "სიახლეები: საინფორმაციო ტექნოლოგიები", "News: Information Technology")),
            Map.entry(Topic.NEWS,               new Entry(2,  "სიახლეები",                             "News")),
            Map.entry(Topic.GENERAL,            new Entry(2,  "სიახლეები",                             "News"))
    );

    public static LinkInfo getCategoryNews(Topic topic, boolean isGeorgian) {
        Entry e = ENTRIES.getOrDefault(topic, DEFAULT);
        String lang = isGeorgian ? "ka" : "en";
        String url = String.format("https://www.geostat.ge/%s/news?year=&month=&category=%d", lang, e.categoryId());
        return new LinkInfo(url, e.titleKa(), e.titleEn());
    }
}