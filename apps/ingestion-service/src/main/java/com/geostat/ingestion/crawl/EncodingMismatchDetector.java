package com.geostat.ingestion.crawl;

import java.util.regex.Pattern;

/**
 * Detects potential character encoding mismatch after fetch.
 *
 * <p>Strategy: after fetch, check for Georgian character presence on /ka/ pages.
 * geostat.ge is a Georgian government site — every /ka/ page MUST contain Georgian.
 * If /ka/ page has zero Georgian characters → encoding mismatch.
 *
 * <p>Does NOT re-encode. Logs WARN. Sets document.encoding_issue = true.
 */
public final class EncodingMismatchDetector {

    // Georgian Unicode block: U+10A0–U+10FF (script) + U+2D00–U+2D2F (supplement)
    private static final Pattern GEORGIAN_CHARS =
            Pattern.compile("[\u10A0-\u10FF\u2D00-\u2D2F]");

    private EncodingMismatchDetector() {}

    /**
     * Returns true if the page appears to be a Georgian-language page
     * but contains no Georgian characters — strong signal of encoding corruption.
     * Only checks /ka/ URLs to avoid false positives on English pages.
     */
    public static boolean looksCorrupted(String url, String htmlText) {
        if (htmlText == null || htmlText.isBlank()) {
            return false;
        }
        boolean isKaUrl = url != null && url.contains("/ka/");
        if (!isKaUrl) {
            return false;
        }
        return !GEORGIAN_CHARS.matcher(htmlText).find();
    }
}
