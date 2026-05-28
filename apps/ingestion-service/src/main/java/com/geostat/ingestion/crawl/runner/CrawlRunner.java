package com.geostat.ingestion.crawl.runner;

import com.geostat.ingestion.crawl.event.CrawlCompletionEvent;
import com.geostat.ingestion.crawl.fetch.FetchedPage;
import com.geostat.ingestion.crawl.job.CrawlContinuationService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher eventPublisher;

    public CrawlRunner(
            CrawlRunStore crawlRunStore,
            @Lazy CrawlContinuationService crawlContinuationService,
            ApplicationEventPublisher eventPublisher) {
        this.crawlRunStore = crawlRunStore;
        this.crawlContinuationService = crawlContinuationService;
        this.eventPublisher = eventPublisher;
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

        int workers = config.workerThreads();
        ExecutorService pool = Executors.newFixedThreadPool(
                workers, r -> new Thread(r, "crawl-worker-" + runId.toString().substring(0, 8)));

        Semaphore domainRateLimiter = config.rateLimitMs() > 0 ? new Semaphore(1) : null;
        ScheduledExecutorService rateClock = config.rateLimitMs() > 0
                ? Executors.newSingleThreadScheduledExecutor(
                        r -> new Thread(r, "crawl-rate-" + runId.toString().substring(0, 8)))
                : null;

        AtomicInteger pagesFetched = new AtomicInteger(0);
        AtomicInteger linksDiscovered = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);

        try {
            while (pagesFetched.get() < config.maxPages()) {
                List<UUID> batch = crawlRunStore.nextQueuedFrontierIds(runId);
                if (batch.isEmpty()) {
                    break;
                }

                List<CompletableFuture<Void>> futures = batch.stream()
                        .filter(ignored -> pagesFetched.get() < config.maxPages())
                        .map(frontierId -> CompletableFuture.runAsync(
                                () -> {
                                    try {
                                        if (domainRateLimiter != null) {
                                            domainRateLimiter.acquire();
                                        }
                                        try {
                                            Optional<FetchedPage> htmlPage =
                                                    crawlRunStore.fetchHtml(frontierId, runId, config);
                                            if (htmlPage.isEmpty()) {
                                                return;
                                            }

                                            CrawlRunStore.PersistedPage persisted =
                                                    crawlRunStore.persistFetched(
                                                            frontierId, runId, htmlPage.get(), config);

                                            crawlRunStore.indexPersistedPage(persisted);
                                            linksDiscovered.addAndGet(persisted.linksDiscovered());
                                            pagesFetched.incrementAndGet();
                                        } finally {
                                            if (domainRateLimiter != null && rateClock != null) {
                                                rateClock.schedule(
                                                        () -> domainRateLimiter.release(),
                                                        config.rateLimitMs(),
                                                        TimeUnit.MILLISECONDS);
                                            }
                                        }
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                    } catch (Exception e) {
                                        log.warn("fetch failed frontier={}: {}", frontierId, e.getMessage());
                                        crawlRunStore.markFrontierFailed(frontierId, e.getMessage());
                                        failures.incrementAndGet();
                                    }
                                },
                                pool))
                        .toList();

                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            }
        } finally {
            pool.shutdown();
            pool.awaitTermination(60, TimeUnit.SECONDS);
            if (rateClock != null) {
                rateClock.shutdown();
                rateClock.awaitTermination(5, TimeUnit.SECONDS);
            }
        }

        long queuedRemaining = crawlRunStore.countQueuedFrontier(runId);
        crawlRunStore.markCompleted(
                runId, pagesFetched.get(), linksDiscovered.get(), failures.get(), queuedRemaining);
        crawlRunStore.resolveDocumentLinkTargets(config.corpusId());
        eventPublisher.publishEvent(
                new CrawlCompletionEvent(runId, config.corpusId(), config.corpusName()));
        try {
            crawlContinuationService.scheduleContinuationIfNeeded(runId, queuedRemaining);
        } catch (Exception e) {
            log.warn("continuation scheduling failed for run {}: {}", runId, e.getMessage());
        }
    }
}
