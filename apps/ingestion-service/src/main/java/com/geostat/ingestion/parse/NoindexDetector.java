package com.geostat.ingestion.parse;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public final class NoindexDetector {

    private NoindexDetector() {}

    /**
     * Returns true if the page has a robots noindex directive.
     * Checks (in priority order):
     *   1. &lt;meta name="robots" content="noindex"&gt;
     *   2. &lt;meta name="robots" content="noindex,nofollow"&gt;
     *   3. Both "robots" and "googlebot" named meta tags.
     * Case-insensitive.
     */
    public static boolean isNoindex(Document html) {
        for (Element meta : html.select("meta[name~=(?i)robots], meta[name~=(?i)googlebot]")) {
            String content = meta.attr("content").toLowerCase();
            if (content.contains("noindex")) {
                return true;
            }
        }
        return false;
    }
}
