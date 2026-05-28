package com.geostat.ingestion.parse.profile;

/** Crawl throughput limits from ops/config/corpus/*-policy.yaml (synced to DB policy JSON). */
public record CorpusCrawlLimits(
        Integer workerThreads,
        Integer crawlDelay,
        Integer rateLimitMs) {

    public static final CorpusCrawlLimits EMPTY = new CorpusCrawlLimits(null, null, null);

    public boolean isEmpty() {
        return workerThreads == null && crawlDelay == null && rateLimitMs == null;
    }
}
