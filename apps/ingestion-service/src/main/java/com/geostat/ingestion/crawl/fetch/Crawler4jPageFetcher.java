package com.geostat.ingestion.crawl.fetch;

import com.geostat.ingestion.crawl.policy.CorpusPolicy;
import com.geostat.ingestion.parse.profile.RoutingUrlFilter;
import com.geostat.ingestion.persistence.entity.CorpusEntity;
import edu.uci.ics.crawler4j.crawler.Page;
import edu.uci.ics.crawler4j.crawler.exceptions.PageBiggerThanMaxSizeException;
import edu.uci.ics.crawler4j.fetcher.PageFetchResult;
import edu.uci.ics.crawler4j.url.WebURL;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.http.HttpStatus;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("db")
public class Crawler4jPageFetcher {

    private static final int MAX_REDIRECTS = 5;

    private final CrawlFetchInfrastructure infrastructure;
    private final RoutingUrlFilter routingUrlFilter;
    private final ConditionalHttpFetcher conditionalHttpFetcher = new ConditionalHttpFetcher();

    public Crawler4jPageFetcher(CrawlFetchInfrastructure infrastructure, RoutingUrlFilter routingUrlFilter) {
        this.infrastructure = infrastructure;
        this.routingUrlFilter = routingUrlFilter;
    }

    /** Conditional GET when stored validators exist (freshness refresh). */
    public FetchedPage fetchConditional(String url, CorpusEntity corpus, String etag, java.time.Instant lastModified)
            throws IOException, InterruptedException, RobotsBlockedException, PolicyBlockedException,
                    PageNotModifiedException {
        if (!routingUrlFilter.shouldEnqueue(url, corpus)) {
            throw new PolicyBlockedException(url);
        }
        if ((etag == null || etag.isBlank()) && lastModified == null) {
            return fetch(url, corpus);
        }
        return conditionalHttpFetcher.fetch(url, CrawlFetchInfrastructure.USER_AGENT, etag, lastModified);
    }

    public FetchedPage fetch(String url, CorpusEntity corpus)
            throws IOException, InterruptedException, RobotsBlockedException, PolicyBlockedException {
        if (!routingUrlFilter.shouldEnqueue(url, corpus)) {
            throw new PolicyBlockedException(url);
        }

        WebURL webUrl = new WebURL();
        webUrl.setURL(url);

        if (CorpusPolicy.respectRobotsTxt(corpus) && !infrastructure.robotsServer().allows(webUrl)) {
            throw new RobotsBlockedException(url);
        }

        PageFetchResult fetchResult = fetchWithRedirects(webUrl);
        int statusCode = fetchResult.getStatusCode();
        if (statusCode < 200 || statusCode >= 300) {
            throw new IOException("HTTP " + statusCode + " for " + url);
        }

        Page page = new Page(webUrl);
        fetchResult.fetchContent(page, 10 * 1024 * 1024);
        Document document = Jsoup.parse(toHtml(page), webUrl.getURL());
        return new FetchedPage(
                webUrl.getURL(),
                statusCode,
                document,
                HttpResponseHeaders.etag(fetchResult),
                HttpResponseHeaders.lastModified(fetchResult));
    }

    private PageFetchResult fetchWithRedirects(WebURL startUrl)
            throws IOException, InterruptedException {
        WebURL current = startUrl;
        for (int attempt = 0; attempt < MAX_REDIRECTS; attempt++) {
            PageFetchResult result;
            try {
                result = infrastructure.pageFetcher().fetchPage(current);
            } catch (PageBiggerThanMaxSizeException e) {
                throw new IOException("Page exceeds max download size: " + startUrl.getURL(), e);
            }
            String movedTo = result.getMovedToUrl();
            if (movedTo != null && isRedirect(result.getStatusCode())) {
                result.discardContentIfNotConsumed();
                current = new WebURL();
                current.setURL(movedTo);
                continue;
            }
            return result;
        }
        throw new IOException("Too many redirects for " + startUrl.getURL());
    }

    private static boolean isRedirect(int statusCode) {
        return statusCode == HttpStatus.SC_MOVED_PERMANENTLY
                || statusCode == HttpStatus.SC_MOVED_TEMPORARILY
                || statusCode == HttpStatus.SC_MULTIPLE_CHOICES
                || statusCode == HttpStatus.SC_SEE_OTHER
                || statusCode == HttpStatus.SC_TEMPORARY_REDIRECT
                || statusCode == 308;
    }

    private static String toHtml(Page page) {
        if (page.getContentData() == null) {
            return "";
        }
        if (page.getContentCharset() == null) {
            return new String(page.getContentData(), StandardCharsets.UTF_8);
        }
        try {
            return new String(page.getContentData(), page.getContentCharset());
        } catch (Exception e) {
            return new String(page.getContentData(), StandardCharsets.UTF_8);
        }
    }
}
