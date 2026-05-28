package com.geostat.ingestion.crawl.fetch;

/** Conditional GET headers for re-crawl (If-None-Match preferred over If-Modified-Since). */
public record FetchOptions(String ifModifiedSince, String ifNoneMatch) {

    public static FetchOptions none() {
        return new FetchOptions(null, null);
    }

    public static FetchOptions of(String etagHttp, String lastModifiedHttp) {
        return new FetchOptions(lastModifiedHttp, etagHttp);
    }

    public boolean hasConditionals() {
        return (ifNoneMatch != null && !ifNoneMatch.isBlank())
                || (ifModifiedSince != null && !ifModifiedSince.isBlank());
    }
}
