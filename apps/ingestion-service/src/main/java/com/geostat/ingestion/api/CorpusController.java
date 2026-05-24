package com.geostat.ingestion.api;

import com.geostat.ingestion.persistence.entity.CorpusEntity;
import com.geostat.ingestion.persistence.repository.CorpusRepository;
import com.geostat.ingestion.persistence.repository.EvaluationQueryRepository;
import com.geostat.ingestion.persistence.entity.EvaluationQueryEntity;
import com.geostat.ingestion.quality.CorpusQualityAuditor;
import com.geostat.ingestion.quality.CorpusQualityReport;
import com.geostat.ingestion.quality.CorpusReindexReport;
import com.geostat.ingestion.quality.CorpusReindexService;
import com.geostat.ingestion.quality.DocumentFreshnessRefreshService;
import com.geostat.ingestion.quality.DocumentFreshnessRefreshService.FreshnessRefreshReport;
import com.geostat.ingestion.quality.PlaywrightRefetchService;
import com.geostat.ingestion.quality.PlaywrightRefetchService.PlaywrightRefetchReport;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("db")
@RequestMapping("/api/v1/ingestion/corpora")
public class CorpusController {

    private final CorpusRepository corpusRepository;
    private final CorpusQualityAuditor qualityAuditor;
    private final CorpusReindexService reindexService;
    private final EvaluationQueryRepository evaluationQueryRepository;
    private final PlaywrightRefetchService playwrightRefetchService;
    private final DocumentFreshnessRefreshService freshnessRefreshService;

    public CorpusController(
            CorpusRepository corpusRepository,
            CorpusQualityAuditor qualityAuditor,
            CorpusReindexService reindexService,
            EvaluationQueryRepository evaluationQueryRepository,
            PlaywrightRefetchService playwrightRefetchService,
            DocumentFreshnessRefreshService freshnessRefreshService) {
        this.corpusRepository = corpusRepository;
        this.qualityAuditor = qualityAuditor;
        this.reindexService = reindexService;
        this.evaluationQueryRepository = evaluationQueryRepository;
        this.playwrightRefetchService = playwrightRefetchService;
        this.freshnessRefreshService = freshnessRefreshService;
    }

    @GetMapping
    public List<CorpusSummary> list() {
        return corpusRepository.findAll().stream()
                .map(CorpusSummary::from)
                .toList();
    }

    @GetMapping("/{name}/quality")
    public CorpusQualityReport quality(@PathVariable String name) {
        return qualityAuditor.audit(name);
    }

    @PostMapping("/{name}/reindex")
    public CorpusReindexReport reindex(@PathVariable String name) {
        return reindexService.reindexParsedDocuments(name);
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

    @GetMapping("/{name}/evaluation-queries")
    public List<EvaluationQueryView> evaluationQueries(@PathVariable String name) {
        return evaluationQueryRepository.findByCorpusNameAndActiveTrueOrderByLocaleAscQueryTextAsc(name).stream()
                .map(EvaluationQueryView::from)
                .toList();
    }

    public record EvaluationQueryView(
            String locale, String queryText, String expectUrl, int minChunks) {
        static EvaluationQueryView from(EvaluationQueryEntity entity) {
            return new EvaluationQueryView(
                    entity.getLocale(),
                    entity.getQueryText(),
                    entity.getExpectUrl(),
                    entity.getMinChunks());
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
