package com.geostat.ingestion.catalog.readiness;

import com.geostat.ingestion.catalog.AggregationProperties;
import com.geostat.ingestion.catalog.refresh.CatalogViewStatusService;
import com.geostat.ingestion.config.IngestionProperties;
import com.geostat.ingestion.enrichment.runner.EnrichmentProperties;
import com.geostat.ingestion.persistence.repository.CurationOverrideRepository;
import com.geostat.ingestion.persistence.repository.EvaluationQueryRepository;
import com.geostat.ingestion.quality.persistence.CorpusPipelineMetrics;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** P1-15 / spec section 14 — single readiness view before eval gate or derived catalog cutover. */
@Service
@Profile("db")
public class DerivationReadinessService {

    static final int GOLDEN_SET_MIN = 150;
    static final int CURATION_BUDGET_MAX = 50;
    static final double SUMMARY_COVERAGE_MIN = 0.95;
    static final double PAGE_KIND_COVERAGE_MIN = 0.95;
    static final double TOPIC_ASSIGNMENT_MIN = 0.80;

    private final DerivationReadinessReader reader;
    private final EvaluationQueryRepository evaluationQueryRepository;
    private final CurationOverrideRepository curationOverrideRepository;
    private final EnrichmentProperties enrichmentProperties;
    private final AggregationProperties aggregationProperties;
    private final IngestionProperties ingestionProperties;
    private final ObjectProvider<CatalogViewStatusService> catalogViewStatusService;

    public DerivationReadinessService(
            DerivationReadinessReader reader,
            EvaluationQueryRepository evaluationQueryRepository,
            CurationOverrideRepository curationOverrideRepository,
            EnrichmentProperties enrichmentProperties,
            AggregationProperties aggregationProperties,
            IngestionProperties ingestionProperties,
            ObjectProvider<CatalogViewStatusService> catalogViewStatusService) {
        this.reader = reader;
        this.evaluationQueryRepository = evaluationQueryRepository;
        this.curationOverrideRepository = curationOverrideRepository;
        this.enrichmentProperties = enrichmentProperties;
        this.aggregationProperties = aggregationProperties;
        this.ingestionProperties = ingestionProperties;
        this.catalogViewStatusService = catalogViewStatusService;
    }

