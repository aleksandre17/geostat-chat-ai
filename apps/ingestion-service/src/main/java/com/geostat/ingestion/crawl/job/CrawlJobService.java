package com.geostat.ingestion.crawl.job;

import com.geostat.ingestion.crawl.frontier.FrontierResumeService;
import com.geostat.ingestion.crawl.frontier.UrlHasher;
import com.geostat.ingestion.crawl.runner.CrawlRunner;
import com.geostat.ingestion.persistence.entity.CorpusEntity;
import com.geostat.ingestion.persistence.entity.CrawlRunEntity;
import com.geostat.ingestion.persistence.entity.UrlFrontierEntity;
import com.geostat.ingestion.persistence.model.CrawlRunStatus;
import com.geostat.ingestion.persistence.model.FrontierStatus;
import com.geostat.ingestion.persistence.repository.CorpusRepository;
import com.geostat.ingestion.persistence.repository.CrawlRunRepository;
import com.geostat.ingestion.persistence.repository.UrlFrontierRepository;
import com.geostat.platform.contracts.ingestion.IngestionJobRequest;
import com.geostat.platform.contracts.ingestion.IngestionJobStatus;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

@Service
@Profile("db")
public class CrawlJobService {

    private static final String DEFAULT_CORPUS = "geostat-portal";

    private static final EnumSet<CrawlRunStatus> ACTIVE =
            EnumSet.of(CrawlRunStatus.pending, CrawlRunStatus.running);

    private final CorpusRepository corpusRepository;
    private final CrawlRunRepository crawlRunRepository;
    private final UrlFrontierRepository urlFrontierRepository;
    private final FrontierResumeService frontierResumeService;
    private final CrawlRunner crawlRunner;

    public CrawlJobService(
            CorpusRepository corpusRepository,
            CrawlRunRepository crawlRunRepository,
            UrlFrontierRepository urlFrontierRepository,
            FrontierResumeService frontierResumeService,
            CrawlRunner crawlRunner) {
        this.corpusRepository = corpusRepository;
        this.crawlRunRepository = crawlRunRepository;
        this.urlFrontierRepository = urlFrontierRepository;
        this.frontierResumeService = frontierResumeService;
        this.crawlRunner = crawlRunner;
    }

    @Transactional
    public IngestionJobStatus startJob(IngestionJobRequest request) {
        CorpusEntity corpus = resolveCorpus(request.corpusName());
        if (crawlRunRepository.existsByCorpus_IdAndStatusIn(corpus.getId(), ACTIVE)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "crawl already active for corpus: " + corpus.getName());
        }

        Optional<CrawlRunEntity> previousRun =
                crawlRunRepository.findFirstByCorpus_IdOrderByCreatedAtDesc(corpus.getId());

        CrawlRunEntity run = new CrawlRunEntity();
        run.setCorpus(corpus);
        run.setTriggeredBy("api");
        run.setStatus(CrawlRunStatus.pending);
        run.setConfigSnapshot(configSnapshot(request, corpus.getName()));
        run = crawlRunRepository.save(run);

        if (request.fullRecrawl()) {
            seedFrontier(run, corpus, request);
        } else if (request.seedUrl() != null && !request.seedUrl().isBlank()) {
            seedFrontier(run, corpus, request);
        } else if (!tryResumeFrontier(run, previousRun)) {
            seedFrontier(run, corpus, request);
        }

        scheduleCrawlAfterCommit(run.getId());
        return toStatus(run, "crawl queued");
    }

    @Transactional(readOnly = true)
    public IngestionJobStatus getJob(UUID runId) {
        CrawlRunEntity run = crawlRunRepository.findById(runId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "job not found"));
        return toStatus(run, statsSummary(run));
    }

    private boolean tryResumeFrontier(CrawlRunEntity run, Optional<CrawlRunEntity> previousRun) {
        if (previousRun.isEmpty()) {
            return false;
        }
        int copied = frontierResumeService.copyQueuedFrontier(previousRun.get().getId(), run);
        return copied > 0;
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

    private CorpusEntity resolveCorpus(String corpusName) {
        String name = corpusName == null || corpusName.isBlank() ? DEFAULT_CORPUS : corpusName.trim();
        return corpusRepository.findByName(name)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "corpus not found: " + name));
    }

    private void seedFrontier(CrawlRunEntity run, CorpusEntity corpus, IngestionJobRequest request) {
        List<String> seeds = new ArrayList<>();
        if (request.seedUrl() != null && !request.seedUrl().isBlank()) {
            seeds.add(request.seedUrl().trim());
        } else {
            seeds.addAll(corpus.getSeedUrls());
        }
        if (seeds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "no seed URLs for corpus");
        }
        for (String seed : seeds) {
            enqueue(run, seed, 0, null);
        }
    }

    private void enqueue(CrawlRunEntity run, String url, int depth, String parentUrl) {
        String hash = UrlHasher.hash(url);
        if (urlFrontierRepository.existsByCrawlRun_IdAndUrlHash(run.getId(), hash)) {
            return;
        }
        UrlFrontierEntity frontier = new UrlFrontierEntity();
        frontier.setCrawlRun(run);
        frontier.setUrl(url);
        frontier.setUrlHash(hash);
        frontier.setDepth(depth);
        frontier.setParentUrl(parentUrl);
        frontier.setStatus(FrontierStatus.queued);
        frontier.setAttemptCount(0);
        urlFrontierRepository.save(frontier);
    }

    private static Map<String, Object> configSnapshot(IngestionJobRequest request, String corpusName) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("corpusName", corpusName);
        if (request.seedUrl() != null) {
            snapshot.put("seedUrl", request.seedUrl());
        }
        snapshot.put("fullRecrawl", request.fullRecrawl());
        return snapshot;
    }

    private static IngestionJobStatus toStatus(CrawlRunEntity run, String message) {
        return new IngestionJobStatus(run.getId().toString(), run.getStatus().name(), message);
    }

    private static String statsSummary(CrawlRunEntity run) {
        Map<String, Object> stats = run.getStats();
        if (stats == null || stats.isEmpty()) {
            return "no stats yet";
        }
        return stats.toString();
    }
}
