package com.geostat.ingestion.crawl.runner;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Profile("db")
public class CrawlRunner {

    private static final Logger log = LoggerFactory.getLogger(CrawlRunner.class);

    private final CrawlRunStore crawlRunStore;

    public CrawlRunner(CrawlRunStore crawlRunStore) {
        this.crawlRunStore = crawlRunStore;
    }

    @Async
    public void executeAsync(UUID runId) {
        try {
            runCrawl(runId);
        } catch (Exception e) {
            log.error("crawl run {} failed", runId, e);
            crawlRunStore.markFailed(runId, e.getMessage());
        }
    }

    void runCrawl(UUID runId) throws InterruptedException {
        RunConfig config = crawlRunStore.loadRunConfig(runId);
        crawlRunStore.markRunning(runId);

        int pagesFetched = 0;
        int linksDiscovered = 0;
        int failures = 0;

        while (pagesFetched < config.maxPages()) {
            List<UUID> frontierIds = crawlRunStore.nextQueuedFrontierIds(runId);
            if (frontierIds.isEmpty()) {
                break;
            }
            for (UUID frontierId : frontierIds) {
                if (pagesFetched >= config.maxPages()) {
                    break;
                }
                try {
                    Optional<CrawlRunStore.PersistedPage> page =
                            crawlRunStore.processFrontier(frontierId, runId, config);
                    if (page.isPresent()) {
                        crawlRunStore.indexPersistedPage(page.get());
                        linksDiscovered += page.get().linksDiscovered();
                        pagesFetched++;
                    }
                } catch (Exception e) {
                    log.warn("fetch failed for frontier {}: {}", frontierId, e.getMessage());
                    crawlRunStore.markFrontierFailed(frontierId, e.getMessage());
                    failures++;
                }
                if (config.rateLimitMs() > 0) {
                    Thread.sleep(config.rateLimitMs());
                }
            }
        }

        crawlRunStore.markCompleted(runId, pagesFetched, linksDiscovered, failures);
    }
}
