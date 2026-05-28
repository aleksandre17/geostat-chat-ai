package com.geostat.ingestion.crawl;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

/**
 * Normalizes crawled URLs to a canonical form before deduplication.
 *
 * <p>Removes tracking, session, and UI-only query parameters that do not affect content identity.
 * Applied in {@link com.geostat.ingestion.parse.profile.PolicyUrlFilter} and during document
 * persistence in {@link com.geostat.ingestion.crawl.runner.CrawlRunStore}.
 */
public final class UrlNormalizer {

    private UrlNormalizer() {}

    // Parameters that never affect page content identity
    private static final Set<String> STRIP_PARAMS = Set.of(
            // UTM tracking
            "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
            // Social media tracking
            "fbclid", "gclid", "msclkid", "twclid", "igshid",
            // Referrer / share
            "ref", "referrer", "share",
            // UI / display toggles (content determined by URL path, not param)
            "lang",
            // Analytics / AB test IDs
            "mc_cid", "mc_eid", "_ga", "wickedsource");

    /**
     * Returns the canonical form of the URL:
     *
     * <ul>
     *   <li>Strips tracking query parameters
     *   <li>Removes fragment (#anchor) — always UI-only
     *   <li>Removes trailing slash from path (except root "/")
     *   <li>Lowercases scheme and host
     * </ul>
     *
     * Returns the original URL string if normalization fails (parse error).
     */
    public static String normalize(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        try {
            URI uri = new URI(url).normalize();
            String query = buildCleanQuery(uri.getQuery());
            URI normalized = new URI(
                    uri.getScheme().toLowerCase(),
                    null,
                    uri.getHost().toLowerCase(),
                    uri.getPort(),
                    normalizePath(uri.getPath()),
                    query,
                    null // strip fragment
                    );
            return normalized.toString();
        } catch (URISyntaxException e) {
            return url; // safe fallback — unparseable URL unchanged
        }
    }

    private static String buildCleanQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return null;
        }
        String[] pairs = rawQuery.split("&");
        StringBuilder clean = new StringBuilder();
        for (String pair : pairs) {
            String key = pair.split("=", 2)[0].toLowerCase();
            if (!STRIP_PARAMS.contains(key)) {
                if (!clean.isEmpty()) {
                    clean.append('&');
                }
                clean.append(pair);
            }
        }
        return clean.isEmpty() ? null : clean.toString();
    }

    private static String normalizePath(String path) {
        if (path == null || path.equals("/")) {
            return "/";
        }
        return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }
}
