package com.geostat.platform.crawl;

import java.util.List;

/**
 * Port: discovers all configured corpora and orchestrates their crawl runs.
 *
 * <p>Implementations auto-discover corpus configurations from a directory,
 * create one {@link CrawlJob} per corpus, and execute them with configurable concurrency.
 *
 * <p>Adding a new corpus requires only a new {@code *-policy.yaml} file —
 * no code changes.
 */
public interface CrawlOrchestrator {

    /**
     * Discover all corpus configurations from the configured directory.
     * Returns one {@link CrawlJob} per {@code *-policy.yaml} file found.
     */
    List<CrawlJob> discoverJobs();

    /**
     * Execute all jobs with configured concurrency.
     * Each job runs independently; one failure does not abort others.
     *
     * @param jobs list from {@link #discoverJobs()}
     */
    void executeAll(List<CrawlJob> jobs);

    /**
     * Execute a single corpus crawl job.
     * Used for manual trigger via REST API or scheduled job.
     */
    void executeSingle(CrawlJob job);
}
