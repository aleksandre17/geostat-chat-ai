package com.geostat.platform.crawl;

/**
 * Port: fetches a web page and returns raw content for parsing.
 * Implementations may use static HTTP (crawler4j), headless browser (Playwright),
 * or a backend API, depending on the corpus render mode.
 */
public interface PageFetcher {

    /**
     * @param url     canonical URL to fetch
     * @param options fetch configuration (timeout, user-agent, render mode)
     * @return        fetched page; never null
     * @throws PageFetchException on unrecoverable fetch failure
     */
    FetchedPage fetch(String url, FetchOptions options) throws PageFetchException;
}
