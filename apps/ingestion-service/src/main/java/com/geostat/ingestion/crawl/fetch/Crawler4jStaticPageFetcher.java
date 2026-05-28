package com.geostat.ingestion.crawl.fetch;

import com.geostat.platform.crawl.PageFetchException;
import com.geostat.platform.crawl.PageFetcher;
import com.geostat.platform.crawl.RenderMode;
import java.io.IOException;
import org.jsoup.nodes.Document;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * {@link PageFetcher} adapter for the STATIC render mode.
 *
 * <p>The main crawl path for static corpora (e.g. geostat-portal) is managed by
 * crawler4j's internal fetch loop via {@link Crawler4jPageFetcher}. This adapter
 * covers the on-demand single-URL path (e.g. quality re-fetch, API-triggered crawl)
 * by delegating to {@link ConditionalHttpFetcher} — a plain Java {@code HttpClient}
 * with redirect-following and encoding-mismatch detection.
 */
@Component
@ConditionalOnProperty(
        name = "geostat.ingestion.crawl.fetch-mode",
        havingValue = "static",
        matchIfMissing = true)
public class Crawler4jStaticPageFetcher implements PageFetcher {

    private final ConditionalHttpFetcher httpFetcher;

    public Crawler4jStaticPageFetcher(ConditionalHttpFetcher httpFetcher) {
        this.httpFetcher = httpFetcher;
    }

    @Override
    public com.geostat.platform.crawl.FetchedPage fetch(
            String url, com.geostat.platform.crawl.FetchOptions options) throws PageFetchException {
        String agent = (options.userAgent() != null && !options.userAgent().isBlank())
                ? options.userAgent()
                : CrawlFetchInfrastructure.USER_AGENT;
        var network = options.network();
        int timeoutMs = options.timeoutMs() > 0 ? options.timeoutMs() : 10_000;
        try {
            FetchedPage internal = httpFetcher.fetch(url, agent, FetchOptions.none(), network, timeoutMs);
            Document doc = internal.html(); // null on 304 Not Modified
            String html = doc != null ? doc.outerHtml() : "";
            return new com.geostat.platform.crawl.FetchedPage(
                    internal.canonicalUrl(),
                    html,
                    internal.statusCode(),
                    "text/html",
                    RenderMode.STATIC);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PageFetchException(url, "fetch interrupted", e);
        } catch (IOException e) {
            throw new PageFetchException(url, "HTTP fetch failed: " + e.getMessage(), e);
        }
    }
}
