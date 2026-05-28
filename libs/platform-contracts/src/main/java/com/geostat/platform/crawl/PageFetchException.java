package com.geostat.platform.crawl;

/** Thrown on unrecoverable fetch failure. */
public class PageFetchException extends RuntimeException {

    public PageFetchException(String url, String reason, Throwable cause) {
        super("Failed to fetch [" + url + "]: " + reason, cause);
    }
}
