package com.geostat.ingestion.crawl.job;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for multi-corpus parallel crawl orchestration.
 * All values configurable via environment variables — no hardcodes.
 */
@ConfigurationProperties(prefix = "geostat.ingestion.crawl")
public record CrawlProperties(
        int concurrentCorpora,
        int crawlTimeoutHours,
        long statusPollIntervalMs) {

    public CrawlProperties {
        if (concurrentCorpora <= 0) concurrentCorpora = 2;
        if (crawlTimeoutHours <= 0) crawlTimeoutHours = 6;
        if (statusPollIntervalMs <= 0) statusPollIntervalMs = 10_000L;
    }
}