    public DerivationReadinessReport assess(String corpusName) {
        if (!reader.corpusExists(corpusName)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Corpus not found: " + corpusName);
        }

        long goldenCount =
                evaluationQueryRepository
                        .findByCorpusNameAndActiveTrueOrderByLocaleAscQueryTextAsc(corpusName)
                        .size();
        long curationActive = curationOverrideRepository.countActive(Instant.now());
        DerivationReadinessReader.EnrichmentMetrics enrichment = reader.loadEnrichmentMetrics(corpusName);
        CorpusPipelineMetrics pipeline = reader.loadPipelineMetrics(corpusName);
        DerivationReadinessReader.TopicClusterMetrics clusters = reader.loadTopicClusterMetrics(corpusName);
        long portalLinks = reader.countPortalLinks(corpusName);

        double vectorCoverage = coverage(pipeline.indexedChunks(), pipeline.totalChunks());
        double summaryCoverage = coverage(enrichment.withSummary(), enrichment.parsedDocuments());
        double pageKindCoverage = coverage(enrichment.withPageKind(), enrichment.parsedDocuments());
        double topicCoverage = coverage(enrichment.withTopicCluster(), enrichment.parsedDocuments());

        List<GateCheck> checks = new ArrayList<>();
        checks.add(check(
                "golden_set",
                goldenCount >= GOLDEN_SET_MIN,
                goldenCount + " active queries (min " + GOLDEN_SET_MIN + ")",
                (double) goldenCount,
                (double) GOLDEN_SET_MIN));
        checks.add(check(
                "parsed_corpus",
                enrichment.parsedDocuments() > 0,
                enrichment.parsedDocuments() + " parsed documents",
                (double) enrichment.parsedDocuments(),
                1.0));
        checks.add(check(
                "vector_coverage",
                vectorCoverage >= ingestionProperties.qualityAudit().vectorCoverageMin(),
                percent(vectorCoverage) + " indexed chunks (min "
                        + percent(ingestionProperties.qualityAudit().vectorCoverageMin()) + ")",
                vectorCoverage,
                ingestionProperties.qualityAudit().vectorCoverageMin()));
        checks.add(check(
                "curation_budget",
                curationActive <= CURATION_BUDGET_MAX,
                curationActive + " active overrides (max " + CURATION_BUDGET_MAX + ")",
                (double) curationActive,
                (double) CURATION_BUDGET_MAX,
                true,
                false));

        if (enrichmentProperties.isEnabled()) {
            checks.add(check(
                    "summary_coverage",
                    summaryCoverage >= SUMMARY_COVERAGE_MIN,
                    percent(summaryCoverage) + " with summary (min " + percent(SUMMARY_COVERAGE_MIN) + ")",
                    summaryCoverage,
                    SUMMARY_COVERAGE_MIN));
            checks.add(check(
                    "page_kind_coverage",
                    pageKindCoverage >= PAGE_KIND_COVERAGE_MIN,
                    percent(pageKindCoverage) + " classified page_kind (min "
                            + percent(PAGE_KIND_COVERAGE_MIN) + ")",
                    pageKindCoverage,
                    PAGE_KIND_COVERAGE_MIN));
            checks.add(check(
                    "topic_assignment",
                    topicCoverage >= TOPIC_ASSIGNMENT_MIN,
                    percent(topicCoverage) + " with topic_cluster (min " + percent(TOPIC_ASSIGNMENT_MIN) + ")",
                    topicCoverage,
                    TOPIC_ASSIGNMENT_MIN));
            checks.add(check(
                    "topic_clusters_approved",
                    clusters.approvedClusters() > 0,
                    clusters.approvedClusters() + " approved clusters (pending "
                            + clusters.pendingClusters() + ")",
                    (double) clusters.approvedClusters(),
                    1.0));
        }

        if (aggregationProperties.isEnabled()) {
            checks.add(check(
                    "mv_portal_links",
                    portalLinks > 0,
                    portalLinks + " rows in mv_portal_link",
                    (double) portalLinks,
                    1.0));
            CatalogViewStatusService catalogStatus = catalogViewStatusService.getIfAvailable();
            if (catalogStatus != null) {
                CatalogViewStatusService.CatalogStatusReport mvStatus = catalogStatus.status();
                checks.add(check(
                        "catalog_mv_fresh",
                        !mvStatus.anyStale(),
                        mvStatus.anyStale()
                                ? "catalog MV stale (> "
                                        + mvStatus.staleThresholdHours()
                                        + "h) — POST /catalog:refresh"
                                : "catalog MVs refreshed within "
                                        + mvStatus.staleThresholdHours()
                                        + "h",
                        mvStatus.anyStale() ? 0.0 : 1.0,
                        1.0));
            }
        }

        boolean readyForEvalGate = checks.stream().allMatch(GateCheck::passed);

        return new DerivationReadinessReport(
                corpusName,
                Instant.now(),
                enrichmentProperties.isEnabled(),
                aggregationProperties.isEnabled(),
                readyForEvalGate,
                checks);
    }

    private static GateCheck check(
            String id, boolean passed, String detail, Double actual, Double target) {
        return check(id, passed, detail, actual, target, true, true);
    }

    private static GateCheck check(
            String id,
            boolean passed,
            String detail,
            Double actual,
            Double target,
            boolean higherIsBetter,
            boolean blockEval) {
        return new GateCheck(id, passed, detail, actual, target, higherIsBetter, blockEval);
    }

    private static double coverage(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0.0;
        }
        return (double) numerator / (double) denominator;
    }

    private static String percent(double value) {
        return String.format("%.1f%%", value * 100.0);
    }

    public record DerivationReadinessReport(
            String corpusName,
            Instant checkedAt,
            boolean enrichmentEnabled,
            boolean aggregationEnabled,
            boolean readyForEvalGate,
            List<GateCheck> checks) {}

    public record GateCheck(
            String id,
            boolean passed,
            String detail,
            Double actual,
            Double target,
            boolean higherIsBetter,
            boolean blockEval) {
        GateCheck(String id, boolean passed, String detail, Double actual, Double target) {
            this(id, passed, detail, actual, target, true, true);
        }
    }
}
