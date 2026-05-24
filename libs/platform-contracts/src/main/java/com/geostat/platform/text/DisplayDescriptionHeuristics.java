package com.geostat.platform.text;

import java.text.Normalizer;
import java.util.Locale;

/** Shared rules for page/card display descriptions (RAG-L11). */
public final class DisplayDescriptionHeuristics {

    private static final int DISPLAY_MAX = 240;

    private static final String[] BOILERPLATE_MARKERS = {
        "ვებგვერდის ადაპტ",
        "adapted version of the website",
        "united nations development program",
        "გაეროს განვითარების პროგრამ",
        "government of sweden",
        "შვედეთის მთავრობ",
        "საჯარო სამართლის იურიდიული პირი",
        "official statistics of georgia",
        "ცენტრალური ოფისი:",
        "central office:",
        "საქსტატის ოფიციალური ვებგვერდი",
        "geostat official website",
        "skip to content",
        "უკან დაბრუნება",
        "go back "
    };

    private DisplayDescriptionHeuristics() {}

    public static boolean isBoilerplate(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = Normalizer.normalize(
                text.strip().replaceAll("\\s+", " "), Normalizer.Form.NFKC);
        if (normalized.startsWith("×")) {
            return true;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        for (String marker : BOILERPLATE_MARKERS) {
            if (normalized.contains(marker) || lower.contains(marker.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    public static boolean isUsableDescription(String text, int minChars) {
        return text != null
                && text.strip().length() >= minChars
                && !isBoilerplate(text);
    }

    public static String fromTitleAndSection(String pageTitle, String sectionPath) {
        if (pageTitle == null || pageTitle.isBlank()) {
            return null;
        }
        String title = pageTitle.strip();
        if (sectionPath != null && !sectionPath.isBlank()) {
            return trimToDisplay(title + " — " + sectionPath.strip());
        }
        return trimToDisplay(title);
    }

    public static String trimToDisplay(String text) {
        if (text == null) {
            return null;
        }
        String normalized = text.strip().replaceAll("\\s+", " ");
        if (normalized.length() <= DISPLAY_MAX) {
            return normalized;
        }
        return normalized.substring(0, DISPLAY_MAX - 3).strip() + "...";
    }
}
