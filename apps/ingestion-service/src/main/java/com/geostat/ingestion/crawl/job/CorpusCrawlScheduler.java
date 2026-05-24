package com.geostat.ingestion.crawl.job;

import com.geostat.ingestion.config.IngestionProperties;
import com.geostat.ingestion.crawl.frontier.FrontierResumeService;
import com.geostat.ingestion.crawl.policy.CorpusPolicy;
import com.geostat.ingestion.quality.DocumentFreshnessRefreshService;
import com.geostat.ingestion.persistence.entity.CorpusEntity;
import com.geostat.ingestion.persistence.entity.CrawlRunEntity;
import com.geostat.ingestion.persistence.model.CorpusStatus;
import com.geostat.ingestion.persistence.model.CrawlRunStatus;
import com.geostat.ingestion.persistence.repository.CorpusRepository;
import com.geostat.ingestion.persistence.repository.CrawlRunRepository;
import com.geostat.platform.contracts.ingestion.IngestionJobRequest;
import com.geostat.platform.contracts.ingestion.IngestionJobStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodic crawl + continuation for active corpora (B-26 / Q-09). */
@Component
@Profile("db")
@ConditionalOnProperty(prefix = "geostat.ingestion.scheduler", name = "enabled", havingValue = "true")
public class CorpusCrawlScheduler {

    private static final Logger log = LoggerFactory.getLogger(CorpusCrawlScheduler.class);

    private static final EnumSet<CrawlRunStatus> ACTIVE =
            EnumSet.of(CrawlRunStatus.pending, CrawlRunStatus.running);

    private final CorpusRepository corpusRepository;
    private final CrawlRunRepository crawlRunRepository;
    private final CrawlJobService crawlJobService;
    private final CrawlContinuationService crawlContinuationService;
    private final FrontierResumeService frontierResumeService;
    private final DocumentFreshnessRefreshService freshnessRefreshService;
    private final IngestionProperties properties;

    public CorpusCrawlScheduler(
            CorpusRepository corpusRepository,
            CrawlRunRepository crawlRunRepository,
            CrawlJobService crawlJobService,
            CrawlContinuationService crawlContinuationService,
            FrontierResumeService frontierResumeService,
            DocumentFreshnessRefreshService freshnessRefreshService,
            IngestionProperties properties) {
        this.corpusRepository = corpusRepository;
        this.crawlRunRepository = crawlRunRepository;
        this.crawlJobService = crawlJobService;
        this.crawlContinuationService = crawlContinuationService;
        this.frontierResumeService = frontierResumeService;
        this.freshnessRefreshService = freshnessRefreshService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${geostat.ingestion.scheduler.fixed-delay-ms:3600000}")
    void tick() {
        List<CorpusEntity> corpora = corpusRepository.findAll().stream()
                .filter(c -> c.getStatus() == CorpusStatus.active)
                .toList();
        for (CorpusEntity corpus : corpora) {
            try {
                tickCorpus(corpus);
            } catch (Exception e) {
                log.warn("scheduler tick failed for corpus {}: {}", corpus.getName(), e.getMessage());
            }
        }
    }

    private void tickCorpus(CorpusEntity corpus) {
        if (crawlRunRepository.existsByCorpus_IdAndStatusIn(corpus.getId(), ACTIVE)) {
            return;
        }
        if (properties.freshness().enabled()) {
            try {
                var report = freshnessRefreshService.refreshStale(
                        corpus.getName(), properties.freshness().batchSize());
                if (report.candidates() > 0) {
                    log.info(
                            "freshness refresh corpus={} candidates={} updated={} notModified={}",
                            corpus.getName(),
                            report.candidates(),
                            report.updated(),
                            report.notModified());
                }
            } catch (Exception e) {
                log.warn("freshness refresh failed for {}: {}", corpus.getName(), e.getMessage());
            }
        }
        Optional<CrawlRunEntity> latest = crawlRunRepository.findFirstByCorpus_IdOrderByCreatedAtDesc(corpus.getId());
        if (latest.isPresent()) {
            long queued = frontierResumeService.countQueued(latest.get().getId());
            if (queued > 0 && CorpusPolicy.autoContinue(corpus)) {
                UUID continued = crawlContinuationService.startContinuation(corpus, latest.get().getId());
                if (continued != null) {
                    log.info("scheduler started continuation {} for corpus {}", continued, corpus.getName());
                }
                return;
            }
        }
        if (!shouldStartPeriodicCrawl(corpus, latest.orElse(null))) {
            return;
        }
        IngestionJobStatus status = crawlJobService.startJob(
                new IngestionJobRequest(corpus.getName(), null, false));
        log.info("scheduler started crawl job {} for corpus {}", status.jobId(), corpus.getName());
    }

    private boolean shouldStartPeriodicCrawl(CorpusEntity corpus, CrawlRunEntity latest) {
        if (latest == null) {
            return true;
        }
        if (latest.getStatus() == CrawlRunStatus.failed) {
            return true;
        }
        Instant finished = latest.getFinishedAt();
        if (finished == null) {
            return false;
        }
        long delayMs = properties.scheduler().fixedDelayMs();
        return Duration.between(finished, Instant.now()).toMillis() >= delayMs;
    }
}
