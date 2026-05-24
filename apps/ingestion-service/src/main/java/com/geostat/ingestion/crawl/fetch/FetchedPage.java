package com.geostat.ingestion.crawl.fetch;

import java.time.Instant;
import org.jsoup.nodes.Document;

public record FetchedPage(
        String url,
        int statusCode,
        Document html,
        String httpEtag,
        Instant lastModified) {

    public FetchedPage(String url, int statusCode, Document html) {
        this(url, statusCode, html, null, null);
    }
}
