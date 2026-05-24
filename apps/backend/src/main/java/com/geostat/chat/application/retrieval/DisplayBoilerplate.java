package com.geostat.chat.application.retrieval;

/** GeoStat site boilerplate filters for RAG-L11 citation snippets. */
final class DisplayBoilerplate {

    private static final String[] MARKERS = {
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
        "skip to content",
        "უკან დაბრუნება",
        "go back "
    };

    private DisplayBoilerplate() {}

    static boolean isBoilerplate(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.strip().replaceAll("\\s+", " ");
        if (normalized.startsWith("×")) {
            return true;
        }
        for (String marker : MARKERS) {
            if (normalized.contains(marker)) {
                return true;
            }
        }
        return false;
    }
}
