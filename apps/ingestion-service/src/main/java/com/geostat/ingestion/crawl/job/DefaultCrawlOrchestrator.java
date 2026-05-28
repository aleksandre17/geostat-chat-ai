package com.geostat.ingestion.crawl.job;

import com.geostat.ingestion.parse.profile.CorpusConfigurationLoader;
import com.geostat.ingestion.persistence.repository.CorpusRepository;
import com.geostat.platform.contracts.ingestion.IngestionJobRequest;
import com.geostat.platform.contracts.ingestion.IngestionJobStatus;
import com.geostat.platform.crawl.CrawlJob;
import com.geostat.platform.crawl.CrawlOrchestrator;
import com.geostat.platform.crawl.RenderMode;
import com.geostat.platform.parse.CorpusPolicyV2;
import com.geostat.platform.parse.ParseProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Default orchestrator that auto-discovers corpus configurations and runs
 * each corpus crawl in a bounded thread pool.
 *
 * <p>Adding a new corpus: create {@code ops/config/corpus/{name}-policy.yaml}
 * and register the corpus in DB ({@code ingestion.corpus} table).
 * Zero code changes required.
 */
@Component
@Profile("db")
public class DefaultCrawlOrchestrator implements CrawlOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(DefaultCrawlOrchestrator.class);

    private static final Set<String> TERMINAL_STATES = Set.of("completed", "failed", "cancelled");

    private final CorpusConfigurationLoader configLoader;
    private final CorpusRepository corpusRepository;
    private final CrawlJobService crawlJobService;
    private final CrawlProperties props;

    public DefaultCrawlOrchestrator(
            CorpusConfigurationLoader configLoader,
            CorpusRepository corpusRepository,
            CrawlJobService crawlJobService,
            CrawlProperties props) {
        this.configLoader = configLoader;
        this.corpusRepository = corpusRepository;
        this.crawlJobService = crawlJobService;
        this.props = props;
    }

    @Override
    public List<CrawlJob> discoverJobs() {
        List<CorpusPolicyV2> policies = configLoader.loadAllPolicies();
        List<CrawlJob> jobs = new ArrayList<>(policies.size());
        for (CorpusPolicyV2 policy : policies) {
            String name = policy.corpus();
            corpusRepository.findByName(name).ifPresentOrElse(
                    entity -> {
                        UUID corpusId = entity.getId();
                        ParseProfile profile = configLoader.parseProfileFor(name);
                        jobs.add(new CrawlJob(name, corpusId, policy, profile));
                        log.info("[orchestrator] discovered corpus: {}", name);
                    },
                    () -> log.warn(
                            "[orchestrator] corpus '{}' has policy YAML but no DB row — skipped", name));
        }
        return List.copyOf(jobs);
    }

    @Override
    public void executeAll(List<CrawlJob> jobs) {
        if (jobs.isEmpty()) {
            log.info("[orchestrator] no corpus jobs to execute");
            return;
        }
        int threads = Math.min(jobs.size(), props.concurrentCorpora());
        log.info("[orchestrator] starting {} corpus crawls with {} threads", jobs.size(), threads);

        ExecutorService pool = Executors.newFixedThreadPool(
                threads, r -> new Thread(r, "corpus-crawl-" + Thread.currentThread().threadId()));
        try {
            List<CompletableFuture<Void>> futures = jobs.stream()
                    .map(job -> CompletableFuture.runAsync(() -> executeSingle(job), pool))
                    .toList();
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                    .get(props.crawlTimeoutHours(), TimeUnit.HOURS);
        } catch (Exception e) {
            log.error("[orchestrator] corpus crawl batch failed or timed out: {}", e.getMessage(), e);
        } finally {
            pool.shutdownNow();
        }
        log.info("[orchestrator] all corpus crawls finished");
    }

    @Override
    public void executeSingle(CrawlJob job) {
        log.info("[orchestrator] starting crawl for corpus: {}", job.corpusName());
        RenderMode renderMode = job.profile() != null ? job.profile().renderMode() : null;
        IngestionJobRequest request = new IngestionJobRequest(job.corpusName(), null, false, renderMode);
        IngestionJobStatus status = crawlJobService.startJob(request);
        UUID runId = UUID.fromString(status.jobId());
        waitForTerminalRun(job.corpusName(), runId);
    }

    private void waitForTerminalRun(String corpusName, UUID runId) {
        long deadlineNanos = System.nanoTime() + TimeUnit.HOURS.toNanos(props.crawlTimeoutHours());
        long pollMs = props.statusPollIntervalMs();

        while (System.nanoTime() < deadlineNanos) {
            IngestionJobStatus status = crawlJobService.getJobStatus(runId);
            if (TERMINAL_STATES.contains(status.state())) {
                log.info(
                        "[orchestrator] corpus {} run {} finished: {} ({})",
                        corpusName,
                        runId,
                        status.state(),
                        status.message());
                return;
            }
            log.info(
                    "[orchestrator] corpus {} run {} in progress: {}",
                    corpusName,
                    runId,
                    status.state());
            try {
                Thread.sleep(pollMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("[orchestrator] interrupted waiting for corpus {} run {}", corpusName, runId);
                return;
            }
        }
        log.error(
                "[orchestrator] corpus {} run {} timed out after {} hours",
                corpusName,
                runId,
                props.crawlTimeoutHours());
    }
}
