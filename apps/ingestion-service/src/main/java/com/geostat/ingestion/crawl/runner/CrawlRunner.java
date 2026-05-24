package com.geostat.ingestion.crawl.runner;

import com.geostat.ingestion.crawl.job.CrawlContinuationService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Profile("db")
public class CrawlRunner {

    private static final Logger log = LoggerFactory.getLogger(CrawlRunner.class);

    private final CrawlRunStore crawlRunStore;
    private final CrawlContinuationService crawlContinuationService;

    public CrawlRunner(CrawlRunStore crawlRunStore, @Lazy CrawlContinuationService crawlContinuationService) {
        this.crawlRunStore = crawlRunStore;
        this.crawlContinuationService = crawlContinuationService;
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

        long queuedRemaining = crawlRunStore.countQueuedFrontier(runId);
        crawlRunStore.markCompleted(runId, pagesFetched, linksDiscovered, failures, queuedRemaining);
        try {
            crawlContinuationService.scheduleContinuationIfNeeded(runId, queuedRemaining);
        } catch (Exception e) {
            log.warn("continuation scheduling failed for run {}: {}", runId, e.getMessage());
        }
    }
}
