package com.geostat.ingestion.api;

import com.geostat.ingestion.api.dto.FrontierEnqueueRequest;
import com.geostat.ingestion.enrichment.authority.AuthorityRecomputeService;
import com.geostat.ingestion.parse.frontier.FrontierEnqueueService;
import com.geostat.ingestion.parse.quality.ParseQualityService;
import com.geostat.ingestion.parse.reparse.CorpusReparseAlreadyRunningException;
import com.geostat.ingestion.parse.reparse.CorpusReparseProgress;
import com.geostat.ingestion.parse.reparse.CorpusReparseService;
import com.geostat.ingestion.enrichment.runner.EnrichmentBackfillAlreadyRunningException;
import com.geostat.ingestion.enrichment.runner.EnrichmentBackfillProgress;
import com.geostat.ingestion.enrichment.runner.EnrichmentBackfillService;
import com.geostat.ingestion.enrichment.topic.TopicRemineService;
import com.geostat.platform.enrichment.TopicMiningReport;
import com.geostat.ingestion.persistence.entity.CorpusEntity;
import com.geostat.ingestion.persistence.repository.CorpusRepository;
import com.geostat.ingestion.persistence.repository.EvaluationQueryRepository;
import com.geostat.ingestion.persistence.entity.EvaluationQueryEntity;
import com.geostat.ingestion.quality.CorpusQualityAuditor;
import com.geostat.ingestion.quality.CorpusQualityReport;
import com.geostat.ingestion.catalog.readiness.DerivationReadinessService;
import com.geostat.ingestion.catalog.readiness.DerivationReadinessService.DerivationReadinessReport;
import com.geostat.ingestion.quality.CorpusLifecycleSyncService;
import com.geostat.ingestion.quality.CorpusLifecycleSyncService.CorpusLifecycleSyncReport;
import com.geostat.ingestion.quality.CorpusReindexReport;
import com.geostat.ingestion.quality.CorpusReindexService;
import com.geostat.ingestion.quality.DocumentFreshnessRefreshService;
import com.geostat.ingestion.quality.DocumentFreshnessRefreshService.FreshnessRefreshReport;
import com.geostat.ingestion.quality.PlaywrightRefetchService;
import com.geostat.ingestion.quality.PlaywrightRefetchService.PlaywrightRefetchReport;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Profile("db")
@RequestMapping("/api/v1/ingestion/corpora")
public class CorpusController {

    private final CorpusRepository corpusRepository;
    private final CorpusQualityAuditor qualityAuditor;
    private final CorpusReindexService reindexService;
    private final CorpusLifecycleSyncService corpusLifecycleSyncService;
    private final DerivationReadinessService derivationReadinessService;
    private final EvaluationQueryRepository evaluationQueryRepository;
    private final PlaywrightRefetchService playwrightRefetchService;
    private final DocumentFreshnessRefreshService freshnessRefreshService;
    private final Optional<AuthorityRecomputeService> authorityRecomputeService;
    private final Optional<TopicRemineService> topicRemineService;
    private final Optional<EnrichmentBackfillService> enrichmentBackfillService;
    private final FrontierEnqueueService frontierEnqueueService;
    private final CorpusReparseService corpusReparseService;
    private final ParseQualityService parseQualityService;

    public CorpusController(
            CorpusRepository corpusRepository,
            CorpusQualityAuditor qualityAuditor,
            CorpusReindexService reindexService,
            CorpusLifecycleSyncService corpusLifecycleSyncService,
            DerivationReadinessService derivationReadinessService,
            EvaluationQueryRepository evaluationQueryRepository,
            PlaywrightRefetchService playwrightRefetchService,
            DocumentFreshnessRefreshService freshnessRefreshService,
            ObjectProvider<AuthorityRecomputeService> authorityRecomputeService,
            ObjectProvider<TopicRemineService> topicRemineService,
            ObjectProvider<EnrichmentBackfillService> enrichmentBackfillService,
            FrontierEnqueueService frontierEnqueueService,
            CorpusReparseService corpusReparseService,
            ParseQualityService parseQualityService) {
        this.corpusRepository = corpusRepository;
        this.qualityAuditor = qualityAuditor;
        this.reindexService = reindexService;
        this.corpusLifecycleSyncService = corpusLifecycleSyncService;
        this.derivationReadinessService = derivationReadinessService;
        this.evaluationQueryRepository = evaluationQueryRepository;
        this.playwrightRefetchService = playwrightRefetchService;
        this.freshnessRefreshService = freshnessRefreshService;
        this.authorityRecomputeService = Optional.ofNullable(authorityRecomputeService.getIfAvailable());
        this.topicRemineService = Optional.ofNullable(topicRemineService.getIfAvailable());
        this.enrichmentBackfillService = Optional.ofNullable(enrichmentBackfillService.getIfAvailable());
        this.frontierEnqueueService = frontierEnqueueService;
        this.corpusReparseService = corpusReparseService;
        this.parseQualityService = parseQualityService;
    }

