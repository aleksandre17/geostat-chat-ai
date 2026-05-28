package com.geostat.platform.crawl;

import com.geostat.platform.parse.ParseProfile;

/**
 * Port: classifies a fetched page into a well-known page kind.
 *
 * <p>Implementations may use URL patterns, HTML signals, or both.
 * Must always return a non-null, non-blank kind — use {@link PageKind#UNKNOWN} as fallback.
 *
 * <p>Detection rules are loaded from {@code pageKindRules} in the corpus parse profile YAML.
 * Zero code changes required to add or modify rules.
 */
public interface PageKindDetector {

    /**
     * Detect the page kind for the given page.
     *
     * @param page    fetched page with URL and raw HTML
     * @param profile corpus parse profile containing pageKindRules from YAML
     * @return        non-null, non-blank page kind string
     */
    String detect(FetchedPage page, ParseProfile profile);
}
