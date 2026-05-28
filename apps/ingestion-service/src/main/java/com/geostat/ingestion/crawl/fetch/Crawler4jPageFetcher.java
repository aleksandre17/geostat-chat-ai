package com.geostat.ingestion.crawl.fetch;



import com.geostat.ingestion.crawl.EncodingMismatchDetector;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;

import org.springframework.stereotype.Component;



@Component

@Profile("db")

public class Crawler4jPageFetcher {

    private static final Logger log = LoggerFactory.getLogger(Crawler4jPageFetcher.class);

    private static final int MAX_REDIRECTS = 5;



    private final CrawlFetchInfrastructure infrastructure;

    private final RoutingUrlFilter routingUrlFilter;

    private final ConditionalHttpFetcher conditionalHttpFetcher;



    public Crawler4jPageFetcher(
            CrawlFetchInfrastructure infrastructure,
            RoutingUrlFilter routingUrlFilter,
            ConditionalHttpFetcher conditionalHttpFetcher) {

        this.infrastructure = infrastructure;

        this.routingUrlFilter = routingUrlFilter;

        this.conditionalHttpFetcher = conditionalHttpFetcher;

    }



    /** Conditional GET when stored validators exist (freshness refresh or re-crawl). */

    public FetchedPage fetchConditional(String url, CorpusEntity corpus, FetchOptions options)

            throws IOException, InterruptedException, RobotsBlockedException, PolicyBlockedException {

        return fetch(url, corpus, options);

    }



    public FetchedPage fetch(String url, CorpusEntity corpus)

            throws IOException, InterruptedException, RobotsBlockedException, PolicyBlockedException {

        return fetch(url, corpus, FetchOptions.none());

    }



    public FetchedPage fetch(String url, CorpusEntity corpus, FetchOptions options)

            throws IOException, InterruptedException, RobotsBlockedException, PolicyBlockedException {

        if (!routingUrlFilter.shouldEnqueue(url, corpus)) {

            throw new PolicyBlockedException(url);

        }

        WebURL webUrl = new WebURL();

        webUrl.setURL(url);

        if (CorpusPolicy.respectRobotsTxt(corpus) && !infrastructure.robotsServer().allows(webUrl)) {

            throw new RobotsBlockedException(url);

        }

        if (options.hasConditionals()) {

            return conditionalHttpFetcher.fetch(url, CrawlFetchInfrastructure.USER_AGENT, options);

        }



        RedirectFetchResult redirectFetch = fetchWithRedirects(webUrl);

        int statusCode = redirectFetch.result().getStatusCode();

        if (statusCode < 200 || statusCode >= 300) {

            throw new IOException("HTTP " + statusCode + " for " + url);

        }



        WebURL finalWebUrl = redirectFetch.finalWebUrl();

        Page page = new Page(finalWebUrl);

        redirectFetch.result().fetchContent(page, 10 * 1024 * 1024);

        String html = toHtml(page);
        String fetchUrl = finalWebUrl.getURL();
        boolean encodingIssue = false;
        if (EncodingMismatchDetector.looksCorrupted(fetchUrl, html)) {
            String contentType = HttpResponseHeaders.contentType(redirectFetch.result());
            log.warn(
                    "[encoding] Possible encoding mismatch: /ka/ page with no Georgian chars. url={} Content-Type={}",
                    fetchUrl,
                    contentType);
            // Document will be REJECTED by LanguageConsistencyValidator — that's correct.
            // This WARN allows operators to investigate the server config.
            encodingIssue = true;
        }

        Document document = Jsoup.parse(html, fetchUrl);
        String etagHttp = HttpResponseHeaders.etag(redirectFetch.result());

        String lastModifiedHttp = HttpResponseHeaders.lastModifiedHttp(redirectFetch.result());

        return new FetchedPage(

                url,

                fetchUrl,

                statusCode,

                document,

                etagHttp,

                HttpResponseHeaders.lastModified(redirectFetch.result()),

                lastModifiedHttp,

                etagHttp,

                encodingIssue);

    }



    private record RedirectFetchResult(PageFetchResult result, WebURL finalWebUrl) {}



    private RedirectFetchResult fetchWithRedirects(WebURL startUrl)

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

            return new RedirectFetchResult(result, current);

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

