package com.geostat.ingestion.crawl.fetch;

import com.geostat.platform.crawl.FetchedPage;
import com.geostat.platform.crawl.FetchOptions;
import com.geostat.platform.crawl.PageFetchException;
import com.geostat.platform.crawl.PageFetcher;
import java.util.Optional;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Primary PageFetcher: routes to the appropriate implementation based on RenderMode.
 * Add new render modes here as new fetcher implementations become available.
 */
@Primary
@Component
public class RoutingPageFetcher implements PageFetcher {

    private final Optional<Crawler4jStaticPageFetcher> staticFetcher;
    private final Optional<HeadlessBrowserPageFetcher> headlessFetcher;

    public RoutingPageFetcher(
            Optional<Crawler4jStaticPageFetcher> staticFetcher,
            Optional<HeadlessBrowserPageFetcher> headlessFetcher) {
        this.staticFetcher   = staticFetcher;
        this.headlessFetcher = headlessFetcher;
    }

    @Override
    public FetchedPage fetch(String url, FetchOptions options) throws PageFetchException {
        return switch (options.renderMode()) {
            case STATIC -> staticFetcher
                    .orElseThrow(() -> new PageFetchException(url, "static fetcher not configured", null))
                    .fetch(url, options);
            case HEADLESS -> headlessFetcher
                    .orElseThrow(() -> new PageFetchException(url, "headless fetcher not configured", null))
                    .fetch(url, options);
            // planned: v2 — API-based SPA fetching (e.g. public JSON endpoints)
            case API -> throw new UnsupportedOperationException(
                    "RenderMode.API fetch is not yet implemented. "
                            + "Wire an ApiPageFetcher bean and add it to RoutingPageFetcher (planned: v2).");
        };
    }
}
