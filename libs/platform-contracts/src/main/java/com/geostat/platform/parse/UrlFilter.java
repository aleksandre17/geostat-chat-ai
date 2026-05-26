package com.geostat.platform.parse;

/** Decides whether a discovered URL should enter the crawl frontier. */
public interface UrlFilter {

    boolean shouldEnqueue(String url, CorpusPolicyV2 policy);
}
