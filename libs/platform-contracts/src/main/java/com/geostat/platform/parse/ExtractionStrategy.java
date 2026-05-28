package com.geostat.platform.parse;

/**
 * Per-corpus or per-page-kind extraction strategy.
 * Implementations encapsulate site-specific extraction logic that cannot be
 * expressed purely through ParseProfile YAML selectors.
 *
 * <p>Strategies are registered by {@link ExtractionStrategyRegistry} and
 * resolved at runtime by corpus name + page kind.
 *
 * <p>New strategy: add one class annotated {@code @Component} — nothing else changes.
 */
public interface ExtractionStrategy {

    /**
     * Unique strategy identifier (e.g. "geostat-news", "yaml-configured-fallback").
     * Used for logging and registry lookup.
     */
    String strategyId();

    /**
     * Returns true if this strategy handles the given corpus + page kind combination.
     * Evaluated in registration order; first match wins.
     */
    boolean supports(String corpusName, String pageKind);

    /**
     * Returns true when this strategy is the YAML-profile-driven fallback
     * (delegates back to the default extractor path).
     */
    default boolean isDefaultFallback() {
        return false;
    }

    /**
     * Extract structured content from the given HTML page.
     *
     * @param page    raw HTML + URL
     * @param profile base profile (YAML-loaded) as hint — may be ignored
     * @return        extracted document content
     */
    CleanedDocument extract(HtmlPageInput page, ParseProfile profile);
}
