package com.geostat.ingestion.crawl.job;

import com.geostat.ingestion.crawl.frontier.FrontierResumeService;
import com.geostat.ingestion.crawl.policy.CorpusPolicy;
import com.geostat.ingestion.crawl.runner.CrawlRunner;
import com.geostat.ingestion.persistence.entity.CorpusEntity;
import com.geostat.ingestion.persistence.entity.CrawlRunEntity;
import com.geostat.ingestion.persistence.model.CrawlRunStatus;
import com.geostat.ingestion.persistence.repository.CrawlRunRepository;
import java.util.EnumSet;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Schedules continuation runs when frontier remains queued (B-26). */
@Service
@Profile("db")
public class CrawlContinuationService {

    private static final Logger log = LoggerFactory.getLogger(CrawlContinuationService.class);

    private static final EnumSet<CrawlRunStatus> ACTIVE =
            EnumSet.of(CrawlRunStatus.pending, CrawlRunStatus.running);

    private final CrawlRunRepository crawlRunRepository;
    private final FrontierResumeService frontierResumeService;
    private final CrawlRunner crawlRunner;

    public CrawlContinuationService(
            CrawlRunRepository crawlRunRepository,
            FrontierResumeService frontierResumeService,
            CrawlRunner crawlRunner) {
        this.crawlRunRepository = crawlRunRepository;
        this.frontierResumeService = frontierResumeService;
        this.crawlRunner = crawlRunner;
    }

    @Transactional(readOnly = true)
    public void scheduleContinuationIfNeeded(UUID completedRunId, long queuedRemaining) {
        if (queuedRemaining <= 0) {
            return;
        }
        CrawlRunEntity completed = crawlRunRepository.findById(completedRunId).orElse(null);
        if (completed == null) {
            return;
        }
        CorpusEntity corpus = completed.getCorpus();
        if (!CorpusPolicy.autoContinue(corpus)) {
            log.info(
                    "crawl run {} finished with {} queued URLs; autoContinue disabled for corpus {}",
                    completedRunId,
                    queuedRemaining,
                    corpus.getName());
            return;
        }
        try {
            startContinuation(corpus, completedRunId);
        } catch (Exception e) {
            log.warn("failed to schedule crawl continuation for run {}: {}", completedRunId, e.getMessage());
        }
    }

    @Transactional
    public UUID startContinuation(CorpusEntity corpus, UUID sourceRunId) {
        if (crawlRunRepository.existsByCorpus_IdAndStatusIn(corpus.getId(), ACTIVE)) {
            log.debug("skip continuation for corpus {} — active run exists", corpus.getName());
            return null;
        }
        long queued = frontierResumeService.countQueued(sourceRunId);
        if (queued == 0) {
            return null;
        }

        CrawlRunEntity run = new CrawlRunEntity();
        run.setCorpus(corpus);
        run.setTriggeredBy("continuation");
        run.setStatus(CrawlRunStatus.pending);
        run = crawlRunRepository.save(run);

        int copied = frontierResumeService.copyQueuedFrontier(sourceRunId, run);
        if (copied == 0) {
            run.setStatus(CrawlRunStatus.cancelled);
            crawlRunRepository.save(run);
            return null;
        }

        log.info("continuation run {} for corpus {} — copied {} queued URLs from {}", run.getId(), corpus.getName(), copied, sourceRunId);
        scheduleCrawlAfterCommit(run.getId());
        return run.getId();
    }

    private void scheduleCrawlAfterCommit(UUID runId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    crawlRunner.executeAsync(runId);
                }
            });
            return;
        }
        crawlRunner.executeAsync(runId);
    }
}
