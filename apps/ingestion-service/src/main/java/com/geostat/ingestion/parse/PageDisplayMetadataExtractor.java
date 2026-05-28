package com.geostat.ingestion.parse;

import java.util.List;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/** Extracts human-readable display metadata from parsed HTML (RAG-L11). */
@Component
public class PageDisplayMetadataExtractor {

    static final int MIN_META_CHARS = 40;
    static final int DISPLAY_MAX = 240;
    static final int LEAD_MIN = 40;

    public DisplayMetadata extract(Document html, String pageTitle, List<String> sectionPath) {
        String metaDescription = firstNonBlank(
                metaContent(html, "description"),
                metaProperty(html, "og:description"),
                metaProperty(html, "twitter:description"));
        if (DisplayBoilerplate.isBoilerplate(metaDescription)) {
            metaDescription = null;
        }
        String leadText = extractLeadParagraph(html);
        String displayDescription = resolveDisplay(metaDescription, leadText, pageTitle, sectionPath);
        return new DisplayMetadata(metaDescription, leadText, displayDescription);
    }

    /** Applies JSON-LD description when HTML meta tags did not yield a description. */
    public DisplayMetadata withJsonLdDescriptionFallback(
            DisplayMetadata display,
            String jsonLdDescription,
            String pageTitle,
            List<String> sectionPath) {
        String metaDescription = display.metaDescription();
        String displayDescription = display.displayDescription();
        if ((metaDescription == null || metaDescription.isBlank()) && jsonLdDescription != null) {
            metaDescription = jsonLdDescription;
            if (displayDescription == null || displayDescription.isBlank()) {
                displayDescription =
                        resolveDisplay(metaDescription, display.leadText(), pageTitle, sectionPath);
            }
        }
        return new DisplayMetadata(metaDescription, display.leadText(), displayDescription);
    }

    public static String resolveDisplay(
            String metaDescription, String leadText, String pageTitle, List<String> sectionPath) {
        if (DisplayBoilerplate.isUsable(metaDescription, MIN_META_CHARS)) {
            return trimToDisplay(metaDescription);
        }
        if (DisplayBoilerplate.isUsable(leadText, LEAD_MIN)) {
            return trimToDisplay(leadText);
        }
        String section = SectionPathExtractor.joinPath(sectionPath);
        String fromTitle = titleSectionDisplay(pageTitle, section);
        if (fromTitle != null && fromTitle.length() >= 10) {
            return fromTitle;
        }
        return null;
    }

    static String titleSectionDisplay(String pageTitle, String section) {
        if (pageTitle == null || pageTitle.isBlank()) {
            return null;
        }
        String title = pageTitle.strip();
        if (section != null && !section.isBlank()) {
            return trimToDisplay(title + " — " + section.strip());
        }
        return trimToDisplay(title);
    }

    static String extractLeadParagraph(Document html) {
        if (html == null) {
            return null;
        }
        Element root = html.selectFirst("main, article, [role=main]");
        if (root == null) {
            root = html.body();
        }
        if (root == null) {
            return null;
        }
        for (Element p : root.select("p")) {
            String text = p.text().strip().replaceAll("\\s+", " ");
            if (text.length() >= LEAD_MIN
                    && looksLikeProse(text)
                    && !DisplayBoilerplate.isBoilerplate(text)) {
                return text;
            }
        }
        return null;
    }

    static boolean looksLikeProse(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        long letters = text.chars().filter(Character::isLetter).count();
        return letters >= LEAD_MIN / 2;
    }

    private static String metaContent(Document html, String name) {
        Element el = html.selectFirst("meta[name=" + cssEscape(name) + "]");
        return el != null ? el.attr("content").strip() : "";
    }

    private static String metaProperty(Document html, String property) {
        Element el = html.selectFirst("meta[property=" + cssEscape(property) + "]");
        return el != null ? el.attr("content").strip() : "";
    }

    private static String cssEscape(String value) {
        return "\"" + value.replace("\"", "\\\"") + "\"";
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.strip();
            }
        }
        return null;
    }

    private static boolean isUsable(String text, int min) {
        return text != null && text.strip().length() >= min;
    }

    static String trimToDisplay(String text) {
        if (text == null) {
            return null;
        }
        String normalized = text.strip().replaceAll("\\s+", " ");
        if (normalized.length() <= DISPLAY_MAX) {
            return normalized;
        }
        return normalized.substring(0, DISPLAY_MAX - 3).strip() + "...";
    }

    public record DisplayMetadata(String metaDescription, String leadText, String displayDescription) {}
}
