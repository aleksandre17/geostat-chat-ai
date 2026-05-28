package com.geostat.ingestion.crawl.fetch;

import com.geostat.platform.crawl.FetchOptions;
import com.geostat.platform.crawl.PageFetchException;
import com.geostat.platform.crawl.PageFetcher;
import com.geostat.platform.crawl.RenderMode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * {@link PageFetcher} adapter for the HEADLESS render mode.
 *
 * <p>Bridges the platform-contract {@code PageFetcher} port (which returns a
 * {@code com.geostat.platform.crawl.FetchedPage} with a raw HTML string) to the
 * internal {@link PlaywrightPageFetcher} (which returns the richer
 * {@code com.geostat.ingestion.crawl.fetch.FetchedPage} carrying a Jsoup {@code Document}).
 *
 * <p>Active only when {@code geostat.ingestion.playwright.enabled=true} — the same
 * condition that activates {@link PlaywrightPageFetcher}. Using {@code @ConditionalOnProperty}
 * (rather than {@code @ConditionalOnBean}) ensures deterministic bean registration order
 * during component scanning. Routing to HEADLESS vs STATIC is a per-request decision
 * made by {@link RoutingPageFetcher} based on {@link FetchOptions#renderMode()}.
 */
@Component
@ConditionalOnProperty(prefix = "geostat.ingestion.playwright", name = "enabled", havingValue = "true")
public class HeadlessBrowserPageFetcher implements PageFetcher {

    private final PlaywrightPageFetcher playwrightFetcher;

    public HeadlessBrowserPageFetcher(PlaywrightPageFetcher playwrightFetcher) {
        this.playwrightFetcher = playwrightFetcher;
    }

    /**
     * Fetches {@code url} via Playwright and adapts the result to the platform contract.
     *
     * <p>The internal {@link com.geostat.ingestion.crawl.fetch.FetchedPage} carries a Jsoup
     * {@code Document}; the platform record expects a raw HTML string — conversion is
     * {@code document.outerHtml()}.
     */
    @Override
    public com.geostat.platform.crawl.FetchedPage fetch(String url, FetchOptions options)
            throws PageFetchException {
        com.geostat.ingestion.crawl.fetch.FetchedPage internal =
                playwrightFetcher.fetchPage(url, options);
        String html = internal.html() == null ? "" : internal.html().outerHtml();
        return new com.geostat.platform.crawl.FetchedPage(
                internal.canonicalUrl(),
                html,
                internal.statusCode(),
                "text/html",
                RenderMode.HEADLESS);
    }
}
