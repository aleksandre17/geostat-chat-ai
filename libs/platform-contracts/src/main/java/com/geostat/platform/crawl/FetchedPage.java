package com.geostat.platform.crawl;

/**
 * Result of fetching a single URL.
 * Contains the raw HTML and metadata about how it was fetched.
 */
public record FetchedPage(
        String url,
        String html,
        int httpStatus,
        String contentType,
        RenderMode renderMode) {}