    @GetMapping
    public List<CorpusSummary> list() {
        return corpusRepository.findAll().stream()
                .map(CorpusSummary::from)
                .toList();
    }

    /** P0 L1 — live metrics aligned to ops/eval/corpus-quality-gate.yaml gate ids. */
    @GetMapping("/{name}/parse-quality")
    public ParseQualityService.ParseQualityReport parseQuality(@PathVariable String name) {
        return parseQualityService.assess(name);
    }

    /** P0 L1 — bulk enqueue curated/manual URLs at depth 0. */
    @PostMapping("/{name}/frontier:enqueue")
    public FrontierEnqueueService.FrontierEnqueueResult frontierEnqueue(
            @PathVariable String name, @RequestBody FrontierEnqueueRequest request) {
        return frontierEnqueueService.enqueue(name, request.urls());
    }

    /** P0 L1 — async reparse using profile extractor (requires parse profile enabled). */
    @PostMapping("/{name}/reparse")
    public CorpusReparseResponse reparse(
            @PathVariable String name,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int limit) {
        try {
            int queued = corpusReparseService.queueReparse(name, limit);
            String status = queued == 0 ? "nothing_to_do" : "accepted";
            return new CorpusReparseResponse(name, status, queued);
        } catch (CorpusReparseAlreadyRunningException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @GetMapping("/{name}/reparse/status")
    public CorpusReparseProgress reparseStatus(@PathVariable String name) {
        CorpusReparseProgress job = corpusReparseService.progress();
        if (job.corpusName() != null && !job.corpusName().equals(name)) {
            return CorpusReparseProgress.idle();
        }
        return job;
    }

    @GetMapping("/{name}/quality")
    public CorpusQualityReport quality(@PathVariable String name) {
        return qualityAuditor.audit(name);
    }

    /** P1-15 — derivation + eval gate readiness (spec section 14 quality gates). */
    @GetMapping("/{name}/derivation-readiness")
    public DerivationReadinessReport derivationReadiness(@PathVariable String name) {
        return derivationReadinessService.assess(name);
    }

    @PostMapping("/{name}/reindex")
    public CorpusReindexReport reindex(@PathVariable String name) {
        return reindexService.reindexParsedDocuments(name);
    }

    /** RAG §17 — sync Qdrant payload metadata (serveState/pageKind/scoreBoost) without re-embedding. */
    @PostMapping("/{name}/lifecycle:sync-qdrant")
    public CorpusLifecycleSyncReport syncQdrantLifecycle(@PathVariable String name) {
        return corpusLifecycleSyncService.syncQdrantMetadata(name);
    }

    /** P3-03b — refetch empty-body URLs via Playwright when audit recommends SPA fetch. */
    @PostMapping("/{name}/playwright-refetch")
    public PlaywrightRefetchReport playwrightRefetch(
            @PathVariable String name,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "10") int limit) {
        return playwrightRefetchService.refetchEmptyBodies(name, limit);
    }

    /** Incremental re-fetch using stored HTTP validators (If-None-Match / If-Modified-Since). */
    @PostMapping("/{name}/freshness-refresh")
    public FreshnessRefreshReport freshnessRefresh(
            @PathVariable String name,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "50") int limit) {
        return freshnessRefreshService.refreshStale(name, limit);
    }

