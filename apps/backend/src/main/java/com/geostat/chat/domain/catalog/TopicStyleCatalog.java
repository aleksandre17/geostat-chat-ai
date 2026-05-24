package com.geostat.chat.domain.catalog;

import com.geostat.chat.domain.catalog.Topic;
import com.geostat.chat.domain.catalog.TopicDefinition.TopicStyle;

import java.util.Map;

public final class TopicStyleCatalog {

    private TopicStyleCatalog() {}

    public record LinkTypeStyle(String icon, String bgColor, String lightBg, String labelKa, String labelEn) {}

    public static final Map<Topic, TopicStyle> STYLES = Map.ofEntries(
            Map.entry(Topic.NATIONAL_ACCOUNTS,  new TopicStyle("erovnuli_angarishebi",              "#3B82F6", "#DBEAFE")),
            Map.entry(Topic.BUSINESS,            new TopicStyle("biznesSeqtori",                     "#64748B", "#F1F5F9")),
            Map.entry(Topic.PRICES,              new TopicStyle("fasebis_statistika",                "#22C55E", "#DCFCE7")),
            Map.entry(Topic.TRADE,               new TopicStyle("sagareovachroba",                   "#06B6D4", "#CFFAFE")),
            Map.entry(Topic.FDI,                 new TopicStyle("pirdapiri_ucxouri_invisticiebi",    "#F59E0B", "#FEF3C7")),
            Map.entry(Topic.MONETARY,            new TopicStyle("erovnuli_angarishebi",              "#6366F1", "#E0E7FF")),
            Map.entry(Topic.GOVERNMENT_FINANCE,  new TopicStyle("saxemlmwipo_finansebis_stat",       "#78716C", "#F5F5F4")),
            Map.entry(Topic.POPULATION,          new TopicStyle("mosaxleoba_statistika",             "#0EA5E9", "#E0F2FE")),
            Map.entry(Topic.EMPLOYMENT,          new TopicStyle("dasaqmeba_xelpasi",                 "#2563EB", "#DBEAFE")),
            Map.entry(Topic.LIVING_STANDARDS,    new TopicStyle("cxovrebis_done",                   "#F97316", "#FFEDD5")),
            Map.entry(Topic.HEALTHCARE,          new TopicStyle("jadacva_socialuri_uzrunvelyopa",    "#EF4444", "#FEE2E2")),
            Map.entry(Topic.EDUCATION,           new TopicStyle("ganatleba_mecnier_sportl_kultura",  "#A855F7", "#F3E8FF")),
            Map.entry(Topic.CRIME,               new TopicStyle("samartaldargvevisStatistika",       "#4B5563", "#F3F4F6")),
            Map.entry(Topic.GENDER,              new TopicStyle("mosaxleoba_statistika",             "#EC4899", "#FCE7F3")),
            Map.entry(Topic.YOUTH,               new TopicStyle("mosaxleoba_statistika",             "#EAB308", "#FEF9C3")),
            Map.entry(Topic.DISABILITY,          new TopicStyle("jadacva_socialuri_uzrunvelyopa",    "#3B82F6", "#DBEAFE")),
            Map.entry(Topic.AGRICULTURE,         new TopicStyle("soflis_meurneoba_sasursato_usap",   "#84CC16", "#ECFCCB")),
            Map.entry(Topic.INDUSTRY,            new TopicStyle("mrewveloba_energetika_msh",         "#71717A", "#F4F4F5")),
            Map.entry(Topic.TOURISM,             new TopicStyle("turizmis_statistika",               "#14B8A6", "#CCFBF1")),
            Map.entry(Topic.SERVICES,            new TopicStyle("momxsaxurebis_statistika",          "#F43F5E", "#FFE4E6")),
            Map.entry(Topic.ICT,                 new TopicStyle("sainpormacio_sakomunikacia",         "#8B5CF6", "#EDE9FE")),
            Map.entry(Topic.ENVIRONMENT,         new TopicStyle("garemosStatistika",                 "#10B981", "#D1FAE5")),
            Map.entry(Topic.REGIONS,             new TopicStyle("regionaluri_statistika",            "#D97706", "#FEF3C7")),
            Map.entry(Topic.CALENDAR,            new TopicStyle("news",                              "#F87171", "#FEE2E2")),
            Map.entry(Topic.NEWS,                new TopicStyle("news",                              "#737373", "#F5F5F5")),
            Map.entry(Topic.METHODOLOGY,         new TopicStyle("methodology",                       "#92400E", "#FEF3C7")),
            Map.entry(Topic.SURVEYS,             new TopicStyle("quest",                             "#0891B2", "#CFFAFE")),
            Map.entry(Topic.PUBLICATIONS,        new TopicStyle("publication",                       "#B45309", "#FEF3C7")),
            Map.entry(Topic.CONTACT,             new TopicStyle("contact",                           "#16A34A", "#DCFCE7")),
            Map.entry(Topic.STRUCTURE,           new TopicStyle("biznes_registri",                   "#475569", "#F1F5F9")),
            Map.entry(Topic.DATABASE,            new TopicStyle("metadata",                          "#6B7280", "#F3F4F6")),
            Map.entry(Topic.ABOUT_US,            new TopicStyle("biznes_registri",                   "#3B82F6", "#DBEAFE")),
            Map.entry(Topic.MANAGEMENT,          new TopicStyle("biznes_registri",                   "#4F46E5", "#E0E7FF")),
            Map.entry(Topic.TERRITORIAL,         new TopicStyle("regionaluri_statistika",            "#57534E", "#F5F5F4")),
            Map.entry(Topic.VACANCIES,           new TopicStyle("dasaqmeba_xelpasi",                 "#059669", "#D1FAE5")),
            Map.entry(Topic.TENDERS,             new TopicStyle("publication",                       "#CA8A04", "#FEF9C3")),
            Map.entry(Topic.LEGISLATION,         new TopicStyle("samartaldargvevisStatistika",       "#334155", "#F1F5F9")),
            Map.entry(Topic.BUDGET,              new TopicStyle("saxemlmwipo_finansebis_stat",       "#EAB308", "#FEF9C3")),
            Map.entry(Topic.DATA_QUALITY,        new TopicStyle("methodology",                       "#22C55E", "#DCFCE7")),
            Map.entry(Topic.INTERNATIONAL,       new TopicStyle("sagareovachroba",                   "#0284C7", "#E0F2FE")),
            Map.entry(Topic.ANNIVERSARY,         new TopicStyle("news",                              "#EC4899", "#FCE7F3")),
            Map.entry(Topic.SDG,                 new TopicStyle("garemosStatistika",                 "#EA580C", "#FFEDD5")),
            Map.entry(Topic.PHOTO_GALLERY,       new TopicStyle("infographic",                       "#D946EF", "#FAE8FF")),
            Map.entry(Topic.VIDEO_GALLERY,       new TopicStyle("video",                             "#DC2626", "#FEE2E2")),
            Map.entry(Topic.INFOGRAPHIC,         new TopicStyle("infographic",                       "#7C3AED", "#EDE9FE")),
            Map.entry(Topic.MEDIA,               new TopicStyle("video",                             "#A855F7", "#F3E8FF")),
            Map.entry(Topic.PROJECTS,            new TopicStyle("mravalindeksat_gamokvlelva",        "#2563EB", "#DBEAFE")),
            Map.entry(Topic.EXTERNAL_LINKS,      new TopicStyle("sagareovachroba",                   "#6B7280", "#F3F4F6")),
            Map.entry(Topic.CLASSIFIER,          new TopicStyle("metadata",                          "#0D9488", "#CCFBF1")),
            Map.entry(Topic.I_RATING,            new TopicStyle("erovnuli_angarishebi",              "#F59E0B", "#FEF3C7")),
            Map.entry(Topic.GENERAL,             new TopicStyle("erovnuli_angarishebi",              "#3B82F6", "#DBEAFE"))
    );

