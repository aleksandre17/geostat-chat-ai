package com.geostat.ingestion.crawl.fetch;

/** HTTP 304 — stored validators still valid; body unchanged. */
public class PageNotModifiedException extends Exception {

    private final String url;

    public PageNotModifiedException(String url) {
        super("Not modified: " + url);
        this.url = url;
    }

    public String url() {
        return url;
    }
}
