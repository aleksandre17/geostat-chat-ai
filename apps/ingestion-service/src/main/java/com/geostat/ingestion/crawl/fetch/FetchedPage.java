package com.geostat.ingestion.crawl.fetch;



import java.time.Instant;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;



public record FetchedPage(

        String url,

        String finalUrl,

        int statusCode,

        Document html,

        String httpEtag,

        Instant lastModified,

        String lastModifiedHttp,

        String etagHttp,

        boolean encodingIssue) {



    public FetchedPage(String url, int statusCode, Document html) {

        this(url, null, statusCode, html, null, null, null, null, false);

    }



    public FetchedPage(String url, int statusCode, Document html, String httpEtag, Instant lastModified) {

        this(url, null, statusCode, html, httpEtag, lastModified, null, httpEtag, false);

    }



    /** Maps a platform-contract fetch result into the internal Jsoup-based record. */
    public static FetchedPage fromPlatform(String requestedUrl, com.geostat.platform.crawl.FetchedPage platform) {
        if (platform.httpStatus() == 304) {
            return new FetchedPage(requestedUrl, platform.url(), 304, null, null, null, null, null, false);
        }
        String html = platform.html() != null ? platform.html() : "";
        Document document = Jsoup.parse(html, requestedUrl);
        String resolvedUrl = platform.url();
        String finalUrl = resolvedUrl != null && !resolvedUrl.equals(requestedUrl) ? resolvedUrl : null;
        return new FetchedPage(
                requestedUrl, finalUrl, platform.httpStatus(), document, null, null, null, null, false);
    }

    /** True if server confirmed content is unchanged (304 Not Modified). */

    public boolean notModified() {

        return statusCode == 304;

    }



    /** Returns finalUrl when a redirect occurred, otherwise the originally requested url. */

    public String canonicalUrl() {

        return finalUrl != null && !finalUrl.equals(url) ? finalUrl : url;

    }

}

