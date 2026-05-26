package com.geostat.ingestion.parse.profile;

import com.geostat.platform.parse.BoilerplateMarkers;
import com.geostat.platform.parse.ParseProfile;
import java.util.List;

/**
 * Inline default profile used when {@code ops/config/corpus/<corpus>-parse.yaml} is absent
 * (HANDOFF-P0-L1 §3.5).
 *
 * <p>Markers are bucketed by match semantic:
 *
 * <ul>
 *   <li>{@code ka}/{@code en} → startsWith-only (short UI labels: buttons, link text).
 *   <li>{@code containsKa}/{@code containsEn} → contains-anywhere (long descriptive
 *       boilerplate that may be embedded mid-sentence).
 * </ul>
 */
public final class DefaultParseProfile {

    private static final List<String> ROOT_SELECTORS = List.of(
            ".value-databases-section",
            ".archive-section",
            ".news-section",
            ".albums-section",
            "section.infographic",
            "section.home-statistic-category",
            "main",
            "article",
            "[role=main]",
            ".content-area",
            "#content");

    private static final List<String> REMOVE_SELECTORS = List.of(
            "script",
            "style",
            "noscript",
            "iframe",
            "svg",
            "nav",
            "footer",
            "header",
            "#fin_info",
            ".breadcrumb-wrapper",
            ".rightbar-wrapper",
            ".fixed-contact-blocks",
            ".social-static-icons",
            ".social-static-icon",
            ".pagination",
            "geostat-chat-widget",
            ".accessibility-notice",
            ".cookie-banner",
            ".social-share",
            ".breadcrumb",
            "#skip-to-content");

    private static final List<String> STARTS_WITH_KA = List.of(
            "უკან დაბრუნება",
            "სრულად ნახვა",
            "არქივი",
            "აუდიო გახმოვანების",
            "გამოიწერეთ სიახლეები",
            "ცენტრალური ოფისი:");

    private static final List<String> STARTS_WITH_EN = List.of(
            "skip to content",
            "read more",
            "archive",
            "audio narration",
            "subscribe to news",
            "central office:");

    private static final List<String> CONTAINS_KA = List.of(
            "ვებგვერდის ადაპტირებული ვერსია",
            "საქსტატის ოფიციალური ვებგვერდი",
            "საჯარო სამართლის იურიდიული პირი",
            "გაეროს განვითარების პროგრამ",
            "შვედეთის მთავრობ",
            "Crafted by");

    private static final List<String> CONTAINS_EN = List.of(
            "adapted version of the website",
            "official statistics of georgia",
            "official website of geostat",
            "united nations development program",
            "government of sweden",
            "Crafted by");

    public static final ParseProfile GEOSTAT_PORTAL = new ParseProfile(
            "geostat-portal",
            ROOT_SELECTORS,
            REMOVE_SELECTORS,
            new BoilerplateMarkers(
                    STARTS_WITH_KA,
                    STARTS_WITH_EN,
                    List.of(),
                    CONTAINS_KA,
                    CONTAINS_EN,
                    List.of()),
            true,
            true,
            true,
            true,
            List.of("htmlLang", "urlSegment", "metaContentLanguage"));

    private DefaultParseProfile() {}
}