    /** P1 cutover — async enrichment backfill for parsed documents. */
    @PostMapping("/{name}/enrichment:backfill")
    public EnrichmentBackfillResponse enrichmentBackfill(
            @PathVariable String name,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int limit,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "true") boolean onlyMissing) {
        EnrichmentBackfillService service = enrichmentBackfillService.orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_IMPLEMENTED,
                "Enrichment disabled - set geostat.ingestion.enrichment.enabled=true"));
        try {
            int queued = service.queueBackfill(name, limit, onlyMissing);
            String status = queued == 0 ? "nothing_to_do" : "accepted";
            return new EnrichmentBackfillResponse(name, status, queued);
        } catch (EnrichmentBackfillAlreadyRunningException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    /** P1 cutover — in-flight backfill progress (async job state). */
    @GetMapping("/{name}/enrichment/status")
    public EnrichmentBackfillProgress enrichmentStatus(@PathVariable String name) {
        EnrichmentBackfillService service = enrichmentBackfillService.orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_IMPLEMENTED,
                "Enrichment disabled - set geostat.ingestion.enrichment.enabled=true"));
        EnrichmentBackfillProgress job = service.progress();
        if (job.corpusName() != null && !job.corpusName().equals(name)) {
            return EnrichmentBackfillProgress.idle();
        }
        return job;
    }

    /** RAG-U01e — recompute PageRank authority for corpus (idempotent batch). */
    @PostMapping("/{name}/authority:recompute")
    public AuthorityRecomputeResponse authorityRecompute(@PathVariable String name) {
        AuthorityRecomputeService service = authorityRecomputeService.orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_IMPLEMENTED,
                "Enrichment disabled - set geostat.ingestion.enrichment.enabled=true (RAG-U01e)"));
        service.recomputeForCorpusName(name);
        return new AuthorityRecomputeResponse(name, "accepted");
    }

    /** RAG-U01g — remine topic clusters for corpus (batch k-means + Gemini labels). */
    @PostMapping("/{name}/topics:remine")
    public TopicRemineResponse topicRemine(@PathVariable String name) {
        TopicRemineService service = topicRemineService.orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_IMPLEMENTED,
                "Enrichment disabled - set geostat.ingestion.enrichment.enabled=true (RAG-U01g)"));
        TopicMiningReport report = service.remineForCorpusName(name);
        return new TopicRemineResponse(
                name,
                "accepted",
                report.clustersCreated(),
                report.documentsAssigned(),
                report.documentsSkipped());
    }

    @GetMapping("/{name}/evaluation-queries")
    public List<EvaluationQueryView> evaluationQueries(@PathVariable String name) {
        return evaluationQueryRepository.findByCorpusNameAndActiveTrueOrderByLocaleAscQueryTextAsc(name).stream()
                .map(EvaluationQueryView::from)
                .toList();
    }

    public record AuthorityRecomputeResponse(String corpusName, String status) {}

    public record TopicRemineResponse(
            String corpusName,
            String status,
            int clustersCreated,
            int documentsAssigned,
            int documentsSkipped) {}

    public record EnrichmentBackfillResponse(String corpusName, String status, int documentsQueued) {}

    public record CorpusReparseResponse(String corpusName, String status, int documentsQueued) {}

    public record EvaluationQueryView(
            String locale,
            String queryText,
            String expectUrl,
            int minChunks,
            String expectedIntent,
            List<Map<String, Object>> expectedEntities,
            String difficulty,
            String source) {
        static EvaluationQueryView from(EvaluationQueryEntity entity) {
            return new EvaluationQueryView(
                    entity.getLocale(),
                    entity.getQueryText(),
                    entity.getExpectUrl(),
                    entity.getMinChunks(),
                    entity.getExpectedIntent(),
                    List.copyOf(entity.getExpectedEntities()),
                    entity.getDifficulty(),
                    entity.getSource());
        }
    }

    public record CorpusSummary(String name, List<String> seedUrls, String status) {
        static CorpusSummary from(CorpusEntity entity) {
            return new CorpusSummary(
                    entity.getName(),
                    entity.getSeedUrls(),
                    entity.getStatus().name());
        }
    }
}