    public static final Map<String, LinkTypeStyle> LINK_TYPE_STYLES = Map.ofEntries(
            Map.entry("portal",       new LinkTypeStyle("erovnuli_angarishebi",         "#8B5CF6", "#EDE9FE", "პორტალი",         "Portal")),
            Map.entry("statistics",   new LinkTypeStyle("biznesSeqtori",                "#3B82F6", "#DBEAFE", "სტატისტიკა",       "Statistics")),
            Map.entry("metadata",     new LinkTypeStyle("metadata",                     "#06B6D4", "#CFFAFE", "მეტამონაცემები",   "Metadata")),
            Map.entry("methodology",  new LinkTypeStyle("methodology",                  "#F59E0B", "#FEF3C7", "მეთოდოლოგია",      "Methodology")),
            Map.entry("news",         new LinkTypeStyle("news",                         "#10B981", "#D1FAE5", "სიახლეები",        "News")),
            Map.entry("general",      new LinkTypeStyle("biznes_registri",              "#6B7280", "#F3F4F6", "ბმული",            "Link")),
            Map.entry("management",   new LinkTypeStyle("biznes_registri",              "#4F46E5", "#E0E7FF", "მმართველობა",      "Management")),
            Map.entry("about_us",     new LinkTypeStyle("biznes_registri",              "#3B82F6", "#DBEAFE", "ჩვენ შესახებ",     "About Us")),
            Map.entry("surveys",      new LinkTypeStyle("quest",                        "#0891B2", "#CFFAFE", "გამოკვლევები",     "Surveys")),
            Map.entry("calendar",     new LinkTypeStyle("news",                         "#F87171", "#FEE2E2", "კალენდარი",        "Calendar")),
            Map.entry("publications", new LinkTypeStyle("publication",                  "#B45309", "#FEF3C7", "პუბლიკაციები",     "Publications")),
            Map.entry("database",     new LinkTypeStyle("metadata",                     "#6B7280", "#F3F4F6", "მონაცემთა ბაზა",  "Database")),
            Map.entry("legislation",  new LinkTypeStyle("samartaldargvevisStatistika",  "#334155", "#F1F5F9", "კანონმდებლობა",    "Legislation")),
            Map.entry("contact",      new LinkTypeStyle("contact",                      "#16A34A", "#DCFCE7", "საკონტაქტო",       "Contact")),
            Map.entry("structure",    new LinkTypeStyle("biznes_registri",              "#475569", "#F1F5F9", "სტრუქტურა",        "Structure")),
            Map.entry("source",         new LinkTypeStyle("publication",                  "#6B7280", "#F3F4F6", "წყარო",            "Source"))
    );

    public static LinkTypeStyle getLinkTypeStyle(String type) {
        if (type == null) return LINK_TYPE_STYLES.get("general");
        return LINK_TYPE_STYLES.getOrDefault(type.toLowerCase(), LINK_TYPE_STYLES.get("general"));
    }

    public static String getLinkTypeLabel(String type, boolean isGeorgian) {
        LinkTypeStyle s = getLinkTypeStyle(type);
        return s == null ? (isGeorgian ? "ბმული" : "Link") : (isGeorgian ? s.labelKa() : s.labelEn());
    }
}